package com.example.attendify.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.adapters.HistoryAdapter;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.SubjectRepository;
import com.example.attendify.ThemeApplier;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MonthDetailActivity extends AppCompatActivity {

    private String monthYear;
    private String section;

    private List<AttendanceRecord> allMonthRecords = new ArrayList<>();
    private List<SubjectRepository.SubjectItem> teacherSubjects = new ArrayList<>();

    private Spinner spinnerSubject;
    private RecyclerView rvDetails;
    private TextView tvTitle;

    private String selectedSubject   = "All Subjects";
    private String selectedSubjectId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_month_detail);

        androidx.activity.EdgeToEdge.enable(this);

        monthYear = getIntent().getStringExtra("MONTH_YEAR");
        section   = getIntent().getStringExtra("SECTION");

        tvTitle = findViewById(R.id.tv_month_title);
        tvTitle.setText(monthYear + (section != null && !section.isEmpty() ? " - " + section : ""));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

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

        // Hide section spinner — not used here
        View sectionSpinner = findViewById(R.id.spinner_section_detail);
        if (sectionSpinner != null) sectionSpinner.setVisibility(View.GONE);

        spinnerSubject = findViewById(R.id.spinner_subject_detail);
        rvDetails      = findViewById(R.id.rv_month_details);

        rvDetails.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.btn_export_month).setOnClickListener(v -> exportMonthData());

        loadData();
    }

    private void loadData() {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        SubjectRepository.getInstance().getTeacherSubjects(user.getId(),
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                        teacherSubjects = new ArrayList<>();
                        for (SubjectRepository.SubjectItem s : subjects) {
                            if (section == null || section.isEmpty() || section.equals(s.section)) {
                                teacherSubjects.add(s);
                            }
                        }

                        List<String> subjectIds = new ArrayList<>();
                        for (SubjectRepository.SubjectItem s : subjects) subjectIds.add(s.id);

                        AttendanceRepository.getInstance().getHistoryForSubjects(subjectIds,
                                new AttendanceRepository.AttendanceCallback() {
                                    @Override
                                    public void onSuccess(List<AttendanceRecord> records) {
                                        runOnUiThread(() -> {
                                            filterByMonth(records);
                                            setupSubjectSpinner();
                                        });
                                    }

                                    @Override
                                    public void onFailure(String errorMessage) {
                                        runOnUiThread(() -> Toast.makeText(
                                                MonthDetailActivity.this,
                                                "Error: " + errorMessage,
                                                Toast.LENGTH_SHORT).show());
                                    }
                                });
                    }

                    @Override
                    public void onFailure(String errorMessage) {}
                });
    }

    private void filterByMonth(List<AttendanceRecord> records) {
        SimpleDateFormat sdfInput  = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        SimpleDateFormat sdfOutput = new SimpleDateFormat("MMMM yyyy",  Locale.ENGLISH);

        allMonthRecords.clear();
        for (AttendanceRecord rec : records) {
            try {
                Date d = sdfInput.parse(rec.getDate());
                if (sdfOutput.format(d).equals(monthYear)) {
                    allMonthRecords.add(rec);
                }
            } catch (ParseException e) { /* skip */ }
        }
    }

    private void setupSubjectSpinner() {
        List<String> subjects = new ArrayList<>();
        subjects.add("All Subjects");

        Set<String> seen = new LinkedHashSet<>();
        for (SubjectRepository.SubjectItem s : teacherSubjects) seen.add(s.name);
        subjects.addAll(seen);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, subjects) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                ((TextView) v.findViewById(android.R.id.text1)).setTextSize(11f);
                return v;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                ((TextView) v.findViewById(android.R.id.text1)).setTextSize(11f);
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSubject.setAdapter(adapter);

        spinnerSubject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSubject   = subjects.get(position);
                selectedSubjectId = null;
                if (!"All Subjects".equals(selectedSubject)) {
                    for (SubjectRepository.SubjectItem s : teacherSubjects) {
                        if (selectedSubject.equals(s.name)) {
                            selectedSubjectId = s.id;
                            break;
                        }
                    }
                }
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void applyFilters() {
        // Build allowed subject ID set
        Set<String> allowedIds = new HashSet<>();
        for (SubjectRepository.SubjectItem s : teacherSubjects) {
            boolean sectionOk = section == null || section.isEmpty() || section.equals(s.section);
            boolean subjectOk = "All Subjects".equals(selectedSubject) || selectedSubject.equals(s.name);
            if (sectionOk && subjectOk) allowedIds.add(s.id);
        }

        // Build subjectId -> name lookup
        Map<String, String> subjectNameMap = new HashMap<>();
        for (SubjectRepository.SubjectItem s : teacherSubjects) subjectNameMap.put(s.id, s.name);

        // Group by date + subjectId — one card per date per subject
        Map<String, List<AttendanceRecord>> byDateSubject = new TreeMap<>(Collections.reverseOrder());
        for (AttendanceRecord rec : allMonthRecords) {
            if (allowedIds.contains(rec.getSubjectId())) {
                String key = rec.getDate() + "|||" + rec.getSubjectId();
                byDateSubject.computeIfAbsent(key, k -> new ArrayList<>()).add(rec);
            }
        }

        // Build one summary AttendanceRecord per date+subject
        List<AttendanceRecord> summaries = new ArrayList<>();
        for (Map.Entry<String, List<AttendanceRecord>> e : byDateSubject.entrySet()) {
            int p = 0, l = 0, a = 0;
            String subjectId = e.getValue().get(0).getSubjectId();
            String date      = e.getValue().get(0).getDate();
            for (AttendanceRecord r : e.getValue()) {
                p += r.getPresent();
                l += r.getLate();
                a += r.getAbsent();
            }
            AttendanceRecord summary = new AttendanceRecord(date, p, a, l);
            summary.setSubject(subjectNameMap.getOrDefault(subjectId, ""));
            summaries.add(summary);
        }

        // Use arrow layout (true) for this teacher detail screen
        HistoryAdapter histAdapter = new HistoryAdapter(this, summaries, true);
        histAdapter.setOnItemClickListener(record -> {
            String timeDisplay = getTimeForDate(record.getDate(), allowedIds);
            // Find subjectId for this card's subject name
            String cardSubjectId = "";
            for (SubjectRepository.SubjectItem s : teacherSubjects) {
                if (s.name.equals(record.getSubject())) {
                    cardSubjectId = s.id;
                    break;
                }
            }
            Intent intent = new Intent(this, DateStudentListActivity.class);
            intent.putExtra("DATE",       record.getDate());
            intent.putExtra("TIME",       timeDisplay);
            intent.putExtra("SECTION",    section);
            intent.putExtra("SUBJECT",    record.getSubject());
            intent.putExtra("SUBJECT_ID", cardSubjectId);
            intent.putExtra("MODE",       "teacher");
            startActivity(intent);
        });
        rvDetails.setAdapter(histAdapter);
    }

    private String getTimeForDate(String date, Set<String> allowedIds) {
        Set<String> times = new LinkedHashSet<>();
        for (AttendanceRecord rec : allMonthRecords) {
            if (rec.getDate().equals(date) && allowedIds.contains(rec.getSubjectId())) {
                if (rec.getTime() != null && !rec.getTime().isEmpty()
                        && !"--:--".equals(rec.getTime())) {
                    times.add(rec.getTime());
                }
            }
        }
        return String.join(" - ", times);
    }

    private void exportMonthData() {
        Set<String> allowedIds = new HashSet<>();
        for (SubjectRepository.SubjectItem s : teacherSubjects) {
            boolean sectionOk = section == null || section.isEmpty() || section.equals(s.section);
            boolean subjectOk = "All Subjects".equals(selectedSubject) || selectedSubject.equals(s.name);
            if (sectionOk && subjectOk) allowedIds.add(s.id);
        }

        List<AttendanceRecord> filtered = new ArrayList<>();
        for (AttendanceRecord rec : allMonthRecords) {
            if (allowedIds.contains(rec.getSubjectId())) filtered.add(rec);
        }

        String fileName = "Attendance_" + monthYear.replace(" ", "_") + "_"
                + (section != null ? section : "");
        com.example.attendify.utils.ExportUtils.exportToCsv(this, fileName, filtered);
    }
}