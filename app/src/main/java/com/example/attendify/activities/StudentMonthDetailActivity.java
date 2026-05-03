package com.example.attendify.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
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
import com.example.attendify.ThemeApplier;
import com.example.attendify.utils.ExportUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;

public class StudentMonthDetailActivity extends AppCompatActivity {

    private String monthYear;
    private String initialSubject;

    private List<AttendanceRecord> allMonthRecords = new ArrayList<>();

    private Spinner spinnerSubject;
    private RecyclerView rvDetails;
    private TextView tvTitle;

    private String selectedSubject = "All Subjects";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_month_detail);

        androidx.activity.EdgeToEdge.enable(this);

        monthYear = getIntent().getStringExtra("MONTH_YEAR");
        initialSubject = getIntent().getStringExtra("SUBJECT");

        tvTitle = findViewById(R.id.tv_month_title);
        tvTitle.setText(monthYear);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_export_month).setOnClickListener(v -> exportMonthData());

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

        spinnerSubject = findViewById(R.id.spinner_subject_detail);
        rvDetails      = findViewById(R.id.rv_month_details);

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
                    filterByMonth(records);
                    setupSpinner();
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> Toast.makeText(StudentMonthDetailActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show());
            }
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

    private void setupSpinner() {
        Set<String> subjects = new HashSet<>();
        subjects.add("All Subjects");
        for (AttendanceRecord rec : allMonthRecords) {
            if (rec.getSubject() != null && !rec.getSubject().isEmpty()) {
                subjects.add(rec.getSubject());
            }
        }
        
        List<String> subjectList = new ArrayList<>(subjects);
        Collections.sort(subjectList);
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, subjectList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSubject.setAdapter(adapter);
        
        if (initialSubject != null && subjectList.contains(initialSubject)) {
            spinnerSubject.setSelection(subjectList.indexOf(initialSubject));
            selectedSubject = initialSubject;
        }

        spinnerSubject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSubject = subjectList.get(position);
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void applyFilters() {
        Map<String, AttendanceRecord> dailySummaries = new TreeMap<>(Collections.reverseOrder());

        for (AttendanceRecord rec : allMonthRecords) {
            if (selectedSubject.equals("All Subjects") || selectedSubject.equals(rec.getSubject())) {
                AttendanceRecord summary = dailySummaries.get(rec.getDate());
                if (summary == null) {
                    summary = new AttendanceRecord(rec.getDate(), 0, 0, 0);
                    dailySummaries.put(rec.getDate(), summary);
                }
                
                int p = summary.getPresent() + rec.getPresent();
                int l = summary.getLate() + rec.getLate();
                int a = summary.getAbsent() + rec.getAbsent();
                
                dailySummaries.put(rec.getDate(), new AttendanceRecord(rec.getDate(), p, a, l));
            }
        }

        rvDetails.setAdapter(new HistoryAdapter(this, new ArrayList<>(dailySummaries.values())));
    }

    private void exportMonthData() {
        List<AttendanceRecord> filtered = new ArrayList<>();
        for (AttendanceRecord rec : allMonthRecords) {
            if (selectedSubject.equals("All Subjects") || selectedSubject.equals(rec.getSubject())) {
                filtered.add(rec);
            }
        }
        
        String fileName = "My_Attendance_" + monthYear.replace(" ", "_") + "_" + selectedSubject.replace(" ", "_");
        ExportUtils.exportToCsvStudent(this, fileName, filtered);
    }
}
