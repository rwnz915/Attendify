package com.example.attendify.models;

import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.Student;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock student roster and per-student attendance history.
 *
 * HOW TO SWAP IN REAL DATA:
 *   Replace method bodies with API/DB calls — e.g. ApiService.getStudents()
 *   or StudentDao.getAll(). The return types stay the same.
 */
public class MockStudentData {

    // ── Class roster (used by teacher's AttendanceFragment) ───────────────────
    public static List<Student> getStudents() {
        List<Student> list = new ArrayList<>();
        list.add(new Student(1, "Morandarte, Renz",   Student.STATUS_PRESENT, "07:55 AM"));
        list.add(new Student(2, "Tiozon, Hendrix",    Student.STATUS_PRESENT, "07:58 AM"));
        list.add(new Student(3, "Puti, Jericho",      Student.STATUS_LATE,    "08:15 AM"));
        list.add(new Student(4, "Desaliza, Cyrus",    Student.STATUS_ABSENT,  "--:--"));
        list.add(new Student(5, "Susvilla, Andrei",   Student.STATUS_PRESENT, "07:50 AM"));
        list.add(new Student(6, "Cunanan, Angelo",    Student.STATUS_PRESENT, "08:00 AM"));
        list.add(new Student(7, "Lozano, Nash",       Student.STATUS_ABSENT,  "--:--"));
        list.add(new Student(8, "Protestante, Angel", Student.STATUS_PRESENT, "07:45 AM"));
        return list;
    }

    /**
     * Per-student attendance history (used by StudentHomeFragment).
     * Each record = one class session for the given student.
     *
     * Add entries for each student as needed. Swap with an API call later:
     *   ApiService.getStudentHistory(studentId)
     */
    public static List<AttendanceRecord> getStudentHistory(String studentName) {
        List<AttendanceRecord> list = new ArrayList<>();

        switch (studentName) {
            case "Morandarte, Renz":
                list.add(new AttendanceRecord("Feb 11, 2026", "Entrepreneurship", "07:55 AM", "Present"));
                list.add(new AttendanceRecord("Feb 10, 2026", "Entrepreneurship", "07:58 AM", "Present"));
                list.add(new AttendanceRecord("Feb 09, 2026", "Entrepreneurship", "08:12 AM", "Late"));
                list.add(new AttendanceRecord("Feb 08, 2026", "Entrepreneurship", "07:50 AM", "Present"));
                list.add(new AttendanceRecord("Feb 07, 2026", "Entrepreneurship", "07:45 AM", "Present"));
                break;

            case "Tiozon, Hendrix":
                list.add(new AttendanceRecord("Feb 11, 2026", "Entrepreneurship", "07:58 AM", "Present"));
                list.add(new AttendanceRecord("Feb 10, 2026", "Entrepreneurship", "08:05 AM", "Present"));
                list.add(new AttendanceRecord("Feb 09, 2026", "Entrepreneurship", "--:--",    "Absent"));
                list.add(new AttendanceRecord("Feb 08, 2026", "Entrepreneurship", "07:55 AM", "Present"));
                break;

            // Add more students here as needed
        }

        return list;
    }
}