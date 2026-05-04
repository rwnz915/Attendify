package com.example.attendify.activities;

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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.ThemeManager;
import com.example.attendify.models.ExcuseLetter;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.ExcuseLetterRepository;

import java.util.List;

public class ExcuseLetterActivity extends AppCompatActivity {

    private RecyclerView rv;
    private LinearLayout emptyState;
    private ProgressBar  progressBar;
    private int          themeAccentColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_excuse_letter);

        androidx.activity.EdgeToEdge.enable(this);

        UserProfile elUser = AuthRepository.getInstance().getLoggedInUser();
        if (elUser != null) {
            ThemeApplier.applyHeader(this, elUser.getRole(),
                    findViewById(R.id.excuse_header_bg));
            ThemeApplier.applyButton(this, elUser.getRole(),
                    findViewById(R.id.btn_submit_new));
            themeAccentColor = ThemeManager.getPrimaryColor(this, elUser.getRole());
        }

        View bottomBar = findViewById(R.id.bottom_bar);
        if (bottomBar != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
                int navBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navBarHeight);
                return insets;
            });
        }

        // Back button
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        rv          = findViewById(R.id.rv_excuse_letters);
        emptyState  = findViewById(R.id.layout_empty);
        progressBar = findViewById(R.id.progress_bar);

        View btnSubmitNew = findViewById(R.id.btn_submit_new);
        if (btnSubmitNew != null) {
            btnSubmitNew.setOnClickListener(v -> {
                Intent intent = new Intent(this, SubmitExcuseLetterActivity.class);
                startActivity(intent);
            });
        }

        loadLetters();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (letters.isEmpty()) {
                                emptyState.setVisibility(View.VISIBLE);
                                rv.setVisibility(View.GONE);
                            } else {
                                rv.setVisibility(View.VISIBLE);
                                emptyState.setVisibility(View.GONE);
                                rv.setLayoutManager(new LinearLayoutManager(ExcuseLetterActivity.this));
                                rv.setAdapter(new StatusAdapter(letters, themeAccentColor));
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(ExcuseLetterActivity.this,
                                    "Failed to load: " + errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    // ── Inner RecyclerView adapter ────────────────────────────────────────────

    private class StatusAdapter extends RecyclerView.Adapter<StatusAdapter.VH> {

        private final List<ExcuseLetter> items;
        private final int accentColor;

        StatusAdapter(List<ExcuseLetter> items, int accentColor) {
            this.items = items;
            this.accentColor = accentColor;
        }

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

            if (h.tvSubject != null) {
                String subj = letter.getSubjectName();
                h.tvSubject.setText(subj != null && !subj.isEmpty() ? subj : "—");
            }

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
                default:
                    h.tvStatus.setTextColor(0xFFF57F17);
                    h.tvStatus.setBackgroundResource(R.drawable.bg_badge_late);
                    break;
            }

            if (letter.hasImage()) {
                h.btnViewAttachment.setVisibility(View.VISIBLE);
                h.btnViewAttachment.setTextColor(accentColor);
                h.btnViewAttachment.setBackground(buildOutlineDrawable(accentColor));
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

    private android.graphics.drawable.GradientDrawable buildOutlineDrawable(int color) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setColor(0x00000000);
        gd.setStroke((int)(1.5f * getResources().getDisplayMetrics().density), color);
        gd.setCornerRadius(50 * getResources().getDisplayMetrics().density);
        return gd;
    }
}
