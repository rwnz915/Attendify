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

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.ExcuseLetterRepository;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.SubjectRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.example.attendify.ThemeApplier;
import com.example.attendify.ThemeManager;

public class HomeFragment extends Fragment {

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Apply saved theme to header
        UserProfile meTheme = AuthRepository.getInstance().getLoggedInUser();
        String roleTheme = meTheme != null ? meTheme.getRole() : "teacher";
        ThemeApplier.applyHeader(requireContext(), roleTheme, view.findViewById(R.id.home_header_bg));

        // Apply theme to profile circle avatar
        ThemeApplier.applyOval(requireContext(), roleTheme, view.findViewById(R.id.iv_profile_circle));

        // Apply theme to btn_start: white bg with themed text color
        android.widget.TextView btnStart = view.findViewById(R.id.btn_start);
        if (btnStart != null) {
            android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
            btnBg.setColor(0xFFFFFFFF);
            btnBg.setCornerRadius(com.example.attendify.ThemeApplier.primary(requireContext(), roleTheme)); // reuse dp helper
            btnBg.setCornerRadius(50f * requireContext().getResources().getDisplayMetrics().density);
            btnStart.setBackground(btnBg);
            btnStart.setTextColor(com.example.attendify.ThemeManager.getPrimaryColor(requireContext(), roleTheme));
        }

        // Apply theme to quick-action Class List icon (btn_class_list)
        android.widget.ImageView btnClassList = view.findViewById(R.id.btn_class_list);
        if (btnClassList != null) {
            ThemeApplier.applyLightTint(requireContext(), roleTheme, btnClassList, 14);
            btnClassList.setColorFilter(com.example.attendify.ThemeManager.getPrimaryColor(requireContext(), roleTheme));
        }

