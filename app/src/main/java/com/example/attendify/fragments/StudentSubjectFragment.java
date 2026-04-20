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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.R;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.SubjectRepository;
import com.example.attendify.repository.SubjectRepository.SubjectItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StudentSubjectFragment extends Fragment {

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
    }

    // ── Load from Firestore ───────────────────────────────────────────────────

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
                            refreshUI();
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

    // ── Refresh UI ────────────────────────────────────────────────────────────

    private void refreshUI() {
        tvTotalSubjects.setText(String.valueOf(subjectList.size()));

        Set<String> sections = new HashSet<>();
        for (SubjectItem s : subjectList) {
            if (s.section != null) sections.add(s.section);
        }
        tvTotalSections.setText(String.valueOf(sections.size()));

        // Section chips
        chipContainer.removeAllViews();
        List<String> chipLabels = new ArrayList<>();
        chipLabels.add("All");
        chipLabels.addAll(new ArrayList<>(sections));
        for (String label : chipLabels) chipContainer.addView(buildChip(label));

        // Subject cards
        subjectContainer.removeAllViews();
        for (SubjectItem subject : subjectList) {
            if (activeSection.equals("All") ||
                    activeSection.equals(subject.section)) {
                subjectContainer.addView(buildSubjectCard(subject));
            }
        }
    }

    // ── Subject Card ──────────────────────────────────────────────────────────

    private View buildSubjectCard(SubjectItem subject) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(dp(16));
        cardBg.setColor(Color.WHITE);
        cardBg.setStroke(dp(1), Color.parseColor("#FFE5E7EB"));
        card.setBackground(cardBg);

        // Color dot
        View dot = new View(requireContext());
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(4), dp(48));
        dotLp.setMarginEnd(dp(14));
        dot.setLayoutParams(dotLp);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.RECTANGLE);
        dotBg.setCornerRadius(dp(4));
        try {
            dotBg.setColor(Color.parseColor(subject.color != null ? subject.color : "#3B82F6"));
        } catch (Exception e) {
            dotBg.setColor(Color.parseColor("#3B82F6"));
        }
        dot.setBackground(dotBg);
        card.addView(dot);

        // Text block
        LinearLayout textBlock = new LinearLayout(requireContext());
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView tvName = new TextView(requireContext());
        tvName.setText(subject.name);
        tvName.setTextSize(16);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(Color.parseColor("#FF111827"));
        textBlock.addView(tvName);

        TextView tvSection = new TextView(requireContext());
        tvSection.setText(subject.section);
        tvSection.setTextSize(13);
        tvSection.setTextColor(Color.parseColor("#FF6B7280"));
        textBlock.addView(tvSection);

        if (subject.schedule != null) {
            TextView tvSchedule = new TextView(requireContext());
            tvSchedule.setText(subject.schedule);
            tvSchedule.setTextSize(12);
            tvSchedule.setTextColor(Color.parseColor("#FF9CA3AF"));
            textBlock.addView(tvSchedule);
        }

        if (subject.teacher != null) {
            TextView tvTeacher = new TextView(requireContext());
            tvTeacher.setText(subject.teacher);
            tvTeacher.setTextSize(12);
            tvTeacher.setTextColor(Color.parseColor("#FF9CA3AF"));
            textBlock.addView(tvTeacher);
        }

        card.addView(textBlock);
        return card;
    }

    // ── Section Chip ──────────────────────────────────────────────────────────

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
                if (activeSection.equals("All") ||
                        activeSection.equals(subject.section)) {
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
}