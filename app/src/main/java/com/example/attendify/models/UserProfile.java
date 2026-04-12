package com.example.attendify.models;

public class UserProfile {

    // ── Role constants ────────────────────────────────────────────────────────
    public static final String ROLE_TEACHER   = "teacher";
    public static final String ROLE_STUDENT   = "student";
    public static final String ROLE_SECRETARY = "secretary";

    // ── Fields ────────────────────────────────────────────────────────────────
    private final String id;
    private final String name;
    private final String role;
    private final String section;
    private final String studentId;   // non-null for students only

    // ── Constructor ───────────────────────────────────────────────────────────
    public UserProfile(String id, String name, String role,
                       String section, String studentId) {
        this.id        = id;
        this.name      = name;
        this.role      = role;
        this.section   = section;
        this.studentId = studentId;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String getId()        { return id; }
    public String getName()      { return name; }
    public String getRole()      { return role; }
    public String getSection()   { return section; }
    public String getStudentId() { return studentId; }  // null for teacher/secretary

    // ── Role helpers ──────────────────────────────────────────────────────────
    public boolean isTeacher()   { return ROLE_TEACHER.equals(role); }
    public boolean isStudent()   { return ROLE_STUDENT.equals(role); }
    public boolean isSecretary() { return ROLE_SECRETARY.equals(role); }
}