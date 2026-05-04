package com.example.attendify.models;

public class AttendanceRecord {
    private String date;
    private int present;
    private int absent;
    private int late;

    private String subject;
    private String subjectId;
    private String time;
    private String statusLabel;
    private String studentId;
    private String studentName;

    public AttendanceRecord(String date, int present, int absent, int late) {
        this.date    = date;
        this.present = present;
        this.absent  = absent;
        this.late    = late;
    }

    public AttendanceRecord(String date, String subject, String time, String statusLabel) {
        this(date, subject, "", time, statusLabel, "", "");
    }

    public AttendanceRecord(String date, String subject, String subjectId, String time, String statusLabel, String studentId, String studentName) {
        this.date        = date != null ? date : "";
        this.subject     = subject != null ? subject : "";
        this.subjectId   = subjectId != null ? subjectId : "";
        this.time        = time != null ? time : "--:--";
        this.statusLabel = statusLabel != null ? statusLabel : "";
        this.studentId   = studentId != null ? studentId : "";
        this.studentName = studentName != null ? studentName : "";
        this.present = "Present".equals(this.statusLabel) ? 1 : 0;
        this.late    = "Late".equals(this.statusLabel)    ? 1 : 0;
        this.absent  = "Absent".equals(this.statusLabel)  ? 1 : 0;
    }

    public String getDate()        { return date; }
    public int    getPresent()     { return present; }
    public int    getAbsent()      { return absent; }
    public int    getLate()        { return late; }
    public int    getTotal()       { return present + absent + late; }
    public String getSubject()     { return subject; }
    public String getSubjectId()   { return subjectId; }
    public String getTime()        { return time; }
    public String getStatusLabel() { return statusLabel; }
    public String getStudentId()   { return studentId; }
    public String getStudentName() { return studentName; }

    // Setter for subject name (used when building summary records)
    public void setSubject(String subject) { this.subject = subject; }
}