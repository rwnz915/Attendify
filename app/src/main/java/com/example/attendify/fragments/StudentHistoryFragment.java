package com.example.attendify.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.activities.StudentMonthDetailActivity;
import com.example.attendify.adapters.MonthHistoryAdapter;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.utils.ExportUtils;
import com.example.attendify.ThemeApplier;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class StudentHistoryFragment extends Fragment {

    private List<AttendanceRecord> allRecords = new ArrayList<>();
    
    private TextView tvTotalPresent, tvTotalLate, tvTotalAbsent, tvEmpty;
    private Spinner spinnerSubject;
    private RecyclerView rvMonths;
    private ProgressBar progressBar;
    private Button btnExport;

    private String selectedSubject = "All Subjects";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Apply saved theme to header
        UserProfile shUser = AuthRepository.getInstance().getLoggedInUser();
        if (shUser != null) {
            ThemeApplier.applyHeader(requireContext(), shUser.getRole(), view.findViewById(R.id.history_header));
        }

        View header = view.findViewById(R.id.history_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        tvTotalPresent = view.findViewById(R.id.tv_total_present);
        tvTotalLate    = view.findViewById(R.id.tv_total_late);
        tvTotalAbsent   = view.findViewById(R.id.tv_total_absent);
        tvEmpty         = view.findViewById(R.id.tv_empty);
        
        spinnerSubject = view.findViewById(R.id.spinner_subject);
        rvMonths      = view.findViewById(R.id.rv_history);
        progressBar   = view.findViewById(R.id.progress_bar);
        btnExport     = view.findViewById(R.id.btn_export);

        rvMonths.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        btnExport.setOnClickListener(v -> exportCurrentData());

        loadHistory();
    }

    private void loadHistory() {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        AttendanceRepository.getInstance().getStudentHistory(user.getId(), new AttendanceRepository.AttendanceCallback() {
            @Override
            public void onSuccess(List<AttendanceRecord> records) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    allRecords = records;
                    setupSubjectSpinner();
                    applyFilters();
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Failed to load history: " + errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void setupSubjectSpinner() {
        Set<String> subjects = new HashSet<>();
        subjects.add("All Subjects");
        for (AttendanceRecord rec : allRecords) {
            if (rec.getSubject() != null && !rec.getSubject().isEmpty()) {
                subjects.add(rec.getSubject());
            }
        }
        
        List<String> subjectList = new ArrayList<>(subjects);
        Collections.sort(subjectList);
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, subjectList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSubject.setAdapter(adapter);
        
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
        List<AttendanceRecord> filtered = new ArrayList<>();
        int p = 0, l = 0, a = 0;
        
        for (AttendanceRecord rec : allRecords) {
            if (selectedSubject.equals("All Subjects") || selectedSubject.equals(rec.getSubject())) {
                filtered.add(rec);
                p += rec.getPresent();
                l += rec.getLate();
                a += rec.getAbsent();
            }
        }

        tvTotalPresent.setText(String.valueOf(p));
        tvTotalLate.setText(String.valueOf(l));
        tvTotalAbsent.setText(String.valueOf(a));
        
        updateMonthList(filtered);
    }

    private void updateMonthList(List<AttendanceRecord> filteredRecords) {
        SimpleDateFormat sdfInput = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        SimpleDateFormat sdfOutput = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
        
        Map<String, MonthHistoryAdapter.MonthSummary> groups = new LinkedHashMap<>();
        
        // Grouping
        for (AttendanceRecord rec : filteredRecords) {
            try {
                Date date = sdfInput.parse(rec.getDate());
                String key = sdfOutput.format(date);
                
                MonthHistoryAdapter.MonthSummary s = groups.get(key);
                if (s == null) {
                    s = new MonthHistoryAdapter.MonthSummary();
                    s.monthYear = key;
                    groups.put(key, s);
                }
                s.present += rec.getPresent();
                s.late    += rec.getLate();
                s.absent  += rec.getAbsent();
            } catch (ParseException e) {}
        }

        List<MonthHistoryAdapter.MonthSummary> summaries = new ArrayList<>(groups.values());
        MonthHistoryAdapter adapter = new MonthHistoryAdapter(requireContext(), summaries);
        adapter.setOnMonthClickListener(summary -> {
            Intent intent = new Intent(requireContext(), StudentMonthDetailActivity.class);
            intent.putExtra("MONTH_YEAR", summary.monthYear);
            intent.putExtra("SUBJECT", selectedSubject);
            startActivity(intent);
        });
        rvMonths.setAdapter(adapter);
        
        tvEmpty.setVisibility(summaries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void exportCurrentData() {
        List<AttendanceRecord> filtered = new ArrayList<>();
        for (AttendanceRecord rec : allRecords) {
            if (selectedSubject.equals("All Subjects") || selectedSubject.equals(rec.getSubject())) {
                filtered.add(rec);
            }
        }
        
        String fileName = "My_Attendance_Report_" + selectedSubject.replace(" ", "_");
        ExportUtils.exportToCsvStudent(requireContext(), fileName, filtered);
    }
}
