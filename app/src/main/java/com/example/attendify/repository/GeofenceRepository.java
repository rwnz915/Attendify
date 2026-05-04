package com.example.attendify.repository;

import android.content.Context;
import android.util.Log;

import com.example.attendify.LocalCacheManager;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * GeofenceRepository
 *
 * Handles recording a student's geofence time-in event and resetting status on exit.
 *
 * Firestore structure:
 *   geofence / {autoID} / {
 *     userID:    string   — Firebase Auth UID
 *     timeIn:    string   — ISO timestamp e.g. "2026-05-03T08:45:00"
 *     subjectId: string   — active subject doc ID at time-in ("" if none)
 *     status:    string   — always "in school"
 *   }
 *
 * Status update:
 *   users / {userID} / { status: "in school" | "absent", statusDate: "yyyy-MM-dd" }
 */
public class GeofenceRepository {

    private static final String TAG = "GeofenceRepository";
    private static final String KEY_PENDING = "pending_geofence_entries";
    private static final SimpleDateFormat ISO_FMT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    private static GeofenceRepository instance;
    private GeofenceRepository() {}

    public static GeofenceRepository getInstance() {
        if (instance == null) instance = new GeofenceRepository();
        return instance;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call when the device enters the geofence.
     *
     * @param context   any Context
     * @param userId    Firebase Auth UID of the logged-in student
     * @param subjectId Firestore ID of the currently active subject, or "" if none
     */
    public void recordTimeIn(Context context, String userId, String subjectId) {
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "recordTimeIn: userId is null — skipping");
            return;
        }
        Context appCtx = context.getApplicationContext();
        String timeIn = ISO_FMT.format(new Date());
        String sid    = (subjectId != null) ? subjectId : "";

        if (LocalCacheManager.isOnline(appCtx)) {
            writeToFirestore(appCtx, userId, timeIn, sid);
            flushPendingEntries(appCtx, userId);
        } else {
            savePendingEntry(appCtx, userId, timeIn, sid);
            Log.d(TAG, "Offline — time-in cached: " + timeIn);
        }
    }

    /** Backwards-compatible overload for callers without a subjectId. */
    public void recordTimeIn(Context context, String userId) {
        recordTimeIn(context, userId, "");
    }

    /**
     * Call when the device exits the geofence.
     *
     * Resets users/{userId}/status back to "absent" ONLY if the teacher has
     * not yet recorded an attendance entry for this student today (any subject).
     * If an attendance doc already exists, the status is left untouched —
     * a marked student is never flipped back to absent.
     *
     * @param userId Firebase Auth UID of the logged-in student
     */
    public void resetStatusOnExit(String userId) {
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "resetStatusOnExit: userId is null — skipping");
            return;
        }

        String today = DATE_FMT.format(new Date());

        // Check if teacher has already recorded attendance for this student today
        FirebaseFirestore.getInstance()
                .collection("attendance")
                .whereEqualTo("studentId", userId)
                .whereEqualTo("date", today)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        // Teacher already marked this student today — leave status alone
                        Log.d(TAG, "Geofence exit ignored — attendance already recorded for " + userId);
                        return;
                    }
                    // No attendance record yet — reset to absent
                    Map<String, Object> userUpdate = new HashMap<>();
                    userUpdate.put("status",     "absent");
                    userUpdate.put("statusDate", today);
                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(userId)
                            .update(userUpdate)
                            .addOnSuccessListener(v ->
                                    Log.d(TAG, "User status -> 'absent' on geofence exit (" + today + ")"))
                            .addOnFailureListener(e ->
                                    Log.w(TAG, "Status reset failed on exit: " + e.getMessage()));
                })
                .addOnFailureListener(e ->
                        Log.w(TAG, "resetStatusOnExit attendance query failed: " + e.getMessage()));
    }

    /** Flush offline-cached entries to Firestore when connectivity resumes. */
    public void flushPendingEntries(Context context, String userId) {
        if (userId == null || userId.isEmpty()) return;
        Context appCtx = context.getApplicationContext();
        LocalCacheManager cache = LocalCacheManager.getInstance(appCtx);
        String json = cache.getRaw(userId, KEY_PENDING);
        if (json == null || json.equals("[]") || json.isEmpty()) return;

        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) return;
            Log.d(TAG, "Flushing " + arr.length() + " pending entries...");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                writeToFirestore(appCtx,
                        obj.getString("userID"),
                        obj.getString("timeIn"),
                        obj.optString("subjectId", ""));
            }
            cache.putRaw(userId, KEY_PENDING, "[]");
        } catch (JSONException e) {
            Log.e(TAG, "flushPendingEntries JSON error", e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void writeToFirestore(Context ctx, String userId, String timeIn, String subjectId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. geofence collection — includes subjectId and status
        Map<String, Object> data = new HashMap<>();
        data.put("userID",    userId);
        data.put("timeIn",    timeIn);
        data.put("subjectId", subjectId);
        data.put("status",    "in school");

        db.collection("geofence")
                .add(data)
                .addOnSuccessListener(ref ->
                        Log.d(TAG, "Time-in written: " + ref.getId()))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Firestore write failed — caching for retry", e);
                    savePendingEntry(ctx, userId, timeIn, subjectId);
                });

        // 2. Update users/{userId}/status + statusDate
        //    statusDate lets readers reject stale "in school" from a previous day.
        String todayDate = DATE_FMT.format(new Date());
        Map<String, Object> userUpdate = new HashMap<>();
        userUpdate.put("status",     "in school");
        userUpdate.put("statusDate", todayDate);
        db.collection("users").document(userId)
                .update(userUpdate)
                .addOnSuccessListener(v -> Log.d(TAG, "User status -> 'in school' (" + todayDate + ")"))
                .addOnFailureListener(e -> Log.w(TAG, "Status update failed: " + e.getMessage()));
    }

    private void savePendingEntry(Context ctx, String userId, String timeIn, String subjectId) {
        LocalCacheManager cache = LocalCacheManager.getInstance(ctx);
        String existing = cache.getRaw(userId, KEY_PENDING);
        try {
            JSONArray arr = (existing != null && !existing.isEmpty())
                    ? new JSONArray(existing) : new JSONArray();
            JSONObject obj = new JSONObject();
            obj.put("userID",    userId);
            obj.put("timeIn",    timeIn);
            obj.put("subjectId", subjectId != null ? subjectId : "");
            arr.put(obj);
            cache.putRaw(userId, KEY_PENDING, arr.toString());
            Log.d(TAG, "Pending entries: " + arr.length());
        } catch (JSONException e) {
            Log.e(TAG, "savePendingEntry JSON error", e);
        }
    }
}