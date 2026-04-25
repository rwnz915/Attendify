package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.ApprovalRepository;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.models.ApprovalRequest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_start).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).selectTab(2);
        });

        view.findViewById(R.id.btn_subjects).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).selectTab(1);
        });

        view.findViewById(R.id.card_pending_approvals).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new ApprovalRequestsFragment())
                        .addToBackStack(null)
                        .commit());

        loadTodayAttendance(view);
        loadPendingApprovals(view);
        loadRecentHistory(view);
    }

    // ── Today's attendance summary ────────────────────────────────────────────

    private void loadTodayAttendance(View view) {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        String subjectId = ""; // TODO: pass selected subject when subject picker is added

        // Today's date formatted to match Firestore date strings e.g. "Apr 20, 2026"
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date());

        AttendanceRepository.getInstance().getTodayAttendance(subjectId, today,
                new AttendanceRepository.SummaryCallback() {
                    @Override
                    public void onSuccess(AttendanceRecord summary) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            ((TextView) view.findViewById(R.id.tv_today_present))
                                    .setText(String.valueOf(summary.getPresent()));
                            ((TextView) view.findViewById(R.id.tv_today_total))
                                    .setText("/ " + (summary.getPresent() + summary.getAbsent()));
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        // Non-critical — silently ignore or show zero
                    }
                });
    }

    // ── Pending approvals count ───────────────────────────────────────────────

    private void loadPendingApprovals(View view) {
        ApprovalRepository.getInstance().getPendingApprovals(
                new ApprovalRepository.ApprovalsCallback() {
                    @Override
                    public void onSuccess(List<ApprovalRequest> approvals) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() ->
                                ((TextView) view.findViewById(R.id.tv_pending_count))
                                        .setText(String.valueOf(approvals.size())));
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        // Non-critical — leave count as 0
                    }
                });
    }

    // ── Recent activity list ──────────────────────────────────────────────────

    private void loadRecentHistory(View view) {
        String subjectId = ""; // TODO: pass selected subject when subject picker is added

        AttendanceRepository.getInstance().getHistory(subjectId,
                new AttendanceRepository.AttendanceCallback() {
                    @Override
                    public void onSuccess(List<AttendanceRecord> records) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            LinearLayout container = view.findViewById(R.id.recent_activity_container);
                            container.removeAllViews();

                            if (records.isEmpty()) {
                                TextView empty = new TextView(requireContext());
                                empty.setText("No attendance records yet.");
                                empty.setTextColor(0xFF9E9E9E);
                                empty.setPadding(0, 16, 0, 16);
                                container.addView(empty);
                                return;
                            }

                            LayoutInflater li = LayoutInflater.from(requireContext());
                            for (AttendanceRecord record : records) {
                                View card = li.inflate(R.layout.item_activity_record, container, false);
                                ((TextView) card.findViewById(R.id.tv_record_date))
                                        .setText(record.getDate());
                                ((TextView) card.findViewById(R.id.tv_record_present))
                                        .setText(record.getPresent() + " P");
                                ((TextView) card.findViewById(R.id.tv_record_absent))
                                        .setText(record.getAbsent() + " A");
                                container.addView(card);
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            // Show inline message instead of a toast so it doesn't feel like a crash
                            LinearLayout container = view.findViewById(R.id.recent_activity_container);
                            container.removeAllViews();
                            TextView empty = new TextView(requireContext());
                            empty.setText("No attendance records yet.");
                            empty.setTextColor(0xFF9E9E9E);
                            empty.setPadding(0, 16, 0, 16);
                            container.addView(empty);
                        });
                    }
                });
    }
}