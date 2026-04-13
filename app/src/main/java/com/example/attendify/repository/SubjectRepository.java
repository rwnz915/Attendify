package com.example.attendify.repository;

import com.example.attendify.models.MockSubjectData;
import com.example.attendify.models.AttendanceRecord;

import java.util.List;

/**
 * Access point for student-facing subject data.
 *
 * Used by: StudentSubjectFragment
 *
 * HOW TO SWAP IN REAL DATA:
 *   Replace each method body with the appropriate API/DB call.
 */
public class SubjectRepository {

    private static SubjectRepository instance;

    private SubjectRepository() {}

    public static SubjectRepository getInstance() {
        if (instance == null) instance = new SubjectRepository();
        return instance;
    }

    public List<MockSubjectData.StudentSubject> getStudentSubjects(String studentName) {
        return MockSubjectData.getStudentSubjects(studentName); // ← swap: ApiService.getStudentSubjects(id)
    }

    public List<AttendanceRecord> getSubjectHistory(String studentName, String subjectName) {
        return MockSubjectData.getSubjectHistory(studentName, subjectName); // ← swap: ApiService.getSubjectHistory(studentId, subjectId)
    }
}