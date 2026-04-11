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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SubjectFragment extends Fragment {

    // ─────────────────────────────────────────────────────────────────────────
    // Data Models
    // ─────────────────────────────────────────────────────────────────────────

    public static class Student {
        public int    id;
        public String name;
        public String studentId;

        public Student(int id, String name, String studentId) {
            this.id        = id;
            this.name      = name;
            this.studentId = studentId;
        }
    }

    public static class Subject {
        public int           id;
        public String        name;
        public String        section;
        public String        teacher;
        public String        schedule;
        public String        color;
        public int           attendanceRate;
        public List<Student> students = new ArrayList<>();

        public Subject(int id, String name, String section,
                       String teacher, String schedule, String color, int attendanceRate) {
            this.id             = id;
            this.name           = name;
            this.section        = section;
            this.teacher        = teacher;
            this.schedule       = schedule;
            this.color          = color;
            this.attendanceRate = attendanceRate;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────────

    private final List<Subject> subjectList = new ArrayList<>();
    private int nextSubjectId = 1;
    private String activeSection = "All";

    // ─────────────────────────────────────────────────────────────────────────
    // Views
    // ─────────────────────────────────────────────────────────────────────────

    private LinearLayout subjectContainer;
    private LinearLayout chipContainer;
    private TextView     tvTotalSubjects;
    private TextView     tvTotalSections;

    // ─────────────────────────────────────────────────────────────────────────
    // Fragment Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

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

        seedData();
        refreshAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Seed Data — edit here to add/remove subjects and students
    // ─────────────────────────────────────────────────────────────────────────

    private void seedData() {

        // ── Mathematics · IT-201 ─────────────────────────────────────────────
        Subject math = new Subject(nextSubjectId++,
                "Mathematics", "IT-201", "Ms. Johnson",
                "MWF 8:00-9:30 AM", "#3B82F6", 88);
        math.students.add(new Student(1,  "Morandarte, Renz",    "2024-00123"));
        math.students.add(new Student(2,  "Tiozon, Hendrix",     "2024-00124"));
        math.students.add(new Student(3,  "Puti, Jericho",       "2024-00125"));
        math.students.add(new Student(4,  "Desaliza, Cyrus",     "2024-00126"));
        math.students.add(new Student(5,  "Susvilla, Andrei",    "2024-00127"));
        math.students.add(new Student(6,  "Cunanan, Angelo",     "2024-00128"));
        math.students.add(new Student(7,  "Lozano, Nash",        "2024-00129"));
        math.students.add(new Student(8,  "Protestante, Angel",  "2024-00130"));
        subjectList.add(math);

        // ── Physics · IT-201 ────────────────────────────────────────────────
        Subject physics = new Subject(nextSubjectId++,
                "Physics", "IT-201", "Mr. Smith",
                "TTh 10:00-11:30 AM", "#8B5CF6", 82);
        physics.students.add(new Student(1,  "Morandarte, Renz",    "2024-00123"));
        physics.students.add(new Student(2,  "Tiozon, Hendrix",     "2024-00124"));
        physics.students.add(new Student(3,  "Puti, Jericho",       "2024-00125"));
        physics.students.add(new Student(4,  "Desaliza, Cyrus",     "2024-00126"));
        physics.students.add(new Student(5,  "Susvilla, Andrei",    "2024-00127"));
        physics.students.add(new Student(6,  "Cunanan, Angelo",     "2024-00128"));
        physics.students.add(new Student(7,  "Lozano, Nash",        "2024-00129"));
        physics.students.add(new Student(8,  "Protestante, Angel",  "2024-00130"));
        subjectList.add(physics);

        // ── Programming · IT-202 ────────────────────────────────────────────
        Subject prog = new Subject(nextSubjectId++,
                "Programming", "IT-202", "Ms. Davis",
                "MWF 1:00-2:30 PM", "#10B981", 91);
        prog.students.add(new Student(9,  "Garcia, Maria",   "2024-00131"));
        prog.students.add(new Student(10, "Santos, Juan",    "2024-00132"));
        prog.students.add(new Student(11, "Cruz, Sofia",     "2024-00133"));
        prog.students.add(new Student(12, "Reyes, Miguel",   "2024-00134"));
        prog.students.add(new Student(13, "Torres, Andrea",  "2024-00135"));
        prog.students.add(new Student(14, "Flores, Carlos",  "2024-00136"));
        subjectList.add(prog);

        // ── Entrepreneurship · IT-203 ────────────────────────────────────────
        Subject entrep = new Subject(nextSubjectId++,
                "Entrepreneurship", "IT-203", "Ms. Rodriguez",
                "TTh 8:00-9:30 AM", "#F59E0B", 85);
        entrep.students.add(new Student(15, "Villanueva, Lea",   "2024-00137"));
        entrep.students.add(new Student(16, "Mendoza, Paolo",    "2024-00138"));
        entrep.students.add(new Student(17, "Aquino, Trisha",    "2024-00139"));
        entrep.students.add(new Student(18, "Ramos, Jerome",     "2024-00140"));
        entrep.students.add(new Student(19, "Castillo, Bianca",  "2024-00141"));
        entrep.students.add(new Student(20, "Navarro, Felix",    "2024-00142"));
        subjectList.add(entrep);

        // ── Database Systems · IT-202 ────────────────────────────────────────
        Subject db = new Subject(nextSubjectId++,
                "Database Systems", "IT-202", "Mr. Brown",
                "MWF 10:00-11:30 AM", "#EF4444", 79);
        db.students.add(new Student(9,  "Garcia, Maria",   "2024-00131"));
        db.students.add(new Student(10, "Santos, Juan",    "2024-00132"));
        db.students.add(new Student(11, "Cruz, Sofia",     "2024-00133"));
        db.students.add(new Student(12, "Reyes, Miguel",   "2024-00134"));
        db.students.add(new Student(13, "Torres, Andrea",  "2024-00135"));
        db.students.add(new Student(14, "Flores, Carlos",  "2024-00136"));
        subjectList.add(db);

        // ── Web Development · IT-203 ─────────────────────────────────────────
        Subject web = new Subject(nextSubjectId++,
                "Web Development", "IT-203", "Ms. Wilson",
                "TTh 1:00-2:30 PM", "#06B6D4", 93);
        web.students.add(new Student(15, "Villanueva, Lea",   "2024-00137"));
        web.students.add(new Student(16, "Mendoza, Paolo",    "2024-00138"));
        web.students.add(new Student(17, "Aquino, Trisha",    "2024-00139"));
        web.students.add(new Student(18, "Ramos, Jerome",     "2024-00140"));
        web.students.add(new Student(19, "Castillo, Bianca",  "2024-00141"));
        web.students.add(new Student(20, "Navarro, Felix",    "2024-00142"));
        subjectList.add(web);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Refresh UI
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshAll() {
        // Update stat counters
        tvTotalSubjects.setText(String.valueOf(subjectList.size()));
        Set<String> sections = new HashSet<>();
        for (Subject s : subjectList) sections.add(s.section);
        tvTotalSections.setText(String.valueOf(sections.size()));

        // Rebuild filter chips
        chipContainer.removeAllViews();
        List<String> chipLabels = new ArrayList<>();
        chipLabels.add("All");
        chipLabels.addAll(new ArrayList<>(sections));
        for (String label : chipLabels) {
            chipContainer.addView(buildChip(label));
        }

        // Rebuild subject cards (filtered by active section)
        subjectContainer.removeAllViews();
        for (Subject subject : subjectList) {
            if (activeSection.equals("All") || subject.section.equals(activeSection)) {
                subjectContainer.addView(buildSubjectCard(subject));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build Section Filter Chip
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
        chip.setOnClickListener(v -> {
            activeSection = label;
            refreshAll();
        });
        return chip;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build Subject Card
    // ─────────────────────────────────────────────────────────────────────────

    private View buildSubjectCard(Subject subject) {
        View card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_subject, subjectContainer, false);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(20));
        try { bg.setColor(Color.parseColor(subject.color)); }
        catch (Exception e) { bg.setColor(Color.parseColor("#3B82F6")); }
        card.setBackground(bg);

        ((TextView) card.findViewById(R.id.tv_item_section))
                .setText(subject.section);
        ((TextView) card.findViewById(R.id.tv_item_name))
                .setText(subject.name);
        ((TextView) card.findViewById(R.id.tv_item_teacher))
                .setText(subject.teacher);
        ((TextView) card.findViewById(R.id.tv_item_schedule))
                .setText(subject.schedule);
        ((TextView) card.findViewById(R.id.tv_item_student_count))
                .setText(subject.students.size() + " Students");

        TextView tvAttendance = card.findViewById(R.id.tv_item_attendance);
        if (tvAttendance != null) {
            tvAttendance.setText(subject.attendanceRate == 0
                    ? "No data yet"
                    : subject.attendanceRate + "% Attendance");
        }

        card.setOnClickListener(v -> showSubjectDetailSheet(subject));
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Subject Detail Sheet
    // ─────────────────────────────────────────────────────────────────────────
    private void showSubjectDetailSheet(Subject subject) {

        // ── FrameLayout as root (allows X button overlay) ────────────────────────
        android.widget.FrameLayout frameRoot = new android.widget.FrameLayout(requireContext());

        // ── Sheet: header + scrollable body ─────────────────────────────────────
        LinearLayout sheet = new LinearLayout(requireContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundColor(Color.TRANSPARENT);
        android.widget.FrameLayout.LayoutParams sheetParams =
                new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        sheet.setLayoutParams(sheetParams);

        // ── Colored header ───────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(28), dp(60), dp(20));

        GradientDrawable headerBg = new GradientDrawable();
        try { headerBg.setColor(Color.parseColor(subject.color)); }
        catch (Exception e) { headerBg.setColor(Color.parseColor("#3B82F6")); }
        headerBg.setCornerRadii(new float[]{ dp(28),dp(28), dp(28),dp(28), 0,0, 0,0 });
        header.setBackground(headerBg);

        // Section label
        TextView tvSection = new TextView(requireContext());
        tvSection.setText(subject.section);
        tvSection.setTextSize(12);
        tvSection.setTextColor(Color.WHITE);
        tvSection.setAlpha(0.85f);

        // Subject name
        TextView tvName = new TextView(requireContext());
        tvName.setText(subject.name);
        tvName.setTextSize(24);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.setMargins(0, dp(2), 0, dp(10));
        tvName.setLayoutParams(nameParams);

        // Teacher row
        LinearLayout teacherRow = new LinearLayout(requireContext());
        teacherRow.setOrientation(LinearLayout.HORIZONTAL);
        teacherRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        trp.setMargins(0, 0, 0, dp(4));
        teacherRow.setLayoutParams(trp);
        TextView tvTeacherIcon = new TextView(requireContext());
        tvTeacherIcon.setText("👤 ");
        tvTeacherIcon.setTextSize(12);
        TextView tvTeacher = new TextView(requireContext());
        tvTeacher.setText(subject.teacher);
        tvTeacher.setTextSize(13);
        tvTeacher.setTextColor(Color.WHITE);
        tvTeacher.setAlpha(0.9f);
        teacherRow.addView(tvTeacherIcon);
        teacherRow.addView(tvTeacher);

        // Schedule row
        LinearLayout scheduleRow = new LinearLayout(requireContext());
        scheduleRow.setOrientation(LinearLayout.HORIZONTAL);
        scheduleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvScheduleIcon = new TextView(requireContext());
        tvScheduleIcon.setText("🕐 ");
        tvScheduleIcon.setTextSize(12);
        TextView tvSchedule = new TextView(requireContext());
        tvSchedule.setText(subject.schedule);
        tvSchedule.setTextSize(13);
        tvSchedule.setTextColor(Color.WHITE);
        tvSchedule.setAlpha(0.9f);
        scheduleRow.addView(tvScheduleIcon);
        scheduleRow.addView(tvSchedule);

        header.addView(tvSection);
        header.addView(tvName);
        header.addView(teacherRow);
        header.addView(scheduleRow);

        // ── Scrollable body ──────────────────────────────────────────────────────
        android.widget.ScrollView scrollView = new android.widget.ScrollView(requireContext());
        scrollView.setBackgroundColor(Color.WHITE);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

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

        TextView tvCount = new TextView(requireContext());
        tvCount.setText(subject.students.size() + " students");
        tvCount.setTextSize(13);
        tvCount.setTextColor(Color.parseColor("#9CA3AF"));

        headingRow.addView(tvHeading);
        headingRow.addView(tvCount);
        body.addView(headingRow);

        // Student list container
        LinearLayout studentList = new LinearLayout(requireContext());
        studentList.setOrientation(LinearLayout.VERTICAL);
        studentList.setBackgroundColor(Color.WHITE);
        body.addView(studentList);

        // body → scrollView (only once, never re-added)
        scrollView.addView(body);

        // sheet = header + scrollView
        sheet.addView(header);
        sheet.addView(scrollView);

        // ── X close button overlay ───────────────────────────────────────────────
        TextView btnClose = new TextView(requireContext());
        btnClose.setText("✕");
        btnClose.setTextSize(16);
        btnClose.setTextColor(Color.WHITE);
        btnClose.setGravity(Gravity.CENTER);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(0x33FFFFFF);
        btnClose.setBackground(closeBg);
        android.widget.FrameLayout.LayoutParams closeParams =
                new android.widget.FrameLayout.LayoutParams(dp(32), dp(32));
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeParams.setMargins(0, dp(16), dp(16), 0);
        btnClose.setLayoutParams(closeParams);

        // Rounded top corners on frameRoot
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.WHITE);
        sheetBg.setCornerRadii(new float[]{ dp(28),dp(28), dp(28),dp(28), 0,0, 0,0 });
        frameRoot.setBackground(sheetBg);

        // frameRoot = sheet + X button
        frameRoot.addView(sheet);
        frameRoot.addView(btnClose);

        // ── Build and show dialog ────────────────────────────────────────────────
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(frameRoot)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        // Render students AFTER show() so views are fully attached
        renderStudentList(studentList, subject);

        // Apply window styling AFTER show() — must be after or gets overridden
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setGravity(Gravity.BOTTOM);

            int screenHeight = requireContext().getResources()
                    .getDisplayMetrics().heightPixels;
            android.view.WindowManager.LayoutParams lp =
                    dialog.getWindow().getAttributes();
            lp.width            = android.view.WindowManager.LayoutParams.MATCH_PARENT;
            lp.height           = (int) (screenHeight * 0.88f);
            lp.horizontalMargin = 0;
            dialog.getWindow().setAttributes(lp);
        }
    }

    private void renderStudentList(LinearLayout container, Subject subject) {
        container.removeAllViews();
        container.setBackgroundColor(Color.WHITE);

        if (subject.students.isEmpty()) {
            TextView tvEmpty = new TextView(requireContext());
            tvEmpty.setText("No students enrolled.");
            tvEmpty.setTextSize(13);
            tvEmpty.setTextColor(Color.parseColor("#9CA3AF"));
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, dp(20), 0, dp(8));
            container.addView(tvEmpty);
            return;
        }

        for (Student s : subject.students) {
            // Row card
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

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, 0, 0, dp(10));
            row.setLayoutParams(rowParams);

            // Avatar circle
            TextView avatar = new TextView(requireContext());
            avatar.setText("👤");
            avatar.setTextSize(16);
            avatar.setGravity(Gravity.CENTER);
            GradientDrawable avatarBg = new GradientDrawable();
            avatarBg.setShape(GradientDrawable.OVAL);
            avatarBg.setColor(Color.parseColor("#F3F4F6"));
            avatar.setBackground(avatarBg);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(44), dp(44));
            ap.setMargins(0, 0, dp(12), 0);
            avatar.setLayoutParams(ap);

            // Name + ID column
            LinearLayout info = new LinearLayout(requireContext());
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(requireContext());
            tvName.setText(s.name);
            tvName.setTextSize(14);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setTextColor(Color.parseColor("#111827"));

            TextView tvId = new TextView(requireContext());
            tvId.setText(s.studentId);
            tvId.setTextSize(12);
            tvId.setTextColor(Color.parseColor("#9CA3AF"));
            LinearLayout.LayoutParams idParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            idParams.setMargins(0, dp(2), 0, 0);
            tvId.setLayoutParams(idParams);

            info.addView(tvName);
            info.addView(tvId);

            // Status badge
            TextView badge = new TextView(requireContext());
            badge.setTextSize(11);
            badge.setTypeface(null, android.graphics.Typeface.BOLD);
            badge.setPadding(dp(10), dp(4), dp(10), dp(4));

            // Default to "Present" for now — wire to real attendance data in future
            String status = "Present";
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            badgeBg.setCornerRadius(dp(10));
            switch (status) {
                case "Late":
                    badgeBg.setColor(Color.parseColor("#FEF9C3"));
                    badge.setTextColor(Color.parseColor("#A16207"));
                    break;
                case "Absent":
                    badgeBg.setColor(Color.parseColor("#FEE2E2"));
                    badge.setTextColor(Color.parseColor("#B91C1C"));
                    break;
                default:
                    badgeBg.setColor(Color.parseColor("#DCFCE7"));
                    badge.setTextColor(Color.parseColor("#15803D"));
                    break;
            }
            badge.setText(status);
            badge.setBackground(badgeBg);

            row.addView(avatar);
            row.addView(info);
            row.addView(badge);
            container.addView(row);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    private int dp(int dp) {
        float d = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * d);
    }
}