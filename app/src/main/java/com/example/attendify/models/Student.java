package com.example.attendify.models;

public class Student {
    public static final int STATUS_PRESENT = 0;
    public static final int STATUS_LATE = 1;
    public static final int STATUS_ABSENT = 2;

    private int id;
    private String name;
    private int status;
    private String time;

    public Student(int id, String name, int status, String time) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.time = time;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getStatus() { return status; }
    public String getTime() { return time; }

    public void cycleStatus() {
        status = (status + 1) % 3;
        switch (status) {
            case STATUS_PRESENT: time = "07:55 AM"; break;
            case STATUS_LATE:    time = "08:15 AM"; break;
            case STATUS_ABSENT:  time = "--:--";    break;
        }
    }

    public String getStatusLabel() {
        switch (status) {
            case STATUS_PRESENT: return "Present";
            case STATUS_LATE:    return "Late";
            case STATUS_ABSENT:  return "Absent";
            default:             return "Present";
        }
    }
}
