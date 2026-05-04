package com.example.attendify.activities;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.ThemeManager;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SecuritySettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_security_settings);

        androidx.activity.EdgeToEdge.enable(this);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) { finish(); return; }

        // Apply theme to header
        ThemeApplier.applyHeader(this, user.getRole(), findViewById(R.id.security_header));

        // Status bar padding
        android.view.View header = findViewById(R.id.security_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        // Apply theme tint to the lock icon circle on the card
        int primary   = ThemeManager.getPrimaryColor(this, user.getRole());
        int lightTint = ThemeManager.getLightTintColor(this, user.getRole());
        android.widget.ImageView icon = findViewById(R.id.iv_password_icon);
        if (icon != null) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(lightTint);
            icon.setBackground(gd);
            icon.setColorFilter(primary);
        }

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Card click → open change-password dialog
        findViewById(R.id.card_change_password).setOnClickListener(v -> showChangePasswordDialog(user.getRole()));
    }

    private void showChangePasswordDialog(String role) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_change_password);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        // Theme icon inside dialog
        int primary   = ThemeManager.getPrimaryColor(this, role);
        int lightTint = ThemeManager.getLightTintColor(this, role);
        android.widget.ImageView dialogIcon = dialog.findViewById(R.id.dialog_pw_icon);
        if (dialogIcon != null) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(lightTint);
            dialogIcon.setBackground(gd);
            dialogIcon.setColorFilter(primary);
        }

        // Theme Update button
        MaterialButton btnUpdate = dialog.findViewById(R.id.btn_dialog_update);
        ThemeApplier.applyButton(this, role, btnUpdate);

        TextInputEditText etCurrent = dialog.findViewById(R.id.et_current_password);
        TextInputEditText etNew     = dialog.findViewById(R.id.et_new_password);
        TextInputEditText etConfirm = dialog.findViewById(R.id.et_confirm_password);
        TextView tvError            = dialog.findViewById(R.id.tv_dialog_error);

        dialog.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());

        btnUpdate.setOnClickListener(v -> {
            String currentPw = etCurrent.getText() != null ? etCurrent.getText().toString().trim() : "";
            String newPw     = etNew.getText()     != null ? etNew.getText().toString().trim()     : "";
            String confirmPw = etConfirm.getText() != null ? etConfirm.getText().toString().trim() : "";

            if (TextUtils.isEmpty(currentPw) || TextUtils.isEmpty(newPw) || TextUtils.isEmpty(confirmPw)) {
                showError(tvError, "All fields are required");
                return;
            }
            if (newPw.length() < 6) {
                showError(tvError, "Password must be at least 6 characters");
                return;
            }
            if (!newPw.equals(confirmPw)) {
                showError(tvError, "Passwords do not match");
                return;
            }

            tvError.setVisibility(android.view.View.GONE);
            btnUpdate.setEnabled(false);
            btnUpdate.setText("Updating…");

            updatePassword(currentPw, newPw, dialog, btnUpdate, tvError);
        });

        dialog.show();
    }

    private void showError(TextView tv, String msg) {
        tv.setText(msg);
        tv.setVisibility(android.view.View.VISIBLE);
    }

    private void updatePassword(String currentPw, String newPw,
                                Dialog dialog, MaterialButton btnUpdate, TextView tvError) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPw);

        user.reauthenticate(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                user.updatePassword(newPw).addOnCompleteListener(updateTask -> {
                    if (updateTask.isSuccessful()) {
                        dialog.dismiss();
                        Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        btnUpdate.setEnabled(true);
                        btnUpdate.setText("Update");
                        showError(tvError, "Error: " +
                                (updateTask.getException() != null
                                        ? updateTask.getException().getMessage()
                                        : "Unknown error"));
                    }
                });
            } else {
                btnUpdate.setEnabled(true);
                btnUpdate.setText("Update");
                showError(tvError, "Incorrect current password.");
            }
        });
    }
}