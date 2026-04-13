package com.example.attendify.fragments;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.R;
import com.example.attendify.models.MockSubjectData.StudentSubject;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.SubjectRepository;

import java.util.List;

public class StudentSubjectFragment extends Fragment {

    private String studentName;
    private List<StudentSubject> subjectList;
    private String activeSection = "All";

    private LinearLayout subjectContainer;
    private LinearLayout chipContainer;
    private TextView     tvTotalSubjects;
    private TextView     tvTotalSections;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_subject, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        studentName = user.getName();

        subjectContainer = view.findViewById(R.id.subject_container);
        chipContainer    = view.findViewById(R.id.chip_container);
        tvTotalSubjects  = view.findViewById(R.id.tv_total_subjects);
        tvTotalSections  = view.findViewById(R.id.tv_total_sections);

        subjectList = SubjectRepository.getInstance().getStudentSubjects(studentName);
        refreshAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Refresh UI
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshAll() {
        tvTotalSubjects.setText(String.valueOf(subjectList.size()));

        java.util.Set<String> sections = new java.util.HashSet<>();
        for (StudentSubject s : subjectList) sections.add(s.section);
        tvTotalSections.setText(String.valueOf(sections.size()));

        // Rebuild section filter chips
        chipContainer.removeAllViews();
        java.util.List<String> chipLabels = new java.util.ArrayList<>();
        chipLabels.add("All");
        chipLabels.addAll(new java.util.ArrayList<>(sections));
        for (String label : chipLabels) chipContainer.addView(buildChip(label));

        // Rebuild subject cards
        subjectContainer.removeAllViews();
        for (StudentSubject subject : subjectList) {
            if (activeSection.equals("All") || subject.section.equals(activeSection)) {
                subjectContainer.addView(buildSubjectCard(subject));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Section Filter Chip
    // ─────────────────────────────────────────────────────────────────────────

    private View buildChip(String label) {
        TextView chip = new TextView(requireContext());
        chip.setText(label);
        chip.setTextSize(12);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(lp);

        boolean isActive = label.equals(activeSection);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(20));
        if (isActive) {
            bg.setColor(Color.parseColor("#1E293B"));
            chip.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.WHITE);
            bg.setStroke(dp(1), Color.parseColor("#E5E7EB"));
            chip.setTextColor(Color.parseColor("#6B7280"));
        }
        chip.setBackground(bg);
        chip.setOnClickListener(v -> { activeSection = label; refreshAll(); });
        return chip;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Subject Card
    // ─────────────────────────────────────────────────────────────────────────

    private View buildSubjectCard(StudentSubject subject) {
        View card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_subject, subjectContainer, false);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(20));
        try { bg.setColor(Color.parseColor(subject.color)); }
        catch (Exception e) { bg.setColor(Color.parseColor("#3B82F6")); }
        card.setBackground(bg);

        ((TextView) card.findViewById(R.id.tv_item_section)).setText(subject.section);
        ((TextView) card.findViewById(R.id.tv_item_name)).setText(subject.name);
        ((TextView) card.findViewById(R.id.tv_item_teacher)).setText(subject.teacher);
        ((TextView) card.findViewById(R.id.tv_item_schedule)).setText(subject.schedule);

        // Compute this student's attendance rate for this subject
        List<AttendanceRecord> history =
                SubjectRepository.getInstance().getSubjectHistory(studentName, subject.name);
        int total   = history.size();
        int present = 0;
        for (AttendanceRecord r : history) present += r.getPresent();
        int rate = total > 0 ? Math.round((present * 100f) / total) : 0;

        // Reuse tv_item_student_count to show attendance rate instead of student count
        ((TextView) card.findViewById(R.id.tv_item_student_count))
                .setText(rate + "% Attendance");

        TextView tvAttendance = card.findViewById(R.id.tv_item_attendance);
        if (tvAttendance != null) tvAttendance.setVisibility(View.GONE);

        card.setOnClickListener(v -> showSubjectDetailSheet(subject, history));
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Detail Sheet — shows stats + per-session log for this student
    // ─────────────────────────────────────────────────────────────────────────

    private void showSubjectDetailSheet(StudentSubject subject, List<AttendanceRecord> history) {

        // ── Compute stats ─────────────────────────────────────────────────────
        int totalPresent = 0, totalLate = 0, totalAbsent = 0;
        for (AttendanceRecord r : history) {
            totalPresent += r.getPresent();
            totalLate    += r.getLate();
            totalAbsent  += r.getAbsent();
        }
        int total = history.size();
        int rate  = total > 0 ? Math.round((totalPresent * 100f) / total) : 0;

        // ── Root frame ────────────────────────────────────────────────────────
        android.widget.FrameLayout frameRoot =
                new android.widget.FrameLayout(requireContext());
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.parseColor("#F9FAFB"));
        sheetBg.setCornerRadii(new float[]{ dp(28),dp(28), dp(28),dp(28), 0,0, 0,0 });
        frameRoot.setBackground(sheetBg);

        // ── Sheet (header + scroll) ───────────────────────────────────────────
        LinearLayout sheet = new LinearLayout(requireContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        // ── Colored header ────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(28), dp(60), dp(20));
        GradientDrawable headerBg = new GradientDrawable();
        try { headerBg.setColor(Color.parseColor(subject.color)); }
        catch (Exception e) { headerBg.setColor(Color.parseColor("#3B82F6")); }
        headerBg.setCornerRadii(new float[]{ dp(28),dp(28), dp(28),dp(28), 0,0, 0,0 });
        header.setBackground(headerBg);

        TextView tvSection = new TextView(requireContext());
        tvSection.setText(subject.section);
        tvSection.setTextSize(12);
        tvSection.setTextColor(Color.WHITE);
        tvSection.setAlpha(0.85f);

        TextView tvName = new TextView(requireContext());
        tvName.setText(subject.name);
        tvName.setTextSize(24);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams nameP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameP.setMargins(0, dp(2), 0, dp(10));
        tvName.setLayoutParams(nameP);

        addIconRow(header, "👤 ", subject.teacher, 13);
        addIconRow(header, "🕐 ", subject.schedule, 13);

        header.addView(tvSection, 0);
        header.addView(tvName, 1);

        // ── Attendance rate pill ──────────────────────────────────────────────
        TextView tvRate = new TextView(requireContext());
        tvRate.setText(rate + "% Attendance");
        tvRate.setTextSize(12);
        tvRate.setTypeface(null, android.graphics.Typeface.BOLD);
        tvRate.setTextColor(Color.WHITE);
        tvRate.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable rateBg = new GradientDrawable();
        rateBg.setShape(GradientDrawable.RECTANGLE);
        rateBg.setCornerRadius(dp(20));
        rateBg.setColor(0x33FFFFFF);
        tvRate.setBackground(rateBg);
        LinearLayout.LayoutParams rateP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rateP.setMargins(0, dp(12), 0, 0);
        tvRate.setLayoutParams(rateP);
        header.addView(tvRate);

        // ── Scrollable body ───────────────────────────────────────────────────
        android.widget.ScrollView scrollView = new android.widget.ScrollView(requireContext());
        scrollView.setBackgroundColor(Color.parseColor("#F9FAFB"));
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(Color.parseColor("#F9FAFB"));
        body.setPadding(dp(16), dp(16), dp(16), dp(28));

        // ── Stat cards row (Present / Late / Absent) ──────────────────────────
        LinearLayout statsRow = new LinearLayout(requireContext());
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srp.setMargins(0, 0, 0, dp(16));
        statsRow.setLayoutParams(srp);

        statsRow.addView(buildStatCard("Present", totalPresent, "#DCFCE7", "#15803D"));
        statsRow.addView(buildStatCard("Late",    totalLate,    "#FEF9C3", "#A16207"));
        statsRow.addView(buildStatCard("Absent",  totalAbsent,  "#FEE2E2", "#B91C1C"));

        body.addView(statsRow);

        // ── Session log heading ───────────────────────────────────────────────
        TextView tvHeading = new TextView(requireContext());
        tvHeading.setText("Attendance log");
        tvHeading.setTextSize(15);
        tvHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        tvHeading.setTextColor(Color.parseColor("#111827"));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.setMargins(0, 0, 0, dp(10));
        tvHeading.setLayoutParams(hp);
        body.addView(tvHeading);

        // ── Session log cards ─────────────────────────────────────────────────
        if (history.isEmpty()) {
            TextView tvEmpty = new TextView(requireContext());
            tvEmpty.setText("No records yet.");
            tvEmpty.setTextSize(13);
            tvEmpty.setTextColor(Color.parseColor("#9CA3AF"));
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, dp(20), 0, 0);
            body.addView(tvEmpty);
        } else {
            for (AttendanceRecord record : history) {
                body.addView(buildSessionCard(record));
            }
        }

        scrollView.addView(body);
        sheet.addView(header);
        sheet.addView(scrollView);

        // ── Close button ──────────────────────────────────────────────────────
        TextView btnClose = new TextView(requireContext());
        btnClose.setText("✕");
        btnClose.setTextSize(16);
        btnClose.setTextColor(Color.WHITE);
        btnClose.setGravity(Gravity.CENTER);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(0x33FFFFFF);
        btnClose.setBackground(closeBg);
        android.widget.FrameLayout.LayoutParams closeP =
                new android.widget.FrameLayout.LayoutParams(dp(32), dp(32));
        closeP.gravity = Gravity.TOP | Gravity.END;
        closeP.setMargins(0, dp(16), dp(16), 0);
        btnClose.setLayoutParams(closeP);

        frameRoot.addView(sheet);
        frameRoot.addView(btnClose);

        // ── Show dialog ───────────────────────────────────────────────────────
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(frameRoot)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setGravity(Gravity.BOTTOM);
            int screenH = requireContext().getResources().getDisplayMetrics().heightPixels;
            android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.width            = android.view.WindowManager.LayoutParams.MATCH_PARENT;
            lp.height           = (int) (screenH * 0.88f);
            lp.horizontalMargin = 0;
            dialog.getWindow().setAttributes(lp);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stat Card (Present / Late / Absent)
    // ─────────────────────────────────────────────────────────────────────────

    private View buildStatCard(String label, int count, String bgHex, String textHex) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(14), dp(8), dp(14));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(0, 0, dp(8), 0);
        card.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        try { bg.setColor(Color.parseColor(bgHex)); }
        catch (Exception e) { bg.setColor(Color.WHITE); }
        card.setBackground(bg);

