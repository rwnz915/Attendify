package com.example.attendify.models;

public class AttendanceRecord {
    private String date;
    private int present;
    private int absent;
    private int late;

    public AttendanceRecord(String date, int present, int absent, int late) {
        this.date = date;
        this.present = present;
        this.absent = absent;
        this.late = late;
    }

    public String getDate()    { return date; }
    public int getPresent()    { return present; }
    public int getAbsent()     { return absent; }
    public int getLate()       { return late; }
    public int getTotal()      { return present + absent + late; }
}
