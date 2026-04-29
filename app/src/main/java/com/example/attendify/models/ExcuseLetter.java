package com.example.attendify.models;

/**
 * Represents an excuse letter submitted by a student.
 *
 * Firestore collection: "excuse_letters"
 * Added fields (v2): section, subjectId, subjectName, teacherId
 * Image is now REQUIRED (not optional).
 */
public class ExcuseLetter {

    private String docId;
    private String studentId;
    private String studentName;
    private String studentNumber;
    private String section;
    private String subjectId;
    private String subjectName;
    private String teacherId;
    private String message;
    private String imageUrl;
    private String fileId;
    private String status;
    private Object submittedAt;

    public ExcuseLetter() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getDocId()         { return docId; }
    public String getStudentId()     { return studentId; }
    public String getStudentName()   { return studentName; }
    public String getStudentNumber() { return studentNumber; }
    public String getSection()       { return section; }
    public String getSubjectId()     { return subjectId; }
    public String getSubjectName()   { return subjectName; }
    public String getTeacherId()     { return teacherId; }
    public String getMessage()       { return message; }
    public String getImageUrl()      { return imageUrl; }
    public String getFileId()        { return fileId; }
    public String getStatus()        { return status; }
    public Object getSubmittedAt()   { return submittedAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setDocId(String docId)                 { this.docId = docId; }
    public void setStudentId(String studentId)         { this.studentId = studentId; }
    public void setStudentName(String studentName)     { this.studentName = studentName; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }
    public void setSection(String section)             { this.section = section; }
    public void setSubjectId(String subjectId)         { this.subjectId = subjectId; }
    public void setSubjectName(String subjectName)     { this.subjectName = subjectName; }
    public void setTeacherId(String teacherId)         { this.teacherId = teacherId; }
    public void setMessage(String message)             { this.message = message; }
    public void setImageUrl(String imageUrl)           { this.imageUrl = imageUrl; }
    public void setFileId(String fileId)               { this.fileId = fileId; }
    public void setStatus(String status)               { this.status = status; }
    public void setSubmittedAt(Object submittedAt)     { this.submittedAt = submittedAt; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean hasImage() { return imageUrl != null && !imageUrl.isEmpty(); }

    public String getInitial() {
        return (studentName != null && !studentName.isEmpty())
                ? String.valueOf(studentName.charAt(0)).toUpperCase()
                : "?";
    }
}