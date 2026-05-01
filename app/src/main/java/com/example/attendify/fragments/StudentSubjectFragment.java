package com.example.attendify.fragments;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.R;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.SubjectRepository;
import com.example.attendify.repository.SubjectRepository.SubjectItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StudentSubjectFragment extends Fragment {

    // Purple-anchored palette that complements the student theme (#6D28D9 → #8B5CF6).
    // Used as fallback when subject.color is null in Firestore.
    private static final String[] PRESET_COLORS = {
            "#7C3AED", "#0D9488", "#6D28D9", "#1D4ED8",
            "#9333EA", "#0891B2", "#A855F7", "#4F46E5"
    };

    private UserProfile currentUser;
    private List<SubjectItem> subjectList = new ArrayList<>();
    private String activeSection = "All";

    private LinearLayout subjectContainer;
    private LinearLayout chipContainer;
    private TextView     tvTotalSubjects;
    private TextView     tvTotalSections;
    private ProgressBar  progressBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_subject, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        subjectContainer = view.findViewById(R.id.subject_container);
        chipContainer    = view.findViewById(R.id.chip_container);
        tvTotalSubjects  = view.findViewById(R.id.tv_total_subjects);
        tvTotalSections  = view.findViewById(R.id.tv_total_sections);
        progressBar      = view.findViewById(R.id.progress_bar);

        currentUser = AuthRepository.getInstance().getLoggedInUser();
        if (currentUser == null) return;

        loadSubjectsFromFirestore();

        // ── TEST: Record a dummy attendance entry ─────────────────────────────────
        /*Button btnTest = new Button(requireContext());
        btnTest.setText("Test Record Attendance");
        btnTest.setOnClickListener(v -> {
            AttendanceRepository.getInstance().recordAttendance(
                    "student_001",          // studentId
                    "Juan Dela Cruz",       // studentName
                    "subject_abc",          // subjectId
                    "Mathematics",          // subjectName
                    "2025-04-25",           // date  (yyyy-MM-dd)
                    "08:30 AM",             // time
                    "Present",              // status: Present / Late / Absent
                    new AttendanceRepository.SubmitCallback() {
                        @Override
                        public void onSuccess() {
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(getContext(),
                                            "✅ Attendance recorded!", Toast.LENGTH_SHORT).show());
                        }
                        @Override
                        public void onFailure(String errorMessage) {
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(getContext(),
                                            "❌ Failed: " + errorMessage, Toast.LENGTH_LONG).show());
                        }
                    }
            );
        });
        subjectContainer.addView(btnTest);*/
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load from Firestore
    // ─────────────────────────────────────────────────────────────────────────

    private void loadSubjectsFromFirestore() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        subjectContainer.removeAllViews();

        String section = currentUser.getSection(); // e.g. "IT-203"

        SubjectRepository.getInstance().getStudentSubjects(section,
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectItem> subjects) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            subjectList = subjects;
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            refreshAll();
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(),
                                    "Failed to load subjects: " + errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Refresh UI
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshAll() {
        tvTotalSubjects.setText(String.valueOf(subjectList.size()));

        Set<String> sections = new HashSet<>();
        for (SubjectItem s : subjectList) {
            if (s.section != null) sections.add(s.section);
        }
        tvTotalSections.setText(String.valueOf(sections.size()));

        // Rebuild section filter chips
        chipContainer.removeAllViews();
        List<String> chipLabels = new ArrayList<>();
        chipLabels.add("All");
        chipLabels.addAll(new ArrayList<>(sections));
        for (String label : chipLabels) chipContainer.addView(buildChip(label));

        // Rebuild subject cards
        subjectContainer.removeAllViews();
        for (SubjectItem subject : subjectList) {
            if (activeSection.equals("All") || activeSection.equals(subject.section)) {
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

        updateChipStyle(chip, label.equals(activeSection));

        chip.setOnClickListener(v -> {
            activeSection = label;
            // Re-style all chips
            for (int i = 0; i < chipContainer.getChildCount(); i++) {
                View child = chipContainer.getChildAt(i);
                if (child instanceof TextView) {
                    updateChipStyle((TextView) child,
                            ((TextView) child).getText().toString().equals(activeSection));
                }
            }
            // Rebuild subject cards
            subjectContainer.removeAllViews();
            for (SubjectItem subject : subjectList) {
                if (activeSection.equals("All") || activeSection.equals(subject.section)) {
                    subjectContainer.addView(buildSubjectCard(subject));
                }
            }
        });

        return chip;
    }

    private void updateChipStyle(TextView chip, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(20));
        if (active) {
            bg.setColor(Color.parseColor("#6D28D9"));
            chip.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.WHITE);
            bg.setStroke(dp(1), Color.parseColor("#E5E7EB"));
            chip.setTextColor(Color.parseColor("#6B7280"));
        }
        chip.setBackground(bg);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Subject Card  (colored full-card style from Doc 1)
    // ─────────────────────────────────────────────────────────────────────────

    private View buildSubjectCard(SubjectItem subject) {
        // Inflate the same item_subject layout used in Doc 1
        View card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_subject, subjectContainer, false);

        // Colored rounded background
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(20));
        // Use subject.color from Firestore; fall back to the purple-themed preset palette
        int cardIndex = subjectList.indexOf(subject);
        String fallback = PRESET_COLORS[cardIndex >= 0 ? cardIndex % PRESET_COLORS.length : 0];
        try {
            bg.setColor(Color.parseColor(subject.color != null ? subject.color : fallback));
        } catch (Exception e) {
            bg.setColor(Color.parseColor(fallback));
        }
        card.setBackground(bg);

        // Populate fields (null-safe)
        setText(card, R.id.tv_item_section,  subject.section);
        setText(card, R.id.tv_item_name,     subject.name);
        setText(card, R.id.tv_item_teacher,  subject.teacher);
        setText(card, R.id.tv_item_schedule, subject.schedule);

        // Attendance rate — loaded from Firestore via SubjectRepository
        loadAttendanceRate(card, subject);

        // Hide the generic attendance TextView if present
        TextView tvAttendance = card.findViewById(R.id.tv_item_attendance);
        if (tvAttendance != null) tvAttendance.setVisibility(View.GONE);

        // Tap → detail sheet (history loaded inside)
        card.setOnClickListener(v -> loadAndShowDetailSheet(subject));

        return card;
    }

    /** Safely set text; hides view if value is null. */
    private void setText(View root, int viewId, String value) {
        TextView tv = root.findViewById(viewId);
        if (tv == null) return;
        if (value != null) {
            tv.setText(value);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load attendance rate for card badge from Firestore
    // ─────────────────────────────────────────────────────────────────────────

    private void loadAttendanceRate(View card, SubjectItem subject) {
        TextView tvCount = card.findViewById(R.id.tv_item_student_count);
        if (tvCount == null) return;
        tvCount.setText("Loading…");

        String studentId = currentUser.getId();

        SubjectRepository.getInstance().getSubjectHistory(studentId, subject.id,
                new SubjectRepository.AttendanceCallback() {
                    @Override
                    public void onSuccess(List<AttendanceRecord> history) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            int total = history.size();
                            int present = 0;
                            for (AttendanceRecord r : history) present += r.getPresent();
                            int rate = total > 0 ? Math.round((present * 100f) / total) : 0;
                            tvCount.setText(rate + "% Attendance");
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> tvCount.setText("N/A"));
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load history then show detail sheet
    // ─────────────────────────────────────────────────────────────────────────

    private void loadAndShowDetailSheet(SubjectItem subject) {
        String studentId = currentUser.getId();

        //Log.d("DEBUG_SUBJECT", "UI subjectId: " + subject.id);
        //Log.d("DEBUG_SUBJECT", "User ID: " + currentUser.getId());

        SubjectRepository.getInstance().getSubjectHistory(studentId, subject.id,
                new SubjectRepository.AttendanceCallback() {
                    @Override
                    public void onSuccess(List<AttendanceRecord> history) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() ->
                                showSubjectDetailSheet(subject, history));
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() ->
                                // Open the sheet with empty history instead of blocking the user.
                                // A missing-index / permission error is rare; no records is the
                                // common case and the sheet already handles it gracefully.
                                showSubjectDetailSheet(subject, new ArrayList<>()));
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Detail Sheet — wraps content, capped at 88 % screen height
    // ─────────────────────────────────────────────────────────────────────────

    private void showSubjectDetailSheet(SubjectItem subject, List<AttendanceRecord> history) {

        // ── Compute stats ─────────────────────────────────────────────────────
        int totalPresent = 0, totalLate = 0, totalAbsent = 0;
        for (AttendanceRecord r : history) {
            totalPresent += r.getPresent();
            totalLate    += r.getLate();
            totalAbsent  += r.getAbsent();
        }
        int total = history.size();
        int rate  = total > 0 ? Math.round((totalPresent * 100f) / total) : 0;

        // ── Root: MaxHeightFrameLayout caps at 88 % ───────────────────────────
        int screenH = requireContext().getResources().getDisplayMetrics().heightPixels;
        MaxHeightFrameLayout frameRoot = new MaxHeightFrameLayout(requireContext());
        frameRoot.setMaxHeight((int) (screenH * 0.88f));

        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.parseColor("#F9FAFB"));
        sheetBg.setCornerRadii(new float[]{ dp(28),dp(28), dp(28),dp(28), 0,0, 0,0 });
        frameRoot.setBackground(sheetBg);

        // ── Sheet (header + scroll) ───────────────────────────────────────────
        LinearLayout sheet = new LinearLayout(requireContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

        // ── Colored header ────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(28), dp(60), dp(20));
        GradientDrawable headerBg = new GradientDrawable();
        int sheetIdx = subjectList.indexOf(subject);
        String sheetFallback = PRESET_COLORS[sheetIdx >= 0 ? sheetIdx % PRESET_COLORS.length : 0];
        try { headerBg.setColor(Color.parseColor(subject.color != null ? subject.color : sheetFallback)); }
        catch (Exception e) { headerBg.setColor(Color.parseColor(sheetFallback)); }
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

        if (subject.teacher != null)  addIconRow(header, "👤 ", subject.teacher, 13);
        if (subject.schedule != null) addIconRow(header, "🕐 ", subject.schedule, 13);

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
        // WRAP_CONTENT so the sheet grows with content up to the cap
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(Color.parseColor("#F9FAFB"));
        body.setPadding(dp(16), dp(16), dp(16), dp(28));

        // ── Stat cards row ────────────────────────────────────────────────────
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

        // ── Session log heading row (label + count) ───────────────────────────
        LinearLayout logHeadingRow = new LinearLayout(requireContext());
        logHeadingRow.setOrientation(LinearLayout.HORIZONTAL);
        logHeadingRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lhrP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lhrP.setMargins(0, 0, 0, dp(10));
        logHeadingRow.setLayoutParams(lhrP);

        TextView tvHeading = new TextView(requireContext());
        tvHeading.setText("Attendance log");
        tvHeading.setTextSize(15);
        tvHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        tvHeading.setTextColor(Color.parseColor("#111827"));
        tvHeading.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvLogCount = new TextView(requireContext());
        tvLogCount.setTextSize(13);
        tvLogCount.setTextColor(Color.parseColor("#9CA3AF"));
        tvLogCount.setText(total + " session" + (total == 1 ? "" : "s"));

        logHeadingRow.addView(tvHeading);
        logHeadingRow.addView(tvLogCount);
        body.addView(logHeadingRow);

        // ── Session log cards ─────────────────────────────────────────────────
        if (history.isEmpty()) {
            TextView tvEmpty = new TextView(requireContext());
            tvEmpty.setText("No records yet.");
            tvEmpty.setTextSize(13);
            tvEmpty.setTextColor(Color.parseColor("#9CA3AF"));
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, dp(20), 0, dp(8));
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

        // ── Show dialog — WRAP_CONTENT height, capped by MaxHeightFrameLayout ─
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(frameRoot)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setGravity(Gravity.BOTTOM);
            android.view.WindowManager.LayoutParams wlp = dialog.getWindow().getAttributes();
            wlp.width            = android.view.WindowManager.LayoutParams.MATCH_PARENT;
            wlp.height           = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            wlp.horizontalMargin = 0;
            dialog.getWindow().setAttributes(wlp);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MaxHeightFrameLayout — wraps content but never exceeds a pixel cap
    // ─────────────────────────────────────────────────────────────────────────

    private static class MaxHeightFrameLayout extends android.widget.FrameLayout {
        private int maxHeight = Integer.MAX_VALUE;

        public MaxHeightFrameLayout(android.content.Context context) {
            super(context);
        }

        public void setMaxHeight(int maxHeightPx) {
            this.maxHeight = maxHeightPx;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int cappedHeight = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, cappedHeight);
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

        TextView tvCount = new TextView(requireContext());
        tvCount.setText(String.valueOf(count));
        tvCount.setTextSize(28);
        tvCount.setTypeface(null, android.graphics.Typeface.BOLD);
        try { tvCount.setTextColor(Color.parseColor(textHex)); }
        catch (Exception e) { tvCount.setTextColor(Color.BLACK); }
        tvCount.setGravity(Gravity.CENTER);

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
    // Session Log Card
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

        // ── Colored status bar ────────────────────────────────────────────────
        String status = record.getStatusLabel();
        int barColor;
        switch (status) {
            case "Late":   barColor = Color.parseColor("#F59E0B"); break;
            case "Absent": barColor = Color.parseColor("#EF4444"); break;
            default:       barColor = Color.parseColor("#10B981"); break;
        }
        View bar = new View(requireContext());
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(barColor);
        barBg.setCornerRadius(dp(4));
        bar.setBackground(barBg);
        LinearLayout.LayoutParams barP = new LinearLayout.LayoutParams(dp(4), dp(44));
        barP.setMargins(0, 0, dp(14), 0);
        bar.setLayoutParams(barP);

        // ── Center: date + subject ────────────────────────────────────────────
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

        TextView tvTime = new TextView(requireContext());
        tvTime.setText(record.getTime());
        tvTime.setTextSize(13);
        tvTime.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTime.setTextColor(Color.parseColor("#374151"));
        tvTime.setGravity(Gravity.END);

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