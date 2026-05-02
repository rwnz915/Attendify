package com.example.attendify.fragments;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.attendify.ThemeManager;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;

public class RoleSelectionFragment extends Fragment {

    private static final String PREF_REMEMBER  = "remember_me";
    private static final String PREF_EMAIL     = "saved_email";
    private static final String PREF_PASSWORD  = "saved_password";
    private static final String PREF_ROLE      = "saved_role";

    private String selectedRole = "teacher";

    private TextView     tabTeacher, tabStudent, tabSecretary;
    private TextView     tvError;
    private MaterialCardView iconCard;
    private ImageView    ivLogo;
    private TextInputLayout tilEmail, tilPassword;
    private EditText     etEmail, etPassword;
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

        tabTeacher   = view.findViewById(R.id.tab_teacher);
        tabStudent   = view.findViewById(R.id.tab_student);
        tabSecretary = view.findViewById(R.id.tab_secretary);
        tvError      = view.findViewById(R.id.tv_error);
        iconCard     = view.findViewById(R.id.icon_card);
        ivLogo       = view.findViewById(R.id.iv_logo);
        tilEmail     = view.findViewById(R.id.til_email);
        tilPassword  = view.findViewById(R.id.til_password);
        etEmail      = view.findViewById(R.id.et_email);
        etPassword   = view.findViewById(R.id.et_password);
        cbRememberMe = view.findViewById(R.id.cb_remember_me);
        btnLogin     = view.findViewById(R.id.btn_login);

        tabTeacher.setOnClickListener(v   -> selectRole("teacher"));
        tabStudent.setOnClickListener(v   -> selectRole("student"));
        tabSecretary.setOnClickListener(v -> selectRole("secretary"));

        // Restore saved credentials
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        boolean remembered = prefs.getBoolean(PREF_REMEMBER, false);
        if (remembered) {
            etEmail.setText(prefs.getString(PREF_EMAIL, ""));
            cbRememberMe.setChecked(true);
            selectRole(prefs.getString(PREF_ROLE, "teacher"));
        } else {
            selectRole("teacher");
        }

        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    // ── Role tab switching ────────────────────────────────────────────────────

    private void selectRole(String role) {
        selectedRole = role;

        int accentColor = ThemeManager.getPrimaryColor(requireContext(), role);
        int lightTint   = ThemeManager.getLightTintColor(requireContext(), role);

        resetTab(tabTeacher);
        resetTab(tabStudent);
        resetTab(tabSecretary);

        TextView activeTab = role.equals("student") ? tabStudent
                : role.equals("secretary") ? tabSecretary
                : tabTeacher;
        applyTabSelected(activeTab, accentColor);

        ColorStateList accentList = ColorStateList.valueOf(accentColor);
        tilEmail.setBoxStrokeColorStateList(accentList);
        tilEmail.setHintTextColor(accentList);
        tilPassword.setBoxStrokeColorStateList(accentList);
        tilPassword.setHintTextColor(accentList);
        cbRememberMe.setButtonTintList(accentList);
        btnLogin.setBackgroundTintList(accentList);

        if (ivLogo != null) ivLogo.setColorFilter(accentColor);

        tvError.setVisibility(View.GONE);
        tilEmail.setError(null);
        tilPassword.setError(null);
    }

    private void applyTabSelected(TextView tab, int accentColor) {
        float density = getResources().getDisplayMetrics().density;

        android.graphics.drawable.GradientDrawable stroke =
                new android.graphics.drawable.GradientDrawable();
        stroke.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        stroke.setColor(0x00000000);
        stroke.setStroke((int)(3 * density), accentColor);
        stroke.setCornerRadius(2 * density);

        android.graphics.drawable.LayerDrawable layer =
                new android.graphics.drawable.LayerDrawable(
                        new android.graphics.drawable.Drawable[]{stroke});
        layer.setLayerInset(0,
                (int)(-4 * density),
                (int)(-4 * density),
                (int)(-4 * density),
                0);

        tab.setBackground(layer);
        tab.setTextColor(accentColor);
    }

    private void resetTab(TextView tab) {
        tab.setBackgroundResource(android.R.color.transparent);
        tab.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_500));
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

                        SharedPreferences prefs = PreferenceManager
                                .getDefaultSharedPreferences(requireContext());

                        // Save or clear credentials
                        if (cbRememberMe.isChecked()) {
                            prefs.edit()
                                    .putBoolean(PREF_REMEMBER, true)
                                    .putString(PREF_EMAIL, email)
                                    .putString(PREF_PASSWORD, password)
                                    .putString(PREF_ROLE, selectedRole)
                                    .apply();
                        } else {
                            prefs.edit()
                                    .putBoolean(PREF_REMEMBER, false)
                                    .remove(PREF_EMAIL)
                                    .remove(PREF_PASSWORD)
                                    .remove(PREF_ROLE)
                                    .apply();
                        }

                        // 1. Sync theme → 2. Check setup → 3. Navigate once
                        ThemeManager.loadThemeFromFirestore(requireContext(), user.getRole(),
                                themeKey -> {
                                    if (getActivity() == null) return;
                                    ThemeManager.checkSetupDoneFromFirestore(requireContext(),
                                            isDone -> {
                                                if (getActivity() == null) return;
                                                getActivity().runOnUiThread(() -> {
                                                    RoleSelectionActivity activity =
                                                            (RoleSelectionActivity) getActivity();
                                                    if (isDone) {
                                                        activity.onRoleSelected(selectedRole);
                                                    } else {
                                                        activity.startSetup(selectedRole);
                                                    }
                                                });
                                            });
                                });
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