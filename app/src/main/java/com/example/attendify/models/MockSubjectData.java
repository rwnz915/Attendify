package com.example.attendify.models;

import com.example.attendify.models.AttendanceRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock subject list and per-subject attendance history for students.
 *
 * HOW TO SWAP IN REAL DATA:
 *   Replace method bodies with API calls — e.g. ApiService.getStudentSubjects(studentId)
 *   and ApiService.getSubjectAttendance(studentId, subjectId).
 */
public class MockSubjectData {

    public static class StudentSubject {
        public int    id;
        public String name;
        public String section;
        public String teacher;
        public String schedule;
        public String color;

        public StudentSubject(int id, String name, String section,
                              String teacher, String schedule, String color) {
            this.id       = id;
            this.name     = name;
            this.section  = section;
            this.teacher  = teacher;
            this.schedule = schedule;
            this.color    = color;
        }
    }

    // ── Subjects enrolled by the logged-in student ────────────────────────────
    public static List<StudentSubject> getStudentSubjects(String studentName) {
        // All students in IT-201 share this roster for now
        // Swap with ApiService.getStudentSubjects(studentId) later
        List<StudentSubject> list = new ArrayList<>();
        list.add(new StudentSubject(1, "Mathematics",    "IT-201", "Ms. Johnson",   "MWF 8:00-9:30 AM",   "#3B82F6"));
        list.add(new StudentSubject(2, "Physics",        "IT-201", "Mr. Smith",     "TTh 10:00-11:30 AM", "#8B5CF6"));
        list.add(new StudentSubject(3, "Programming",    "IT-201", "Ms. Davis",     "MWF 1:00-2:30 PM",   "#10B981"));
        list.add(new StudentSubject(4, "Entrepreneurship","IT-201","Ms. Rodriguez", "TTh 8:00-9:30 AM",   "#F59E0B"));
        list.add(new StudentSubject(5, "Database Systems","IT-201","Mr. Brown",     "MWF 10:00-11:30 AM", "#EF4444"));
        list.add(new StudentSubject(6, "Web Development","IT-201", "Ms. Wilson",    "TTh 1:00-2:30 PM",   "#06B6D4"));
        return list;
    }

    /**
     * Per-subject attendance history for a student.
     * Each record = one session they attended (or missed) for that subject.
     */
    public static List<AttendanceRecord> getSubjectHistory(String studentName, String subjectName) {
        List<AttendanceRecord> list = new ArrayList<>();

        switch (subjectName) {
            case "Mathematics":
                list.add(new AttendanceRecord("Apr 11, 2026", "Mathematics", "07:55 AM", "Present"));
                list.add(new AttendanceRecord("Apr 09, 2026", "Mathematics", "07:58 AM", "Present"));
                list.add(new AttendanceRecord("Apr 07, 2026", "Mathematics", "08:14 AM", "Late"));
                list.add(new AttendanceRecord("Apr 04, 2026", "Mathematics", "--:--",    "Absent"));
                list.add(new AttendanceRecord("Apr 02, 2026", "Mathematics", "07:50 AM", "Present"));
                list.add(new AttendanceRecord("Mar 31, 2026", "Mathematics", "07:52 AM", "Present"));
                list.add(new AttendanceRecord("Mar 28, 2026", "Mathematics", "08:20 AM", "Late"));
                list.add(new AttendanceRecord("Mar 26, 2026", "Mathematics", "07:48 AM", "Present"));
                break;

            case "Physics":
                list.add(new AttendanceRecord("Apr 10, 2026", "Physics", "10:02 AM", "Present"));
                list.add(new AttendanceRecord("Apr 08, 2026", "Physics", "--:--",    "Absent"));
                list.add(new AttendanceRecord("Apr 03, 2026", "Physics", "10:00 AM", "Present"));
                list.add(new AttendanceRecord("Apr 01, 2026", "Physics", "10:18 AM", "Late"));
                list.add(new AttendanceRecord("Mar 27, 2026", "Physics", "10:01 AM", "Present"));
                list.add(new AttendanceRecord("Mar 25, 2026", "Physics", "10:00 AM", "Present"));
                break;

            case "Programming":
                list.add(new AttendanceRecord("Apr 11, 2026", "Programming", "01:00 PM", "Present"));
                list.add(new AttendanceRecord("Apr 09, 2026", "Programming", "01:05 PM", "Present"));
                list.add(new AttendanceRecord("Apr 07, 2026", "Programming", "01:00 PM", "Present"));
                list.add(new AttendanceRecord("Apr 04, 2026", "Programming", "01:12 PM", "Late"));
                list.add(new AttendanceRecord("Apr 02, 2026", "Programming", "01:00 PM", "Present"));
                list.add(new AttendanceRecord("Mar 31, 2026", "Programming", "--:--",    "Absent"));
                break;

            case "Entrepreneurship":
                list.add(new AttendanceRecord("Apr 10, 2026", "Entrepreneurship", "07:55 AM", "Present"));
                list.add(new AttendanceRecord("Apr 08, 2026", "Entrepreneurship", "08:05 AM", "Late"));
                list.add(new AttendanceRecord("Apr 03, 2026", "Entrepreneurship", "07:50 AM", "Present"));
                list.add(new AttendanceRecord("Apr 01, 2026", "Entrepreneurship", "07:53 AM", "Present"));
                list.add(new AttendanceRecord("Mar 27, 2026", "Entrepreneurship", "--:--",    "Absent"));
                break;

            case "Database Systems":
                list.add(new AttendanceRecord("Apr 11, 2026", "Database Systems", "10:00 AM", "Present"));
                list.add(new AttendanceRecord("Apr 09, 2026", "Database Systems", "10:22 AM", "Late"));
                list.add(new AttendanceRecord("Apr 07, 2026", "Database Systems", "10:01 AM", "Present"));
                list.add(new AttendanceRecord("Apr 04, 2026", "Database Systems", "--:--",    "Absent"));
                list.add(new AttendanceRecord("Apr 02, 2026", "Database Systems", "10:00 AM", "Present"));
                break;

            case "Web Development":
                list.add(new AttendanceRecord("Apr 10, 2026", "Web Development", "01:00 PM", "Present"));
                list.add(new AttendanceRecord("Apr 08, 2026", "Web Development", "01:00 PM", "Present"));
                list.add(new AttendanceRecord("Apr 03, 2026", "Web Development", "01:00 PM", "Present"));
                list.add(new AttendanceRecord("Apr 01, 2026", "Web Development", "01:08 PM", "Present"));
                list.add(new AttendanceRecord("Mar 27, 2026", "Web Development", "01:15 PM", "Late"));
                list.add(new AttendanceRecord("Mar 25, 2026", "Web Development", "--:--",    "Absent"));
                break;
        }

        return list;
    }
}