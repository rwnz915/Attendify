package com.example.attendify.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
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
import com.example.attendify.activities.MonthDetailActivity;
import com.example.attendify.adapters.MonthHistoryAdapter;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.SubjectRepository;
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

public class HistoryFragment extends Fragment {

    private List<AttendanceRecord> allRecords = new ArrayList<>();
    private List<SubjectRepository.SubjectItem> teacherSubjects = new ArrayList<>();

    private TextView tvTotalPresent, tvTotalLate, tvTotalAbsent, tvEmpty;
    private Spinner spinnerSection, spinnerSubject;
    private RecyclerView rvMonths;
    private ProgressBar progressBar;

    private String selectedSection = "All Sections";
    private String selectedSubject = "All Subjects";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Apply saved theme to header
        UserProfile histUser = AuthRepository.getInstance().getLoggedInUser();
        if (histUser != null) {
            ThemeApplier.applyHeader(requireContext(), histUser.getRole(),
                    view.findViewById(R.id.history_header));

            // Tint export icon with theme primary color
            ImageView btnExport = view.findViewById(R.id.btn_export);
            if (btnExport != null) {
                int primary = com.example.attendify.ThemeManager.getPrimaryColor(
                        requireContext(), histUser.getRole());
                btnExport.setColorFilter(primary);
            }
        }

        // Expand header to cover status bar
        View header = view.findViewById(R.id.history_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        // Bind views
        tvTotalPresent = view.findViewById(R.id.tv_total_present);
        tvTotalLate    = view.findViewById(R.id.tv_total_late);
        tvTotalAbsent  = view.findViewById(R.id.tv_total_absent);
        tvEmpty        = view.findViewById(R.id.tv_empty);
        spinnerSection = view.findViewById(R.id.spinner_section);
        spinnerSubject = view.findViewById(R.id.spinner_subject);
        rvMonths       = view.findViewById(R.id.rv_history);
        progressBar    = view.findViewById(R.id.progress_bar);

        // Set current date
        TextView tvDate = view.findViewById(R.id.tv_stats_date);
        if (tvDate != null) {
            tvDate.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
                    .format(new Date()));
        }

        rvMonths.setLayoutManager(new LinearLayoutManager(requireContext()));

        view.findViewById(R.id.btn_export).setOnClickListener(v -> exportCurrentData());

