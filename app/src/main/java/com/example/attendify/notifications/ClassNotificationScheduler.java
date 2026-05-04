package com.example.attendify.notifications;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.attendify.LocalCacheManager;
import com.example.attendify.notifications.NotificationGuard;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.SubjectRepository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ClassNotificationScheduler
 *
 * Handles:
 *  1. Posting a notification 30 minutes before the next subject starts.
 *  2. Determining the accurate "in school" time for attendance:
 *     - Queries Firestore geofence records for this user on today's date.
 *     - Returns the LOWEST timeIn timestamp among today's records.
 *     - Falls back to LocalCache if offline.
 *
 * Usage:
 *   Call {@link #scheduleUpcomingClassAlerts(Context)} from MainActivity.onResume()
 *   or after login. It uses a single-shot Handler that fires ~500 ms before the
 *   30-minute window, so it does not drain battery with a persistent background loop.
 */
public class ClassNotificationScheduler {

    private static final String TAG = "ClassNotifScheduler";
    private static final long EARLY_MS = 30 * 60 * 1000L; // 30 min in ms

    private static ClassNotificationScheduler instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingRunnable;

    private ClassNotificationScheduler() {}

    public static ClassNotificationScheduler getInstance() {
        if (instance == null) instance = new ClassNotificationScheduler();
        return instance;
    }

    // ── Schedule class-start alerts ──────────────────────────────────────────

    /**
     * Loads the user's subjects and schedules a one-shot notification for the
     * nearest upcoming class that is ≥ 30 minutes away.
     *
     * Safe to call repeatedly — cancels any previously scheduled runnable first.
     */
    public void scheduleUpcomingClassAlerts(Context ctx) {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        // Cancel any existing scheduled alert
        if (pendingRunnable != null) {
            handler.removeCallbacks(pendingRunnable);
            pendingRunnable = null;
        }

        String section = user.getSection();  // students & secretary have section
        boolean isTeacher = "teacher".equals(user.getRole());

        if (isTeacher) {
            SubjectRepository.getInstance().getTeacherSubjects(user.getId(),
                    new SubjectRepository.SubjectsCallback() {
                        @Override
                        public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                            scheduleFromSubjects(ctx, subjects, user);
                        }
                        @Override public void onFailure(String e) {}
                    });
        } else if (section != null && !section.isEmpty()) {
            SubjectRepository.getInstance().getStudentSubjects(section,
                    new SubjectRepository.SubjectsCallback() {
                        @Override
                        public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                            scheduleFromSubjects(ctx, subjects, user);
                        }
                        @Override public void onFailure(String e) {}
                    });
        }
    }

    private void scheduleFromSubjects(Context ctx,
                                      List<SubjectRepository.SubjectItem> subjects,
                                      UserProfile user) {
        // Find the next upcoming subject (start time > now)
        long nowMs = System.currentTimeMillis();
        SubjectRepository.SubjectItem nextSubject = null;
        long nextStartMs = Long.MAX_VALUE;

        for (SubjectRepository.SubjectItem s : subjects) {
            if (s.schedule == null) continue;
            long startMs = subjectStartMs(s.schedule);
            if (startMs < 0) continue;
            if (startMs > nowMs && startMs < nextStartMs) {
                nextStartMs = startMs;
                nextSubject = s;
            }
        }

        if (nextSubject == null) return;

        // Fire 30 min before start; or immediately if we're already inside the window
        long fireAtMs = nextStartMs - EARLY_MS;
        long delayMs  = fireAtMs - nowMs;

        if (delayMs < 0) delayMs = 0; // already in window — fire now

        final SubjectRepository.SubjectItem alertSubject = nextSubject;
        final String timeLabel = extractFormattedTime(alertSubject.schedule);
        final String userId = user.getId();
        final String role   = user.getRole();

        final String subjectKey = alertSubject.id != null ? alertSubject.id : alertSubject.name;
        pendingRunnable = () -> {
            // Guard: only fire once per subject per day per role
            if (!NotificationGuard.shouldFire(ctx, userId, subjectKey, "class_soon_" + role)) {
                Log.d(TAG, "Alert suppressed (already fired today): " + alertSubject.name);
                return;
            }

            // Post the system notification
            if ("student".equals(role)) {
                NotificationHelper.notifyStudentClassSoon(ctx, alertSubject.name, timeLabel);
            } else if ("teacher".equals(role)) {
                NotificationHelper.notifyTeacherClassStarting(ctx, alertSubject.name,
                        alertSubject.section != null ? alertSubject.section : "");
            } else { // secretary
                NotificationHelper.notifySecretaryClassStarting(ctx, alertSubject.name, timeLabel);
            }

            // Persist to NotificationStore
            String title = "teacher".equals(role) ? "Class Starting" : "Class Starting Soon";
            String body  = "teacher".equals(role)
                    ? alertSubject.name + " (" + alertSubject.section + ") is starting."
                    : alertSubject.name + " starts at " + timeLabel + ". Get ready!";

            NotificationStore.getInstance().save(ctx, userId, title, body);

            Log.d(TAG, "Alert fired for: " + alertSubject.name);
        };

        handler.postDelayed(pendingRunnable, delayMs);
        Log.d(TAG, "Scheduled alert for '" + alertSubject.name + "' in " + (delayMs / 1000) + "s");
    }

    // ── Accurate school time-in ───────────────────────────────────────────────

    public interface TimeInCallback {
        /** @param timeIn ISO timestamp of earliest geofence entry today, or null if none */
        void onResult(String timeIn, String subjectId);
    }

    /**
     * Returns the EARLIEST geofence time-in recorded for this user today.
     * This is the "in school" time shown in attendance / student fragments.
     * Works offline by reading from the Firestore geofence collection or
     * falling back to the pending-entries local cache.
     *
     * @param subjectFilter  if non-empty, only returns entries for this subjectId
     *                       (pass "" to get global earliest regardless of subject)
     */
    public void getEarliestTimeInToday(Context ctx, String userId,
                                       String subjectFilter,
                                       TimeInCallback cb) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date());

        if (!LocalCacheManager.isOnline(ctx)) {
            // Offline: scan pending cache entries
            String cached = LocalCacheManager.getInstance(ctx).getRaw(userId, "pending_geofence_entries");
            String earliest = scanCacheForEarliest(cached, today, subjectFilter);
            cb.onResult(earliest, subjectFilter);
            return;
        }

        Query q = FirebaseFirestore.getInstance()
                .collection("geofence")
                .whereEqualTo("userID", userId);

        if (!subjectFilter.isEmpty()) {
            q = q.whereEqualTo("subjectId", subjectFilter);
        }

        q.get().addOnSuccessListener(snap -> {
            String earliest = null;
            String earliestSubjectId = "";

            for (QueryDocumentSnapshot doc : snap) {
                String timeIn = doc.getString("timeIn");
                if (timeIn == null || !timeIn.startsWith(today)) continue;

                if (earliest == null || timeIn.compareTo(earliest) < 0) {
                    earliest = timeIn;
                    String sid = doc.getString("subjectId");
                    earliestSubjectId = sid != null ? sid : "";
                }
            }

            cb.onResult(earliest, earliestSubjectId);
        }).addOnFailureListener(e -> {
            Log.w(TAG, "getEarliestTimeInToday failed", e);
            cb.onResult(null, "");
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns epoch ms for the start time of {@code schedule} on today's date. */
    private long subjectStartMs(String schedule) {
        try {
            String[] parts = schedule.trim().split("\\s+", 2);
            if (parts.length < 2) return -1;
            String[] times = parts[1].split("-", 2);
            String startStr = times[0].trim().toUpperCase(Locale.ENGLISH);
            SimpleDateFormat sdf = new SimpleDateFormat("h:mma", Locale.ENGLISH);
            Date parsed = sdf.parse(startStr);
            if (parsed == null) return -1;

            Calendar c = Calendar.getInstance();
            Calendar p = Calendar.getInstance();
            p.setTime(parsed);
            c.set(Calendar.HOUR_OF_DAY, p.get(Calendar.HOUR_OF_DAY));
            c.set(Calendar.MINUTE,      p.get(Calendar.MINUTE));
            c.set(Calendar.SECOND,      0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        } catch (ParseException e) {
            return -1;
        }
    }

    private String extractFormattedTime(String schedule) {
        try {
            String[] parts = schedule.trim().split("\\s+", 2);
            if (parts.length < 2) return "";
            String startStr = parts[1].split("-")[0].trim().toUpperCase(Locale.ENGLISH);
            SimpleDateFormat inFmt  = new SimpleDateFormat("h:mma",  Locale.ENGLISH);
            SimpleDateFormat outFmt = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
            Date d = inFmt.parse(startStr);
            return d != null ? outFmt.format(d) : startStr;
        } catch (ParseException e) {
            return "";
        }
    }

    private String scanCacheForEarliest(String json, String today, String subjectFilter) {
        if (json == null || json.isEmpty()) return null;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            String earliest = null;
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                String timeIn = obj.optString("timeIn", "");
                String sid    = obj.optString("subjectId", "");
                if (!timeIn.startsWith(today)) continue;
                if (!subjectFilter.isEmpty() && !subjectFilter.equals(sid)) continue;
                if (earliest == null || timeIn.compareTo(earliest) < 0) {
                    earliest = timeIn;
                }
            }
            return earliest;
        } catch (Exception e) {
            return null;
        }
    }
}