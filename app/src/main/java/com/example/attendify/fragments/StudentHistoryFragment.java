package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.adapters.HistoryAdapter;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;

import java.util.List;

public class StudentHistoryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View header = view.findViewById(R.id.history_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        RecyclerView rv      = view.findViewById(R.id.rv_history);
        ProgressBar progress = view.findViewById(R.id.progress_bar);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        if (progress != null) progress.setVisibility(View.VISIBLE);

        // Use the first subject the teacher handles
        // When subject selection is added, pass the selected subjectId here
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        String subjectId = ""; // TODO: pass selected subject ID when subject picker is added

        AttendanceRepository.getInstance().getHistory(subjectId,
                new AttendanceRepository.AttendanceCallback() {
                    @Override
                    public void onSuccess(List<AttendanceRecord> records) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (progress != null) progress.setVisibility(View.GONE);
                            rv.setAdapter(new HistoryAdapter(requireContext(), records));
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (progress != null) progress.setVisibility(View.GONE);
                            Toast.makeText(getContext(),
                                    "Failed to load history: " + errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }
}