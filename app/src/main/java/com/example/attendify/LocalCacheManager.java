package com.example.attendify;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.example.attendify.models.UserProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LocalCacheManager
 *
 * Persists all Firestore data to SharedPreferences as JSON so the app
 * can work fully offline. On every successful network fetch the cache
 * is refreshed automatically. On network failure the cached copy is
 * returned so the UI is never empty.
 *
 * Keys (all prefixed with the logged-in UID so multiple accounts
 * never cross-pollute each other):
 *
 *   <uid>_user_profile          – UserProfile JSON
 *   <uid>_subjects              – List<SubjectItem> JSON
 *   <uid>_students_<section>    – List<Student-like Map> JSON
 *   <uid>_attendance_<subjectId>– List<AttendanceRecord-like Map> JSON
 *   <uid>_attendance_today_<subjectId>_<date> – summary Map JSON
 *   <uid>_student_history       – List<AttendanceRecord-like Map> JSON
 *   <uid>_approvals             – List<ApprovalRequest-like Map> JSON
 *   <uid>_excuse_letters_<tag>  – List<ExcuseLetter-like Map> JSON
 *   <uid>_theme                 – String
 *   <uid>_setup_done            – boolean String
 *
 * Global (not per-UID):
 *   cache_uid                   – last logged-in UID (for auto-login restore)
 *   cache_role                  – last role
 */
public class LocalCacheManager {

    private static final String TAG = "LocalCacheManager";
    private static final String PREFS_NAME = "attendify_cache";

    // Global keys
    private static final String KEY_UID  = "cache_uid";
    private static final String KEY_ROLE = "cache_role";

    private static LocalCacheManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;

    // ── Singleton ────────────────────────────────────────────────────────────

    private LocalCacheManager(Context ctx) {
        prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson  = new GsonBuilder().serializeNulls().create();
    }

    public static LocalCacheManager getInstance(Context ctx) {
        if (instance == null) {
            instance = new LocalCacheManager(ctx);
        }
        return instance;
    }

    // ── Network check ────────────────────────────────────────────────────────

