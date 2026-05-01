package com.example.attendify.fragments;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.example.attendify.R;
import com.example.attendify.RoleSelectionActivity;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;

public class RoleSelectionFragment extends Fragment {

    private static final String PREF_REMEMBER  = "remember_me";
    private static final String PREF_EMAIL     = "saved_email";
    private static final String PREF_ROLE      = "saved_role";

    private String selectedRole = "teacher";

    private TextView  tabTeacher, tabStudent, tabSecretary;
    private TextView  tvLoginTitle, tvError;
    private ImageView ivRoleIcon;
    private MaterialCardView iconCard;
    private TextInputLayout tilEmail, tilPassword;
    private EditText  etEmail, etPassword;
    private com.google.android.material.checkbox.MaterialCheckBox cbRememberMe;

    private MaterialButton btnLogin;

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

        tabTeacher    = view.findViewById(R.id.tab_teacher);
        tabStudent    = view.findViewById(R.id.tab_student);
        tabSecretary  = view.findViewById(R.id.tab_secretary);
        //tvLoginTitle  = view.findViewById(R.id.tv_login_title);
        tvError       = view.findViewById(R.id.tv_error);
        //ivRoleIcon    = view.findViewById(R.id.iv_role_icon);
        iconCard      = view.findViewById(R.id.icon_card);
        tilEmail      = view.findViewById(R.id.til_email);
        tilPassword   = view.findViewById(R.id.til_password);
        etEmail       = view.findViewById(R.id.et_email);
        etPassword    = view.findViewById(R.id.et_password);
        cbRememberMe  = view.findViewById(R.id.cb_remember_me);
        btnLogin      = view.findViewById(R.id.btn_login);

        // Tab clicks
        tabTeacher.setOnClickListener(v  -> selectRole("teacher"));
        tabStudent.setOnClickListener(v  -> selectRole("student"));
        tabSecretary.setOnClickListener(v -> selectRole("secretary"));

        // Restore saved credentials
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        boolean remembered = prefs.getBoolean(PREF_REMEMBER, false);
        if (remembered) {
            etEmail.setText(prefs.getString(PREF_EMAIL, ""));
            cbRememberMe.setChecked(true);
            String savedRole = prefs.getString(PREF_ROLE, "teacher");
            selectRole(savedRole);
        } else {
            selectRole("teacher");
        }

        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    // ── Role tab switching ────────────────────────────────────────────────────

    private void selectRole(String role) {
        selectedRole = role;

        int blueColor    = ContextCompat.getColor(requireContext(), R.color.blue_600);
        int purpleColor  = ContextCompat.getColor(requireContext(), R.color.purple_600);
        int greenColor   = ContextCompat.getColor(requireContext(), R.color.green_700);
        int gray500      = ContextCompat.getColor(requireContext(), R.color.gray_500);

        int accentColor, iconBgColor, iconDrawable, tabSelectedBg;
        String loginTitle;

        switch (role) {
            case "student":
                accentColor   = purpleColor;
                iconBgColor   = ContextCompat.getColor(requireContext(), R.color.purple_50);
                iconDrawable  = R.drawable.ic_user;
                loginTitle    = "Student Login";
                break;
            case "secretary":
                accentColor   = greenColor;
                iconBgColor   = ContextCompat.getColor(requireContext(), R.color.green_50);
                iconDrawable  = R.drawable.ic_secretary;
                loginTitle    = "Secretary Login";
                break;
            default: // teacher
                accentColor   = blueColor;
                iconBgColor   = ContextCompat.getColor(requireContext(), R.color.blue_50);
                iconDrawable  = R.drawable.ic_teacher;
                loginTitle    = "Teacher Login";
                break;
        }

        // Update icon + title
        //.setImageResource(iconDrawable);
        //ivRoleIcon.setColorFilter(accentColor);
        //iconCard.setCardBackgroundColor(iconBgColor);
        //tvLoginTitle.setText(loginTitle);

        // Update tab visuals
        resetTab(tabTeacher);
        resetTab(tabStudent);
        resetTab(tabSecretary);

        tabTeacher.setTextColor(gray500);
        tabStudent.setTextColor(gray500);
        tabSecretary.setTextColor(gray500);

        TextView activeTab = role.equals("student") ? tabStudent
                : role.equals("secretary") ? tabSecretary
                : tabTeacher;
        activeTab.setBackgroundResource(getTabBgForRole(role));
        activeTab.setTextColor(accentColor);  // ← change from white to accentColor

        // Update field accent colors
        ColorStateList accentList = ColorStateList.valueOf(accentColor);
        tilEmail.setBoxStrokeColorStateList(accentList);
        tilEmail.setHintTextColor(accentList);
        tilPassword.setBoxStrokeColorStateList(accentList);
        tilPassword.setHintTextColor(accentList);
        cbRememberMe.setButtonTintList(accentList);
        btnLogin.setBackgroundTintList(accentList);

        // Clear errors
        tvError.setVisibility(View.GONE);
        tilEmail.setError(null);
        tilPassword.setError(null);
    }

    private void resetTab(TextView tab) {
        tab.setBackgroundResource(android.R.color.transparent);
        tab.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_500));
    }

    private int getTabBgForRole(String role) {
        // All selected tabs use a colored pill — reuse bg_tab_selected_blue shape
        // but tint is handled via setBackgroundResource + tint workaround below
        switch (role) {
            case "student":   return R.drawable.bg_tab_selected_purple;
            case "secretary": return R.drawable.bg_tab_selected_green;
            default:          return R.drawable.bg_tab_selected_blue;
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    private void attemptLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        tilEmail.setError(null);
        tilPassword.setError(null);
        tvError.setVisibility(View.GONE);

        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_empty_email));
            return;
        }
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_empty_password));
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Signing in…");

        AuthRepository.getInstance().login(email, password, selectedRole,
                new AuthRepository.LoginCallback() {
                    @Override
                    public void onSuccess(UserProfile user) {
                        if (getActivity() == null) return;

                        // Save remember me
                        SharedPreferences prefs = PreferenceManager
                                .getDefaultSharedPreferences(requireContext());
                        if (cbRememberMe.isChecked()) {
                            prefs.edit()
                                    .putBoolean(PREF_REMEMBER, true)
                                    .putString(PREF_EMAIL, email)
                                    .putString(PREF_ROLE, selectedRole)
                                    .apply();
                        } else {
                            prefs.edit()
                                    .putBoolean(PREF_REMEMBER, false)
                                    .remove(PREF_EMAIL)
                                    .remove(PREF_ROLE)
                                    .apply();
                        }

                        ((RoleSelectionActivity) getActivity()).onRoleSelected(selectedRole);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            tvError.setText(errorMessage);
                            tvError.setVisibility(View.VISIBLE);
                            btnLogin.setEnabled(true);
                            btnLogin.setText(R.string.btn_sign_in);
                        });
                    }
                });
    }
}