package com.example.attendify.fragments;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SecuritySettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_security_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        // Apply theme to header
        ThemeApplier.applyHeader(requireContext(), user.getRole(), view.findViewById(R.id.security_header));

        // Apply theme tint to the lock icon circle on the card
        int primary   = com.example.attendify.ThemeManager.getPrimaryColor(requireContext(), user.getRole());
        int lightTint = com.example.attendify.ThemeManager.getLightTintColor(requireContext(), user.getRole());
        android.widget.ImageView icon = view.findViewById(R.id.iv_password_icon);
        if (icon != null) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(lightTint);
            icon.setBackground(gd);
            icon.setColorFilter(primary);
        }

        // System back button
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        navigateBackToProfile();
                    }
                });

        view.findViewById(R.id.btn_back).setOnClickListener(v -> navigateBackToProfile());

        // Card click → open change-password dialog
        view.findViewById(R.id.card_change_password).setOnClickListener(v -> showChangePasswordDialog(user.getRole()));
    }

    private void showChangePasswordDialog(String role) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_change_password);

        // Transparent + rounded corners via CardView in the layout
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        // Theme icon inside dialog
        int primary   = com.example.attendify.ThemeManager.getPrimaryColor(requireContext(), role);
        int lightTint = com.example.attendify.ThemeManager.getLightTintColor(requireContext(), role);
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
        ThemeApplier.applyButton(requireContext(), role, btnUpdate);

        TextInputEditText etCurrent = dialog.findViewById(R.id.et_current_password);
        TextInputEditText etNew     = dialog.findViewById(R.id.et_new_password);
        TextInputEditText etConfirm = dialog.findViewById(R.id.et_confirm_password);
        TextView tvError            = dialog.findViewById(R.id.tv_dialog_error);

        dialog.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());

        btnUpdate.setOnClickListener(v -> {
            String currentPw = etCurrent.getText() != null ? etCurrent.getText().toString().trim() : "";
            String newPw     = etNew.getText()     != null ? etNew.getText().toString().trim()     : "";
            String confirmPw = etConfirm.getText() != null ? etConfirm.getText().toString().trim() : "";

            // Inline validation
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

            tvError.setVisibility(View.GONE);
            btnUpdate.setEnabled(false);
            btnUpdate.setText("Updating…");

            updatePassword(currentPw, newPw, dialog, btnUpdate, tvError);
        });

        dialog.show();
    }

    private void showError(TextView tv, String msg) {
        tv.setText(msg);
        tv.setVisibility(View.VISIBLE);
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
                        Toast.makeText(requireContext(), "Password updated successfully",
                                Toast.LENGTH_SHORT).show();
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

    private void navigateBackToProfile() {
        requireActivity().getSupportFragmentManager().popBackStack();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity main = (MainActivity) getActivity();
                main.currentTab = -1;
                main.selectTab(4);
            }
        }, 200);
    }
}