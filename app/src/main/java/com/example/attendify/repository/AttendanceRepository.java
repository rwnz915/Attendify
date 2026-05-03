package com.example.attendify.repository;

import android.content.Context;
import android.util.Log;

import com.example.attendify.LocalCacheManager;
import com.example.attendify.models.AttendanceRecord;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fetches class-wide attendance records from Firestore.
 *
 * OFFLINE SUPPORT
 * ───────────────
 * Every successful Firestore fetch is cached in LocalCacheManager.
 * When offline, the cached copy is returned instead.
 *
 * Firestore collection: "attendance"
 * Fields per document:
 *   studentId   (string) — Firebase Auth UID
 *   studentName (string) — full name
 *   subjectId   (string)
 *   subjectName (string)
 *   date        (string) — e.g. "Apr 11, 2026"
 *   time        (string) — e.g. "07:55 AM" or "--:--"
 *   status      (string) — "Present" | "Late" | "Absent"
 */
public class AttendanceRepository {

    private static final String TAG = "AttendanceRepository";

    private static AttendanceRepository instance;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private Context appContext;

    private AttendanceRepository() {}

    public static AttendanceRepository getInstance() {
        if (instance == null) instance = new AttendanceRepository();
        return instance;
    }

    public void init(Context ctx) {
        this.appContext = ctx.getApplicationContext();
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public interface AttendanceCallback {
        void onSuccess(List<AttendanceRecord> records);
        void onFailure(String errorMessage);
    }

    public interface SummaryCallback {
        void onSuccess(AttendanceRecord summary);
        void onFailure(String errorMessage);
    }

    public interface SubjectSummariesCallback {
        void onSuccess(List<SubjectSummary> summaries);
        void onFailure(String errorMessage);
    }

    public interface SubmitCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    // ── Past session history (used by HistoryFragment) ────────────────────────

    public void getHistory(String subjectId, AttendanceCallback callback) {
        String uid = getCachedUid();

        if (isOffline()) {
            List<AttendanceRecord> cached = loadHistoryFromCache(uid, subjectId);
            if (cached != null) {
                callback.onSuccess(cached);
            } else {
                callback.onFailure("No cached history. Please connect to the internet.");
            }
            return;
        }

        db.collection("attendance")
                .whereEqualTo("subjectId", subjectId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<AttendanceRecord> list = new ArrayList<>();
                    List<Map<String, Object>> raw = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        AttendanceRecord record = docToRecord(doc);
                        list.add(record);
                        raw.add(recordToMap(doc));
                    }

                    sortByDateDescending(list);
                    saveHistoryToCache(uid, subjectId, raw);
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getHistory failed — trying cache", e);
                    List<AttendanceRecord> cached = loadHistoryFromCache(uid, subjectId);
                    if (cached != null) {
                        callback.onSuccess(cached);
                    } else {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    public void getHistoryForSubjects(List<String> subjectIds, AttendanceCallback callback) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        // Firestore whereIn limit is 10 (or 30 in newer versions). 
        // For simplicity, we fetch all if more than 10, or split. 
        // But usually a teacher has few subjects.
        db.collection("attendance")
                .whereIn("subjectId", subjectIds)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<AttendanceRecord> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        list.add(docToRecord(doc));
                    }
                    sortByDateDescending(list);
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Today's summary for a subject ─────────────────────────────────────────

    public void getTodayAttendance(String subjectId, String date,
                                   SummaryCallback callback) {
        String uid = getCachedUid();

        if (isOffline()) {
            AttendanceRecord cached = loadTodaySummaryFromCache(uid, subjectId, date);
            if (cached != null) {
                callback.onSuccess(cached);
            } else {
                callback.onFailure("No cached summary. Please connect to the internet.");
            }
            return;
        }

        db.collection("attendance")
                .whereEqualTo("subjectId", subjectId)
                .whereEqualTo("date", date)
                .get()
                .addOnSuccessListener(snapshot -> {
                    int present = 0, absent = 0, late = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String status = doc.getString("status");
                        if ("Present".equals(status)) present++;
                        else if ("Absent".equals(status)) absent++;
                        else if ("Late".equals(status))   late++;
                    }
                    AttendanceRecord summary = new AttendanceRecord(date, present, absent, late);

                    // Cache summary
                    Map<String, Object> m = new HashMap<>();
                    m.put("date", date);
                    m.put("present", (double) present);
                    m.put("absent",  (double) absent);
                    m.put("late",    (double) late);
                    if (uid != null && appContext != null) {
                        LocalCacheManager.getInstance(appContext)
                                .saveTodaySummary(uid, subjectId, date, m);
                    }

                    callback.onSuccess(summary);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getTodayAttendance failed — trying cache", e);
                    AttendanceRecord cached = loadTodaySummaryFromCache(uid, subjectId, date);
                    if (cached != null) {
                        callback.onSuccess(cached);
                    } else {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    // ── Today summaries across multiple subjects ──────────────────────────────

    public void getTodaySubjectSummaries(
            List<SubjectRepository.SubjectItem> subjects,
            String date,
            SubjectSummariesCallback callback) {

        if (subjects == null || subjects.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        String uid = getCachedUid();

        if (isOffline()) {
            List<Map<String, Object>> cachedRaw =
                    appContext != null && uid != null
                            ? LocalCacheManager.getInstance(appContext).getTodaySummaries(uid, date)
                            : null;

            if (cachedRaw != null) {
                callback.onSuccess(mapsToSummaries(cachedRaw));
            } else {
                callback.onFailure("No cached summaries. Please connect to the internet.");
            }
            return;
        }

        List<SubjectSummary> results = new ArrayList<>();
        List<Map<String, Object>> rawResults = new ArrayList<>();
        int[] remaining = {subjects.size()};

        for (SubjectRepository.SubjectItem subj : subjects) {
            db.collection("attendance")
                    .whereEqualTo("subjectId", subj.id)
                    .whereEqualTo("date", date)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        int present = 0, absent = 0, late = 0;
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String status = doc.getString("status");
                            if ("Present".equals(status)) present++;
                            else if ("Absent".equals(status)) absent++;
                            else if ("Late".equals(status))   late++;
                        }
                        SubjectSummary s = new SubjectSummary();
                        s.subjectId   = subj.id;
                        s.subjectName = subj.name;
                        s.section     = subj.section;
                        s.schedule    = subj.schedule;
                        s.date        = date;
                        s.present     = present;
                        s.absent      = absent;
                        s.late        = late;
                        s.total       = present + absent + late;

                        synchronized (results) {
                            results.add(s);
                            rawResults.add(summaryToMap(s));
                            remaining[0]--;
                            if (remaining[0] == 0) {
                                // Cache the batch result
                                if (appContext != null && uid != null) {
                                    LocalCacheManager.getInstance(appContext)
                                            .saveTodaySummaries(uid, date, rawResults);
                                }
                                callback.onSuccess(results);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        synchronized (results) {
                            remaining[0]--;
                            if (remaining[0] == 0) {
                                callback.onSuccess(results);
                            }
                        }
                    });
        }
    }

    public static class SubjectSummary {
        public String subjectId;
        public String subjectName;
        public String section;
        public String schedule;
        public String date;
        public int    present;
        public int    absent;
        public int    late;
        public int    total;
    }

    // ── Record a single student's attendance ──────────────────────────────────

    public void recordAttendance(String studentId, String studentName,
                                 String subjectId, String subjectName,
                                 String date, String time, String status,
                                 SubmitCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("studentId",   studentId);
        data.put("studentName", studentName);
        data.put("subjectId",   subjectId);
        data.put("subjectName", subjectName);
        data.put("date",        date);
        data.put("time",        time);
        data.put("status",      status);
        data.put("recordedAt",
                com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("attendance").add(data)
                .addOnSuccessListener(ref -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Student's own attendance history ──────────────────────────────────────

    public void getStudentHistory(String studentId, AttendanceCallback callback) {
        String uid = studentId; // student's own uid is the cache key here

        if (isOffline()) {
            List<AttendanceRecord> cached = loadStudentHistoryFromCache(uid);
            if (cached != null) {
                callback.onSuccess(cached);
            } else {
                callback.onFailure("No cached history. Please connect to the internet.");
            }
            return;
        }

        db.collection("attendance")
                .whereEqualTo("studentId", studentId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<AttendanceRecord> list = new ArrayList<>();
                    List<Map<String, Object>> raw = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        AttendanceRecord record = docToRecord(doc);
                        list.add(record);
                        raw.add(recordToMap(doc));
                    }

                    sortByDateDescending(list);
                    saveStudentHistoryToCache(uid, raw);
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getStudentHistory failed — trying cache", e);
                    List<AttendanceRecord> cached = loadStudentHistoryFromCache(uid);
                    if (cached != null) {
                        callback.onSuccess(cached);
                    } else {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    // ── Cache helpers ──────────────────────────────────────────────────────────

    private void saveHistoryToCache(String uid, String subjectId,
                                    List<Map<String, Object>> raw) {
        if (appContext == null || uid == null) return;
        LocalCacheManager.getInstance(appContext)
                .saveAttendanceHistory(uid, subjectId, raw);
    }

    private List<AttendanceRecord> loadHistoryFromCache(String uid, String subjectId) {
        if (appContext == null || uid == null) return null;
        List<Map<String, Object>> raw =
                LocalCacheManager.getInstance(appContext)
                        .getAttendanceHistory(uid, subjectId);
        return raw != null ? mapsToRecords(raw) : null;
    }

    private AttendanceRecord loadTodaySummaryFromCache(String uid, String subjectId,
                                                       String date) {
        if (appContext == null || uid == null) return null;
        Map<String, Object> m = LocalCacheManager.getInstance(appContext)
                .getTodaySummary(uid, subjectId, date);
        if (m == null) return null;
        int present = ((Number) m.getOrDefault("present", 0d)).intValue();
        int absent  = ((Number) m.getOrDefault("absent",  0d)).intValue();
        int late    = ((Number) m.getOrDefault("late",    0d)).intValue();
        return new AttendanceRecord(date, present, absent, late);
    }

    private void saveStudentHistoryToCache(String uid, List<Map<String, Object>> raw) {
        if (appContext == null || uid == null) return;
        LocalCacheManager.getInstance(appContext).saveStudentHistory(uid, raw);
    }

    private List<AttendanceRecord> loadStudentHistoryFromCache(String uid) {
        if (appContext == null || uid == null) return null;
        List<Map<String, Object>> raw =
                LocalCacheManager.getInstance(appContext).getStudentHistory(uid);
        return raw != null ? mapsToRecords(raw) : null;
    }

    // ── Converters ────────────────────────────────────────────────────────────

    private AttendanceRecord docToRecord(DocumentSnapshot doc) {
        return new AttendanceRecord(
                doc.getString("date"),
                doc.getString("subjectName"),
                doc.getString("subjectId"),
                doc.getString("time"),
                doc.getString("status"),
                doc.getString("studentId"),
                doc.getString("studentName"));
    }

    private Map<String, Object> recordToMap(DocumentSnapshot doc) {
        Map<String, Object> m = new HashMap<>();
        m.put("date",        doc.getString("date"));
        m.put("subjectName", doc.getString("subjectName"));
        m.put("subjectId",   doc.getString("subjectId"));
        m.put("time",        doc.getString("time"));
        m.put("status",      doc.getString("status"));
        m.put("studentId",   doc.getString("studentId"));
        m.put("studentName", doc.getString("studentName"));
        return m;
    }

    private List<AttendanceRecord> mapsToRecords(List<Map<String, Object>> raw) {
        List<AttendanceRecord> list = new ArrayList<>();
        for (Map<String, Object> m : raw) {
            list.add(new AttendanceRecord(
                    str(m, "date"), str(m, "subjectName"), str(m, "subjectId"),
                    str(m, "time"), str(m, "status"),
                    str(m, "studentId"), str(m, "studentName")));
        }
        return list;
    }

    private Map<String, Object> summaryToMap(SubjectSummary s) {
        Map<String, Object> m = new HashMap<>();
        m.put("subjectId",   s.subjectId);
        m.put("subjectName", s.subjectName);
        m.put("section",     s.section);
        m.put("schedule",    s.schedule);
        m.put("date",        s.date);
        m.put("present",     (double) s.present);
        m.put("absent",      (double) s.absent);
        m.put("late",        (double) s.late);
        m.put("total",       (double) s.total);
        return m;
    }

    private List<SubjectSummary> mapsToSummaries(List<Map<String, Object>> raw) {
        List<SubjectSummary> list = new ArrayList<>();
        for (Map<String, Object> m : raw) {
            SubjectSummary s = new SubjectSummary();
            s.subjectId   = str(m, "subjectId");
            s.subjectName = str(m, "subjectName");
            s.section     = str(m, "section");
            s.schedule    = str(m, "schedule");
            s.date        = str(m, "date");
            s.present     = num(m, "present");
            s.absent      = num(m, "absent");
            s.late        = num(m, "late");
            s.total       = num(m, "total");
            list.add(s);
        }
        return list;
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private int num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    // ── Sort helper ───────────────────────────────────────────────────────────

    private void sortByDateDescending(List<AttendanceRecord> list) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        Collections.sort(list, (a, b) -> {
            try {
                Date da = sdf.parse(a.getDate() != null ? a.getDate() : "");
                Date db = sdf.parse(b.getDate() != null ? b.getDate() : "");
                if (da == null || db == null) return 0;
                return db.compareTo(da);
            } catch (ParseException e) {
                return 0;
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isOffline() {
        return appContext != null && !LocalCacheManager.isOnline(appContext);
    }

    private String getCachedUid() {
        if (appContext == null) return null;
        return LocalCacheManager.getInstance(appContext).getCachedUid();
    }
}