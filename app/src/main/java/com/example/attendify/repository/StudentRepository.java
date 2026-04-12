package com.example.attendify.repository;

import com.example.attendify.models.MockStudentData;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.Student;

import java.util.List;

/**
 * Access point for student roster and per-student history.
 *
 * Used by: AttendanceFragment (teacher), StudentHomeFragment (student).
 *
 * HOW TO SWAP IN REAL DATA:
 *   Replace each method body with the appropriate API/DB call.
 *   The return types and signatures stay the same.
 */
public class StudentRepository {

    private static StudentRepository instance;

    private StudentRepository() {}

    public static StudentRepository getInstance() {
        if (instance == null) instance = new StudentRepository();
        return instance;
    }

    /** Full class roster for today's attendance sheet. */
    public List<Student> getStudents() {
        return MockStudentData.getStudents(); // ← swap: ApiService.getStudents()
    }

    /** Per-student session history, identified by name for now. */
    public List<AttendanceRecord> getStudentHistory(String studentName) {
        return MockStudentData.getStudentHistory(studentName); // ← swap: ApiService.getStudentHistory(id)
    }
}