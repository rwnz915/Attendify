package com.example.attendify.geofence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.attendify.LocalCacheManager;
import com.example.attendify.notifications.NotificationGuard;
import com.example.attendify.notifications.NotificationHelper;
import com.example.attendify.notifications.NotificationStore;
import com.example.attendify.repository.GeofenceRepository;
import com.example.attendify.repository.SubjectRepository;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class GeofenceReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) {
            Log.w(TAG, "GeofencingEvent error or null");
            return;
        }

        int transition = event.getGeofenceTransition();

        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.d(TAG, "Entered school geofence — recording time-in");

            String userId = LocalCacheManager.getInstance(context).getCachedUid();
            if (userId == null) {
                Log.w(TAG, "No cached UID — user not logged in, skipping time-in");
                return;
            }

            // Resolve the currently active subject so we can link the geofence
            // event to it.  SubjectRepository needs the student's section, which
            // is stored in UserProfile; we read it from LocalCacheManager.
            String section = LocalCacheManager.getInstance(context).getCachedSection();

            if (section != null && !section.isEmpty()) {
                SubjectRepository.getInstance().getStudentSubjects(section,
                        new SubjectRepository.SubjectsCallback() {
                            @Override
                            public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                                String activeSubjectId = findActiveSubjectId(subjects);
                                GeofenceRepository.getInstance()
                                        .recordTimeIn(context, userId, activeSubjectId);

                                // Find the active subject name for a friendlier notification body
                                String activeSubjectName = "";
                                for (SubjectRepository.SubjectItem s : subjects) {
                                    if (activeSubjectId != null && activeSubjectId.equals(s.id)) {
                                        activeSubjectName = s.name != null ? s.name : "";
                                        break;
                                    }
                                }

                                // Fire arrival notification (once per day)
                                if (NotificationGuard.shouldFire(context, userId, "geofence", "arrived_at_school")) {
                                    NotificationHelper.notifyStudentArrivedAtSchool(context, activeSubjectName);
                                    String body = activeSubjectName.isEmpty()
                                            ? "Welcome! Your arrival at school has been recorded."
                                            : "Welcome! Your arrival has been recorded for " + activeSubjectName + ".";
                                    NotificationStore.getInstance().save(context, userId,
                                            "Arrived at School", body);
                                }
                            }

                            @Override
                            public void onFailure(String error) {
                                // Fall back: record time-in without a subjectId
                                Log.w(TAG, "Could not load subjects: " + error + " — recording without subjectId");
                                GeofenceRepository.getInstance().recordTimeIn(context, userId, "");

                                // Still fire the arrival notification
                                if (NotificationGuard.shouldFire(context, userId, "geofence", "arrived_at_school")) {
                                    NotificationHelper.notifyStudentArrivedAtSchool(context, "");
                                    NotificationStore.getInstance().save(context, userId,
                                            "Arrived at School",
                                            "Welcome! Your arrival at school has been recorded.");
                                }
                            }
                        });
            } else {
                // No section cached yet — record without subjectId
                GeofenceRepository.getInstance().recordTimeIn(context, userId, "");

                // Fire arrival notification (once per day)
                if (NotificationGuard.shouldFire(context, userId, "geofence", "arrived_at_school")) {
                    NotificationHelper.notifyStudentArrivedAtSchool(context, "");
                    NotificationStore.getInstance().save(context, userId,
                            "Arrived at School",
                            "Welcome! Your arrival at school has been recorded.");
                }
            }
        }

        if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Log.d(TAG, "Exited school geofence — resetting status if not yet marked");
            String exitUserId = LocalCacheManager.getInstance(context).getCachedUid();
            if (exitUserId != null) {
                GeofenceRepository.getInstance().resetStatusOnExit(exitUserId);
            }
        }
    }

    // ── Schedule helpers ──────────────────────────────────────────────────────

    /**
     * Returns the Firestore document ID of the subject whose schedule window
     * includes the current time on the current day, or "" if none match.
     */
    private String findActiveSubjectId(List<SubjectRepository.SubjectItem> subjects) {
        if (subjects == null) return "";
        Calendar now        = Calendar.getInstance();
        int calDow          = now.get(Calendar.DAY_OF_WEEK);
        int nowMin          = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        for (SubjectRepository.SubjectItem item : subjects) {
            if (item.schedule == null || item.schedule.isEmpty()) continue;
            String[] parts = item.schedule.trim().split("\\s+", 2);
            if (parts.length < 2) continue;
            if (!dayMatchesToday(parts[0], calDow)) continue;
            String[] times = parts[1].split("-", 2);
            if (times.length < 2) continue;
            int startMin = parseTime(times[0].trim());
            int endMin   = parseTime(times[1].trim());
            if (startMin < 0 || endMin < 0) continue;
            if (nowMin >= startMin && nowMin <= endMin) {
                Log.d(TAG, "Active subject at geofence enter: " + item.id + " (" + item.name + ")");
                return item.id != null ? item.id : "";
            }
        }
        return "";
    }

    private boolean dayMatchesToday(String dayCodes, int calDow) {
        dayCodes = dayCodes.toUpperCase(Locale.ENGLISH);
        boolean hasThu = dayCodes.contains("TH");
        boolean hasTue = dayCodes.replace("TH", "").contains("T");
        boolean hasSun = dayCodes.contains("SU");
        boolean hasSat = !hasSun && dayCodes.contains("S");
        switch (calDow) {
            case Calendar.MONDAY:    return dayCodes.contains("M");
            case Calendar.TUESDAY:   return hasTue;
            case Calendar.WEDNESDAY: return dayCodes.contains("W");
            case Calendar.THURSDAY:  return hasThu;
            case Calendar.FRIDAY:    return dayCodes.contains("F");
            case Calendar.SATURDAY:  return hasSat;
            case Calendar.SUNDAY:    return hasSun;
            default:                 return false;
        }
    }

    private int parseTime(String t) {
        try {
            t = t.trim().toLowerCase(Locale.ENGLISH);
            boolean pm = t.contains("pm");
            boolean am = t.contains("am");
            t = t.replace("pm", "").replace("am", "").trim();
            String[] hm = t.split(":");
            int h = Integer.parseInt(hm[0].trim());
            int m = Integer.parseInt(hm[1].trim());
            if (pm && h != 12) h += 12;
            if (am && h == 12) h = 0;
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }
}