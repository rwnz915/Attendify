package com.example.attendify.fragments;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.R;
import com.example.attendify.models.ExcuseLetter;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.ExcuseLetterRepository;

import java.util.List;

/**
 * Teacher view: tabbed Pending / History screen for excuse letters.
 *
 * Pending tab  — letters awaiting teacher action (approve / reject).
 * History tab  — letters already decided (approved / rejected), with
 *                a "Change Decision" button so the teacher can correct
 *                an accidental mis-click.
 *
 * Filtering is scoped to THIS teacher's subjects only via teacherId.
 */
public class ApprovalRequestsFragment extends Fragment {

    // ── Tab state ─────────────────────────────────────────────────────────────

    private static final int TAB_PENDING = 0;
    private static final int TAB_HISTORY = 1;

    private int currentTab = TAB_PENDING;

    // ── Views ─────────────────────────────────────────────────────────────────

    private RecyclerView rv;
    private LinearLayout emptyState;
    private TextView     tvEmptyTitle, tvEmptySubtitle;
    private ProgressBar  progressBar;
    private TextView     tabPending, tabHistory;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_approval_requests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        rv              = view.findViewById(R.id.rv_approvals);
        emptyState      = view.findViewById(R.id.tv_empty);
        tvEmptyTitle    = view.findViewById(R.id.tv_empty_title);
        tvEmptySubtitle = view.findViewById(R.id.tv_empty_subtitle);
        progressBar     = view.findViewById(R.id.progress_bar);
        tabPending      = view.findViewById(R.id.tab_pending);
        tabHistory      = view.findViewById(R.id.tab_history);

        tabPending.setOnClickListener(v -> switchTab(TAB_PENDING));
        tabHistory.setOnClickListener(v -> switchTab(TAB_HISTORY));

