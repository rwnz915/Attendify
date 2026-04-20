package com.example.attendify.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.R;
import com.example.attendify.RoleSelectionActivity;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;

public class RoleSelectionFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_role_selection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Each role card now shows the login dialog first
        view.findViewById(R.id.card_teacher).setOnClickListener(v ->
                showLoginDialog("teacher"));

        view.findViewById(R.id.card_student).setOnClickListener(v ->
                showLoginDialog("student"));

        view.findViewById(R.id.card_secretary).setOnClickListener(v ->
                showLoginDialog("secretary"));
    }

    // ── Login Dialog ──────────────────────────────────────────────────────────

    private void showLoginDialog(String role) {
        if (getContext() == null) return;

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_login);
        dialog.setCancelable(true);

        // Round corners on the dialog window
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        // Views
        TextView  tvTitle   = dialog.findViewById(R.id.dialog_title);
        ImageView ivIcon    = dialog.findViewById(R.id.dialog_role_icon);
        EditText  etEmail   = dialog.findViewById(R.id.et_email);
        EditText  etPass    = dialog.findViewById(R.id.et_password);
        TextView  tvError   = dialog.findViewById(R.id.tv_error);
        Button    btnLogin  = dialog.findViewById(R.id.btn_login);
        Button    btnCancel = dialog.findViewById(R.id.btn_cancel);

        // Customise dialog per role
        switch (role) {
            case "teacher":
                tvTitle.setText("Teacher Login");
                ivIcon.setImageResource(R.drawable.book);
                break;
            case "student":
                tvTitle.setText("Student Login");
                ivIcon.setImageResource(R.drawable.user);
                break;
            case "secretary":
                tvTitle.setText("Secretary Login");
                ivIcon.setImageResource(R.drawable.ic_document);
                break;
        }

        // Cancel
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Login
        btnLogin.setOnClickListener(v -> {
            String email    = etEmail.getText().toString().trim();
            String password = etPass.getText().toString().trim();

            // Basic validation
            if (email.isEmpty()) {
                tvError.setText("Please enter your email.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            if (password.isEmpty()) {
                tvError.setText("Please enter your password.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            tvError.setVisibility(View.GONE);
            btnLogin.setEnabled(false);
            btnLogin.setText("Signing in...");

            // Call Firebase login
            AuthRepository.getInstance().login(email, password, role,
                    new AuthRepository.LoginCallback() {

                        @Override
                        public void onSuccess(UserProfile user) {
                            if (getActivity() == null) return;
                            dialog.dismiss();
                            // Navigate to MainActivity
                            ((RoleSelectionActivity) getActivity()).onRoleSelected(role);
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> {
                                tvError.setText(errorMessage);
                                tvError.setVisibility(View.VISIBLE);
                                btnLogin.setEnabled(true);
                                btnLogin.setText("Sign In");
                            });
                        }
                    });
        });

        dialog.show();
    }
}