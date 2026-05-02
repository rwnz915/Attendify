package com.example.attendify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;

import com.example.attendify.fragments.RoleSelectionFragment;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RoleSelectionActivity extends AppCompatActivity {

    private static final String PREF_REMEMBER = "remember_me";
    private static final String PREF_ROLE     = "saved_role";
    private static final String PREF_EMAIL    = "saved_email";
    private static final String PREF_PASSWORD = "saved_password";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean remembered   = prefs.getBoolean(PREF_REMEMBER, false);
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();

        if (remembered && firebaseUser != null) {
            String savedRole = prefs.getString(PREF_ROLE, "teacher");

            firebaseUser.reload().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    AuthRepository.getInstance().fetchUserProfile(
                            firebaseUser.getUid(), savedRole,
                            new AuthRepository.LoginCallback() {
                                @Override
                                public void onSuccess(UserProfile user) {
                                    ThemeManager.loadThemeFromFirestore(
                                            RoleSelectionActivity.this,
                                            user.getRole(), themeKey ->
                                                    ThemeManager.checkSetupDoneFromFirestore(
                                                            RoleSelectionActivity.this, isDone ->
                                                                    runOnUiThread(() -> {
                                                                        if (isDone) {
                                                                            onRoleSelected(savedRole);
                                                                        } else {
                                                                            startSetup(savedRole);
                                                                        }
                                                                    })));
                                }

                                @Override
                                public void onFailure(String errorMessage) {
                                    runOnUiThread(RoleSelectionActivity.this::showLoginFragment);
                                }
                            });
                } else {
                    // Token expired — force re-login
                    runOnUiThread(RoleSelectionActivity.this::showLoginFragment);
                }
            });
        } else {
            showLoginFragment();
        }
    }

    private void showLoginFragment() {
        if (getSupportFragmentManager().findFragmentById(R.id.role_container) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.role_container, new RoleSelectionFragment())
                    .commit();
        }
    }

    public void onRoleSelected(String role) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("userRole", role);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.anim_slide_up, R.anim.anim_fade_out);
        finish();
    }

    public void startSetup(String role) {
        Intent intent = new Intent(this, SetupActivity.class);
        intent.putExtra("userRole", role);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.anim_slide_up, R.anim.anim_fade_out);
        finish();
    }
}