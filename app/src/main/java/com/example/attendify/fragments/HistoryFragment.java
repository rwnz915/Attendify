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
import com.example.attendify.dialogs.ExportDialogFragment;
import com.example.attendify.dialogs.ExportDialogFragment;
import com.example.attendify.utils.ExportUtils;
import com.example.attendify.ThemeApplier;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryFragment extends Fragment {

    private List<AttendanceRecord> allRecords = new ArrayList<>();
    private List<SubjectRepository.SubjectItem> teacherSubjects = new ArrayList<>();

    private TextView tvTotalPresent, tvTotalLate, tvTotalAbsent, tvEmpty;
    private RecyclerView rvMonths;
    private ProgressBar progressBar;
    private Spinner spinnerSection;

    private String selectedSection = "All Sections";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserProfile histUser = AuthRepository.getInstance().getLoggedInUser();
        if (histUser != null) {
            ThemeApplier.applyHeader(requireContext(), histUser.getRole(),
                    view.findViewById(R.id.history_header));

            ImageView btnExport = view.findViewById(R.id.btn_export);
            if (btnExport != null) {
                int primary = com.example.attendify.ThemeManager.getPrimaryColor(
                        requireContext(), histUser.getRole());
                btnExport.setColorFilter(primary);
            }
        }

        View header = view.findViewById(R.id.history_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        tvTotalPresent = view.findViewById(R.id.tv_total_present);
        tvTotalLate    = view.findViewById(R.id.tv_total_late);
        tvTotalAbsent  = view.findViewById(R.id.tv_total_absent);
        tvEmpty        = view.findViewById(R.id.tv_empty);
        rvMonths       = view.findViewById(R.id.rv_history);
        progressBar    = view.findViewById(R.id.progress_bar);
        spinnerSection = view.findViewById(R.id.spinner_section);

        // Hide subject spinner — not used
        View spinnerSubject = view.findViewById(R.id.spinner_subject);
        if (spinnerSubject != null) spinnerSubject.setVisibility(View.GONE);

        TextView tvDate = view.findViewById(R.id.tv_stats_date);
        if (tvDate != null) {
            tvDate.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
                    .format(new Date()));
        }

        rvMonths.setLayoutManager(new LinearLayoutManager(requireContext()));

        view.findViewById(R.id.btn_export).setOnClickListener(v -> exportCurrentData());

        loadSubjectsAndHistory();
    }

    /**
     * Populate the section spinner once subjects are loaded.
     * First item is always "All Sections".
     */
    private void setupSectionSpinner() {
        // Collect unique non-null sections
        List<String> sections = new ArrayList<>();
        sections.add("All Sections");
        for (SubjectRepository.SubjectItem s : teacherSubjects) {
            if (s.section != null && !s.section.isEmpty() && !sections.contains(s.section)) {
                sections.add(s.section);
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                sections
        ) {
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
        spinnerSection.setAdapter(adapter);

        spinnerSection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSection = sections.get(position);
                buildMonthSectionList(); // re-render with filter applied
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
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
                                            setupSectionSpinner(); // populate spinner first
                                            buildMonthSectionList();
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

    /**
     * Build list grouped by (monthYear + section), filtered by selectedSection.
     */
    private void buildMonthSectionList() {
        SimpleDateFormat sdfInput  = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        SimpleDateFormat sdfOutput = new SimpleDateFormat("MMMM yyyy",  Locale.ENGLISH);

        // Build subjectId -> SubjectItem map
        Map<String, SubjectRepository.SubjectItem> subjectMap = new LinkedHashMap<>();
        for (SubjectRepository.SubjectItem s : teacherSubjects) subjectMap.put(s.id, s);

        // Sort records by date descending
        Collections.sort(allRecords, (r1, r2) -> {
            try {
                Date d1 = sdfInput.parse(r1.getDate());
                Date d2 = sdfInput.parse(r2.getDate());
                return d2.compareTo(d1);
            } catch (ParseException e) { return 0; }
        });

        Map<String, MonthHistoryAdapter.MonthSummary> groups = new LinkedHashMap<>();

        int totalP = 0, totalL = 0, totalA = 0;

        for (AttendanceRecord rec : allRecords) {
            SubjectRepository.SubjectItem subj = subjectMap.get(rec.getSubjectId());
            String section = (subj != null && subj.section != null) ? subj.section : "";

            // Apply section filter
            if (!selectedSection.equals("All Sections") && !selectedSection.equals(section)) {
                continue;
            }

            try {
                Date date = sdfInput.parse(rec.getDate());
                String monthYear = sdfOutput.format(date);
                String key = monthYear + "|||" + section;

                MonthHistoryAdapter.MonthSummary s = groups.get(key);
                if (s == null) {
                    s = new MonthHistoryAdapter.MonthSummary();
                    s.monthYear = monthYear;
                    s.subtitle  = section;
                    s.section   = section;
                    groups.put(key, s);
                }
                s.present += rec.getPresent();
                s.late    += rec.getLate();
                s.absent  += rec.getAbsent();

                totalP += rec.getPresent();
                totalL += rec.getLate();
                totalA += rec.getAbsent();
            } catch (ParseException e) { /* skip */ }
        }

        tvTotalPresent.setText(String.valueOf(totalP));
        tvTotalLate.setText(String.valueOf(totalL));
        tvTotalAbsent.setText(String.valueOf(totalA));

        List<MonthHistoryAdapter.MonthSummary> summaries = new ArrayList<>(groups.values());
        MonthHistoryAdapter adapter = new MonthHistoryAdapter(requireContext(), summaries);
        adapter.setOnMonthClickListener(summary -> {
            Intent intent = new Intent(requireContext(), MonthDetailActivity.class);
            intent.putExtra("MONTH_YEAR", summary.monthYear);
            intent.putExtra("SECTION",   summary.section);
            startActivity(intent);
        });
        rvMonths.setAdapter(adapter);

        tvEmpty.setVisibility(summaries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void exportCurrentData() {
        ExportDialogFragment.newInstance()
                .show(getChildFragmentManager(), "export_dialog");
    }
}