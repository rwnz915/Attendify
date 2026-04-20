package com.example.attendify.models;

import java.util.List;

public class UserProfile {

    public static final String ROLE_TEACHER   = "teacher";
    public static final String ROLE_STUDENT   = "student";
    public static final String ROLE_SECRETARY = "secretary";

    // Fields — match Firestore field names exactly
    private String id;           // Firebase Auth UID
    private String firstname;
    private String lastname;
    private String email;
    private String role;
    private String section;      // students only
    private String studentID;    // students only (capital ID — matches Firestore)
    private List<String> sections; // teachers only

    // Required by Firestore deserialization
    public UserProfile() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId()             { return id; }
    public String getFirstname()      { return firstname; }
    public String getLastname()       { return lastname; }
    public String getEmail()          { return email; }
    public String getRole()           { return role; }
    public String getSection()        { return section; }
    public String getStudentID()      { return studentID; }
    public List<String> getSections() { return sections; }

    /** Returns "Lastname, Firstname" — used wherever a full name is displayed */
    public String getFullName() {
        if (lastname != null && firstname != null) return lastname + ", " + firstname;
        if (firstname != null) return firstname;
        return "";
    }

    /** Legacy — some fragments still call getName(). Returns getFullName(). */
    public String getName() { return getFullName(); }

    // ── Setters — needed for Firestore and AuthRepository ────────────────────

    public void setId(String id)                  { this.id = id; }
    public void setFirstname(String firstname)     { this.firstname = firstname; }
    public void setLastname(String lastname)       { this.lastname = lastname; }
    public void setEmail(String email)             { this.email = email; }
    public void setRole(String role)               { this.role = role; }
    public void setSection(String section)         { this.section = section; }
    public void setStudentID(String studentID)     { this.studentID = studentID; }
    public void setSections(List<String> sections) { this.sections = sections; }

    // ── Role helpers ──────────────────────────────────────────────────────────

    public boolean isTeacher()   { return ROLE_TEACHER.equals(role); }
    public boolean isStudent()   { return ROLE_STUDENT.equals(role); }
    public boolean isSecretary() { return ROLE_SECRETARY.equals(role); }
}