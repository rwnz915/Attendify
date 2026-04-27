package com.example.attendify.models;

public class Student {
    public static final int STATUS_PRESENT = 0;
    public static final int STATUS_LATE    = 1;
    public static final int STATUS_ABSENT  = 2;

    private int    id;
    private String name;
    private int    status;
    private String time;
    private String studentId; // Firebase Auth UID — used for Firestore queries
    private String schoolId;  // School-assigned ID e.g. "02000413198" — display only

    public Student(int id, String name, int status, String time) {
        this.id     = id;
        this.name   = name;
        this.status = status;
        this.time   = time;
    }

    public int    getId()        { return id; }
    public String getName()      { return name; }
    public int    getStatus()    { return status; }
    public String getTime()      { return time; }
    public String getStudentId() { return studentId; }
    public void   setStudentId(String studentId) { this.studentId = studentId; }
    public String getSchoolId()  { return schoolId; }
    public void   setSchoolId(String schoolId)   { this.schoolId = schoolId; }

    /** Sets status and time directly from a Firestore record without cycling. */
    public void setStatusFromDb(int statusCode, String arrivalTime) {
        this.status = statusCode;
        this.time   = (arrivalTime != null && !arrivalTime.isEmpty()) ? arrivalTime : "--:--";
    }

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