        loadSubjectsAndHistory();
    }

    private void loadSubjectsAndHistory() {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        SubjectRepository.getInstance().getTeacherSubjects(user.getId(),
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                        teacherSubjects = subjects;
                        setupSpinners();

                        List<String> subjectIds = new ArrayList<>();
                        for (SubjectRepository.SubjectItem s : subjects) subjectIds.add(s.id);

                        AttendanceRepository.getInstance().getHistoryForSubjects(subjectIds,
                                new AttendanceRepository.AttendanceCallback() {
                                    @Override
                                    public void onSuccess(List<AttendanceRecord> records) {
                                        if (getActivity() == null) return;
                                        getActivity().runOnUiThread(() -> {
                                            if (progressBar != null)
                                                progressBar.setVisibility(View.GONE);
                                            allRecords = records;
                                            applyFilters();
                                        });
                                    }

                                    @Override
                                    public void onFailure(String errorMessage) {
                                        if (getActivity() == null) return;
                                        getActivity().runOnUiThread(() -> {
                                            if (progressBar != null)
                                                progressBar.setVisibility(View.GONE);
                                            Toast.makeText(getContext(),
                                                    "Failed: " + errorMessage,
                                                    Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Failed to load subjects",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void setupSpinners() {
        Set<String> sections = new HashSet<>();
        sections.add("All Sections");
        for (SubjectRepository.SubjectItem s : teacherSubjects)
            if (s.section != null) sections.add(s.section);

        List<String> sectionList = new ArrayList<>(sections);
        Collections.sort(sectionList);

        ArrayAdapter<String> sectionAdapter = new ArrayAdapter<String>(
                requireContext(), android.R.layout.simple_spinner_item, sectionList) {
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
        sectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSection.setAdapter(sectionAdapter);

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

        ArrayAdapter<String> subjectAdapter = new ArrayAdapter<String>(
                requireContext(), android.R.layout.simple_spinner_item, subjects) {
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
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSubject.setAdapter(subjectAdapter);

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
        List<AttendanceRecord> filtered = new ArrayList<>();
        int p = 0, l = 0, a = 0;

        Set<String> allowedSubjectIds = new HashSet<>();
        for (SubjectRepository.SubjectItem s : teacherSubjects) {
            boolean sectionMatch = selectedSection.equals("All Sections")
                    || selectedSection.equals(s.section);
            boolean subjectMatch = selectedSubject.equals("All Subjects")
                    || selectedSubject.equals(s.name);
            if (sectionMatch && subjectMatch) allowedSubjectIds.add(s.id);
        }

        for (AttendanceRecord rec : allRecords) {
            if (allowedSubjectIds.contains(rec.getSubjectId())) {
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
        SimpleDateFormat sdfInput  = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        SimpleDateFormat sdfOutput = new SimpleDateFormat("MMMM yyyy",  Locale.ENGLISH);

        Map<String, MonthHistoryAdapter.MonthSummary> groups = new LinkedHashMap<>();

        Collections.sort(filteredRecords, (r1, r2) -> {
            try {
                Date d1 = sdfInput.parse(r1.getDate());
                Date d2 = sdfInput.parse(r2.getDate());
                return d2.compareTo(d1);
            } catch (ParseException e) { return 0; }
        });

        for (AttendanceRecord rec : filteredRecords) {
            try {
                Date date   = sdfInput.parse(rec.getDate());
                String key  = sdfOutput.format(date);

                MonthHistoryAdapter.MonthSummary s = groups.get(key);
                if (s == null) {
                    s = new MonthHistoryAdapter.MonthSummary();
                    s.monthYear = key;
                    groups.put(key, s);
                }
                s.present += rec.getPresent();
                s.late    += rec.getLate();
                s.absent  += rec.getAbsent();
            } catch (ParseException e) { /* skip bad records */ }
        }

        List<MonthHistoryAdapter.MonthSummary> summaries = new ArrayList<>(groups.values());
        MonthHistoryAdapter adapter = new MonthHistoryAdapter(requireContext(), summaries);
        adapter.setOnMonthClickListener(summary -> {
            Intent intent = new Intent(requireContext(), MonthDetailActivity.class);
            intent.putExtra("MONTH_YEAR", summary.monthYear);
            intent.putExtra("SECTION",   selectedSection);
            intent.putExtra("SUBJECT",   selectedSubject);
            startActivity(intent);
        });
        rvMonths.setAdapter(adapter);

        tvEmpty.setVisibility(summaries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void exportCurrentData() {
        List<AttendanceRecord> filtered = new ArrayList<>();
        Set<String> allowedSubjectIds = new HashSet<>();
        for (SubjectRepository.SubjectItem s : teacherSubjects) {
            boolean sectionMatch = selectedSection.equals("All Sections")
                    || selectedSection.equals(s.section);
            boolean subjectMatch = selectedSubject.equals("All Subjects")
                    || selectedSubject.equals(s.name);
            if (sectionMatch && subjectMatch) allowedSubjectIds.add(s.id);
        }

        for (AttendanceRecord rec : allRecords) {
            if (allowedSubjectIds.contains(rec.getSubjectId())) {
                filtered.add(rec);
            }
        }

        String fileName = "Attendance_Report_"
                + selectedSection.replace(" ", "_") + "_"
                + selectedSubject.replace(" ", "_");
        ExportUtils.exportToCsv(requireContext(), fileName, filtered);
    }
}