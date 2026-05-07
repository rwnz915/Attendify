package com.example.attendify.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.attendify.MainActivity;
import com.example.attendify.R;

/**
 * NotificationHelper
 *
 * Central class for posting all in-app notifications via NotificationCompat.
 * Channels:
 *   CHANNEL_ATTENDANCE  – attendance status events (student: class start, late, absent)
 *   CHANNEL_CLASS       – class management (teacher: class starting; secretary: class starting)
 *   CHANNEL_APPROVALS   – new pending excuse-letter approvals (teacher)
 *
 * Each notification is also stored to Firestore so the per-role
 * Notification Fragment can display a persistent, clearable list.
 */
public class NotificationHelper {

    // ── Channel IDs ──────────────────────────────────────────────────────────
    public static final String CHANNEL_ATTENDANCE = "attendify_attendance";
    public static final String CHANNEL_CLASS      = "attendify_class";
    public static final String CHANNEL_APPROVALS  = "attendify_approvals";

    // ── Notification IDs (use unique ints so stacking works) ─────────────────
    public static final int NOTIF_CLASS_STARTING      = 1001;
    public static final int NOTIF_STUDENT_LATE        = 1002;
    public static final int NOTIF_STUDENT_ABSENT      = 1003;
    public static final int NOTIF_NEW_APPROVAL        = 1004;
    public static final int NOTIF_CLASS_STARTING_SEC  = 1005;
    public static final int NOTIF_STUDENT_CLASS_NOW   = 1006; // student: class is starting now
    public static final int NOTIF_STUDENT_ARRIVED     = 1007; // student: geofence enter detected

    // ── Intent extras ────────────────────────────────────────────────────────
    public static final String EXTRA_OPEN_NOTIF_PAGE = "open_notif_page";

    private NotificationHelper() {}

    // ── Channel setup (call once from Application.onCreate) ──────────────────

    public static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ATTENDANCE,
                "Attendance Alerts",
                NotificationManager.IMPORTANCE_HIGH));

        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_CLASS,
                "Class Notifications",
                NotificationManager.IMPORTANCE_HIGH));

        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_APPROVALS,
                "Approval Requests",
                NotificationManager.IMPORTANCE_DEFAULT));
    }

    // ── Generic builder ───────────────────────────────────────────────────────

    private static NotificationCompat.Builder base(Context ctx,
                                                   String channel,
                                                   String title,
                                                   String body) {
        Intent tap = new Intent(ctx, MainActivity.class);
        tap.putExtra(EXTRA_OPEN_NOTIF_PAGE, true);
        tap.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(ctx, 0, tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
    }

    private static void post(Context ctx, int id, NotificationCompat.Builder b) {
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, b.build());
    }

    // ── Student notifications ─────────────────────────────────────────────────

    /** Class about to start (30 min before) */
    public static void notifyStudentClassSoon(Context ctx, String subjectName, String time) {
        post(ctx, NOTIF_CLASS_STARTING,
                base(ctx, CHANNEL_CLASS,
                        "Class Starting Soon",
                        subjectName + " starts at " + time + ". Get ready!"));
    }

    /** Class is starting now (student) */
    public static void notifyStudentClassNow(Context ctx, String subjectName, String time) {
        post(ctx, NOTIF_STUDENT_CLASS_NOW,
                base(ctx, CHANNEL_CLASS,
                        "Class is Starting Now",
                        subjectName + " is starting now at " + time + ". Don't be late!"));
    }

    /** Student has been detected inside the school geofence */
    public static void notifyStudentArrivedAtSchool(Context ctx, String subjectName) {
        String body = (subjectName != null && !subjectName.isEmpty())
                ? "Welcome! Your arrival has been recorded for " + subjectName + "."
                : "Welcome! Your arrival at school has been recorded.";
        post(ctx, NOTIF_STUDENT_ARRIVED,
                base(ctx, CHANNEL_ATTENDANCE,
                        "Arrived at School",
                        body));
    }

    /** Student was marked late */
    public static void notifyStudentLate(Context ctx, String subjectName) {
        post(ctx, NOTIF_STUDENT_LATE,
                base(ctx, CHANNEL_ATTENDANCE,
                        "You're Late",
                        "You were marked late for " + subjectName + "."));
    }

    /** Student was marked absent */
    public static void notifyStudentAbsent(Context ctx, String subjectName) {
        post(ctx, NOTIF_STUDENT_ABSENT,
                base(ctx, CHANNEL_ATTENDANCE,
                        "Marked Absent",
                        "You were marked absent for " + subjectName + ". Submit an excuse letter if needed."));
    }

    // ── Teacher notifications ─────────────────────────────────────────────────

    /** Class starting now (teacher) */
    public static void notifyTeacherClassStarting(Context ctx, String subjectName, String section) {
        post(ctx, NOTIF_CLASS_STARTING,
                base(ctx, CHANNEL_CLASS,
                        "Class Starting",
                        subjectName + " (" + section + ") is starting now."));
    }

    /** New excuse-letter approval pending */
    public static void notifyTeacherNewApproval(Context ctx, int pendingCount) {
        String body = pendingCount == 1
                ? "You have 1 new excuse letter to review."
                : "You have " + pendingCount + " excuse letters pending review.";
        post(ctx, NOTIF_NEW_APPROVAL,
                base(ctx, CHANNEL_APPROVALS, "New Approval Request", body));
    }

    // ── Secretary notifications ───────────────────────────────────────────────

    /** Class starting now (secretary) */
    public static void notifySecretaryClassStarting(Context ctx, String subjectName, String time) {
        post(ctx, NOTIF_CLASS_STARTING_SEC,
                base(ctx, CHANNEL_CLASS,
                        "Class Starting",
                        subjectName + " is starting at " + time + "."));
    }
}