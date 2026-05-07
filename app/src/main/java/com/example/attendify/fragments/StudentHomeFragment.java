package com.example.attendify.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.notifications.ClassNotificationScheduler;
import com.example.attendify.notifications.NotificationHelper;
import com.example.attendify.notifications.NotificationGuard;
import com.example.attendify.notifications.NotificationGuard;
import com.example.attendify.notifications.NotificationStore;
import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.activities.NotificationActivity;
import com.example.attendify.ThemeManager;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.GeofenceRepository;
import com.example.attendify.repository.SubjectRepository;
import com.example.attendify.fragments.ExcuseLetterFragment;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.example.attendify.ThemeApplier;

/**
 * Student Home screen.
 *
 * Header card      ->  Today's active/next subject (like teacher UI).
 *                      Shows attendance status badge (Present / Late / Absent)
 *                      if the student has a record for the active subject today.
 *
 * Stat cards       ->  ALL-TIME totals (Present / Late / Absent). Never resets.
 *
 * Recent list      ->  TODAY's attendance records only. Resets each new day.
 */
public class StudentHomeFragment extends Fragment {

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_home, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        reapplyTheme();
    }

    private void reapplyTheme() {
        View view = getView();
        if (view == null) return;
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;
        String role = user.getRole();

        ThemeApplier.applyHeader(requireContext(), role,
                view.findViewById(R.id.student_header_bg));
        ThemeApplier.applyOval(requireContext(), role,
                view.findViewById(R.id.iv_profile_circle));

        int primary   = ThemeManager.getPrimaryColor(requireContext(), role);
        int lightTint = ThemeManager.getLightTintColor(requireContext(), role);

        // Excuse Letter quick action
        android.widget.ImageView btnClassList = view.findViewById(R.id.btn_class_list);
        if (btnClassList != null) {
            android.graphics.drawable.GradientDrawable gd1 = new android.graphics.drawable.GradientDrawable();
            gd1.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            gd1.setCornerRadius(50f * requireContext().getResources().getDisplayMetrics().density);
            gd1.setColor(lightTint);
            btnClassList.setBackground(gd1);
            btnClassList.setColorFilter(primary);
        }

        // Settings quick action
        android.widget.ImageView btnSettings = view.findViewById(R.id.btn_student_settings);
        if (btnSettings != null) {
            android.graphics.drawable.GradientDrawable gd2 = new android.graphics.drawable.GradientDrawable();
            gd2.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            gd2.setCornerRadius(50f * requireContext().getResources().getDisplayMetrics().density);
            gd2.setColor(lightTint);
            btnSettings.setBackground(gd2);
            btnSettings.setColorFilter(primary);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Apply saved theme to header
        UserProfile userForTheme = AuthRepository.getInstance().getLoggedInUser();
        if (userForTheme != null) {
            String role = userForTheme.getRole();

            ThemeApplier.applyHeader(requireContext(), role,
                    view.findViewById(R.id.student_header_bg));
            ThemeApplier.applyOval(requireContext(), role,
                    view.findViewById(R.id.iv_profile_circle));

            // Excuse Letter quick action — light tint bg + primary icon tint
            android.widget.ImageView btnClassList = view.findViewById(R.id.btn_class_list);
            if (btnClassList != null) {
                ThemeApplier.applyLightTint(requireContext(), role, btnClassList, 12);
                btnClassList.setColorFilter(ThemeManager.getPrimaryColor(requireContext(), role));
            }

            // Settings quick action — same treatment
            android.widget.ImageView btnSettings = view.findViewById(R.id.btn_student_settings);
            if (btnSettings != null) {
                ThemeApplier.applyLightTint(requireContext(), role, btnSettings, 12);
                btnSettings.setColorFilter(ThemeManager.getPrimaryColor(requireContext(), role));
            }
        }

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        // ── Recovery: flush offline cache + catch missed geofence ────────────
        GeofenceRepository.getInstance().flushPendingEntries(requireContext(), user.getId());
        checkAndRecoverMissedGeofence(requireContext(), user.getId(), "");

        ((TextView) view.findViewById(R.id.tv_student_name)).setText(user.getFullName());

        // ── Set today's date label on the combined stat card ──────────────────
        TextView tvDateLabel = view.findViewById(R.id.tv_today_date_label);
        if (tvDateLabel != null) {
            String dateStr = new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.ENGLISH)
                    .format(new Date());
            tvDateLabel.setText(dateStr);
        }

        String today     = DATE_FMT.format(new Date());
        String todayAbbr = getTodayDayAbbr();

        // Step 1: Load subjects to find today's active/next subject
        SubjectRepository.getInstance().getStudentSubjects(user.getSection(),
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                        if (getActivity() == null) return;

                        SubjectRepository.SubjectItem active = getActiveSubject(subjects, todayAbbr);
                        boolean isNext = (active == null);
                        if (active == null) active = getNextSubject(subjects, todayAbbr);
                        final SubjectRepository.SubjectItem todaySubject = active;
                        final boolean finalIsNextEarly = isNext;

                        // If there is an active (non-next) subject, check if it's suspended
                        if (!isNext && todaySubject != null) {
                            // We need the teacher ID from the subject to build the doc ID.
                            // suspended_classes doc format: {teacherId}_{subjectId}_{date}
                            // Query by subjectId + date since student doesn't know teacherId.
                            FirebaseFirestore.getInstance()
                                    .collection("suspended_classes")
                                    .whereEqualTo("subjectId", todaySubject.id)
                                    .whereEqualTo("date", today)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener(snap -> {
                                        if (getActivity() == null) return;

                                        if (!snap.isEmpty()) {
                                            // Class is suspended — find the next subject after this one
                                            SubjectRepository.SubjectItem nextAfter =
                                                    getNextSubjectAfter(subjects, todayAbbr, todaySubject);

                                            getActivity().runOnUiThread(() ->
                                                    updateTodayClassCard(view, todaySubject,
                                                            nextAfter, "Suspended", false));

                                            // Still load attendance stats
                                            loadAttendanceStats(view, user, today, todaySubject, false);
                                        } else {
                                            // Not suspended — proceed normally
                                            getActivity().runOnUiThread(() ->
                                                    updateTodayClassCard(view, todaySubject,
                                                            null, null, false));
                                            loadAttendanceStats(view, user, today, todaySubject, false);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        // On error, proceed without suspension check
                                        if (getActivity() == null) return;
                                        getActivity().runOnUiThread(() ->
                                                updateTodayClassCard(view, todaySubject,
                                                        null, null, false));
                                        loadAttendanceStats(view, user, today, todaySubject, false);
                                    });
                        } else {
                            // No active class (showing next) — no suspension check needed
                            getActivity().runOnUiThread(() ->
                                    updateTodayClassCard(view, todaySubject, null, null, finalIsNextEarly));
                            loadAttendanceStats(view, user, today, todaySubject, finalIsNextEarly);
                        }
                    }

                    @Override
                    public void onFailure(String err) {
                        // Still try loading attendance even if subjects fail
                        loadAttendanceStats(view, user, today, null, true);
                    }
                });

        // Wire up "Excuse Letter" quick action
        view.findViewById(R.id.btn_class_list).setOnClickListener(v ->
                startActivity(new android.content.Intent(getActivity(),
                        com.example.attendify.activities.ExcuseLetterActivity.class)));

        // Excuse Letter
        view.findViewById(R.id.btn_class_list).setOnClickListener(v -> {
            startActivity(new android.content.Intent(getActivity(),
                    com.example.attendify.activities.ExcuseLetterActivity.class));
        });

        // Subjects — switch to the Subjects tab (index 1)
        view.findViewById(R.id.btn_student_subjects).setOnClickListener(v -> {
            if (getActivity() instanceof com.example.attendify.MainActivity)
                ((com.example.attendify.MainActivity) getActivity()).selectTab(1);
        });

        // Settings
        // NEW
        view.findViewById(R.id.btn_student_settings).setOnClickListener(v ->
                startActivity(new android.content.Intent(requireContext(),
                        com.example.attendify.activities.AppSettingsActivity.class)));

        // Notification bell → open notifications page
        View notifBtn = view.findViewById(R.id.fl_notif_container);
        if (notifBtn != null) {
            notifBtn.setClickable(true);
            notifBtn.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity)
                    startActivity(new android.content.Intent(requireActivity(), NotificationActivity.class));
            });
        }

        // Show/hide unread notification dot
        UserProfile meForDot = AuthRepository.getInstance().getLoggedInUser();
        if (meForDot != null) {
            View notifDot = view.findViewById(R.id.view_notif_dot);
            if (notifDot != null) {
                boolean hasUnread = NotificationStore.getInstance().hasUnread(requireContext(), meForDot.getId());
                notifDot.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
            }
        }

        // TEMP TEST — remove before production
        /*if (user != null) {
            NotificationGuard.reset(requireContext(), user.getId(), "geofence", "arrived_at_school");
            NotificationHelper.notifyStudentArrivedAtSchool(requireContext(), "");
            NotificationStore.getInstance().save(requireContext(), user.getId(),
                    "Arrived at School", "Welcome! Your arrival at school has been recorded.");
        }*/
    }

    // ── Today's Class card ────────────────────────────────────────────────────

    /**
     * @param subj       the active/next subject, or null if no class today
     * @param nextSubject the next subject after a suspended one (may be null)
     * @param status     "Present", "Late", "Absent", "Suspended", "In school", or null
     * @param isNext     true if subj is a future class (not currently ongoing)
     */
    private void updateTodayClassCard(View view,
                                      SubjectRepository.SubjectItem subj,
                                      SubjectRepository.SubjectItem nextSubject,
                                      @Nullable String status,
                                      boolean isNext) {
        TextView tvLabel   = view.findViewById(R.id.tv_class_label);
        TextView tvName    = view.findViewById(R.id.tv_today_subject_name);
        TextView tvTime    = view.findViewById(R.id.tv_today_subject_time);
        TextView tvStatus  = view.findViewById(R.id.tv_today_status);
        TextView tvSection = view.findViewById(R.id.tv_today_subject_section);

        if (subj == null) {
            if (tvLabel != null) tvLabel.setText("Today's Class");
            tvName.setText("No class today");
            tvTime.setText("—");
            tvStatus.setVisibility(View.GONE);
            return;
        }

        boolean isSuspended = "Suspended".equals(status);

        if (isSuspended) {
            if (tvLabel != null) tvLabel.setText("Suspended");
            tvName.setText(subj.name);
            tvSection.setText("- " + subj.section);
            tvName.setAlpha(0.5f);
            tvSection.setAlpha(0.5f);

            // Show suspended badge
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText("Suspended");
            tvStatus.setTextColor(0xFFFF6B00);
            tvStatus.setBackgroundResource(R.drawable.bg_badge_suspended);

            // Show next subject time below if available
            if (nextSubject != null) {
                String nextTime = formatScheduleTime(nextSubject.schedule);
                String nextDate = getNextClassDate(nextSubject.schedule);
                tvTime.setText("Next: " + nextSubject.name + "  •  " + nextTime + "  •  " + nextDate);
                tvTime.setAlpha(1f);
            } else {
                tvTime.setText(formatScheduleTime(subj.schedule));
                tvTime.setAlpha(0.5f);
            }
            return;
        }

        // Reset alpha in case we previously showed suspended
        tvName.setAlpha(1f);
        if (tvSection != null) tvSection.setAlpha(1f);
        tvTime.setAlpha(1f);

        if (tvLabel != null) tvLabel.setText(isNext ? "Next Class" : "Today's Class");
        tvName.setText(subj.name);
        tvSection.setText("- " + subj.section);

        String formattedTime = formatScheduleTime(subj.schedule);
        if (isNext) {
            String dateLabel = getNextClassDate(subj.schedule);
            tvTime.setText(formattedTime + "  •  " + dateLabel);
        } else {
            tvTime.setText(formattedTime);
        }

        // Hide status badge for upcoming classes
        if (isNext || status == null || status.isEmpty()) {
            tvStatus.setVisibility(View.GONE);
        } else {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText(status);
            switch (status) {
                case "Present":
                    tvStatus.setTextColor(0xFF2E7D32);
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_present);
                    break;
                case "Late":
                    tvStatus.setTextColor(0xFFF57F17);
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_late);
                    break;
                case "Absent":
                    tvStatus.setTextColor(0xFFC62828);
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_absent);
                    break;
                case "In school":
                case "in school":
                    tvStatus.setTextColor(0xFF1565C0);
                    tvStatus.setBackgroundResource(R.drawable.bg_button_white);
                    break;
                default:
                    tvStatus.setTextColor(0xFF1565C0);
                    tvStatus.setBackgroundResource(R.drawable.bg_button_white);
                    break;
            }
        }

        // Show "In school since H:MM AM" for any non-absent status
        if (!isNext && status != null && !status.isEmpty() && !isSuspended
                && !"Absent".equalsIgnoreCase(status)) {
            UserProfile u = AuthRepository.getInstance().getLoggedInUser();
            if (u != null) {
                TextView tvInSchool = view.findViewById(R.id.tv_in_school_time);
                ClassNotificationScheduler.getInstance().getEarliestTimeInToday(
                        requireContext(), u.getId(),
                        subj.id != null ? subj.id : "",
                        (timeIn, subId) -> {
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> {
                                if (tvInSchool != null && timeIn != null && !timeIn.isEmpty()) {
                                    tvInSchool.setText("In school since " + formatInSchoolTime(timeIn));
                                    tvInSchool.setVisibility(View.VISIBLE);
                                }
                            });
                        });
            }
        }
    }

    // ── Today's attendance list ───────────────────────────────────────────────

    private void bindTodayList(View view, List<AttendanceRecord> todayRecords) {
        LinearLayout container = view.findViewById(R.id.attendance_list);
        container.removeAllViews();

        if (todayRecords.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No attendance recorded today yet.");
            empty.setTextColor(0xFF9E9E9E);
            empty.setPadding(0, 16, 0, 16);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            container.addView(empty);
            return;
        }

        LayoutInflater li = LayoutInflater.from(requireContext());
        for (AttendanceRecord r : todayRecords) {
            View item = li.inflate(R.layout.item_student_attendance_record, container, false);
            ((TextView) item.findViewById(R.id.tv_record_date)).setText(formatDisplayDate(r.getDate()));
            ((TextView) item.findViewById(R.id.tv_record_subject)).setText(r.getSubject());
            ((TextView) item.findViewById(R.id.tv_record_time)).setText(r.getTime());

            TextView tvStatus = item.findViewById(R.id.tv_record_status);
            String status = r.getStatusLabel() != null ? r.getStatusLabel() : "";
            tvStatus.setText(status);
            switch (status) {
                case "Present":
                    tvStatus.setTextColor(0xFF2E7D32);
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_present);
                    break;
                case "Late":
                    tvStatus.setTextColor(0xFFF57F17);
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_late);
                    break;
                case "Absent":
                case "in school":
                    tvStatus.setTextColor(0xFFC62828);
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_absent);
                    break;
            }
            container.addView(item);
        }
    }

    // ── Schedule helpers ──────────────────────────────────────────────────────

    private SubjectRepository.SubjectItem getActiveSubject(
            List<SubjectRepository.SubjectItem> subjects, String todayAbbr) {
        for (SubjectRepository.SubjectItem s : subjects) {
            if (s.schedule == null) continue;
            if (runsToday(s.schedule, todayAbbr) && isClassActive(s.schedule)) return s;
        }
        return null;
    }

    private SubjectRepository.SubjectItem getNextSubject(
            List<SubjectRepository.SubjectItem> subjects, String todayAbbr) {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mma", Locale.ENGLISH);
        Calendar now = Calendar.getInstance();

        for (int offset = 0; offset < 7; offset++) {
            Calendar dayCal = Calendar.getInstance();
            dayCal.add(Calendar.DAY_OF_YEAR, offset);
            String dayAbbr = calToDayAbbr(dayCal.get(Calendar.DAY_OF_WEEK));

            SubjectRepository.SubjectItem earliest = null;
            Date earliestStart = null;

            for (SubjectRepository.SubjectItem s : subjects) {
                if (s.schedule == null) continue;
                String[] scheduleParts = s.schedule.trim().split("\\s+", 2);
                if (scheduleParts.length < 1) continue;
                if (!matchesDay(scheduleParts[0].toUpperCase(Locale.ENGLISH), dayAbbr)) continue;

                String startStr = extractStartTime(s.schedule);
                if (startStr == null) continue;
                try {
                    Date parsed = sdf.parse(startStr.toUpperCase(Locale.ENGLISH));
                    if (parsed == null) continue;

                    Calendar startCal;
                    if (offset == 0) {
                        startCal = toTodayCal(parsed);
                        if (!startCal.after(now)) continue;
                    } else {
                        startCal = (Calendar) dayCal.clone();
                        Calendar tmp = Calendar.getInstance();
                        tmp.setTime(parsed);
                        startCal.set(Calendar.HOUR_OF_DAY, tmp.get(Calendar.HOUR_OF_DAY));
                        startCal.set(Calendar.MINUTE, tmp.get(Calendar.MINUTE));
                        startCal.set(Calendar.SECOND, 0);
                        startCal.set(Calendar.MILLISECOND, 0);
                    }

                    if (earliestStart == null || startCal.getTime().before(earliestStart)) {
                        earliestStart = startCal.getTime();
                        earliest = s;
                    }
                } catch (ParseException ignored) {}
            }

            if (earliest != null) return earliest;
        }
        return null;
    }

    private boolean matchesDay(String daysPart, String abbr) {
        switch (abbr) {
            case "TH": return daysPart.contains("TH");
            case "T":  return daysPart.replace("TH", "").contains("T");
            case "SU": return daysPart.contains("SU");
            case "S":  return daysPart.replace("SU", "").contains("S");
            default:   return daysPart.contains(abbr);
        }
    }

    private String calToDayAbbr(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY:    return "M";
            case Calendar.TUESDAY:   return "T";
            case Calendar.WEDNESDAY: return "W";
            case Calendar.THURSDAY:  return "TH";
            case Calendar.FRIDAY:    return "F";
            case Calendar.SATURDAY:  return "S";
            case Calendar.SUNDAY:    return "SU";
            default: return "";
        }
    }

    private String getNextClassDate(String schedule) {
        if (schedule == null) return "";
        String[] parts = schedule.trim().split("\\s+", 2);
        if (parts.length == 0) return "";
        String daysPart = parts[0].toUpperCase(Locale.ENGLISH);

        SimpleDateFormat outFmt = new SimpleDateFormat("MMM d", Locale.ENGLISH);
        Calendar cal = Calendar.getInstance();

        for (int i = 0; i < 7; i++) {
            String abbr = calToDayAbbr(cal.get(Calendar.DAY_OF_WEEK));
            if (matchesDay(daysPart, abbr)) {
                if (i == 0) {
                    String startStr = parts.length > 1 ? parts[1].split("-")[0].trim() : null;
                    if (startStr != null) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("h:mma", Locale.ENGLISH);
                            Date start = sdf.parse(startStr.toUpperCase(Locale.ENGLISH));
                            if (start != null && Calendar.getInstance().before(toTodayCal(start))) {
                                return outFmt.format(cal.getTime());
                            }
                        } catch (ParseException ignored) {}
                    }
                } else {
                    return outFmt.format(cal.getTime());
                }
            }
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return outFmt.format(Calendar.getInstance().getTime());
    }

    private boolean isClassActive(String schedule) {
        try {
            String[] parts = schedule.trim().split("\\s+", 2);
            if (parts.length < 2) return false;
            String[] times = parts[1].split("-");
            if (times.length < 2) return false;
            SimpleDateFormat sdf = new SimpleDateFormat("h:mma", Locale.ENGLISH);
            Date start = sdf.parse(times[0].trim().toUpperCase(Locale.ENGLISH));
            Date end   = sdf.parse(times[1].trim().toUpperCase(Locale.ENGLISH));
            if (start == null || end == null) return false;
            Calendar now = Calendar.getInstance();
            return now.after(toTodayCal(start)) && now.before(toTodayCal(end));
        } catch (ParseException e) { return false; }
    }

    private boolean runsToday(String schedule, String todayAbbr) {
        String[] parts = schedule.trim().split("\\s+", 2);
        if (parts.length == 0) return false;
        String days = parts[0].toUpperCase(Locale.ENGLISH);
        switch (todayAbbr) {
            case "TH": return days.contains("TH");
            case "T":  return days.replace("TH", "").contains("T");
            case "SU": return days.contains("SU");
            case "S":  return days.replace("SU", "").contains("S");
            default:   return days.contains(todayAbbr);
        }
    }

    private String extractStartTime(String schedule) {
        String[] parts = schedule.trim().split("\\s+", 2);
        if (parts.length < 2) return null;
        return parts[1].split("-")[0].trim();
    }

    private String formatScheduleTime(String schedule) {
        if (schedule == null) return "—";
        String[] parts = schedule.trim().split("\\s+", 2);
        if (parts.length < 2) return schedule;
        String[] times = parts[1].split("-");
        if (times.length < 2) return parts[1];
        try {
            SimpleDateFormat inFmt  = new SimpleDateFormat("h:mma", Locale.ENGLISH);
            SimpleDateFormat outFmt = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
            String s = outFmt.format(inFmt.parse(times[0].trim().toUpperCase(Locale.ENGLISH)));
            String e = outFmt.format(inFmt.parse(times[1].trim().toUpperCase(Locale.ENGLISH)));
            return s + " - " + e;
        } catch (ParseException ex) { return parts[1].replace("-", " - "); }
    }

    private Calendar toTodayCal(Date time) {
        Calendar tmp = Calendar.getInstance();
        tmp.setTime(time);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, tmp.get(Calendar.HOUR_OF_DAY));
        cal.set(Calendar.MINUTE,      tmp.get(Calendar.MINUTE));
        cal.set(Calendar.SECOND,      0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private String getTodayDayAbbr() {
        switch (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY:    return "M";
            case Calendar.TUESDAY:   return "T";
            case Calendar.WEDNESDAY: return "W";
            case Calendar.THURSDAY:  return "TH";
            case Calendar.FRIDAY:    return "F";
            case Calendar.SATURDAY:  return "S";
            case Calendar.SUNDAY:    return "SU";
            default: return "";
        }
    }

    private String formatDisplayDate(String isoDate) {
        try {
            Date d = DATE_FMT.parse(isoDate);
            return new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(d);
        } catch (ParseException e) { return isoDate; }
    }

    // ── Extracted attendance stats loader ─────────────────────────────────────

    private void loadAttendanceStats(View view, UserProfile user, String today,
                                     SubjectRepository.SubjectItem todaySubject,
                                     boolean isNext) {
        AttendanceRepository.getInstance().getStudentHistory(user.getId(),
                new AttendanceRepository.AttendanceCallback() {
                    @Override
                    public void onSuccess(List<AttendanceRecord> allHistory) {
                        if (getActivity() == null) return;

                        int totalPresent = 0, totalLate = 0, totalAbsent = 0;
                        List<AttendanceRecord> todayRecords = new ArrayList<>();

                        for (AttendanceRecord r : allHistory) {
                            totalPresent += r.getPresent();
                            totalLate    += r.getLate();
                            totalAbsent  += r.getAbsent();
                            if (today.equals(r.getDate())) todayRecords.add(r);
                        }

                        String headerStatus = null;
                        if (todaySubject != null) {
                            for (AttendanceRecord r : todayRecords) {
                                if (todaySubject.name != null &&
                                        todaySubject.name.equals(r.getSubject())) {
                                    headerStatus = r.getStatusLabel();
                                    break;
                                }
                            }
                        }

                        final int fp = totalPresent, fl = totalLate, fa = totalAbsent;
                        final List<AttendanceRecord> todayFinal = todayRecords;
                        final String prelimStatus = headerStatus;

                        if (prelimStatus == null && todaySubject != null && !isNext) {
                            FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document(user.getId())
                                    .get()
                                    .addOnSuccessListener(userDoc -> {
                                        // ✅ FIXED — require statusDate to match today
                                        String userStatus   = userDoc.getString("status");
                                        String statusDate   = userDoc.getString("statusDate");
                                        boolean inSchoolToday = "in school".equalsIgnoreCase(userStatus)
                                                && today.equals(statusDate);
                                        final String resolvedStatus = inSchoolToday ? "In school" : null;
                                        if (getActivity() == null) return;
                                        getActivity().runOnUiThread(() -> {
                                            // Never overwrite a Suspended card with In school / attendance status
                                            TextView tvLabel = view.findViewById(R.id.tv_class_label);
                                            boolean alreadySuspended = tvLabel != null
                                                    && "Suspended".equals(tvLabel.getText().toString());
                                            if (!alreadySuspended) {
                                                updateTodayClassCard(view, todaySubject, null,
                                                        resolvedStatus, isNext);
                                            }
                                            setStatCounts(view, fp, fl, fa);
                                            bindTodayList(view, todayFinal);
                                        });
                                    })
                                    .addOnFailureListener(e -> {
                                        if (getActivity() == null) return;
                                        getActivity().runOnUiThread(() -> {
                                            TextView tvLabel = view.findViewById(R.id.tv_class_label);
                                            boolean alreadySuspended = tvLabel != null
                                                    && "Suspended".equals(tvLabel.getText().toString());
                                            if (!alreadySuspended) {
                                                updateTodayClassCard(view, todaySubject, null,
                                                        null, isNext);
                                            }
                                            setStatCounts(view, fp, fl, fa);
                                            bindTodayList(view, todayFinal);
                                        });
                                    });
                        } else {
                            final String finalStatus = prelimStatus;
                            if (todaySubject != null && finalStatus != null) {
                                String sid = todaySubject.id != null ? todaySubject.id : todaySubject.name;
                                if ("Late".equalsIgnoreCase(finalStatus)
                                        && NotificationGuard.shouldFire(requireContext(),
                                        user.getId(), sid, "student_late")) {
                                    NotificationHelper.notifyStudentLate(requireContext(),
                                            todaySubject.name);
                                    NotificationStore.getInstance().save(requireContext(),
                                            user.getId(), "You're Late",
                                            "You were marked late for " + todaySubject.name + ".");
                                } else if ("Absent".equalsIgnoreCase(finalStatus)
                                        && NotificationGuard.shouldFire(requireContext(),
                                        user.getId(), sid, "student_absent")) {
                                    NotificationHelper.notifyStudentAbsent(requireContext(),
                                            todaySubject.name);
                                    NotificationStore.getInstance().save(requireContext(),
                                            user.getId(), "Marked Absent",
                                            "You were marked absent for " + todaySubject.name
                                                    + ". Submit an excuse letter if needed.");
                                }
                            }
                            getActivity().runOnUiThread(() -> {
                                // Never overwrite Suspended card with attendance status
                                TextView tvLabel = view.findViewById(R.id.tv_class_label);
                                boolean alreadySuspended = tvLabel != null
                                        && "Suspended".equals(tvLabel.getText().toString());
                                if (!alreadySuspended) {
                                    updateTodayClassCard(view, todaySubject, null,
                                            finalStatus, isNext);
                                }
                                setStatCounts(view, fp, fl, fa);
                                bindTodayList(view, todayFinal);
                            });
                        }
                    }

                    @Override
                    public void onFailure(String err) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> bindTodayList(view, new ArrayList<>()));
                    }
                });
    }

    private void setStatCounts(View view, int present, int late, int absent) {
        ((TextView) view.findViewById(R.id.tv_present_count)).setText(String.valueOf(present));
        ((TextView) view.findViewById(R.id.tv_late_count)).setText(String.valueOf(late));
        ((TextView) view.findViewById(R.id.tv_absent_count)).setText(String.valueOf(absent));
    }

    /**
     * Returns the next subject that starts AFTER the given suspended subject today,
     * or the next subject on any upcoming day if none today.
     */
    private SubjectRepository.SubjectItem getNextSubjectAfter(
            List<SubjectRepository.SubjectItem> subjects,
            String todayAbbr,
            SubjectRepository.SubjectItem suspended) {

        // Find suspended class end time in minutes
        int suspendedEndMin = -1;
        if (suspended != null && suspended.schedule != null) {
            try {
                String[] parts = suspended.schedule.trim().split("\\s+", 2);
                if (parts.length >= 2) {
                    String[] times = parts[1].split("-");
                    if (times.length >= 2)
                        suspendedEndMin = parseTimeToMinutes(times[1].trim());
                }
            } catch (Exception ignored) {}
        }

        // Look for the earliest subject today that starts after the suspended one ends
        SubjectRepository.SubjectItem earliest = null;
        int earliestStart = Integer.MAX_VALUE;

        for (SubjectRepository.SubjectItem s : subjects) {
            if (s == suspended || s.schedule == null) continue;
            String[] parts = s.schedule.trim().split("\\s+", 2);
            if (parts.length < 1) continue;
            if (!matchesDay(parts[0].toUpperCase(Locale.ENGLISH), todayAbbr)) continue;
            int startMin = parseTimeToMinutes(parts.length > 1
                    ? parts[1].split("-")[0].trim() : "");
            if (startMin > suspendedEndMin && startMin < earliestStart) {
                earliestStart = startMin;
                earliest = s;
            }
        }

        // If nothing found today, fall back to getNextSubject (any upcoming day)
        if (earliest == null) earliest = getNextSubject(subjects, todayAbbr);
        return earliest;
    }

    private int parseTimeToMinutes(String t) {
        if (t == null || t.isEmpty()) return -1;
        try {
            t = t.trim().toLowerCase(Locale.ENGLISH);
            boolean pm = t.contains("pm"), am = t.contains("am");
            t = t.replace("pm", "").replace("am", "").trim();
            String[] hm = t.split(":");
            int h = Integer.parseInt(hm[0].trim());
            int m = hm.length > 1 ? Integer.parseInt(hm[1].trim()) : 0;
            if (pm && h != 12) h += 12;
            if (am && h == 12) h = 0;
            return h * 60 + m;
        } catch (Exception e) { return -1; }
    }

    // ── Format ISO timestamp → "h:mm a" ──────────────────────────────────────
    private String formatInSchoolTime(String iso) {
        if (iso == null || iso.isEmpty()) return "--:--";
        try {
            java.text.SimpleDateFormat inFmt  = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ENGLISH);
            java.text.SimpleDateFormat outFmt = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH);
            java.util.Date d = inFmt.parse(iso);
            return d != null ? outFmt.format(d) : "--:--";
        } catch (Exception e) {
            return "--:--";
        }
    }

    private void checkAndRecoverMissedGeofence(Context ctx, String userId, String subjectId) {
        ClassNotificationScheduler.getInstance().getEarliestTimeInToday(
                ctx, userId, subjectId, (timeIn, sid) -> {
                    if (timeIn != null) return;

                    com.google.android.gms.location.FusedLocationProviderClient fused =
                            com.google.android.gms.location.LocationServices
                                    .getFusedLocationProviderClient(ctx);

                    if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
                            androidx.core.content.ContextCompat.checkSelfPermission(ctx,
                                    android.Manifest.permission.ACCESS_FINE_LOCATION)) return;

                    fused.getLastLocation().addOnSuccessListener(location -> {
                        if (location == null) return;

                        double schoolLat = 14.707776;
                        double schoolLng = 121.050512;
                        float  radiusM   = 80f;

                        float[] result = new float[1];
                        android.location.Location.distanceBetween(
                                location.getLatitude(), location.getLongitude(),
                                schoolLat, schoolLng, result);

                        if (result[0] <= radiusM) {
                            Log.d("StudentHome", "Recovering missed geofence entry");
                            com.example.attendify.repository.GeofenceRepository
                                    .getInstance()
                                    .recordTimeIn(ctx, userId, subjectId);

                            if (com.example.attendify.notifications.NotificationGuard
                                    .shouldFire(ctx, userId, "geofence", "arrived_at_school")) {
                                NotificationHelper.notifyStudentArrivedAtSchool(ctx, "");
                                NotificationStore.getInstance().save(ctx, userId,
                                        "Arrived at School",
                                        "Welcome! Your arrival at school has been recorded.");
                            }
                        }
                    });
                });
    }
}