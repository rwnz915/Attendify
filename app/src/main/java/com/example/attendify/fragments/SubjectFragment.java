package com.example.attendify.fragments;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.R;
import com.example.attendify.models.Student;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.StudentRepository;
import com.example.attendify.repository.SubjectRepository;
import com.example.attendify.repository.SubjectRepository.SubjectItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SubjectFragment extends Fragment {

    private static final String[] PRESET_COLORS = {
            "#3B82F6", "#8B5CF6", "#10B981", "#F59E0B",
            "#EF4444", "#06B6D4", "#EC4899", "#F97316"
    };

    private UserProfile       currentUser;
    private List<SubjectItem> subjectList   = new ArrayList<>();
    private String            activeSection = "All";

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
        return inflater.inflate(R.layout.fragment_subject, container, false);
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

        loadSubjects();
    }

    // ── Load from Firestore ───────────────────────────────────────────────────

    private void loadSubjects() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        subjectContainer.removeAllViews();

        SubjectRepository.getInstance().getTeacherSubjects(currentUser.getId(),
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectItem> subjects) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            subjectList = subjects;
                            if (subjectList.isEmpty()) showEmpty();
                            else refreshUI();
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            showEmpty();
                        });
                    }
                });
    }

    private void showEmpty() {
        tvTotalSubjects.setText("0");
        tvTotalSections.setText("0");
        chipContainer.removeAllViews();
        subjectContainer.removeAllViews();
        TextView tv = new TextView(requireContext());
        tv.setText("No subjects found.");
        tv.setTextColor(0xFF9E9E9E);
        tv.setPadding(0, dp(24), 0, 0);
        subjectContainer.addView(tv);
    }

    // ── Refresh UI ────────────────────────────────────────────────────────────

    private void refreshUI() {
        tvTotalSubjects.setText(String.valueOf(subjectList.size()));

        Set<String> sections = new HashSet<>();
        for (SubjectItem s : subjectList)
            if (s.section != null) sections.add(s.section);
        tvTotalSections.setText(String.valueOf(sections.size()));

        chipContainer.removeAllViews();
        List<String> chips = new ArrayList<>();
        chips.add("All");
        chips.addAll(new ArrayList<>(sections));
        for (String label : chips) chipContainer.addView(buildChip(label));

        subjectContainer.removeAllViews();
        int idx = 0;
        for (SubjectItem subject : subjectList) {
            if (activeSection.equals("All") || activeSection.equals(subject.section)) {
                String color = PRESET_COLORS[idx % PRESET_COLORS.length];
                subjectContainer.addView(buildSubjectCard(subject, color));
            }
            idx++;
        }
    }

    // ── Subject card (uses item_subject.xml — same colored card style) ─────────

    private View buildSubjectCard(SubjectItem subject, String color) {
        View card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_subject, subjectContainer, false);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(20));
        try { bg.setColor(Color.parseColor(color)); }
        catch (Exception e) { bg.setColor(Color.parseColor("#3B82F6")); }
        card.setBackground(bg);

        ((TextView) card.findViewById(R.id.tv_item_section)).setText(subject.section);
        ((TextView) card.findViewById(R.id.tv_item_name)).setText(subject.name);
        ((TextView) card.findViewById(R.id.tv_item_teacher)).setText(subject.teacher);
        ((TextView) card.findViewById(R.id.tv_item_schedule)).setText(subject.schedule);

        TextView tvCount = card.findViewById(R.id.tv_item_student_count);
        tvCount.setText("...");

        TextView tvAttendance = card.findViewById(R.id.tv_item_attendance);
        if (tvAttendance != null) tvAttendance.setText("No data yet");

        // Load student count by section
        if (subject.section != null && !subject.section.isEmpty()) {
            StudentRepository.getInstance().getStudentsBySection(subject.section,
                    new StudentRepository.StudentsCallback() {
                        @Override
                        public void onSuccess(List<Student> students) {
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() ->
                                    tvCount.setText(students.size() + " Students"));
                        }
                        @Override
                        public void onFailure(String e) {
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> tvCount.setText("0 Students"));
                        }
                    });
        } else {
            tvCount.setText("0 Students");
        }

        card.setOnClickListener(v -> showDetailSheet(subject, color));
        return card;
    }

    // ── Detail bottom sheet ───────────────────────────────────────────────────

    private void showDetailSheet(SubjectItem subject, String color) {
        // Use a MaxHeightFrameLayout so the dialog wraps content but never
        // exceeds 88% of the screen height (matching the old fixed size).
        int screenH = requireContext().getResources().getDisplayMetrics().heightPixels;
        MaxHeightFrameLayout frameRoot = new MaxHeightFrameLayout(requireContext());
        frameRoot.setMaxHeight((int) (screenH * 0.88f));

        LinearLayout sheet = new LinearLayout(requireContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

        // Colored header
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(28), dp(60), dp(20));
        GradientDrawable hBg = new GradientDrawable();
        try { hBg.setColor(Color.parseColor(color)); }
        catch (Exception e) { hBg.setColor(Color.parseColor("#3B82F6")); }
        hBg.setCornerRadii(new float[]{ dp(28),dp(28), dp(28),dp(28), 0,0, 0,0 });
        header.setBackground(hBg);

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
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.setMargins(0, dp(2), 0, dp(10));
        tvName.setLayoutParams(nlp);

        LinearLayout teacherRow = new LinearLayout(requireContext());
        teacherRow.setOrientation(LinearLayout.HORIZONTAL);
        teacherRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        trp.setMargins(0, 0, 0, dp(4));
        teacherRow.setLayoutParams(trp);
        TextView tiIcon = new TextView(requireContext()); tiIcon.setText("👤 "); tiIcon.setTextSize(12);
        TextView tvTeacher = new TextView(requireContext());
        tvTeacher.setText(subject.teacher != null ? subject.teacher : "");
        tvTeacher.setTextSize(13); tvTeacher.setTextColor(Color.WHITE); tvTeacher.setAlpha(0.9f);
        teacherRow.addView(tiIcon); teacherRow.addView(tvTeacher);

        LinearLayout scheduleRow = new LinearLayout(requireContext());
        scheduleRow.setOrientation(LinearLayout.HORIZONTAL);
        scheduleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView siIcon = new TextView(requireContext()); siIcon.setText("🕐 "); siIcon.setTextSize(12);
        TextView tvSchedule = new TextView(requireContext());
        tvSchedule.setText(subject.schedule != null ? subject.schedule : "");
        tvSchedule.setTextSize(13); tvSchedule.setTextColor(Color.WHITE); tvSchedule.setAlpha(0.9f);
        scheduleRow.addView(siIcon); scheduleRow.addView(tvSchedule);

        header.addView(tvSection);
        header.addView(tvName);
        header.addView(teacherRow);
        header.addView(scheduleRow);

        // Scrollable body — WRAP_CONTENT so it only takes as much space as needed
        android.widget.ScrollView scrollView = new android.widget.ScrollView(requireContext());
        scrollView.setBackgroundColor(Color.WHITE);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // Let ScrollView fill remaining space up to the cap, but not force-expand
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollView.setLayoutParams(svLp);

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(Color.WHITE);
        body.setPadding(dp(20), dp(16), dp(20), dp(24));

        // Heading row
        LinearLayout headingRow = new LinearLayout(requireContext());
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hrp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hrp.setMargins(0, 0, 0, dp(12));
        headingRow.setLayoutParams(hrp);

        TextView tvHeading = new TextView(requireContext());
        tvHeading.setText("Students");
        tvHeading.setTextSize(16);
        tvHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        tvHeading.setTextColor(Color.parseColor("#111827"));
        tvHeading.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvCountLabel = new TextView(requireContext());
        tvCountLabel.setTextSize(13);
        tvCountLabel.setTextColor(Color.parseColor("#9CA3AF"));
        tvCountLabel.setText("Loading...");

        headingRow.addView(tvHeading);
        headingRow.addView(tvCountLabel);
        body.addView(headingRow);

        LinearLayout studentList = new LinearLayout(requireContext());
        studentList.setOrientation(LinearLayout.VERTICAL);

        // Loading spinner while fetching
        ProgressBar spinner = new ProgressBar(requireContext());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(32), dp(32));
        slp.gravity = Gravity.CENTER_HORIZONTAL;
        slp.setMargins(0, dp(16), 0, dp(16));
        spinner.setLayoutParams(slp);
        studentList.addView(spinner);

        body.addView(studentList);
        scrollView.addView(body);
        sheet.addView(header);
        sheet.addView(scrollView);

        // Close button
        TextView btnClose = new TextView(requireContext());
        btnClose.setText("✕");
        btnClose.setTextSize(16);
        btnClose.setTextColor(Color.WHITE);
        btnClose.setGravity(Gravity.CENTER);
        GradientDrawable cBg = new GradientDrawable();
        cBg.setShape(GradientDrawable.OVAL);
        cBg.setColor(0x33FFFFFF);
        btnClose.setBackground(cBg);
        android.widget.FrameLayout.LayoutParams clp =
                new android.widget.FrameLayout.LayoutParams(dp(32), dp(32));
        clp.gravity = Gravity.TOP | Gravity.END;
        clp.setMargins(0, dp(16), dp(16), 0);
        btnClose.setLayoutParams(clp);

        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(Color.WHITE);
        rootBg.setCornerRadii(new float[]{ dp(28),dp(28), dp(28),dp(28), 0,0, 0,0 });
        frameRoot.setBackground(rootBg);
        frameRoot.addView(sheet);
        frameRoot.addView(btnClose);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setView(frameRoot).create();
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setGravity(Gravity.BOTTOM);
            android.view.WindowManager.LayoutParams wlp = dialog.getWindow().getAttributes();
            wlp.width  = android.view.WindowManager.LayoutParams.MATCH_PARENT;
            // WRAP_CONTENT: the window grows with content up to the MaxHeightFrameLayout cap
            wlp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            wlp.horizontalMargin = 0;
            dialog.getWindow().setAttributes(wlp);
        }

        // Fetch real students by section
        if (subject.section != null && !subject.section.isEmpty()) {
            StudentRepository.getInstance().getStudentsBySection(subject.section,
                    new StudentRepository.StudentsCallback() {
                        @Override
                        public void onSuccess(List<Student> students) {
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> {
                                tvCountLabel.setText(students.size() + " students");
                                renderStudentList(studentList, students);
                            });
                        }
                        @Override
                        public void onFailure(String errorMessage) {
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> {
                                tvCountLabel.setText("0 students");
                                renderStudentList(studentList, new ArrayList<>());
                            });
                        }
                    });
        } else {
            tvCountLabel.setText("0 students");
            renderStudentList(studentList, new ArrayList<>());
        }
    }

    // ── Render student rows ───────────────────────────────────────────────────

    private void renderStudentList(LinearLayout container, List<Student> students) {
        container.removeAllViews();

        if (students.isEmpty()) {
            TextView tvEmpty = new TextView(requireContext());
            tvEmpty.setText("No students enrolled in this section.");
            tvEmpty.setTextSize(13);
            tvEmpty.setTextColor(Color.parseColor("#9CA3AF"));
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, dp(20), 0, dp(8));
            container.addView(tvEmpty);
            return;
        }

        for (Student s : students) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setShape(GradientDrawable.RECTANGLE);
            rowBg.setCornerRadius(dp(16));
            rowBg.setColor(Color.WHITE);
            rowBg.setStroke(dp(1), Color.parseColor("#F3F4F6"));
            row.setBackground(rowBg);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(10));
            row.setLayoutParams(rowLp);

            // Avatar
            TextView avatar = new TextView(requireContext());
            avatar.setText("👤");
            avatar.setTextSize(16);
            avatar.setGravity(Gravity.CENTER);
            GradientDrawable aBg = new GradientDrawable();
            aBg.setShape(GradientDrawable.OVAL);
            aBg.setColor(Color.parseColor("#F3F4F6"));
            avatar.setBackground(aBg);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(44), dp(44));
            alp.setMargins(0, 0, dp(12), 0);
            avatar.setLayoutParams(alp);

            // Name + ID
            LinearLayout info = new LinearLayout(requireContext());
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(requireContext());
            tvName.setText(s.getName());
            tvName.setTextSize(14);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setTextColor(Color.parseColor("#111827"));

            TextView tvId = new TextView(requireContext());
            tvId.setText(s.getSchoolId() != null ? s.getSchoolId() : "");
            tvId.setTextSize(12);
            tvId.setTextColor(Color.parseColor("#9CA3AF"));
            LinearLayout.LayoutParams idLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            idLp.setMargins(0, dp(2), 0, 0);
            tvId.setLayoutParams(idLp);

            info.addView(tvName);
            info.addView(tvId);

            row.addView(avatar);
            row.addView(info);
            container.addView(row);
        }
    }

    // ── Section chip ──────────────────────────────────────────────────────────

    private View buildChip(String label) {
        TextView chip = new TextView(requireContext());
        chip.setText(label);
        chip.setTextSize(12);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);

        updateChipStyle(chip, label.equals(activeSection));
        chip.setOnClickListener(v -> {
            activeSection = label;
            refreshUI();
        });
        return chip;
    }

    private void updateChipStyle(TextView chip, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(20));
        if (active) {
            bg.setColor(Color.parseColor("#FF2563EB"));
            chip.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.WHITE);
            bg.setStroke(dp(1), Color.parseColor("#FFE5E7EB"));
            chip.setTextColor(Color.parseColor("#FF6B7280"));
        }
        chip.setBackground(bg);
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density);
    }

    // ── Helper: FrameLayout with a maximum height constraint ─────────────────

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
}