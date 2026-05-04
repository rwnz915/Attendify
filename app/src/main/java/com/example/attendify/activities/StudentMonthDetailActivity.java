package com.example.attendify.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.ThemeApplier;
import com.example.attendify.utils.ExportUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Shows this student's attendance records for a specific month + subject.
 * Each item shows date + time + status (using item_student_attendance_record style).
 */
public class StudentMonthDetailActivity extends AppCompatActivity {

    private String monthYear;
    private String subjectFilter; // specific subject name passed from StudentHistoryFragment

    private List<AttendanceRecord> allMonthRecords = new ArrayList<>();

    private RecyclerView rvDetails;
    private TextView tvTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_month_detail);

        androidx.activity.EdgeToEdge.enable(this);

        monthYear    = getIntent().getStringExtra("MONTH_YEAR");
        subjectFilter = getIntent().getStringExtra("SUBJECT");

        tvTitle = findViewById(R.id.tv_month_title);
        String titleText = monthYear;
        if (subjectFilter != null && !subjectFilter.isEmpty()) {
            titleText += " - " + subjectFilter;
        }
        tvTitle.setText(titleText);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_export_month).setOnClickListener(v -> exportMonthData());

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user != null) {
            ThemeApplier.applyHeader(this, user.getRole(), findViewById(R.id.detail_header));
        }

        View header = findViewById(R.id.detail_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        // Hide spinner — subject is fixed from what was clicked
        View spinnerSubject = findViewById(R.id.spinner_subject_detail);
        if (spinnerSubject != null) spinnerSubject.setVisibility(View.GONE);

        rvDetails = findViewById(R.id.rv_month_details);
        rvDetails.setLayoutManager(new LinearLayoutManager(this));

        loadData();
    }

    private void loadData() {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        AttendanceRepository.getInstance().getStudentHistory(user.getId(), new AttendanceRepository.AttendanceCallback() {
            @Override
            public void onSuccess(List<AttendanceRecord> records) {
                runOnUiThread(() -> {
                    filterByMonthAndSubject(records);
                    showRecords();
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> Toast.makeText(StudentMonthDetailActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void filterByMonthAndSubject(List<AttendanceRecord> records) {
        SimpleDateFormat sdfInput  = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        SimpleDateFormat sdfOutput = new SimpleDateFormat("MMMM yyyy",  Locale.ENGLISH);

        allMonthRecords.clear();
        for (AttendanceRecord rec : records) {
            try {
                Date d = sdfInput.parse(rec.getDate());
                boolean monthMatch   = sdfOutput.format(d).equals(monthYear);
                boolean subjectMatch = subjectFilter == null || subjectFilter.isEmpty()
                        || subjectFilter.equals(rec.getSubject());
                if (monthMatch && subjectMatch) {
                    allMonthRecords.add(rec);
                }
            } catch (ParseException e) {}
        }

        // Sort by date descending
        Collections.sort(allMonthRecords, (r1, r2) -> {
            try {
                Date d1 = sdfInput.parse(r1.getDate());
                Date d2 = sdfInput.parse(r2.getDate());
                return d2.compareTo(d1);
            } catch (ParseException e) { return 0; }
        });
    }

    private void showRecords() {
        // Use inline adapter displaying item_student_attendance_record for each record
        StudentAttendanceRecordAdapter adapter = new StudentAttendanceRecordAdapter(allMonthRecords);
        rvDetails.setAdapter(adapter);
    }

    private void exportMonthData() {
        String fileName = "My_Attendance_" + monthYear.replace(" ", "_")
                + (subjectFilter != null ? "_" + subjectFilter.replace(" ", "_") : "");
        ExportUtils.exportToCsvStudent(this, fileName, allMonthRecords);
    }

    // ── Inline adapter using item_student_attendance_record layout ────────────

    private static class StudentAttendanceRecordAdapter
            extends RecyclerView.Adapter<StudentAttendanceRecordAdapter.VH> {

        private final List<AttendanceRecord> records;

        StudentAttendanceRecordAdapter(List<AttendanceRecord> records) {
            this.records = records;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_student_attendance_record, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            AttendanceRecord rec = records.get(pos);

            // Format date: "May 2" from "2026-05-02"
            h.tvDate.setText(formatDate(rec.getDate()));
            h.tvSubject.setText(rec.getSubject() != null ? rec.getSubject() : "");
            h.tvTime.setText(rec.getTime() != null ? rec.getTime() : "--:--");

            String status = rec.getStatusLabel();
            h.tvStatus.setText(status != null ? status : "");

            int bgRes, colorRes;
            if ("Present".equals(status)) {
                bgRes    = R.drawable.bg_badge_present;
                colorRes = R.color.green_700;
            } else if ("Late".equals(status)) {
                bgRes    = R.drawable.bg_badge_late;
                colorRes = R.color.yellow_700;
            } else {
                bgRes    = R.drawable.bg_badge_absent;
                colorRes = R.color.red_700;
            }
            h.tvStatus.setBackgroundResource(bgRes);
            h.tvStatus.setTextColor(h.itemView.getContext().getResources()
                    .getColor(colorRes, h.itemView.getContext().getTheme()));
        }

        @Override
        public int getItemCount() { return records.size(); }

        private static String formatDate(String dateStr) {
            try {
                SimpleDateFormat sdfIn  = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
                SimpleDateFormat sdfOut = new SimpleDateFormat("MMMM d",     Locale.ENGLISH);
                Date d = sdfIn.parse(dateStr);
                return sdfOut.format(d);
            } catch (ParseException | NullPointerException e) {
                return dateStr != null ? dateStr : "";
            }
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvDate, tvSubject, tvTime, tvStatus;
            VH(View v) {
                super(v);
                tvDate    = v.findViewById(R.id.tv_record_date);
                tvSubject = v.findViewById(R.id.tv_record_subject);
                tvTime    = v.findViewById(R.id.tv_record_time);
                tvStatus  = v.findViewById(R.id.tv_record_status);
            }
        }
    }
}