package com.example.attendify.models;

import com.example.attendify.models.UserProfile;

import java.util.Arrays;
import java.util.List;

/**
 * Fake login data for the three supported roles: teacher, student, secretary.
 *
 * HOW TO SWAP IN REAL AUTH:
 *   Replace the setLoggedInUserForRole() call in RoleSelectionActivity with a
 *   real login API call. Store the returned UserProfile in SessionManager or
 *   SharedPreferences, then have getLoggedInUser() read from there instead.
 */
public class MockAuthData {

    // ── Simulated session ─────────────────────────────────────────────────────
    private static UserProfile loggedInUser = null;

    // ── Teacher accounts ──────────────────────────────────────────────────────
    public static final UserProfile TEACHER = new UserProfile(
            "teacher_001",
            "Dela Cruz, Maria",
            UserProfile.ROLE_TEACHER,
            null,
            null
    );

    // ── Student accounts ──────────────────────────────────────────────────────
    public static final UserProfile STUDENT_MORANDARTE = new UserProfile(
            "student_001",
            "Morandarte, Renz",
            UserProfile.ROLE_STUDENT,
            "IT-203",
            "2024-00123"
    );

    public static final UserProfile STUDENT_TIOZON = new UserProfile(
            "student_002",
            "Tiozon, Hendrix",
            UserProfile.ROLE_STUDENT,
            "IT-201",
            "2024-00124"
    );

    public static final UserProfile STUDENT_PUTI = new UserProfile(
            "student_003",
            "Puti, Jericho",
            UserProfile.ROLE_STUDENT,
            "IT-201",
            "2024-00125"
    );

    public static final UserProfile STUDENT_DESALIZA = new UserProfile(
            "student_004",
            "Desaliza, Cyrus",
            UserProfile.ROLE_STUDENT,
            "IT-201",
            "2024-00126"
    );

    public static final UserProfile STUDENT_SUSVILLA = new UserProfile(
            "student_005",
            "Susvilla, Andrei",
            UserProfile.ROLE_STUDENT,
            "IT-201",
            "2024-00127"
    );

    public static final UserProfile STUDENT_CUNANAN = new UserProfile(
            "student_006",
            "Cunanan, Angelo",
            UserProfile.ROLE_STUDENT,
            "IT-201",
            "2024-00128"
    );

    public static final UserProfile STUDENT_LOZANO = new UserProfile(
            "student_007",
            "Lozano, Nash",
            UserProfile.ROLE_STUDENT,
            "IT-201",
            "2024-00129"
    );

    public static final UserProfile STUDENT_PROTESTANTE = new UserProfile(
            "student_008",
            "Protestante, Angel",
            UserProfile.ROLE_STUDENT,
            "IT-201",
            "2024-00130"
    );

    // ── Secretary accounts ────────────────────────────────────────────────────
    public static final UserProfile SECRETARY = new UserProfile(
            "secretary_001",
            "Santos, Liza",
            UserProfile.ROLE_SECRETARY,
            null,
            null
    );

    // ── All users list ────────────────────────────────────────────────────────
    public static List<UserProfile> getAllUsers() {
        return Arrays.asList(
                TEACHER,
                STUDENT_MORANDARTE,
                STUDENT_TIOZON,
                STUDENT_PUTI,
                STUDENT_DESALIZA,
                STUDENT_SUSVILLA,
                STUDENT_CUNANAN,
                STUDENT_LOZANO,
                STUDENT_PROTESTANTE,
                SECRETARY
        );
    }

    /**
     * Called by RoleSelectionActivity when a role card is tapped.
     * Maps the role string to the default mock user for that role.
     *
     * Replace with a real login call later — e.g.:
     *   ApiService.login(role, credentials, response -> setLoggedInUser(response.user))
     */
    public static void setLoggedInUserForRole(String role) {
        switch (role) {
            case UserProfile.ROLE_TEACHER:
                loggedInUser = TEACHER;
                break;
            case UserProfile.ROLE_STUDENT:
                loggedInUser = STUDENT_MORANDARTE; // default mock student
                break;
            case UserProfile.ROLE_SECRETARY:
                loggedInUser = SECRETARY;
                break;
            default:
                loggedInUser = null;
                break;
        }
    }

    /**
     * Returns the currently logged-in user.
     * Replace with SessionManager.getUser() when real auth is ready.
     */
    public static UserProfile getLoggedInUser() {
        return loggedInUser;
    }

    /** Called on logout to clear the session. */
    public static void clearLoggedInUser() {
        loggedInUser = null;
    }
}