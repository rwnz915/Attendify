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
}
