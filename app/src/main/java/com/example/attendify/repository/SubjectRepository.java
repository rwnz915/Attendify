package com.example.attendify.repository;

import com.example.attendify.models.AttendanceRecord;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches subject data from Firestore.
 *
 * Teacher:  reads their subjects subcollection → users/{uid}/subjects
 * Student:  queries the top-level subjects collection filtered by section
 */
public class SubjectRepository {

    private static SubjectRepository instance;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private SubjectRepository() {}

    public static SubjectRepository getInstance() {
        if (instance == null) instance = new SubjectRepository();
        return instance;
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
        public String schedule;
        public String color;

        public SubjectItem() {}
    }

    // ── Teacher: fetch from users/{uid}/subjects subcollection ────────────────

    public void getTeacherSubjects(String teacherUid, SubjectsCallback callback) {
        db.collection("users")
                .document(teacherUid)
                .collection("subjects")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<SubjectItem> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        SubjectItem item = new SubjectItem();
                        item.id       = doc.getId();
                        item.name     = doc.getString("name");
                        item.section  = doc.getString("section");
                        item.teacher  = doc.getString("teacher");
                        item.schedule = doc.getString("schedule");
                        item.color    = doc.getString("color");
                        list.add(item);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Student: fetch from top-level subjects collection filtered by section ─

    public void getStudentSubjects(String section, SubjectsCallback callback) {
        db.collection("subjects")
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<SubjectItem> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        SubjectItem item = new SubjectItem();
                        item.id       = doc.getId();
                        item.name     = doc.getString("name");
                        item.section  = doc.getString("section");
                        item.teacher  = doc.getString("teacher");
                        item.schedule = doc.getString("schedule");
                        item.color    = doc.getString("color");
                        list.add(item);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Attendance history for a student in a subject ─────────────────────────

    public interface AttendanceCallback {
        void onSuccess(List<AttendanceRecord> records);
        void onFailure(String errorMessage);
    }

    public void getSubjectHistory(String studentUid, String subjectId, AttendanceCallback callback) {
        db.collection("attendance")
                .whereEqualTo("studentId", studentUid)
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
}