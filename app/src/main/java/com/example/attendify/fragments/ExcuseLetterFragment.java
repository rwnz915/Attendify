package com.example.attendify.fragments;

import android.content.Intent;
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
 * Student Excuse Letter hub.
 *
 * Layout:
 *   ┌─ Header ──────────────────────────────────────────┐
 *   │  ← back     Excuse Letter                         │
 *   └────────────────────────────────────────────────────┘
 *   ┌─ Status list (RecyclerView) ───────────────────────┐
 *   │  Shows all past letters with status badges         │
 *   └────────────────────────────────────────────────────┘
 *   ┌─ Sticky bottom ────────────────────────────────────┐
 *   │  [ Submit New Excuse Letter ]                      │
 *   └────────────────────────────────────────────────────┘
 */
public class ExcuseLetterFragment extends Fragment {

    private RecyclerView rv;
    private LinearLayout emptyState;
    private ProgressBar  progressBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_excuse_letter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        rv          = view.findViewById(R.id.rv_excuse_letters);
        emptyState  = view.findViewById(R.id.layout_empty);
        progressBar = view.findViewById(R.id.progress_bar);

        // Submit button navigates to the form
        view.findViewById(R.id.btn_submit_new).setOnClickListener(v ->
                navigateTo(new SubmitExcuseLetterFragment()));

        loadLetters();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh list when returning from submit screen
        loadLetters();
    }

    private void loadLetters() {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        progressBar.setVisibility(View.VISIBLE);
        rv.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);

        ExcuseLetterRepository.getInstance().getStudentExcuseLetters(
                user.getId(),
                new ExcuseLetterRepository.ListCallback() {
                    @Override
                    public void onSuccess(List<ExcuseLetter> letters) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (letters.isEmpty()) {
                                emptyState.setVisibility(View.VISIBLE);
                                rv.setVisibility(View.GONE);
                            } else {
                                rv.setVisibility(View.VISIBLE);
                                emptyState.setVisibility(View.GONE);
                                rv.setLayoutManager(new LinearLayoutManager(requireContext()));
                                rv.setAdapter(new StatusAdapter(letters));
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(requireContext(),
                                    "Failed to load: " + errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    // ── Inner RecyclerView adapter ────────────────────────────────────────────

    private class StatusAdapter extends RecyclerView.Adapter<StatusAdapter.VH> {

        private final List<ExcuseLetter> items;

        StatusAdapter(List<ExcuseLetter> items) { this.items = items; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_excuse_letter_status, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ExcuseLetter letter = items.get(position);

            h.tvMessage.setText(letter.getMessage());
            h.tvDate.setText(formatTimestamp(letter.getSubmittedAt()));

            // Subject name label
            if (h.tvSubject != null) {
                String subj = letter.getSubjectName();
                h.tvSubject.setText(subj != null && !subj.isEmpty() ? subj : "—");
            }

            // Status badge
            String status = letter.getStatus() != null ? letter.getStatus() : "pending";
            h.tvStatus.setText(capitalise(status));
            switch (status) {
                case "approved":
                    h.tvStatus.setTextColor(0xFF2E7D32);
                    h.tvStatus.setBackgroundResource(R.drawable.bg_badge_present);
                    break;
                case "rejected":
                    h.tvStatus.setTextColor(0xFFC62828);
                    h.tvStatus.setBackgroundResource(R.drawable.bg_badge_absent);
                    break;
                default: // pending
                    h.tvStatus.setTextColor(0xFFF57F17);
                    h.tvStatus.setBackgroundResource(R.drawable.bg_badge_late);
                    break;
            }

            // Attachment button
            if (letter.hasImage()) {
                h.btnViewAttachment.setVisibility(View.VISIBLE);
                h.btnViewAttachment.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(letter.getImageUrl()));
                    intent.setDataAndType(Uri.parse(letter.getImageUrl()), "image/*");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(intent); }
                    catch (Exception e) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(letter.getImageUrl())));
                    }
                });
            } else {
                h.btnViewAttachment.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvMessage, tvDate, tvStatus, tvSubject, btnViewAttachment;
            VH(@NonNull View itemView) {
                super(itemView);
                tvMessage         = itemView.findViewById(R.id.tv_message);
                tvDate            = itemView.findViewById(R.id.tv_date);
                tvStatus          = itemView.findViewById(R.id.tv_status);
                tvSubject         = itemView.findViewById(R.id.tv_subject_name);
                btnViewAttachment = itemView.findViewById(R.id.btn_view_attachment);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void navigateTo(Fragment fragment) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private String capitalise(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String formatTimestamp(Object ts) {
        if (ts == null) return "Just now";
        try {
            com.google.firebase.Timestamp stamp = (com.google.firebase.Timestamp) ts;
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("MMM d, yyyy  h:mm a",
                            java.util.Locale.ENGLISH);
            return sdf.format(stamp.toDate());
        } catch (Exception e) {
            return ts.toString();
        }
    }
}