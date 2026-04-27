package com.example.attendify.repository;

import com.example.attendify.models.AttendanceRecord;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fetches class-wide attendance records from Firestore.
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

    private static AttendanceRepository instance;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private AttendanceRepository() {}

    public static AttendanceRepository getInstance() {
        if (instance == null) instance = new AttendanceRepository();
        return instance;
    }

    // ── Callback ──────────────────────────────────────────────────────────────

    public interface AttendanceCallback {
        void onSuccess(List<AttendanceRecord> records);
        void onFailure(String errorMessage);
    }

    public interface SummaryCallback {
        void onSuccess(AttendanceRecord summary);
        void onFailure(String errorMessage);
    }

    // ── Past session history (used by HistoryFragment) ────────────────────────

    public void getHistory(String subjectId, AttendanceCallback callback) {
        db.collection("attendance")
                .whereEqualTo("subjectId", subjectId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<AttendanceRecord> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        AttendanceRecord record = new AttendanceRecord(
                                doc.getString("date"),
                                doc.getString("subjectName"),
                                doc.getString("time"),
                                doc.getString("status")
                        );
                        list.add(record);
                    }
                    sortByDateDescending(list);
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Today's summary for a subject (present/absent/late counts) ────────────

    public void getTodayAttendance(String subjectId, String date, SummaryCallback callback) {
        db.collection("attendance")
                .whereEqualTo("subjectId", subjectId)
                .whereEqualTo("date", date)
                .get()
                .addOnSuccessListener(snapshot -> {
                    int present = 0, absent = 0, late = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String status = doc.getString("status");
                        if ("Present".equals(status)) present++;
                        else if ("Absent".equals(status))  absent++;
                        else if ("Late".equals(status))    late++;
                    }
                    callback.onSuccess(new AttendanceRecord(date, present, absent, late));
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Today's summary across multiple subjects (Home page) ─────────────────

    /**
     * For each subjectId in the list, query today's attendance and aggregate totals.
     * The callback fires once all queries complete (or immediately on any failure).
     */
    public void getTodaySubjectSummaries(
            List<com.example.attendify.repository.SubjectRepository.SubjectItem> subjects,
            String date,
            SubjectSummariesCallback callback) {

        if (subjects == null || subjects.isEmpty()) {
            callback.onSuccess(new java.util.ArrayList<>());
            return;
        }

        List<SubjectSummary> results = new java.util.ArrayList<>();
        int[] remaining = {subjects.size()};

        for (com.example.attendify.repository.SubjectRepository.SubjectItem subj : subjects) {
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
                            remaining[0]--;
                            if (remaining[0] == 0) callback.onSuccess(results);
                        }
                    })
                    .addOnFailureListener(e -> {
                        synchronized (results) {
                            remaining[0]--;
                            if (remaining[0] == 0) callback.onSuccess(results);
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

    public interface SubjectSummariesCallback {
        void onSuccess(List<SubjectSummary> summaries);
        void onFailure(String errorMessage);
    }

    // ── Record a single student's attendance ──────────────────────────────────

    public interface SubmitCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public void recordAttendance(String studentId, String studentName,
                                 String subjectId, String subjectName,
                                 String date, String time, String status,
                                 SubmitCallback callback) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("studentId",   studentId);
        data.put("studentName", studentName);
        data.put("subjectId",   subjectId);
        data.put("subjectName", subjectName);
        data.put("date",        date);
        data.put("time",        time);
        data.put("status",      status);
        data.put("recordedAt",  com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("attendance").add(data)
                .addOnSuccessListener(ref -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Student's own attendance history ──────────────────────────────────────

    public void getStudentHistory(String studentId, AttendanceCallback callback) {
        db.collection("attendance")
                .whereEqualTo("studentId", studentId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<AttendanceRecord> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        AttendanceRecord record = new AttendanceRecord(
                                doc.getString("date"),
                                doc.getString("subjectName"),
                                doc.getString("time"),
                                doc.getString("status")
                        );
                        list.add(record);
                    }
                    sortByDateDescending(list);
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Helper: sort records newest-first ─────────────────────────────────────

    private void sortByDateDescending(List<AttendanceRecord> list) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        Collections.sort(list, (a, b) -> {
            try {
                Date da = sdf.parse(a.getDate() != null ? a.getDate() : "");
                Date db = sdf.parse(b.getDate() != null ? b.getDate() : "");
                if (da == null || db == null) return 0;
                return db.compareTo(da); // descending
            } catch (ParseException e) {
                return 0;
            }
        });
    }
}