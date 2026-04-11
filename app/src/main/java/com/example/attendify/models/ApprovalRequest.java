package com.example.attendify.models;

public class ApprovalRequest {

    private int id;
    private String studentName;
    private String initial;
    private String dateRange;
    private String reason;

    public ApprovalRequest(int id, String studentName, String initial,
                           String dateRange, String reason) {
        this.id          = id;
        this.studentName = studentName;
        this.initial     = initial;
        this.dateRange   = dateRange;
        this.reason      = reason;
    }

    public int getId()            { return id; }
    public String getStudentName(){ return studentName; }
    public String getInitial()    { return initial; }
    public String getDateRange()  { return dateRange; }
    public String getReason()     { return reason; }
}