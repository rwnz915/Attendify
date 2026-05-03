package com.example.attendify.repository;

import android.content.Context;
import android.util.Log;

import com.example.attendify.LocalCacheManager;
import com.example.attendify.models.AttendanceRecord;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches subject data from Firestore.
 *
 * OFFLINE SUPPORT
 * ───────────────
 * Every successful Firestore fetch is cached in LocalCacheManager.
 * When offline, the cached list is returned instead.
 *
 * Single flat collection: subjects/{autoId}
 *   Fields: name, section, teacherId, teacher, schedule, color
 */
public class SubjectRepository {

    private static final String TAG = "SubjectRepository";

    private static SubjectRepository instance;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private Context appContext;

    private SubjectRepository() {}

    public static SubjectRepository getInstance() {
        if (instance == null) instance = new SubjectRepository();
        return instance;
    }

    public void init(Context ctx) {
        this.appContext = ctx.getApplicationContext();
    }

    // ── Callback ──────────────────────────────────────────────────────────────

    public interface SubjectsCallback {
        void onSuccess(List<SubjectItem> subjects);
        void onFailure(String errorMessage);
    }

    // ── Subject model ─────────────────────────────────────────────────────────

    public static class SubjectItem {
        public String id;
        public String name;
        public String section;
        public String teacher;
        public String teacherId;
        public String schedule;
        public String color;

        public SubjectItem() {}
    }

    // ── Teacher: query flat subjects collection by teacherId ──────────────────

