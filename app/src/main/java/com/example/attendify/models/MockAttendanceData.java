package com.example.attendify.models;

import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.Student;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock class-wide attendance history (used by teacher's HomeFragment and
 * HistoryFragment, and secretary views).
 *
 * HOW TO SWAP IN REAL DATA:
 *   Replace method bodies with API/DB calls — e.g. ApiService.getHistory()
 *   or AttendanceDao.getAll().
 */
public class MockAttendanceData {

    // ── Past session history (used by HistoryFragment) ────────────────────────
    public static List<AttendanceRecord> getHistory() {
        List<AttendanceRecord> list = new ArrayList<>();
        list.add(new AttendanceRecord("Feb 11, 2026", 28, 2, 1));
        list.add(new AttendanceRecord("Feb 10, 2026", 29, 1, 1));
        list.add(new AttendanceRecord("Feb 09, 2026", 30, 0, 1));
        return list;
    }

    /**
     * Derives today's summary live from the student roster.
     * Once real data exists, replace with: ApiService.getTodayAttendance()
     */
    public static AttendanceRecord getTodayAttendance() {
        List<Student> students = MockStudentData.getStudents();
        int present = 0, absent = 0, late = 0;
        for (Student s : students) {
            switch (s.getStatus()) {
                case Student.STATUS_PRESENT: present++; break;
                case Student.STATUS_ABSENT:  absent++;  break;
                case Student.STATUS_LATE:    late++;    break;
            }
        }
        return new AttendanceRecord("Today", present, absent, late);
    }
}