        view.findViewById(R.id.btn_start).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).selectTab(2);
        });
        view.findViewById(R.id.btn_subjects).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).selectTab(1);
        });

        // Pending Approvals is now in the Quick Actions row
        view.findViewById(R.id.card_pending_approvals).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new ApprovalRequestsFragment())
                        .addToBackStack(null)
                        .commit());

        // Contact Parent quick action
        view.findViewById(R.id.btn_contact_parent).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.fragment_container, new TeacherContactParentFragment())
                        .addToBackStack(null)
                        .commit());

        UserProfile me = AuthRepository.getInstance().getLoggedInUser();
        if (me != null) {
            ((TextView) view.findViewById(R.id.tv_teacher_name)).setText(me.getFullName());
        }

        // Set today's date label on the combined stat card
        TextView tvDateLabel = view.findViewById(R.id.tv_today_date_label);
        if (tvDateLabel != null) {
            String dateStr = new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.ENGLISH)
                    .format(new Date());
            tvDateLabel.setText(dateStr);
        }

        loadPendingApprovals(view);
        loadHomeAttendanceData(view);
    }

    // ── Pending approvals ─────────────────────────────────────────────────────

    private void loadPendingApprovals(View view) {
        UserProfile me = AuthRepository.getInstance().getLoggedInUser();
        if (me == null) return;

        ExcuseLetterRepository.getInstance().getPendingByTeacher(
                me.getId(),
                new ExcuseLetterRepository.ListCallback() {
                    @Override
                    public void onSuccess(List<com.example.attendify.models.ExcuseLetter> letters) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            int count = letters.size();
                            TextView badge = view.findViewById(R.id.tv_pending_count_badge);
                            if (badge != null) {
                                if (count > 0) {
                                    badge.setText(count > 99 ? "99+" : String.valueOf(count));
                                    badge.setVisibility(View.VISIBLE);
                                } else {
                                    badge.setVisibility(View.GONE);
                                }
                            }
                        });
                    }
                    @Override public void onFailure(String errorMessage) {}
                });
    }

    // ── Main loader ───────────────────────────────────────────────────────────

    private void loadHomeAttendanceData(View view) {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        String today     = DATE_FMT.format(new Date());
        String todayAbbr = getTodayDayAbbr();

        SubjectRepository.getInstance().getTeacherSubjects(user.getId(),
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {

                        // Count total subjects scheduled for today (for "Classes" stat)
                        int todayClassCount = 0;
                        for (SubjectRepository.SubjectItem s : subjects) {
                            if (s.schedule != null && runsToday(s.schedule, todayAbbr))
                                todayClassCount++;
                        }
                        final int finalTodayClassCount = todayClassCount;

                        // Today's Class card logic
                        SubjectRepository.SubjectItem activeSubject = getActiveSubject(subjects, todayAbbr);
                        SubjectRepository.SubjectItem nextSubject   = null;
                        boolean isNext = false;

                        if (activeSubject == null) {
                            nextSubject = getNextSubject(subjects, todayAbbr);
                            isNext = true;
                        }

                        final SubjectRepository.SubjectItem finalSubject =
                                activeSubject != null ? activeSubject : nextSubject;
                        final boolean finalIsNext = isNext;

                        if (getActivity() != null)
                            getActivity().runOnUiThread(() -> {
                                updateTodayClassCard(view, finalSubject, finalIsNext);
                                // Update the "Classes" column in the combined stat card
                                TextView tvTotal = view.findViewById(R.id.tv_today_total);
                                if (tvTotal != null)
                                    tvTotal.setText(String.valueOf(finalTodayClassCount));
                            });

                        // Attendance stat: sum across all subjects that have started today
                        List<SubjectRepository.SubjectItem> startedToday =
                                filterStartedToday(subjects, todayAbbr);

                        if (startedToday.isEmpty()) {
                            if (getActivity() != null)
                                getActivity().runOnUiThread(() ->
                                        updateUiWithSummaries(view, new ArrayList<>(), today));
                            return;
                        }

                        AttendanceRepository.getInstance()
                                .getTodaySubjectSummaries(startedToday, today,
                                        new AttendanceRepository.SubjectSummariesCallback() {
                                            @Override
                                            public void onSuccess(List<AttendanceRepository.SubjectSummary> summaries) {
                                                if (getActivity() == null) return;
                                                getActivity().runOnUiThread(() ->
                                                        updateUiWithSummaries(view, summaries, today));
                                            }
                                            @Override public void onFailure(String err) {
                                                if (getActivity() == null) return;
                                                getActivity().runOnUiThread(() ->
                                                        updateUiWithSummaries(view, new ArrayList<>(), today));
                                            }
                                        });
                    }
                    @Override public void onFailure(String err) {}
                });
    }

    // ── Today's Class card ────────────────────────────────────────────────────

    private void updateTodayClassCard(View view,
                                      SubjectRepository.SubjectItem subj,
                                      boolean isNext) {
        TextView tvLabel = view.findViewById(R.id.tv_class_label);
        TextView tvName  = view.findViewById(R.id.tv_today_subject_name);
        TextView tvTime  = view.findViewById(R.id.tv_today_subject_time);

        if (subj == null) {
            if (tvLabel != null) tvLabel.setText("Today's Class");
            tvName.setText("No class today");
            tvTime.setText("—");
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

    // ── Stat card + recent-activity list ─────────────────────────────────────

    private void updateUiWithSummaries(View view,
                                       List<AttendanceRepository.SubjectSummary> summaries,
                                       String today) {
        int totalPresent = 0;
        int totalStudents = 0;

        for (AttendanceRepository.SubjectSummary s : summaries) {
            // present + late count as "present"
            totalPresent  += s.present + s.late;
            // total enrolled = present + late + absent in that class
            totalStudents += s.present + s.late + s.absent;
        }

        TextView tvPresent = view.findViewById(R.id.tv_today_present);
        if (tvPresent != null) {
            // Shows e.g. "20/100"
            tvPresent.setText(totalPresent + "/" + totalStudents);
        }

        // Recent activity list (unchanged logic below)
        LinearLayout container = view.findViewById(R.id.recent_activity_container);
        container.removeAllViews();

        List<AttendanceRepository.SubjectSummary> ended = new ArrayList<>();
        for (AttendanceRepository.SubjectSummary s : summaries) {
            if (classHasEnded(s.schedule)) ended.add(s);
        }

        if (ended.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No completed classes yet today.");
            empty.setTextColor(0xFF9E9E9E);
            empty.setPadding(0, 16, 0, 16);
            container.addView(empty);
            return;
        }

        LayoutInflater li = LayoutInflater.from(requireContext());
        for (AttendanceRepository.SubjectSummary s : ended) {
            View card = li.inflate(R.layout.item_activity_record, container, false);
            ((TextView) card.findViewById(R.id.tv_record_date)).setText(formatDisplayDate(today));
            ((TextView) card.findViewById(R.id.tv_record_subject))
                    .setText(s.section + " \u2022 " + s.subjectName);
            ((TextView) card.findViewById(R.id.tv_record_present)).setText(s.present + " P");

            TextView tvLate = card.findViewById(R.id.tv_record_late);
            if (s.late > 0) {
                tvLate.setText(s.late + " L");
                tvLate.setVisibility(View.VISIBLE);
            } else {
                tvLate.setVisibility(View.GONE);
            }

            ((TextView) card.findViewById(R.id.tv_record_absent)).setText(s.absent + " A");
            container.addView(card);
        }
    }

    // ── Schedule helpers ──────────────────────────────────────────────────────

    private List<SubjectRepository.SubjectItem> filterStartedToday(
            List<SubjectRepository.SubjectItem> subjects, String todayAbbr) {
        List<SubjectRepository.SubjectItem> result = new ArrayList<>();
        for (SubjectRepository.SubjectItem subj : subjects) {
            if (subj.schedule == null || subj.schedule.isEmpty()) continue;
            if (runsToday(subj.schedule, todayAbbr) && classHasStarted(subj.schedule))
                result.add(subj);
        }
        return result;
    }

    private boolean classHasStarted(String schedule) {
        try {
            String[] parts = schedule.trim().split("\\s+", 2);
            if (parts.length < 2) return false;
            String startStr = parts[1].split("-")[0].trim().toUpperCase(Locale.ENGLISH);
            SimpleDateFormat sdf = new SimpleDateFormat("h:mma", Locale.ENGLISH);
            Date start = sdf.parse(startStr);
            if (start == null) return false;
            return Calendar.getInstance().after(toTodayCal(start));
        } catch (ParseException e) { return false; }
    }

    private boolean classHasEnded(String schedule) {
        if (schedule == null) return false;
        try {
            String[] parts = schedule.trim().split("\\s+", 2);
            if (parts.length < 2) return false;
            String[] times = parts[1].split("-");
            if (times.length < 2) return false;
            String endStr = times[1].trim().toUpperCase(Locale.ENGLISH);
            SimpleDateFormat sdf = new SimpleDateFormat("h:mma", Locale.ENGLISH);
            Date end = sdf.parse(endStr);
            if (end == null) return false;
            return Calendar.getInstance().after(toTodayCal(end));
        } catch (ParseException e) { return false; }
    }

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
        String[] times = parts[1].split("-");
        return times.length > 0 ? times[0].trim() : null;
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