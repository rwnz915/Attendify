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
 * Single flat collection: subjects/{autoId}
 *   Fields: name, section, teacherId, teacher, schedule, color
 *
 * Teacher: query subjects where teacherId == uid
 * Student: query subjects where section  == studentSection
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
        public String teacherId;
        public String schedule;
        public String color;

        public SubjectItem() {}
    }

    // ── Teacher: query flat subjects collection by teacherId ──────────────────

    public void getTeacherSubjects(String teacherUid, SubjectsCallback callback) {
        db.collection("subjects")
                .whereEqualTo("teacherId", teacherUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<SubjectItem> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        SubjectItem item = new SubjectItem();
                        item.id        = doc.getId();
                        item.name      = doc.getString("name");
                        item.section   = doc.getString("section");
                        item.teacher   = doc.getString("teacher");
                        item.teacherId = doc.getString("teacherId");
                        item.schedule  = doc.getString("schedule");
                        item.color     = doc.getString("color");
                        list.add(item);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Student: query flat subjects collection by section ────────────────────

    public void getStudentSubjects(String section, SubjectsCallback callback) {
        db.collection("subjects")
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<SubjectItem> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        SubjectItem item = new SubjectItem();
                        item.id        = doc.getId();
                        item.name      = doc.getString("name");
                        item.section   = doc.getString("section");
                        item.teacher   = doc.getString("teacher");
                        item.teacherId = doc.getString("teacherId");
                        item.schedule  = doc.getString("schedule");
                        item.color     = doc.getString("color");
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