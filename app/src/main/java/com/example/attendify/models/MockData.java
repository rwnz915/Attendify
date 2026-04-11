package com.example.attendify.models;

import java.util.ArrayList;
import java.util.List;

public class MockData {

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

    public static List<AttendanceRecord> getHistory() {
        List<AttendanceRecord> list = new ArrayList<>();
        list.add(new AttendanceRecord("Feb 11, 2026", 28, 2, 1));
        list.add(new AttendanceRecord("Feb 10, 2026", 29, 1, 1));
        list.add(new AttendanceRecord("Feb 09, 2026", 30, 0, 1));
        return list;
    }

    public static AttendanceRecord getTodayAttendance() {
        // Derived from today's student list
        List<Student> students = getStudents();
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

    public static List<ApprovalRequest> getPendingApprovals() {
        List<ApprovalRequest> list = new ArrayList<>();
        list.add(new ApprovalRequest(1, "Desaliza, Cyrus",  "D", "April 9–10, 2026", "Fever and flu. Attached medical certificate."));
        list.add(new ApprovalRequest(2, "Lozano, Nash",     "L", "April 11, 2026",   "Family emergency out of town."));
        list.add(new ApprovalRequest(3, "Puti, Jericho",    "P", "April 8, 2026",    "Dental appointment. Has clinic certificate."));
        return list;
    }
}