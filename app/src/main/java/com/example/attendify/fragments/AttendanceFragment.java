package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.adapters.StudentAdapter;
import com.example.attendify.models.Student;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.StudentRepository;

import java.util.ArrayList;
import java.util.List;

public class AttendanceFragment extends Fragment {

    private List<Student> allStudents = new ArrayList<>();
    private StudentAdapter adapter;
    private String currentFilter = "All";

    private TextView tvPresent, tvLate, tvAbsent;
    private TextView filterAll, filterPresent, filterLate, filterAbsent;
    private TextView tvEmpty;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_attendance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View header = view.findViewById(R.id.attendance_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        tvPresent     = view.findViewById(R.id.tv_present_count);
        tvLate        = view.findViewById(R.id.tv_late_count);
        tvAbsent      = view.findViewById(R.id.tv_absent_count);
        tvEmpty       = view.findViewById(R.id.tv_empty);
        filterAll     = view.findViewById(R.id.filter_all);
        filterPresent = view.findViewById(R.id.filter_present);
        filterLate    = view.findViewById(R.id.filter_late);
        filterAbsent  = view.findViewById(R.id.filter_absent);
        recyclerView  = view.findViewById(R.id.rv_students);
        progressBar   = view.findViewById(R.id.progress_bar);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new StudentAdapter(requireContext(), new ArrayList<>());
        adapter.setOnStudentClickListener(position -> {
            List<Student> filtered = getFilteredList();
            if (position >= filtered.size()) return;
            Student tapped = filtered.get(position);
            for (Student s : allStudents) {
                if (s.getId() == tapped.getId()) { s.cycleStatus(); break; }
            }
            updateStats();
            applyFilter(currentFilter);
        });
        recyclerView.setAdapter(adapter);

        filterAll.setOnClickListener(v     -> applyFilter("All"));
        filterPresent.setOnClickListener(v -> applyFilter("Present"));
        filterLate.setOnClickListener(v    -> applyFilter("Late"));
        filterAbsent.setOnClickListener(v  -> applyFilter("Absent"));

        loadStudentsFromFirestore();
    }

    // ── Load from Firestore ───────────────────────────────────────────────────

    private void loadStudentsFromFirestore() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        // Get the teacher's first section to load students
        // Teachers can handle multiple sections — for now load the first one
        UserProfile teacher = AuthRepository.getInstance().getLoggedInUser();
        String section = (teacher.getSections() != null && !teacher.getSections().isEmpty())
                ? teacher.getSections().get(0)
                : null;

        if (section == null) {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            Toast.makeText(getContext(), "No section assigned to this teacher.", Toast.LENGTH_SHORT).show();
            return;
        }

        StudentRepository.getInstance().getStudentsBySection(section,
                new StudentRepository.StudentsCallback() {
                    @Override
                    public void onSuccess(List<Student> students) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            allStudents = students;
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            updateStats();
                            applyFilter("All");
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(),
                                    "Failed to load students: " + errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    // ── Filter + UI ───────────────────────────────────────────────────────────

    private List<Student> getFilteredList() {
        if ("All".equals(currentFilter)) return new ArrayList<>(allStudents);
        List<Student> result = new ArrayList<>();
        for (Student s : allStudents)
            if (s.getStatusLabel().equals(currentFilter)) result.add(s);
        return result;
    }

    private void applyFilter(String filter) {
        currentFilter = filter;
        TextView[] filters = {filterAll, filterPresent, filterLate, filterAbsent};
        String[]   labels  = {"All", "Present", "Late", "Absent"};
        for (int i = 0; i < filters.length; i++) {
            if (labels[i].equals(filter)) {
                filters[i].setBackgroundResource(R.drawable.bg_filter_active);
                filters[i].setTextColor(requireContext().getResources()
                        .getColor(R.color.white, requireContext().getTheme()));
            } else {
                filters[i].setBackgroundResource(R.drawable.bg_filter_inactive);
                filters[i].setTextColor(requireContext().getResources()
                        .getColor(R.color.gray_600, requireContext().getTheme()));
            }
        }
        List<Student> filtered = getFilteredList();
        adapter.updateList(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updateStats() {
        int p = 0, l = 0, a = 0;
        for (Student s : allStudents) {
            switch (s.getStatus()) {
                case Student.STATUS_PRESENT: p++; break;
                case Student.STATUS_LATE:    l++; break;
                case Student.STATUS_ABSENT:  a++; break;
            }
        }
        tvPresent.setText(String.valueOf(p));
        tvLate.setText(String.valueOf(l));
        tvAbsent.setText(String.valueOf(a));
    }
}