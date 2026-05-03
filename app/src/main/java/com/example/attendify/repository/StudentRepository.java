package com.example.attendify.repository;

import android.content.Context;
import android.util.Log;

import com.example.attendify.LocalCacheManager;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.Student;
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
 * Fetches student roster and per-student history from Firestore.
 *
 * OFFLINE SUPPORT
 * ───────────────
 * Every successful Firestore fetch is cached in LocalCacheManager.
 * When offline, the cached copy is returned instead.
 *
 * Used by: AttendanceFragment (teacher), StudentHomeFragment (student).
 */
public class StudentRepository {

    private static final String TAG = "StudentRepository";

    private static StudentRepository instance;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private Context appContext;

    private StudentRepository() {}

    public static StudentRepository getInstance() {
        if (instance == null) instance = new StudentRepository();
        return instance;
    }

    public void init(Context ctx) {
        this.appContext = ctx.getApplicationContext();
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

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
        String uid = getCachedUid();

        if (isOffline()) {
            List<Student> cached = loadStudentsBySectionFromCache(uid, section);
            if (cached != null) {
                callback.onSuccess(cached);
            } else {
                callback.onFailure("No cached student list. Please connect to the internet.");
            }
            return;
        }

        db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Student> list = new ArrayList<>();
                    List<Map<String, Object>> raw = new ArrayList<>();
                    int i = 1;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Student student = docToStudent(doc, i++);
                        list.add(student);
                        raw.add(studentToMap(student, doc));
                    }
                    saveStudentsBySectionToCache(uid, section, raw);
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getStudentsBySection failed — trying cache", e);
                    List<Student> cached = loadStudentsBySectionFromCache(uid, section);
                    if (cached != null) {
                        callback.onSuccess(cached);
                    } else {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    // ── All students (no section filter) ─────────────────────────────────────

    public void getAllStudents(StudentsCallback callback) {
        String uid = getCachedUid();

        if (isOffline()) {
            List<Student> cached = loadAllStudentsFromCache(uid);
            if (cached != null) {
                callback.onSuccess(cached);
            } else {
                callback.onFailure("No cached student list. Please connect to the internet.");
            }
            return;
        }

        db.collection("users")
                .whereEqualTo("role", "student")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Student> list = new ArrayList<>();
                    List<Map<String, Object>> raw = new ArrayList<>();
                    int i = 1;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Student student = docToStudent(doc, i++);
                        list.add(student);
                        raw.add(studentToMap(student, doc));
                    }
                    saveAllStudentsToCache(uid, raw);
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getAllStudents failed — trying cache", e);
                    List<Student> cached = loadAllStudentsFromCache(uid);
                    if (cached != null) {
                        callback.onSuccess(cached);
                    } else {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    // ── Per-student attendance history (used by StudentHomeFragment) ──────────

    public void getStudentHistory(String studentId, HistoryCallback callback) {
        if (isOffline()) {
            List<AttendanceRecord> cached = loadStudentHistoryFromCache(studentId);
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
                        AttendanceRecord record = new AttendanceRecord(
                                doc.getString("date"),
                                doc.getString("subjectName"),
                                doc.getString("time"),
                                doc.getString("status")
                        );
                        list.add(record);
                        Map<String, Object> m = new HashMap<>();
                        m.put("date",        doc.getString("date"));
                        m.put("subjectName", doc.getString("subjectName"));
                        m.put("time",        doc.getString("time"));
                        m.put("status",      doc.getString("status"));
                        raw.add(m);
                    }
                    Collections.sort(list, (a, b) -> b.getDate().compareTo(a.getDate()));
                    saveStudentHistoryToCache(studentId, raw);
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getStudentHistory failed — trying cache", e);
                    List<AttendanceRecord> cached = loadStudentHistoryFromCache(studentId);
                    if (cached != null) {
                        callback.onSuccess(cached);
                    } else {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    // ── Cache helpers ──────────────────────────────────────────────────────────

    private void saveStudentsBySectionToCache(String uid, String section,
                                              List<Map<String, Object>> raw) {
        if (appContext == null || uid == null) return;
        LocalCacheManager.getInstance(appContext).saveStudentsBySection(uid, section, raw);
    }

    private List<Student> loadStudentsBySectionFromCache(String uid, String section) {
        if (appContext == null || uid == null) return null;
        List<Map<String, Object>> raw =
                LocalCacheManager.getInstance(appContext).getStudentsBySection(uid, section);
        return raw != null ? mapsToStudents(raw) : null;
    }

    private void saveAllStudentsToCache(String uid, List<Map<String, Object>> raw) {
        if (appContext == null || uid == null) return;
        LocalCacheManager.getInstance(appContext).saveAllStudents(uid, raw);
    }

    private List<Student> loadAllStudentsFromCache(String uid) {
        if (appContext == null || uid == null) return null;
        List<Map<String, Object>> raw =
                LocalCacheManager.getInstance(appContext).getAllStudents(uid);
        return raw != null ? mapsToStudents(raw) : null;
    }

    private void saveStudentHistoryToCache(String studentId,
                                           List<Map<String, Object>> raw) {
        if (appContext == null) return;
        LocalCacheManager.getInstance(appContext).saveStudentHistory(studentId, raw);
    }

    private List<AttendanceRecord> loadStudentHistoryFromCache(String studentId) {
        if (appContext == null) return null;
        List<Map<String, Object>> raw =
                LocalCacheManager.getInstance(appContext).getStudentHistory(studentId);
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

    private Student docToStudent(DocumentSnapshot doc, int num) {
        String firstname = doc.getString("firstname");
        String lastname  = doc.getString("lastname");
        String fullName  = (lastname != null ? lastname : "")
                + ", "
                + (firstname != null ? firstname : "");
        Student student = new Student(num, fullName, Student.STATUS_ABSENT, "--:--");
        student.setStudentId(doc.getId());
        student.setSchoolId(doc.getString("studentID"));
        return student;
    }

    private Map<String, Object> studentToMap(Student student, DocumentSnapshot doc) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",        student.getId());
        m.put("name",      student.getName());
        m.put("studentId", student.getStudentId());
        m.put("schoolId",  student.getSchoolId());
        m.put("status",    student.getStatus());
        m.put("time",      student.getTime());
        return m;
    }

    private List<Student> mapsToStudents(List<Map<String, Object>> raw) {
        List<Student> list = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> m : raw) {
            int id = m.containsKey("id") ? ((Number) m.get("id")).intValue() : ++i;
            Student s = new Student(id, str(m, "name"),
                    Student.STATUS_ABSENT, "--:--");
            s.setStudentId(str(m, "studentId"));
            s.setSchoolId(str(m, "schoolId"));
            list.add(s);
        }
        return list;
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

    // ── Legacy sort helper ────────────────────────────────────────────────────

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