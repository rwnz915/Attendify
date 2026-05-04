package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.MainActivity;
import com.example.attendify.notifications.ClassNotificationScheduler;
import com.example.attendify.notifications.NotificationHelper;
import com.example.attendify.notifications.NotificationStore;
import com.example.attendify.R;
import com.example.attendify.activities.NotificationActivity;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.SubjectRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.attendify.ThemeApplier;

public class SecretaryHomeFragment extends Fragment {

    private TextView tvName;
    private TextView tvPresent, tvLate, tvAbsent;
    private LinearLayout llRecentActivity;
    private TextView tvRecentEmpty;
    private ProgressBar progressRecent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_secretary_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserProfile secThemeUser = AuthRepository.getInstance().getLoggedInUser();
        if (secThemeUser != null) {
            ThemeApplier.applyHeader(requireContext(), secThemeUser.getRole(),
                    view.findViewById(R.id.sec_header_bg));

            String role = secThemeUser.getRole();

            ThemeApplier.applyQuickActionColor(requireContext(), role,
                    view.findViewById(R.id.btn_quick_subjects), 0);
            ThemeApplier.applyQuickActionColor(requireContext(), role,
                    view.findViewById(R.id.btn_quick_class_list), 1);
            ThemeApplier.applyQuickActionColor(requireContext(), role,
                    view.findViewById(R.id.btn_quick_history), 2);
            ThemeApplier.applyQuickActionColor(requireContext(), role,
                    view.findViewById(R.id.btn_quick_settings), 3);
        }

        tvName    = view.findViewById(R.id.tv_sec_name);
        tvPresent = view.findViewById(R.id.tv_overview_present);
        tvLate    = view.findViewById(R.id.tv_overview_late);
        tvAbsent  = view.findViewById(R.id.tv_overview_absent);

        llRecentActivity = view.findViewById(R.id.ll_attention_students);
        tvRecentEmpty    = view.findViewById(R.id.tv_attention_empty);
        progressRecent   = view.findViewById(R.id.progress_attention);

