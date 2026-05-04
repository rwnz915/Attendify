package com.example.attendify.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * NotificationGuard
 *
 * Prevents duplicate notifications by recording a "fired" flag in
 * SharedPreferences keyed by:
 *   userId + date (yyyy-MM-dd) + subjectId/context + notificationType
 *
 * The flag resets automatically on a new calendar day because the date
 * is baked into the key.
 *
 * Usage:
 *   if (NotificationGuard.shouldFire(ctx, userId, subjectId, "class_soon")) {
 *       NotificationHelper.notifyStudentClassSoon(...);
 *   }
 */
public class NotificationGuard {

    private static final String PREFS = "notif_guard";

    private NotificationGuard() {}

    /**
     * Returns true the FIRST time this combination is checked today.
     * Returns false on every subsequent call with the same arguments on the same day.
     * Automatically marks as fired on the first true return.
     */
    public static boolean shouldFire(Context ctx, String userId,
                                     String subjectOrContext, String type) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date());
        String key = userId + "|" + today + "|" + subjectOrContext + "|" + type;

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(key, false)) {
            return false; // already fired today
        }
        prefs.edit().putBoolean(key, true).apply();
        return true;
    }

    /** Manually reset a specific guard (e.g. for testing or after midnight rollover). */
    public static void reset(Context ctx, String userId,
                             String subjectOrContext, String type) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date());
        String key = userId + "|" + today + "|" + subjectOrContext + "|" + type;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply();
    }
}