        // Count number
        TextView tvCount = new TextView(requireContext());
        tvCount.setText(String.valueOf(count));
        tvCount.setTextSize(28);
        tvCount.setTypeface(null, android.graphics.Typeface.BOLD);
        try { tvCount.setTextColor(Color.parseColor(textHex)); }
        catch (Exception e) { tvCount.setTextColor(Color.BLACK); }
        tvCount.setGravity(Gravity.CENTER);

        // Label
        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText(label);
        tvLabel.setTextSize(11);
        try { tvLabel.setTextColor(Color.parseColor(textHex)); }
        catch (Exception e) { tvLabel.setTextColor(Color.GRAY); }
        tvLabel.setAlpha(0.85f);
        tvLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.setMargins(0, dp(2), 0, 0);
        tvLabel.setLayoutParams(llp);

        card.addView(tvCount);
        card.addView(tvLabel);
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Session Log Card (date / arrival time / subject / status)
    // ─────────────────────────────────────────────────────────────────────────

    private View buildSessionCard(AttendanceRecord record) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout.LayoutParams cardP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardP.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardP);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(dp(16));
        cardBg.setColor(Color.WHITE);
        cardBg.setStroke(dp(1), Color.parseColor("#F3F4F6"));
        card.setBackground(cardBg);

        // ── Left: colored status bar ──────────────────────────────────────────
        View bar = new View(requireContext());
        String status = record.getStatusLabel();
        int barColor;
        switch (status) {
            case "Late":   barColor = Color.parseColor("#F59E0B"); break;
            case "Absent": barColor = Color.parseColor("#EF4444"); break;
            default:       barColor = Color.parseColor("#10B981"); break;
        }
        bar.setBackgroundColor(barColor);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(barColor);
        barBg.setCornerRadius(dp(4));
        bar.setBackground(barBg);
        LinearLayout.LayoutParams barP = new LinearLayout.LayoutParams(dp(4), dp(44));
        barP.setMargins(0, 0, dp(14), 0);
        bar.setLayoutParams(barP);

        // ── Center: date + subject name ───────────────────────────────────────
        LinearLayout info = new LinearLayout(requireContext());
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvDate = new TextView(requireContext());
        tvDate.setText(record.getDate());
        tvDate.setTextSize(13);
        tvDate.setTypeface(null, android.graphics.Typeface.BOLD);
        tvDate.setTextColor(Color.parseColor("#111827"));

        TextView tvSubject = new TextView(requireContext());
        tvSubject.setText(record.getSubject());
        tvSubject.setTextSize(12);
        tvSubject.setTextColor(Color.parseColor("#6B7280"));
        LinearLayout.LayoutParams subP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subP.setMargins(0, dp(2), 0, 0);
        tvSubject.setLayoutParams(subP);

        info.addView(tvDate);
        info.addView(tvSubject);

        // ── Right: arrival time + status badge ────────────────────────────────
        LinearLayout right = new LinearLayout(requireContext());
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        // Arrival time
        TextView tvTime = new TextView(requireContext());
        tvTime.setText(record.getTime());
        tvTime.setTextSize(13);
        tvTime.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTime.setTextColor(Color.parseColor("#374151"));
        tvTime.setGravity(Gravity.END);

        // Status badge
        TextView tvBadge = new TextView(requireContext());
        tvBadge.setText(status);
        tvBadge.setTextSize(10);
        tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        tvBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
        tvBadge.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams badgeP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeP.setMargins(0, dp(4), 0, 0);
        badgeP.gravity = Gravity.END;
        tvBadge.setLayoutParams(badgeP);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.RECTANGLE);
        badgeBg.setCornerRadius(dp(10));
        switch (status) {
            case "Late":
                badgeBg.setColor(Color.parseColor("#FEF9C3"));
                tvBadge.setTextColor(Color.parseColor("#A16207"));
                break;
            case "Absent":
                badgeBg.setColor(Color.parseColor("#FEE2E2"));
                tvBadge.setTextColor(Color.parseColor("#B91C1C"));
                break;
            default:
                badgeBg.setColor(Color.parseColor("#DCFCE7"));
                tvBadge.setTextColor(Color.parseColor("#15803D"));
                break;
        }
        tvBadge.setBackground(badgeBg);

        right.addView(tvTime);
        right.addView(tvBadge);

        card.addView(bar);
        card.addView(info);
        card.addView(right);
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper — icon + text row for header
    // ─────────────────────────────────────────────────────────────────────────

    private void addIconRow(LinearLayout parent, String icon, String text, int textSizeSp) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(4));
        row.setLayoutParams(lp);

        TextView tvIcon = new TextView(requireContext());
        tvIcon.setText(icon);
        tvIcon.setTextSize(12);

        TextView tvText = new TextView(requireContext());
        tvText.setText(text);
        tvText.setTextSize(textSizeSp);
        tvText.setTextColor(Color.WHITE);
        tvText.setAlpha(0.9f);

        row.addView(tvIcon);
        row.addView(tvText);
        parent.addView(row);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    private int dp(int dp) {
        float d = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * d);
    }
}