package com.example.attendify.repository;

import com.example.attendify.models.AttendanceRecord;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

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
                .orderBy("date", Query.Direction.DESCENDING)
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
                .orderBy("date", Query.Direction.DESCENDING)
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
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}