    public static boolean isOnline(Context ctx) {
        ConnectivityManager cm =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    // ── UID / Role (global session meta) ─────────────────────────────────────

    public void saveSession(String uid, String role) {
        prefs.edit().putString(KEY_UID, uid).putString(KEY_ROLE, role).apply();
        Log.d(TAG, "Session saved uid=" + uid + " role=" + role);
    }

    public String getCachedUid()  { return prefs.getString(KEY_UID,  null); }
    public String getCachedRole() { return prefs.getString(KEY_ROLE, null); }

    public void clearSession() {
        prefs.edit().remove(KEY_UID).remove(KEY_ROLE).apply();
    }

    // ── UserProfile ───────────────────────────────────────────────────────────

    public void saveUserProfile(String uid, UserProfile user) {
        put(uid, "user_profile", gson.toJson(user));
    }

    public UserProfile getUserProfile(String uid) {
        String json = get(uid, "user_profile");
        if (json == null) return null;
        try {
            return gson.fromJson(json, UserProfile.class);
        } catch (Exception e) {
            Log.w(TAG, "getUserProfile parse error", e);
            return null;
        }
    }

    // ── Subjects ──────────────────────────────────────────────────────────────

    public void saveSubjects(String uid, List<Map<String, Object>> subjects) {
        put(uid, "subjects", gson.toJson(subjects));
    }

    public List<Map<String, Object>> getSubjects(String uid) {
        return getListOfMaps(uid, "subjects");
    }

    // ── Students by section ───────────────────────────────────────────────────

    public void saveStudentsBySection(String uid, String section,
                                      List<Map<String, Object>> students) {
        put(uid, "students_" + sanitize(section), gson.toJson(students));
    }

    public List<Map<String, Object>> getStudentsBySection(String uid, String section) {
        return getListOfMaps(uid, "students_" + sanitize(section));
    }

    // ── All students (no section filter) ─────────────────────────────────────

    public void saveAllStudents(String uid, List<Map<String, Object>> students) {
        put(uid, "students_all", gson.toJson(students));
    }

    public List<Map<String, Object>> getAllStudents(String uid) {
        return getListOfMaps(uid, "students_all");
    }

    // ── Attendance history for a subject ──────────────────────────────────────

    public void saveAttendanceHistory(String uid, String subjectId,
                                      List<Map<String, Object>> records) {
        put(uid, "attendance_" + sanitize(subjectId), gson.toJson(records));
    }

    public List<Map<String, Object>> getAttendanceHistory(String uid, String subjectId) {
        return getListOfMaps(uid, "attendance_" + sanitize(subjectId));
    }

    // ── Today's attendance summary ────────────────────────────────────────────

    public void saveTodaySummary(String uid, String subjectId, String date,
                                 Map<String, Object> summary) {
        put(uid, "attendance_today_" + sanitize(subjectId) + "_" + sanitize(date),
                gson.toJson(summary));
    }

    public Map<String, Object> getTodaySummary(String uid, String subjectId, String date) {
        String json = get(uid, "attendance_today_" + sanitize(subjectId) + "_" + sanitize(date));
        if (json == null) return null;
        try {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            return gson.fromJson(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Per-subject history (used by SubjectRepository) ───────────────────────

    public void saveSubjectHistory(String uid, String subjectId,
                                   List<Map<String, Object>> records) {
        put(uid, "subj_history_" + sanitize(subjectId), gson.toJson(records));
    }

    public List<Map<String, Object>> getSubjectHistory(String uid, String subjectId) {
        return getListOfMaps(uid, "subj_history_" + sanitize(subjectId));
    }

    // ── Student's own attendance history ──────────────────────────────────────

    public void saveStudentHistory(String uid, List<Map<String, Object>> records) {
        put(uid, "student_history", gson.toJson(records));
    }

    public List<Map<String, Object>> getStudentHistory(String uid) {
        return getListOfMaps(uid, "student_history");
    }

    // ── Today summaries list (multiple subjects) ──────────────────────────────

    public void saveTodaySummaries(String uid, String date,
                                   List<Map<String, Object>> summaries) {
        put(uid, "today_summaries_" + sanitize(date), gson.toJson(summaries));
    }

    public List<Map<String, Object>> getTodaySummaries(String uid, String date) {
        return getListOfMaps(uid, "today_summaries_" + sanitize(date));
    }

    // ── Approvals ─────────────────────────────────────────────────────────────

    public void savePendingApprovals(String uid, List<Map<String, Object>> approvals) {
        put(uid, "approvals", gson.toJson(approvals));
    }

    public List<Map<String, Object>> getPendingApprovals(String uid) {
        return getListOfMaps(uid, "approvals");
    }

    // ── Excuse letters ────────────────────────────────────────────────────────

    /** tag = "pending_by_teacher", "all_by_teacher", "by_student", etc. */
    public void saveExcuseLetters(String uid, String tag,
                                  List<Map<String, Object>> letters) {
        put(uid, "excuse_" + sanitize(tag), gson.toJson(letters));
    }

    public List<Map<String, Object>> getExcuseLetters(String uid, String tag) {
        return getListOfMaps(uid, "excuse_" + sanitize(tag));
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    public void saveTheme(String uid, String themeKey) {
        put(uid, "theme", themeKey);
    }

    public String getTheme(String uid) {
        return get(uid, "theme");
    }

    // ── Setup done flag ───────────────────────────────────────────────────────

    public void saveSetupDone(String uid, boolean done) {
        put(uid, "setup_done", String.valueOf(done));
    }

    public Boolean getSetupDone(String uid) {
        String val = get(uid, "setup_done");
        if (val == null) return null;
        return Boolean.parseBoolean(val);
    }

    // ── Raw generic helpers ───────────────────────────────────────────────────

    private void put(String uid, String key, String value) {
        prefs.edit().putString(uid + "_" + key, value).apply();
    }

    private String get(String uid, String key) {
        return prefs.getString(uid + "_" + key, null);
    }

    private List<Map<String, Object>> getListOfMaps(String uid, String key) {
        String json = get(uid, key);
        if (json == null) return null;
        try {
            Type type = new TypeToken<List<Map<String, Object>>>() {}.getType();
            return gson.fromJson(json, type);
        } catch (Exception e) {
            Log.w(TAG, "getListOfMaps parse error for " + key, e);
            return null;
        }
    }

    /** Replace characters that can't go in a SharedPreferences key. */
    private String sanitize(String input) {
        if (input == null) return "null";
        return input.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}