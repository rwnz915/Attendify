package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import com.example.attendify.repository.AuthRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shows attendance history (past 30 days) for all students
 * in the secretary's section. Groups records by date with
 * present / late / absent counts shown via the HistoryAdapter.
 */
public class SecretaryHistoryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_secretary_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Expand green header over status bar
        View header = view.findViewById(R.id.sec_history_header);
        if (header != null) {
            header.setPadding(
                    header.getPaddingLeft(),
                    header.getPaddingTop() + MainActivity.statusBarHeight,
                    header.getPaddingRight(),
                    header.getPaddingBottom());
        }

        RecyclerView rv        = view.findViewById(R.id.rv_sec_history);
        ProgressBar  progress  = view.findViewById(R.id.progress_sec_history);
        TextView     tvEmpty   = view.findViewById(R.id.tv_sec_history_empty);
        TextView     tvSubtitle = view.findViewById(R.id.tv_sec_history_subtitle);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        if (progress != null) progress.setVisibility(View.VISIBLE);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null || user.getSection() == null) {
            if (progress != null) progress.setVisibility(View.GONE);
            if (tvEmpty  != null) tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        String section = user.getSection();
        if (tvSubtitle != null) tvSubtitle.setText("Past 30 Days  \u2022  " + section);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Step 1: get all student UIDs in this section
        db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener(userSnap -> {
                    if (getActivity() == null) return;
                    List<DocumentSnapshot> studentDocs = userSnap.getDocuments();
                    if (studentDocs.isEmpty()) {
                        getActivity().runOnUiThread(() -> {
                            if (progress != null) progress.setVisibility(View.GONE);
                            if (tvEmpty  != null) tvEmpty.setVisibility(View.VISIBLE);
                        });
                        return;
                    }

                    List<String> uids = new ArrayList<>();
                    for (DocumentSnapshot d : studentDocs) uids.add(d.getId());

                    // Step 2: query attendance for those UIDs (max 30 per whereIn)
                    db.collection("attendance")
                            .whereIn("studentId", uids.subList(0, Math.min(uids.size(), 30)))
                            .get()
                            .addOnSuccessListener(attSnap -> {
                                if (getActivity() == null) return;

                                // Group by date → aggregate present/late/absent
                                Map<String, int[]> dateMap = new HashMap<>();
                                for (DocumentSnapshot doc : attSnap.getDocuments()) {
                                    String date   = doc.getString("date");
                                    String status = doc.getString("status");
                                    if (date == null || status == null) continue;

                                    int[] counts = dateMap.getOrDefault(date, new int[3]);
                                    if ("Present".equals(status)) counts[0]++;
                                    else if ("Late".equals(status))    counts[1]++;
                                    else if ("Absent".equals(status))  counts[2]++;
                                    dateMap.put(date, counts);
                                }

                                // Build AttendanceRecord list sorted newest first
                                List<AttendanceRecord> records = new ArrayList<>();
                                for (Map.Entry<String, int[]> entry : dateMap.entrySet()) {
                                    int[] c = entry.getValue();
                                    records.add(new AttendanceRecord(entry.getKey(), c[0], c[2], c[1]));
                                }
                                sortByDateDesc(records);

                                getActivity().runOnUiThread(() -> {
                                    if (progress != null) progress.setVisibility(View.GONE);
                                    if (records.isEmpty()) {
                                        if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                                    } else {
                                        rv.setAdapter(new HistoryAdapter(requireContext(), records));
                                    }
                                });
                            })
                            .addOnFailureListener(e -> {
                                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                                    if (progress != null) progress.setVisibility(View.GONE);
                                    if (tvEmpty  != null) tvEmpty.setVisibility(View.VISIBLE);
                                });
                            });
                })
                .addOnFailureListener(e -> {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        if (progress != null) progress.setVisibility(View.GONE);
                        if (tvEmpty  != null) tvEmpty.setVisibility(View.VISIBLE);
                    });
                });
    }

    private void sortByDateDesc(List<AttendanceRecord> list) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        Collections.sort(list, (a, b) -> {
            try {
                Date da = sdf.parse(a.getDate() != null ? a.getDate() : "");
                Date db = sdf.parse(b.getDate() != null ? b.getDate() : "");
                if (da == null || db == null) return 0;
                return db.compareTo(da);
            } catch (ParseException e) { return 0; }
        });
    }
}