package com.example.attendify.repository;

import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.Student;
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
 * Fetches student roster and per-student history from Firestore.
 *
 * Used by: AttendanceFragment (teacher), StudentHomeFragment (student).
 *
 * Firestore: queries users collection where role == "student"
 * and optionally filters by section.
 */
public class StudentRepository {

    private static StudentRepository instance;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private StudentRepository() {}

    public static StudentRepository getInstance() {
        if (instance == null) instance = new StudentRepository();
        return instance;
    }

    // ── Callback ──────────────────────────────────────────────────────────────

    public interface StudentsCallback {
        void onSuccess(List<Student> students);
        void onFailure(String errorMessage);
    }

    public interface HistoryCallback {
        void onSuccess(List<AttendanceRecord> records);
        void onFailure(String errorMessage);
    }

    // ── Full class roster filtered by section (used by teacher) ───────────────

    public void getStudentsBySection(String section, StudentsCallback callback) {
        db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Student> list = new ArrayList<>();
                    int i = 1;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String firstname = doc.getString("firstname");
                        String lastname  = doc.getString("lastname");
                        String fullName  = (lastname != null ? lastname : "")
                                + ", "
                                + (firstname != null ? firstname : "");

                        // Default status is Absent until teacher marks them
                        Student student = new Student(i++, fullName,
                                Student.STATUS_ABSENT, "--:--");
                        student.setStudentId(doc.getId());
                        student.setSchoolId(doc.getString("studentID"));
                        list.add(student);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── All students (no section filter) ─────────────────────────────────────

    public void getAllStudents(StudentsCallback callback) {
        db.collection("users")
                .whereEqualTo("role", "student")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Student> list = new ArrayList<>();
                    int i = 1;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String firstname = doc.getString("firstname");
                        String lastname  = doc.getString("lastname");
                        String fullName  = (lastname != null ? lastname : "")
                                + ", "
                                + (firstname != null ? firstname : "");

                        Student student = new Student(i++, fullName,
                                Student.STATUS_ABSENT, "--:--");
                        student.setStudentId(doc.getId());
                        student.setSchoolId(doc.getString("studentID"));
                        list.add(student);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Per-student attendance history (used by StudentHomeFragment) ──────────

    public void getStudentHistory(String studentId, HistoryCallback callback) {
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
                    Collections.sort(list, (a, b) -> b.getDate().compareTo(a.getDate()));
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
                return db.compareTo(da);
            } catch (ParseException e) {
                return 0;
            }
        });
    }
}