        loadCurrentTab();
    }

    // ── Tab switching ─────────────────────────────────────────────────────────

    private void switchTab(int tab) {
        if (currentTab == tab) return;
        currentTab = tab;
        updateTabUI();
        loadCurrentTab();
    }

    private void updateTabUI() {
        if (currentTab == TAB_PENDING) {
            tabPending.setBackgroundResource(R.drawable.bg_tab_selected);
            tabPending.setTextColor(0xFF1D4ED8); // blue text on white pill
            tabPending.setTypeface(null, Typeface.BOLD);

            tabHistory.setBackgroundColor(0x00000000);
            tabHistory.setTextColor(0xAAFFFFFF);
            tabHistory.setTypeface(null, Typeface.BOLD);
        } else {
            tabHistory.setBackgroundResource(R.drawable.bg_tab_selected);
            tabHistory.setTextColor(0xFF1D4ED8); // blue text on white pill
            tabHistory.setTypeface(null, Typeface.BOLD);

            tabPending.setBackgroundColor(0x00000000);
            tabPending.setTextColor(0xAAFFFFFF);
            tabPending.setTypeface(null, Typeface.BOLD);
        }
    }

    private void loadCurrentTab() {
        showLoading();
        if (currentTab == TAB_PENDING) {
            loadPendingLetters();
        } else {
            loadHistoryLetters();
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadPendingLetters() {
        UserProfile teacher = AuthRepository.getInstance().getLoggedInUser();
        if (teacher == null) return;

        ExcuseLetterRepository.getInstance().getPendingByTeacher(
                teacher.getId(),
                new ExcuseLetterRepository.ListCallback() {
                    @Override
                    public void onSuccess(List<ExcuseLetter> letters) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (letters.isEmpty()) {
                                showEmpty("No pending requests",
                                        "All excuse letters have been reviewed");
                            } else {
                                showList(new PendingAdapter(letters));
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        showError(errorMessage);
                    }
                });
    }

    private void loadHistoryLetters() {
        UserProfile teacher = AuthRepository.getInstance().getLoggedInUser();
        if (teacher == null) return;

        ExcuseLetterRepository.getInstance().getAllByTeacher(
                teacher.getId(),
                new ExcuseLetterRepository.ListCallback() {
                    @Override
                    public void onSuccess(List<ExcuseLetter> allLetters) {
                        if (getActivity() == null) return;

                        // Filter out "pending" — history shows only decided letters
                        java.util.List<ExcuseLetter> decided = new java.util.ArrayList<>();
                        for (ExcuseLetter l : allLetters) {
                            if (!"pending".equals(l.getStatus())) {
                                decided.add(l);
                            }
                        }

                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (decided.isEmpty()) {
                                showEmpty("No history yet",
                                        "Letters you approve or reject will appear here");
                            } else {
                                showList(new HistoryAdapter(decided));
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        showError(errorMessage);
                    }
                });
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        rv.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    private void showList(RecyclerView.Adapter<?> adapter) {
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        rv.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    private void showEmpty(String title, String subtitle) {
        rv.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        tvEmptyTitle.setText(title);
        tvEmptySubtitle.setText(subtitle);
        emptyState.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(requireContext(), "Failed to load: " + message,
                    Toast.LENGTH_SHORT).show();
        });
    }

    // ── Pending Adapter ───────────────────────────────────────────────────────

    private class PendingAdapter extends RecyclerView.Adapter<PendingAdapter.VH> {

        private final List<ExcuseLetter> items;

        PendingAdapter(List<ExcuseLetter> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_excuse_letter_teacher, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ExcuseLetter letter = items.get(position);

            //h.tvAvatar.setText(letter.getInitial());
            h.tvStudentName.setText(letter.getStudentName());
            h.tvStudentNumber.setText(letter.getStudentNumber() != null
                    ? letter.getStudentNumber() : "");
            h.tvDate.setText(formatTimestamp(letter.getSubmittedAt()));
            h.tvMessage.setText(letter.getMessage());

            if (h.tvSubjectBadge != null) {
                String subj = letter.getSubjectName();
                if (subj != null && !subj.isEmpty()) {
                    h.tvSubjectBadge.setVisibility(View.VISIBLE);
                    h.tvSubjectBadge.setText(subj);
                } else {
                    h.tvSubjectBadge.setVisibility(View.GONE);
                }
            }

            if (letter.hasImage()) {
                h.btnViewAttachment.setVisibility(View.VISIBLE);
                h.btnViewAttachment.setOnClickListener(v -> openImage(letter.getImageUrl()));
            } else {
                h.btnViewAttachment.setVisibility(View.GONE);
            }

            h.btnApprove.setOnClickListener(v ->
                    confirmPendingAction(letter, "approved", h.getAdapterPosition()));
            h.btnDecline.setOnClickListener(v ->
                    confirmPendingAction(letter, "rejected", h.getAdapterPosition()));
        }

        @Override public int getItemCount() { return items.size(); }

        private void confirmPendingAction(ExcuseLetter letter, String action, int position) {
            String label = action.equals("approved") ? "Approve" : "Reject";
            new AlertDialog.Builder(requireContext())
                    .setTitle(label + " Excuse Letter")
                    .setMessage("Are you sure you want to " + label.toLowerCase()
                            + " this excuse letter from " + letter.getStudentName() + "?")
                    .setPositiveButton(label, (d, w) ->
                            applyStatus(letter, action, position, items, this))
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvAvatar, tvStudentName, tvStudentNumber, tvDate,
                    tvMessage, tvSubjectBadge, btnViewAttachment, btnApprove, btnDecline;

            VH(@NonNull View itemView) {
                super(itemView);
                //tvAvatar          = itemView.findViewById(R.id.tv_avatar);
                tvStudentName     = itemView.findViewById(R.id.tv_student_name);
                tvStudentNumber   = itemView.findViewById(R.id.tv_student_number);
                tvDate            = itemView.findViewById(R.id.tv_date);
                tvMessage         = itemView.findViewById(R.id.tv_message);
                tvSubjectBadge    = itemView.findViewById(R.id.tv_subject_badge);
                btnViewAttachment = itemView.findViewById(R.id.btn_view_attachment);
                btnApprove        = itemView.findViewById(R.id.btn_approve);
                btnDecline        = itemView.findViewById(R.id.btn_decline);
            }
        }
    }

    // ── History Adapter ───────────────────────────────────────────────────────

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

        private final List<ExcuseLetter> items;

        HistoryAdapter(List<ExcuseLetter> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_excuse_letter_history, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ExcuseLetter letter = items.get(position);
            boolean isApproved = "approved".equals(letter.getStatus());

            h.tvAvatar.setText(letter.getInitial());
            h.tvStudentName.setText(letter.getStudentName());
            h.tvStudentNumber.setText(letter.getStudentNumber() != null
                    ? letter.getStudentNumber() : "");
            h.tvDate.setText(formatTimestamp(letter.getSubmittedAt()));
            h.tvMessage.setText(letter.getMessage());

            // Status badge appearance
            if (isApproved) {
                h.tvStatusBadge.setText("✓  Approved");
                h.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_green_pill);
                h.tvStatusBadge.setTextColor(0xFF15803D); // green_700
            } else {
                h.tvStatusBadge.setText("✕  Rejected");
                h.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_absent);
                h.tvStatusBadge.setTextColor(0xFFB91C1C); // red_700
            }

            // Subject badge
            String subj = letter.getSubjectName();
            if (subj != null && !subj.isEmpty()) {
                h.tvSubjectBadge.setVisibility(View.VISIBLE);
                h.tvSubjectBadge.setText(subj);
            } else {
                h.tvSubjectBadge.setVisibility(View.GONE);
            }

            // Attachment
            if (letter.hasImage()) {
                h.btnViewAttachment.setVisibility(View.VISIBLE);
                h.btnViewAttachment.setOnClickListener(v -> openImage(letter.getImageUrl()));
            } else {
                h.btnViewAttachment.setVisibility(View.GONE);
            }

            // Change decision — flips to the opposite status
            String targetStatus = isApproved ? "rejected" : "approved";
            String targetLabel  = isApproved ? "Reject"   : "Approve";
            h.btnChangeDecision.setOnClickListener(v ->
                    confirmChangeDecision(letter, targetStatus, targetLabel,
                            h.getAdapterPosition()));
        }

        @Override public int getItemCount() { return items.size(); }

        private void confirmChangeDecision(ExcuseLetter letter,
                                           String newStatus,
                                           String actionLabel,
                                           int position) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Change Decision")
                    .setMessage("Change this letter from \""
                            + letter.getStudentName()
                            + "\" to \"" + actionLabel + "\"?")
                    .setPositiveButton(actionLabel, (d, w) ->
                            applyStatus(letter, newStatus, position, items, this))
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvAvatar, tvStudentName, tvStudentNumber, tvDate,
                    tvMessage, tvSubjectBadge, tvStatusBadge,
                    btnViewAttachment, btnChangeDecision;

            VH(@NonNull View itemView) {
                super(itemView);
                tvAvatar          = itemView.findViewById(R.id.tv_avatar);
                tvStudentName     = itemView.findViewById(R.id.tv_student_name);
                tvStudentNumber   = itemView.findViewById(R.id.tv_student_number);
                tvDate            = itemView.findViewById(R.id.tv_date);
                tvMessage         = itemView.findViewById(R.id.tv_message);
                tvSubjectBadge    = itemView.findViewById(R.id.tv_subject_badge);
                tvStatusBadge     = itemView.findViewById(R.id.tv_status_badge);
                btnViewAttachment = itemView.findViewById(R.id.btn_view_attachment);
                btnChangeDecision = itemView.findViewById(R.id.btn_change_decision);
            }
        }
    }

    // ── Shared status update ──────────────────────────────────────────────────

    /**
     * Applies a new status to a letter and removes it from the list (because
     * after a change it belongs in the other tab).
     */
    private <VH extends RecyclerView.ViewHolder> void applyStatus(
            ExcuseLetter letter,
            String newStatus,
            int position,
            List<ExcuseLetter> items,
            RecyclerView.Adapter<VH> adapter) {

        ExcuseLetterRepository.getInstance().updateStatus(
                letter.getDocId(),
                newStatus,
                new ExcuseLetterRepository.SubmitCallback() {
                    @Override
                    public void onSuccess() {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            items.remove(position);
                            adapter.notifyItemRemoved(position);
                            adapter.notifyItemRangeChanged(position, items.size());

                            if (items.isEmpty()) {
                                if (currentTab == TAB_PENDING) {
                                    showEmpty("No pending requests",
                                            "All excuse letters have been reviewed");
                                } else {
                                    showEmpty("No history yet",
                                            "Letters you approve or reject will appear here");
                                }
                            }

                            String msg;
                            if (newStatus.equals("approved")) {
                                msg = "Excuse letter approved ✓";
                            } else {
                                msg = "Excuse letter rejected";
                            }
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(),
                                        "Failed: " + errorMessage,
                                        Toast.LENGTH_SHORT).show());
                    }
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void openImage(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setDataAndType(Uri.parse(url), "image/*");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(intent); }
        catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
    }

    private String formatTimestamp(Object ts) {
        if (ts == null) return "";
        try {
            com.google.firebase.Timestamp stamp = (com.google.firebase.Timestamp) ts;
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("MMM d, yyyy  h:mm a",
                            java.util.Locale.ENGLISH);
            return sdf.format(stamp.toDate());
        } catch (Exception e) { return ""; }
    }
}