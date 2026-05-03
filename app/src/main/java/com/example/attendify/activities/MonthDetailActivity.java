package com.example.attendify.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MonthDetailActivity extends AppCompatActivity {

    private String monthYear;
    private String initialSection;
    private String initialSubject;

    private List<AttendanceRecord> allMonthRecords = new ArrayList<>();
    private List<SubjectRepository.SubjectItem> teacherSubjects = new ArrayList<>();

    private Spinner spinnerSection, spinnerSubject;
    private RecyclerView rvDetails;
    private TextView tvTitle;

    private String selectedSection = "All Sections";
    private String selectedSubject = "All Subjects";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_month_detail);

        monthYear = getIntent().getStringExtra("MONTH_YEAR");
        initialSection = getIntent().getStringExtra("SECTION");
        initialSubject = getIntent().getStringExtra("SUBJECT");

        tvTitle = findViewById(R.id.tv_month_title);
        tvTitle.setText(monthYear);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Apply theme to header
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

        spinnerSection = findViewById(R.id.spinner_section_detail);
        spinnerSubject = findViewById(R.id.spinner_subject_detail);
        rvDetails      = findViewById(R.id.rv_month_details);
        
        findViewById(R.id.btn_export_month).setOnClickListener(v -> exportMonthData());

        rvDetails.setLayoutManager(new LinearLayoutManager(this));

        loadData();
    }

    private void loadData() {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        SubjectRepository.getInstance().getTeacherSubjects(user.getId(), new SubjectRepository.SubjectsCallback() {
            @Override
            public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                teacherSubjects = subjects;
                
                List<String> subjectIds = new ArrayList<>();
                for (SubjectRepository.SubjectItem s : subjects) subjectIds.add(s.id);
                
                AttendanceRepository.getInstance().getHistoryForSubjects(subjectIds, new AttendanceRepository.AttendanceCallback() {
                    @Override
                    public void onSuccess(List<AttendanceRecord> records) {
                        runOnUiThread(() -> {
                            filterByMonth(records);
                            setupSpinners();
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() -> Toast.makeText(MonthDetailActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {}
        });
    }

    private void filterByMonth(List<AttendanceRecord> records) {
        SimpleDateFormat sdfInput = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        SimpleDateFormat sdfOutput = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);

        allMonthRecords.clear();
        for (AttendanceRecord rec : records) {
            try {
                Date d = sdfInput.parse(rec.getDate());
                if (sdfOutput.format(d).equals(monthYear)) {
                    allMonthRecords.add(rec);
                }
            } catch (ParseException e) {}
        }
    }

    private void setupSpinners() {
        Set<String> sections = new HashSet<>();
        sections.add("All Sections");
        for (SubjectRepository.SubjectItem s : teacherSubjects) if (s.section != null) sections.add(s.section);
        
        List<String> sectionList = new ArrayList<>(sections);
        Collections.sort(sectionList);
        
        ArrayAdapter<String> sectionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sectionList);
        sectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSection.setAdapter(sectionAdapter);
        
        // Set initial selection
        if (initialSection != null && sectionList.contains(initialSection)) {
            spinnerSection.setSelection(sectionList.indexOf(initialSection));
            selectedSection = initialSection;
        }

        spinnerSection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSection = sectionList.get(position);
                updateSubjectSpinner();
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateSubjectSpinner() {
        List<String> subjects = new ArrayList<>();
        subjects.add("All Subjects");
        for (SubjectRepository.SubjectItem s : teacherSubjects) {
            if (selectedSection.equals("All Sections") || selectedSection.equals(s.section)) {
                subjects.add(s.name);
            }
        }
        
        ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, subjects);
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSubject.setAdapter(subjectAdapter);

        // Set initial selection only once
        if (initialSubject != null && subjects.contains(initialSubject)) {
            spinnerSubject.setSelection(subjects.indexOf(initialSubject));
            selectedSubject = initialSubject;
            initialSubject = null; // Clear it so subsequent section changes don't force it
        }

        spinnerSubject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSubject = subjects.get(position);
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void applyFilters() {
        // Group by Date to show daily summary cards
        SimpleDateFormat sdfInput = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        
        Set<String> allowedSubjectIds = new HashSet<>();
        for (SubjectRepository.SubjectItem s : teacherSubjects) {
            boolean sectionMatch = selectedSection.equals("All Sections") || selectedSection.equals(s.section);
            boolean subjectMatch = selectedSubject.equals("All Subjects") || selectedSubject.equals(s.name);
            if (sectionMatch && subjectMatch) allowedSubjectIds.add(s.id);
        }

        // Aggregate records by date
        Map<String, AttendanceRecord> dailySummaries = new TreeMap<>(Collections.reverseOrder());

        for (AttendanceRecord rec : allMonthRecords) {
            if (allowedSubjectIds.contains(rec.getSubjectId())) {
                AttendanceRecord summary = dailySummaries.get(rec.getDate());
                if (summary == null) {
                    summary = new AttendanceRecord(rec.getDate(), 0, 0, 0);
                    dailySummaries.put(rec.getDate(), summary);
                }
                
                // This is a bit tricky because AttendanceRecord constructor 
                // for summary uses numeric fields, but records use statusLabel.
                // We need to add to the existing summary.
                // I'll add a helper method to AttendanceRecord if needed, 
                // but for now I'll just use the getters.
                
                int p = summary.getPresent() + rec.getPresent();
                int l = summary.getLate() + rec.getLate();
                int a = summary.getAbsent() + rec.getAbsent();
                
                // Replace with new summary
                dailySummaries.put(rec.getDate(), new AttendanceRecord(rec.getDate(), p, a, l));
            }
        }

        rvDetails.setAdapter(new HistoryAdapter(this, new ArrayList<>(dailySummaries.values())));
    }

    private void exportMonthData() {
        List<AttendanceRecord> filtered = new ArrayList<>();
        Set<String> allowedSubjectIds = new HashSet<>();
        for (SubjectRepository.SubjectItem s : teacherSubjects) {
            boolean sectionMatch = selectedSection.equals("All Sections") || selectedSection.equals(s.section);
            boolean subjectMatch = selectedSubject.equals("All Subjects") || selectedSubject.equals(s.name);
            if (sectionMatch && subjectMatch) allowedSubjectIds.add(s.id);
        }

        for (AttendanceRecord rec : allMonthRecords) {
            if (allowedSubjectIds.contains(rec.getSubjectId())) {
                filtered.add(rec);
            }
        }

        String fileName = "Attendance_Report_" + monthYear.replace(" ", "_") + "_" + selectedSection.replace(" ", "_");
        com.example.attendify.utils.ExportUtils.exportToCsv(this, fileName, filtered);
    }
}
