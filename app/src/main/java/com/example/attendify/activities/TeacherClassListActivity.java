package com.example.attendify.activities;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.ThemeManager;
import com.example.attendify.models.Student;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.StudentRepository;
import com.example.attendify.repository.SubjectRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Teacher's Class List screen — launched from the Home quick-action button.
 *
 * Logic:
 *  1. Load all subjects owned by the logged-in teacher.
 *  2. Collect distinct sections from those subjects.
 *  3. Fetch students for every section in parallel.
 *  4. Show an "All / Section…" filter row styled like SubjectFragment chips.
 */
public class TeacherClassListActivity extends AppCompatActivity {

    // ── Views ────────────────────────────────────────────────────────────────

    private ProgressBar       progressBar;
    private RecyclerView      rv;
    private TextView          tvEmpty;
    private TextView          tvCount;
    private LinearLayout      chipContainer;

    // ── State ────────────────────────────────────────────────────────────────

    private final List<StudentEntry> allStudents   = new ArrayList<>();
    private final List<String>       sections      = new ArrayList<>();
    private String                   activeSection = "All";
    private int                      accentColor;
    private StudentAdapter           adapter;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);

        buildLayout();                          // all UI constructed in code

        UserProfile me = AuthRepository.getInstance().getLoggedInUser();

        // ── Theme header ──────────────────────────────────────────────────────
        if (me != null) {
            accentColor = ThemeManager.getPrimaryColor(this, me.getRole());
            View headerBg = findViewById(R.id.tcl_header_bg);
            if (headerBg != null) {
                ThemeApplier.applyHeader(this, me.getRole(), headerBg);
            }
        } else {
            accentColor = Color.parseColor("#1D4ED8");
        }

        // ── Back button ───────────────────────────────────────────────────────
        View btnBack = findViewById(R.id.tcl_btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // ── RecyclerView ──────────────────────────────────────────────────────
        adapter = new StudentAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        // ── Load data ─────────────────────────────────────────────────────────
        loadTeacherStudents(me);
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    /**
     * Step 1 – fetch all subjects for this teacher, gather unique sections.
     * Step 2 – fetch students per section in parallel; merge when all done.
     */
    private void loadTeacherStudents(UserProfile teacher) {
        if (teacher == null) {
            showEmpty();
            return;
        }

        showLoading(true);

        SubjectRepository.getInstance().getTeacherSubjects(teacher.getId(),
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                        // Collect ordered unique sections
                        Set<String> sectionSet = new LinkedHashSet<>();
                        for (SubjectRepository.SubjectItem subj : subjects) {
                            if (subj.section != null && !subj.section.isEmpty()) {
                                sectionSet.add(subj.section);
                            }
                        }

                        if (sectionSet.isEmpty()) {
                            runOnUiThread(TeacherClassListActivity.this::showEmpty);
                            return;
                        }

                        sections.clear();
                        sections.addAll(sectionSet);

                        // Update subtitle with bullet-separated section names
                        TextView tvSections = findViewById(R.id.tcl_tv_sections);
                        if (tvSections != null) {
                            runOnUiThread(() ->
                                    tvSections.setText(String.join("  •  ", sections)));
                        }

                        // Fetch students for all sections in parallel
                        List<StudentEntry> combined = new ArrayList<>();
                        AtomicInteger pending = new AtomicInteger(sections.size());

                        for (String section : sections) {
                            StudentRepository.getInstance().getStudentsBySection(section,
                                    new StudentRepository.StudentsCallback() {
                                        @Override
                                        public void onSuccess(List<Student> students) {
                                            synchronized (combined) {
                                                for (Student s : students) {
                                                    combined.add(new StudentEntry(s, section));
                                                }
                                            }
                                            if (pending.decrementAndGet() == 0) {
                                                onAllLoaded(combined);
                                            }
                                        }

                                        @Override
                                        public void onFailure(String err) {
                                            if (pending.decrementAndGet() == 0) {
                                                onAllLoaded(combined);
                                            }
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        runOnUiThread(TeacherClassListActivity.this::showEmpty);
                    }
                });
    }

    /** Called once all parallel fetches complete. */
    private void onAllLoaded(List<StudentEntry> combined) {
        allStudents.clear();
        allStudents.addAll(combined);
        runOnUiThread(() -> {
            showLoading(false);
            buildChips();
            applyFilter();
        });
    }

    // ── Filter chips (SubjectFragment style) ──────────────────────────────────

    private void buildChips() {
        chipContainer.removeAllViews();

        List<String> labels = new ArrayList<>();
        labels.add("All");
        labels.addAll(sections);

        for (String label : labels) {
            chipContainer.addView(buildChip(label));
        }
    }

    private View buildChip(String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setTypeface(null, Typeface.BOLD);
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);

        applyChipStyle(chip, label.equals(activeSection));

        chip.setOnClickListener(v -> {
            activeSection = label;
            buildChips();        // rebuild so styles refresh
            applyFilter();
        });

        return chip;
    }

    /** Matches SubjectFragment's updateChipStyle exactly. */
    private void applyChipStyle(TextView chip, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(20));
        if (active) {
            bg.setColor(accentColor);
            chip.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.WHITE);
            bg.setStroke(dp(1), Color.parseColor("#FFE5E7EB"));
            chip.setTextColor(Color.parseColor("#FF6B7280"));
        }
        chip.setBackground(bg);
    }

    // ── Filter application ────────────────────────────────────────────────────

    private void applyFilter() {
        List<StudentEntry> filtered = new ArrayList<>();
        for (StudentEntry e : allStudents) {
            if ("All".equals(activeSection) || activeSection.equals(e.section)) {
                filtered.add(e);
            }
        }

        adapter.updateList(filtered);
        tvCount.setText(String.valueOf(filtered.size()));

        if (filtered.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
        }
    }

    // ── UI state helpers ──────────────────────────────────────────────────────

    private void showLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            rv.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void showEmpty() {
        showLoading(false);
        tvEmpty.setVisibility(View.VISIBLE);
        rv.setVisibility(View.GONE);
        tvCount.setText("0");
    }

    // ── Data wrapper ──────────────────────────────────────────────────────────

    /** Pairs a Student with its resolved section string. */
    private static class StudentEntry {
        final Student student;
        final String  section;

        StudentEntry(Student student, String section) {
            this.student = student;
            this.section = section;
        }
    }

    // ── RecyclerView adapter ──────────────────────────────────────────────────

    private class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.VH> {

        private List<StudentEntry> list = new ArrayList<>();

        void updateList(List<StudentEntry> newList) {
            list = new ArrayList<>(newList);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Row built entirely in code — mirrors SecretaryClassListActivity's item_student
            // but with an extra section badge on the right.

            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setShape(GradientDrawable.RECTANGLE);
            rowBg.setCornerRadius(dp(16));
            rowBg.setColor(Color.WHITE);
            rowBg.setStroke(dp(1), Color.parseColor("#F3F4F6"));
            row.setBackground(rowBg);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(dp(16), 0, dp(16), dp(10));
            row.setLayoutParams(rowLp);

            // ── Avatar circle ─────────────────────────────────────────────────
            TextView avatar = new TextView(parent.getContext());
            avatar.setTextSize(16f);
            avatar.setTypeface(null, Typeface.BOLD);
            avatar.setGravity(Gravity.CENTER);
            avatar.setTextColor(accentColor);

            GradientDrawable aBg = new GradientDrawable();
            aBg.setShape(GradientDrawable.OVAL);
            int tintBg = (accentColor & 0x00FFFFFF) | 0x1A000000; // ~10 % opacity
            aBg.setColor(tintBg);
            avatar.setBackground(aBg);

            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(44), dp(44));
            alp.setMarginEnd(dp(12));
            avatar.setLayoutParams(alp);

            // ── Name + school-ID column ───────────────────────────────────────
            LinearLayout info = new LinearLayout(parent.getContext());
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(parent.getContext());
            tvName.setTextSize(14f);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setTextColor(Color.parseColor("#111827"));

            TextView tvId = new TextView(parent.getContext());
            tvId.setTextSize(12f);
            tvId.setTextColor(Color.parseColor("#9CA3AF"));
            LinearLayout.LayoutParams idLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            idLp.topMargin = dp(2);
            tvId.setLayoutParams(idLp);

            info.addView(tvName);
            info.addView(tvId);

            // ── Section badge (right side) ────────────────────────────────────
            TextView tvSec = new TextView(parent.getContext());
            tvSec.setTextSize(11f);
            tvSec.setTypeface(null, Typeface.BOLD);
            tvSec.setPadding(dp(8), dp(4), dp(8), dp(4));

            GradientDrawable secBg = new GradientDrawable();
            secBg.setShape(GradientDrawable.RECTANGLE);
            secBg.setCornerRadius(dp(20));
            secBg.setColor(tintBg);
            tvSec.setBackground(secBg);
            tvSec.setTextColor(accentColor);

            row.addView(avatar);
            row.addView(info);
            row.addView(tvSec);

            return new VH(row, avatar, tvName, tvId, tvSec);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            StudentEntry e = list.get(position);
            Student      s = e.student;

            String name = s.getName() != null ? s.getName() : "—";
            h.tvName.setText(name);
            h.tvId.setText(s.getSchoolId() != null ? s.getSchoolId() : "");
            h.avatar.setText(name.isEmpty() ? "?"
                    : String.valueOf(Character.toUpperCase(name.charAt(0))));

            // Section badge — hide when a specific section filter is active (redundant)
            h.tvSection.setText(e.section);
            h.tvSection.setVisibility("All".equals(activeSection) ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView avatar, tvName, tvId, tvSection;

            VH(View v, TextView avatar, TextView tvName, TextView tvId, TextView tvSection) {
                super(v);
                this.avatar    = avatar;
                this.tvName    = tvName;
                this.tvId      = tvId;
                this.tvSection = tvSection;
            }
        }
    }

    // ── Programmatic layout ───────────────────────────────────────────────────

    private void buildLayout() {
        // ── Root ──────────────────────────────────────────────────────────────
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F9FAFB"));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        // ── Header ────────────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setId(R.id.tcl_header_bg);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(64), dp(20), dp(20));  // ← dp(64) top padding
        header.setBackgroundColor(Color.parseColor("#1D4ED8"));

// Back arrow + title row
        // Back arrow + title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleRow.setLayoutParams(titleRowLp);

        ImageView btnBack = new ImageView(this);
        btnBack.setId(R.id.tcl_btn_back);
        btnBack.setImageResource(R.drawable.ic_arrow_back);
        btnBack.setColorFilter(Color.WHITE);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        backLp.gravity = Gravity.CENTER_VERTICAL;
        btnBack.setLayoutParams(backLp);

// Invisible spacer matching the back button width
        ImageView spacer = new ImageView(this);
        spacer.setImageResource(R.drawable.ic_arrow_back);
        spacer.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        spacer.setLayoutParams(spacerLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Class List");
        tvTitle.setTextSize(20f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvTitle.setLayoutParams(titleLp);

        titleRow.addView(btnBack);
        titleRow.addView(tvTitle);
        titleRow.addView(spacer); // balances the left arrow so title is truly centered

        // Section subtitle (filled once subjects load)
        /*TextView tvSections = new TextView(this);
        tvSections.setId(R.id.tcl_tv_sections);
        tvSections.setTextSize(12f);
        tvSections.setTextColor(0xCCFFFFFF);
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        secLp.topMargin = dp(4);
        tvSections.setLayoutParams(secLp);*/

        header.addView(titleRow);
        //header.addView(tvSections);

        // ── "Students: N" label ───────────────────────────────────────────────
        LinearLayout countBar = new LinearLayout(this);
        countBar.setOrientation(LinearLayout.HORIZONTAL);
        countBar.setGravity(Gravity.CENTER_VERTICAL);
        countBar.setPadding(dp(16), dp(12), dp(16), dp(4));

        TextView tvStudentsLabel = new TextView(this);
        tvStudentsLabel.setText("Students: ");
        tvStudentsLabel.setTextSize(13f);
        tvStudentsLabel.setTextColor(Color.parseColor("#6B7280"));

        tvCount = new TextView(this);
        tvCount.setId(R.id.tcl_tv_count);
        tvCount.setText("0");
        tvCount.setTextSize(13f);
        tvCount.setTypeface(null, Typeface.BOLD);
        tvCount.setTextColor(Color.parseColor("#111827"));
        tvCount.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        countBar.addView(tvStudentsLabel);
        countBar.addView(tvCount);

        // ── Chip scroll bar ───────────────────────────────────────────────────
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setPadding(dp(16), dp(4), dp(16), dp(12));

        chipContainer = new LinearLayout(this);
        chipContainer.setId(R.id.tcl_chip_container);
        chipContainer.setOrientation(LinearLayout.HORIZONTAL);
        chipContainer.setGravity(Gravity.CENTER_VERTICAL);
        chipScroll.addView(chipContainer);

        // ── Progress indicator ────────────────────────────────────────────────
        progressBar = new ProgressBar(this);
        progressBar.setId(R.id.tcl_progress);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pbLp.gravity   = Gravity.CENTER_HORIZONTAL;
        pbLp.topMargin = dp(40);
        progressBar.setLayoutParams(pbLp);

        // ── Empty state text ──────────────────────────────────────────────────
        tvEmpty = new TextView(this);
        tvEmpty.setId(R.id.tcl_tv_empty);
        tvEmpty.setText("No students found.");
        tvEmpty.setTextColor(Color.parseColor("#9CA3AF"));
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setVisibility(View.GONE);
        LinearLayout.LayoutParams emLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        emLp.topMargin = dp(40);
        tvEmpty.setLayoutParams(emLp);

        // ── Student list ──────────────────────────────────────────────────────
        rv = new RecyclerView(this);
        rv.setId(R.id.tcl_rv);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // ── Content wrapper ───────────────────────────────────────────────────
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(countBar);
        content.addView(chipScroll);
        content.addView(progressBar);
        content.addView(tvEmpty);
        content.addView(rv);

        root.addView(header);
        root.addView(content);
        setContentView(root);
    }

    // ── dp helper ─────────────────────────────────────────────────────────────

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}