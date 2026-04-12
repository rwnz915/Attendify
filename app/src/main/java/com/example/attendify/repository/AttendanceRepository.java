package com.example.attendify.repository;

import com.example.attendify.models.MockAttendanceData;
import com.example.attendify.models.AttendanceRecord;

import java.util.List;

/**
 * Access point for class-wide attendance records.
 *
 * Used by: HomeFragment (teacher), HistoryFragment (teacher + secretary).
 *
 * HOW TO SWAP IN REAL DATA:
 *   Replace each method body with the appropriate API/DB call.
 */
public class AttendanceRepository {

    private static AttendanceRepository instance;

    private AttendanceRepository() {}

    public static AttendanceRepository getInstance() {
        if (instance == null) instance = new AttendanceRepository();
        return instance;
    }

    /** Past session history list. */
    public List<AttendanceRecord> getHistory() {
        return MockAttendanceData.getHistory(); // ← swap: ApiService.getHistory()
    }

    /** Today's summary (present / absent / late counts). */
    public AttendanceRecord getTodayAttendance() {
        return MockAttendanceData.getTodayAttendance(); // ← swap: ApiService.getTodayAttendance()
    }
}