    public void getTeacherSubjects(String teacherUid, SubjectsCallback callback) {
        String uid = teacherUid;

        // Offline fast-path
        if (isOffline()) {
            List<SubjectItem> cached = loadSubjectsFromCache(uid);
            if (cached != null) {
                callback.onSuccess(cached);
            } else {
                callback.onFailure("No cached subjects. Please connect to the internet.");
            }
            return;
        }

        db.collection("subjects")
                .whereEqualTo("teacherId", teacherUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<SubjectItem> list = new ArrayList<>();
                    List<Map<String, Object>> raw = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        SubjectItem item = docToItem(doc);
                        list.add(item);
                        raw.add(itemToMap(item));
                    }

                    saveSubjectsToCache(uid, raw);
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getTeacherSubjects failed — trying cache", e);
                    List<SubjectItem> cached = loadSubjectsFromCache(uid);
                    if (cached != null) {
                        callback.onSuccess(cached);
                    } else {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    // ── Student: query flat subjects collection by section ────────────────────

    public void getStudentSubjects(String section, SubjectsCallback callback) {
        // We use the logged-in uid from AuthRepository as the cache key
        String uid = getCachedUid();

        // Offline fast-path
        if (isOffline()) {
            List<SubjectItem> cached = loadSubjectsFromCache(uid);
            if (cached != null) {
                callback.onSuccess(cached);
            } else {
                callback.onFailure("No cached subjects. Please connect to the internet.");
            }
            return;
        }

        db.collection("subjects")
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<SubjectItem> list = new ArrayList<>();
                    List<Map<String, Object>> raw = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        SubjectItem item = docToItem(doc);
                        list.add(item);
                        raw.add(itemToMap(item));
                    }

                    saveSubjectsToCache(uid, raw);
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getStudentSubjects failed — trying cache", e);
                    List<SubjectItem> cached = loadSubjectsFromCache(uid);
                    if (cached != null) {
                        callback.onSuccess(cached);
                    } else {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    // ── Attendance history for a student in a subject ─────────────────────────

    public interface AttendanceCallback {
        void onSuccess(List<AttendanceRecord> records);
        void onFailure(String errorMessage);
    }

    public void getSubjectHistory(String studentUid, String subjectId,
                                  AttendanceCallback callback) {
        // Offline fast-path
        if (isOffline()) {
            List<AttendanceRecord> cached = loadSubjectHistoryFromCache(studentUid, subjectId);
            if (cached != null) {
                callback.onSuccess(cached);
            } else {
                callback.onFailure("No cached history. Please connect to the internet.");
            }
            return;
        }

        db.collection("attendance")
                .whereEqualTo("studentId", studentUid)
                .whereEqualTo("subjectId", subjectId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<AttendanceRecord> list = new ArrayList<>();
                    List<Map<String, Object>> raw = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        AttendanceRecord record = new AttendanceRecord(
                                doc.getString("date"),
                                doc.getString("subjectName"),
                                doc.getString("time"),
                                doc.getString("status")
                        );
                        list.add(record);
                        raw.add(recordToMap(doc));
                    }

                    saveSubjectHistoryToCache(studentUid, subjectId, raw);
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getSubjectHistory failed — trying cache", e);
                    List<AttendanceRecord> cached =
                            loadSubjectHistoryFromCache(studentUid, subjectId);
                    if (cached != null) {
                        callback.onSuccess(cached);
                    } else {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    // ── Cache helpers ──────────────────────────────────────────────────────────

    private void saveSubjectsToCache(String uid, List<Map<String, Object>> raw) {
        if (appContext == null || uid == null) return;
        LocalCacheManager.getInstance(appContext).saveSubjects(uid, raw);
    }

    private List<SubjectItem> loadSubjectsFromCache(String uid) {
        if (appContext == null || uid == null) return null;
        List<Map<String, Object>> raw =
                LocalCacheManager.getInstance(appContext).getSubjects(uid);
        if (raw == null) return null;
        List<SubjectItem> list = new ArrayList<>();
        for (Map<String, Object> m : raw) {
            list.add(mapToItem(m));
        }
        return list;
    }

    private void saveSubjectHistoryToCache(String uid, String subjectId,
                                           List<Map<String, Object>> raw) {
        if (appContext == null || uid == null) return;
        LocalCacheManager.getInstance(appContext).saveSubjectHistory(uid, subjectId, raw);
    }

    private List<AttendanceRecord> loadSubjectHistoryFromCache(String uid, String subjectId) {
        if (appContext == null || uid == null) return null;
        List<Map<String, Object>> raw =
                LocalCacheManager.getInstance(appContext).getSubjectHistory(uid, subjectId);
        if (raw == null) return null;
        List<AttendanceRecord> list = new ArrayList<>();
        for (Map<String, Object> m : raw) {
            list.add(new AttendanceRecord(
                    str(m, "date"), str(m, "subjectName"),
                    str(m, "time"), str(m, "status")));
        }
        return list;
    }

    // ── Converters ────────────────────────────────────────────────────────────

    private SubjectItem docToItem(DocumentSnapshot doc) {
        SubjectItem item = new SubjectItem();
        item.id        = doc.getId();
        item.name      = doc.getString("name");
        item.section   = doc.getString("section");
        item.teacher   = doc.getString("teacher");
        item.teacherId = doc.getString("teacherId");
        item.schedule  = doc.getString("schedule");
        item.color     = doc.getString("color");
        return item;
    }

    private Map<String, Object> itemToMap(SubjectItem item) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",        item.id);
        m.put("name",      item.name);
        m.put("section",   item.section);
        m.put("teacher",   item.teacher);
        m.put("teacherId", item.teacherId);
        m.put("schedule",  item.schedule);
        m.put("color",     item.color);
        return m;
    }

    private SubjectItem mapToItem(Map<String, Object> m) {
        SubjectItem item = new SubjectItem();
        item.id        = str(m, "id");
        item.name      = str(m, "name");
        item.section   = str(m, "section");
        item.teacher   = str(m, "teacher");
        item.teacherId = str(m, "teacherId");
        item.schedule  = str(m, "schedule");
        item.color     = str(m, "color");
        return item;
    }

    private Map<String, Object> recordToMap(DocumentSnapshot doc) {
        Map<String, Object> m = new HashMap<>();
        m.put("date",        doc.getString("date"));
        m.put("subjectName", doc.getString("subjectName"));
        m.put("time",        doc.getString("time"));
        m.put("status",      doc.getString("status"));
        return m;
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
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