package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.R;
import com.example.attendify.ThemeManager;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.SubjectRepository;
import com.example.attendify.fragments.ExcuseLetterFragment;

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

                        // Update card name/time first (no status yet)
                        getActivity().runOnUiThread(() -> updateTodayClassCard(view, todaySubject, null, finalIsNextEarly));

                        // Step 2: Load attendance to get all-time stats + today's records
                        AttendanceRepository.getInstance().getStudentHistory(user.getId(),
                                new AttendanceRepository.AttendanceCallback() {
                                    @Override
                                    public void onSuccess(List<AttendanceRecord> allHistory) {
                                        if (getActivity() == null) return;

                                        int totalPresent = 0, totalLate = 0, totalAbsent = 0;
                                        List<AttendanceRecord> todayRecords = new ArrayList<>();

                                        for (AttendanceRecord r : allHistory) {
                                            // All-time totals
                                            totalPresent += r.getPresent();
                                            totalLate    += r.getLate();
                                            totalAbsent  += r.getAbsent();
                                            // Collect today's records
                                            if (today.equals(r.getDate())) todayRecords.add(r);
                                        }

                                        // Find the status for today's active subject
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
                                        final String finalStatus = headerStatus;

                                        getActivity().runOnUiThread(() -> {
                                            // Update header card with status badge
                                            updateTodayClassCard(view, todaySubject, finalStatus, finalIsNextEarly);

                                            // Stat cards — all-time
                                            ((TextView) view.findViewById(R.id.tv_present_count))
                                                    .setText(String.valueOf(fp));
                                            ((TextView) view.findViewById(R.id.tv_late_count))
                                                    .setText(String.valueOf(fl));
                                            ((TextView) view.findViewById(R.id.tv_absent_count))
                                                    .setText(String.valueOf(fa));

                                            // Recent list — today only
                                            bindTodayList(view, todayFinal);
                                        });
                                    }

                                    @Override
                                    public void onFailure(String err) {
                                        if (getActivity() == null) return;
                                        getActivity().runOnUiThread(() ->
                                                bindTodayList(view, new ArrayList<>()));
                                    }
                                });
                    }

                    @Override
                    public void onFailure(String err) {
                        // Still try loading attendance even if subjects fail
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

                                        final int fp = totalPresent, fl = totalLate, fa = totalAbsent;
                                        final List<AttendanceRecord> todayFinal = todayRecords;

                                        getActivity().runOnUiThread(() -> {
                                            ((TextView) view.findViewById(R.id.tv_present_count))
                                                    .setText(String.valueOf(fp));
                                            ((TextView) view.findViewById(R.id.tv_late_count))
                                                    .setText(String.valueOf(fl));
                                            ((TextView) view.findViewById(R.id.tv_absent_count))
                                                    .setText(String.valueOf(fa));
                                            bindTodayList(view, todayFinal);
                                        });
                                    }

                                    @Override
                                    public void onFailure(String e) {
                                        if (getActivity() == null) return;
                                        getActivity().runOnUiThread(() ->
                                                bindTodayList(view, new ArrayList<>()));
                                    }
                                });
                    }
                });

        // Wire up "Excuse Letter" quick action
        view.findViewById(R.id.btn_class_list).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new ExcuseLetterFragment())
                        .addToBackStack(null)
                        .commit());

        // Excuse Letter
        view.findViewById(R.id.btn_class_list).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new ExcuseLetterFragment())
                        .addToBackStack(null)
                        .commit());

        // Subjects — switch to the Subjects tab (index 1)
        view.findViewById(R.id.btn_student_subjects).setOnClickListener(v -> {
            if (getActivity() instanceof com.example.attendify.MainActivity)
                ((com.example.attendify.MainActivity) getActivity()).selectTab(1);
        });

        // Settings — placeholder until a settings screen is added
        view.findViewById(R.id.btn_student_settings).setOnClickListener(v -> {
            // TODO: replace with SettingsFragment when ready
            android.widget.Toast.makeText(requireContext(),
                    "Settings coming soon", android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    // ── Today's Class card ────────────────────────────────────────────────────

    /**
     * Updates the header Today's Class card.
     *
     * @param subj   the active/next subject, or null if no class today
     * @param status the student's attendance status ("Present", "Late", "Absent"), or null if not yet recorded
     */
    private void updateTodayClassCard(View view,
                                      SubjectRepository.SubjectItem subj,
                                      @Nullable String status,
                                      boolean isNext) {
        TextView tvLabel  = view.findViewById(R.id.tv_class_label);
        TextView tvName   = view.findViewById(R.id.tv_today_subject_name);
        TextView tvTime   = view.findViewById(R.id.tv_today_subject_time);
        TextView tvStatus = view.findViewById(R.id.tv_today_status);

        if (subj == null) {
            if (tvLabel != null) tvLabel.setText("Today's Class");
            tvName.setText("No class today");
            tvTime.setText("—");
            tvStatus.setVisibility(View.GONE);
            return;
        }

        if (tvLabel != null) tvLabel.setText(isNext ? "Next Class" : "Today's Class");
        tvName.setText(subj.name);

        String formattedTime = formatScheduleTime(subj.schedule);
        if (isNext) {
            String dateLabel = getNextClassDate(subj.schedule);
            tvTime.setText(formattedTime + "  •  " + dateLabel);
        } else {
            tvTime.setText(formattedTime);
        }

        // Hide status badge for upcoming classes — only show for active class
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
                default:
                    tvStatus.setTextColor(0xFF1565C0);
                    tvStatus.setBackgroundResource(R.drawable.bg_button_white);
                    break;
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
}