        view.findViewById(R.id.btn_quick_subjects).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).selectTab(1);
        });

        view.findViewById(R.id.btn_quick_class_list).setOnClickListener(v -> {
            if (getActivity() != null)
                startActivity(new android.content.Intent(getActivity(),
                        com.example.attendify.activities.SecretaryClassListActivity.class));
        });

        view.findViewById(R.id.btn_quick_history).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).selectTab(3);
        });

        view.findViewById(R.id.btn_quick_settings).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).navigateTo(new AppSettingsFragment());
        });

        loadSecretaryInfo(view);
        loadTodayOverview();
        loadTodayRecentActivity();
        loadCurrentOrNextSubject(view);

        // Notification bell → open notifications page
        View secNotifBtn = view.findViewById(R.id.fl_sec_notif);
        if (secNotifBtn != null) {
            secNotifBtn.setClickable(true);
            secNotifBtn.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity)
                    startActivity(new android.content.Intent(requireActivity(), NotificationActivity.class));
            });
        }

        // Show/hide unread notification dot
        if (secThemeUser != null) {
            View notifDot = view.findViewById(R.id.view_notif_dot);
            if (notifDot != null) {
                boolean hasUnread = NotificationStore.getInstance().hasUnread(requireContext(), secThemeUser.getId());
                notifDot.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void loadSecretaryInfo(View view) {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;
        if (tvName != null) tvName.setText(user.getFullName());

        TextView tvRole = view.findViewById(R.id.tv_sec_role);
        if (tvRole != null) {
            String section = user.getSection();
            tvRole.setText(section != null
                    ? "Class Secretary  \u2022  " + section
                    : "Class Secretary");
        }

        TextView tvLabel = view.findViewById(R.id.tv_overview_label);
        if (tvLabel != null && user.getSection() != null)
            tvLabel.setText("Today's Overview  \u2022  " + user.getSection());

        TextView tvAttentionTitle = view.findViewById(R.id.tv_attention_title);
        if (tvAttentionTitle != null) tvAttentionTitle.setText("Recent Activity");

        TextView tvAttentionSubtitle = view.findViewById(R.id.tv_attention_subtitle);
        if (tvAttentionSubtitle != null)
            tvAttentionSubtitle.setText("Today's attendance by subject");

        TextView tvEmpty = view.findViewById(R.id.tv_attention_empty);
        if (tvEmpty != null) tvEmpty.setText("No attendance recorded today");
    }

    // ── Current / Next subject card ───────────────────────────────────────────

    private void loadCurrentOrNextSubject(View view) {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null || user.getSection() == null) return;

        SubjectRepository.getInstance().getStudentSubjects(user.getSection(),
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                        if (getActivity() == null) return;
                        String todayAbbr = getTodayDayAbbr();

                        SubjectRepository.SubjectItem active = getActiveSubject(subjects, todayAbbr);
                        boolean isNext = false;
                        SubjectRepository.SubjectItem display = active;

                        if (display == null) {
                            display = getNextSubject(subjects, todayAbbr);
                            isNext = true;
                        }

                        final SubjectRepository.SubjectItem finalSubject = display;
                        final boolean finalIsNext = isNext;

                        getActivity().runOnUiThread(() ->
                                updateClassCard(view, finalSubject, finalIsNext));
                    }

                    @Override
                    public void onFailure(String errorMessage) {}
                });
    }

    private void updateClassCard(View view,
                                 SubjectRepository.SubjectItem subj,
                                 boolean isNext) {
        TextView tvStatus  = view.findViewById(R.id.tv_sec_class_status);
        View divider       = view.findViewById(R.id.divider_class_status);
        if (tvStatus == null) return;

        if (subj == null) {
            tvStatus.setVisibility(View.GONE);
            if (divider != null) divider.setVisibility(View.GONE);
            return;
        }

        String formattedTime = formatScheduleTime(subj.schedule);

        if (isNext) {
            String dateLabel = getNextClassDate(subj.schedule);
            tvStatus.setText("Next class: " + subj.name + " - " + formattedTime + " " + dateLabel);
        } else {
            tvStatus.setText("Ongoing class: " + subj.name + " - " + formattedTime);
        }

        tvStatus.setVisibility(View.VISIBLE);
        if (divider != null) divider.setVisibility(View.VISIBLE);
    }

    // ── Today's overview ─────────────────────────────────────────────────────

    private void loadTodayOverview() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date());
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null || user.getSection() == null) return;
        final String section = user.getSection();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener(userSnap -> {
                    if (getActivity() == null) return;
                    List<DocumentSnapshot> docs = userSnap.getDocuments();
                    if (docs.isEmpty()) return;

                    List<String> uids = new ArrayList<>();
                    for (DocumentSnapshot d : docs) uids.add(d.getId());

                    db.collection("attendance")
                            .whereEqualTo("date", today)
                            .whereIn("studentId", uids.subList(0, Math.min(uids.size(), 30)))
                            .get()
                            .addOnSuccessListener(attSnap -> {
                                if (getActivity() == null) return;
                                int present = 0, late = 0, absent = 0;
                                for (DocumentSnapshot doc : attSnap.getDocuments()) {
                                    String status = doc.getString("status");
                                    if (status == null) continue;
                                    switch (status) {
                                        case "Present": present++; break;
                                        case "Late":    late++;    break;
                                        case "Absent":  absent++;  break;
                                    }
                                }
                                final int fp = present, fl = late, fa = absent;
                                getActivity().runOnUiThread(() -> {
                                    if (tvPresent != null) tvPresent.setText(String.valueOf(fp));
                                    if (tvLate    != null) tvLate.setText(String.valueOf(fl));
                                    if (tvAbsent  != null) tvAbsent.setText(String.valueOf(fa));
                                });
                            });
                });
    }

    // ── Recent activity ───────────────────────────────────────────────────────

    private void loadTodayRecentActivity() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date());
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null || user.getSection() == null) {
            showEmptyRecent();
            return;
        }
        final String section = user.getSection();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (progressRecent != null) progressRecent.setVisibility(View.VISIBLE);

        db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener(userSnap -> {
                    if (getActivity() == null) return;
                    List<DocumentSnapshot> docs = userSnap.getDocuments();
                    if (docs.isEmpty()) {
                        getActivity().runOnUiThread(this::showEmptyRecent);
                        return;
                    }

                    List<String> uids = new ArrayList<>();
                    for (DocumentSnapshot d : docs) uids.add(d.getId());

                    db.collection("attendance")
                            .whereEqualTo("date", today)
                            .whereIn("studentId", uids.subList(0, Math.min(uids.size(), 30)))
                            .get()
                            .addOnSuccessListener(attSnap -> {
                                if (getActivity() == null) return;

                                Map<String, int[]> bySubject = new LinkedHashMap<>();
                                for (DocumentSnapshot doc : attSnap.getDocuments()) {
                                    String subjectName = doc.getString("subjectName");
                                    String status      = doc.getString("status");
                                    if (subjectName == null || status == null) continue;
                                    int[] counts = bySubject.computeIfAbsent(
                                            subjectName, k -> new int[3]);
                                    switch (status) {
                                        case "Present": counts[0]++; break;
                                        case "Late":    counts[1]++; break;
                                        case "Absent":  counts[2]++; break;
                                    }
                                }

                                getActivity().runOnUiThread(() -> {
                                    if (progressRecent != null)
                                        progressRecent.setVisibility(View.GONE);

                                    if (bySubject.isEmpty()) {
                                        showEmptyRecent();
                                        return;
                                    }

                                    if (tvRecentEmpty    != null)
                                        tvRecentEmpty.setVisibility(View.GONE);
                                    if (llRecentActivity != null)
                                        llRecentActivity.removeAllViews();

                                    LayoutInflater li = LayoutInflater.from(requireContext());
                                    String dateDisplay = new SimpleDateFormat(
                                            "MMM d, yyyy", Locale.ENGLISH).format(new Date());

                                    for (Map.Entry<String, int[]> entry : bySubject.entrySet()) {
                                        int[] c = entry.getValue();
                                        View card = li.inflate(
                                                R.layout.item_activity_record,
                                                llRecentActivity, false);

                                        ((TextView) card.findViewById(R.id.tv_record_date))
                                                .setText(dateDisplay);
                                        ((TextView) card.findViewById(R.id.tv_record_subject))
                                                .setText(section + " \u2022 " + entry.getKey());
                                        ((TextView) card.findViewById(R.id.tv_record_present))
                                                .setText(c[0] + " P");

                                        TextView tvLateView =
                                                card.findViewById(R.id.tv_record_late);
                                        if (c[1] > 0) {
                                            tvLateView.setText(c[1] + " L");
                                            tvLateView.setVisibility(View.VISIBLE);
                                        } else {
                                            tvLateView.setVisibility(View.GONE);
                                        }

                                        ((TextView) card.findViewById(R.id.tv_record_absent))
                                                .setText(c[2] + " A");

                                        if (llRecentActivity != null)
                                            llRecentActivity.addView(card);
                                    }
                                });
                            })
                            .addOnFailureListener(e -> {
                                if (getActivity() != null)
                                    getActivity().runOnUiThread(this::showEmptyRecent);
                            });
                })
                .addOnFailureListener(e -> {
                    if (getActivity() != null)
                        getActivity().runOnUiThread(this::showEmptyRecent);
                });
    }

    private void showEmptyRecent() {
        if (progressRecent != null) progressRecent.setVisibility(View.GONE);
        if (tvRecentEmpty  != null) tvRecentEmpty.setVisibility(View.VISIBLE);
    }

    // ── Schedule helpers (mirrors HomeFragment) ───────────────────────────────

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
                        startCal.set(Calendar.MINUTE,      tmp.get(Calendar.MINUTE));
                        startCal.set(Calendar.SECOND,      0);
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

    private boolean matchesDay(String daysPart, String abbr) {
        switch (abbr) {
            case "TH": return daysPart.contains("TH");
            case "T":  return daysPart.replace("TH", "").contains("T");
            case "SU": return daysPart.contains("SU");
            case "S":  return daysPart.replace("SU", "").contains("S");
            default:   return daysPart.contains(abbr);
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
            SimpleDateFormat inFmt  = new SimpleDateFormat("h:mma",  Locale.ENGLISH);
            SimpleDateFormat outFmt = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
            String s = outFmt.format(inFmt.parse(times[0].trim().toUpperCase(Locale.ENGLISH)));
            String e = outFmt.format(inFmt.parse(times[1].trim().toUpperCase(Locale.ENGLISH)));
            return s + " - " + e;
        } catch (ParseException ex) { return parts[1].replace("-", " - "); }
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
                    String startStr = parts.length > 1
                            ? parts[1].split("-")[0].trim() : null;
                    if (startStr != null) {
                        try {
                            SimpleDateFormat sdf =
                                    new SimpleDateFormat("h:mma", Locale.ENGLISH);
                            Date start = sdf.parse(startStr.toUpperCase(Locale.ENGLISH));
                            if (start != null && Calendar.getInstance()
                                    .before(toTodayCal(start))) {
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
}