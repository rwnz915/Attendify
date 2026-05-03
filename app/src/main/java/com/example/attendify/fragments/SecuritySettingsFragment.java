package com.example.attendify.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SecuritySettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_security_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        // Apply theme
        ThemeApplier.applyHeader(requireContext(), user.getRole(), view.findViewById(R.id.security_header));
        ThemeApplier.applyButton(requireContext(), user.getRole(), view.findViewById(R.id.btn_update_password));

        // Back button
        view.findViewById(R.id.btn_back).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        TextInputEditText etCurrent = view.findViewById(R.id.et_current_password);
        TextInputEditText etNew = view.findViewById(R.id.et_new_password);
        TextInputEditText etConfirm = view.findViewById(R.id.et_confirm_password);

        view.findViewById(R.id.btn_update_password).setOnClickListener(v -> {
            String currentPw = etCurrent.getText().toString().trim();
            String newPw = etNew.getText().toString().trim();
            String confirmPw = etConfirm.getText().toString().trim();

            if (TextUtils.isEmpty(currentPw) || TextUtils.isEmpty(newPw) || TextUtils.isEmpty(confirmPw)) {
                Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPw.equals(confirmPw)) {
                Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPw.length() < 6) {
                Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            updatePassword(currentPw, newPw);
        });
    }

    private void updatePassword(String currentPw, String newPw) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPw);

        // Re-authenticate is required for password change
        user.reauthenticate(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                user.updatePassword(newPw).addOnCompleteListener(updateTask -> {
                    if (updateTask.isSuccessful()) {
                        Toast.makeText(requireContext(), "Password updated successfully", Toast.LENGTH_SHORT).show();
                        requireActivity().getSupportFragmentManager().popBackStack();
                    } else {
                        Toast.makeText(requireContext(), "Error: " + updateTask.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                Toast.makeText(requireContext(), "Authentication failed. Check current password.", Toast.LENGTH_LONG).show();
            }
        });
    }
}
