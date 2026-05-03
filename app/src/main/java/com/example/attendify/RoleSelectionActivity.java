package com.example.attendify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.attendify.fragments.RoleSelectionFragment;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Entry point. Decides whether to auto-login or show the login screen.
 *
 * OFFLINE AUTO-LOGIN FLOW
 * ───────────────────────
 * 1. "Remember me" pref is set AND Firebase still has a current user token.
 * 2. Online  → reload token from Firebase → fetchUserProfile from Firestore
 *              → cache refreshed → navigate to MainActivity.
 * 3. Offline → skip Firebase token reload → restore UserProfile from local
 *              cache → navigate to MainActivity directly.
 */
public class RoleSelectionActivity extends AppCompatActivity {

    private static final String TAG = "RoleSelectionActivity";

    private static final String PREF_REMEMBER = "remember_me";
    private static final String PREF_ROLE     = "saved_role";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        // Make sure AuthRepository has an application Context for cache access
        AuthRepository.getInstance().init(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean remembered    = prefs.getBoolean(PREF_REMEMBER, false);
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();

        if (remembered && firebaseUser != null) {
            String savedRole = prefs.getString(PREF_ROLE, "teacher");
            String uid       = firebaseUser.getUid();

            // ── Offline path ─────────────────────────────────────────────────
            if (!LocalCacheManager.isOnline(this)) {
                Log.d(TAG, "OFFLINE — restoring session from local cache");
                tryRestoreFromCache(uid, savedRole);
                return;
            }

            // ── Online path: reload Firebase token, then fetch Firestore ─────
            firebaseUser.reload().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    AuthRepository.getInstance().fetchUserProfile(
                            uid, savedRole,
                            new AuthRepository.LoginCallback() {
                                @Override
                                public void onSuccess(UserProfile user) {
                                    ThemeManager.loadThemeFromFirestore(
                                            RoleSelectionActivity.this,
                                            user.getRole(),
                                            themeKey -> ThemeManager.checkSetupDoneFromFirestore(
                                                    RoleSelectionActivity.this,
                                                    isDone -> runOnUiThread(() -> {
                                                        // Cache setup flag
                                                        LocalCacheManager cache =
                                                                LocalCacheManager.getInstance(
                                                                        RoleSelectionActivity.this);
                                                        cache.saveSetupDone(uid, isDone);
                                                        if (themeKey != null) {
                                                            cache.saveTheme(uid, themeKey);
                                                        }

                                                        if (isDone) {
                                                            onRoleSelected(savedRole);
                                                        } else {
                                                            startSetup(savedRole);
                                                        }
                                                    })));
                                }

                                @Override
                                public void onFailure(String errorMessage) {
                                    // Firestore fetch failed — try local cache
                                    Log.w(TAG, "fetchUserProfile failed: " + errorMessage
                                            + " — trying cache");
                                    runOnUiThread(() -> tryRestoreFromCache(uid, savedRole));
                                }
                            });
                } else {
                    // Token reload failed (expired) — try offline cache before forcing re-login
                    Log.w(TAG, "token reload failed — trying cache");
                    runOnUiThread(() -> tryRestoreFromCache(uid, savedRole));
                }
            });

        } else {
            showLoginFragment();
        }
    }

    /**
     * Attempts to restore the session entirely from the local cache.
     * If a cached profile is found, the user lands in MainActivity without
     * touching Firestore or Firebase Auth.
     */
    private void tryRestoreFromCache(String uid, String savedRole) {
        LocalCacheManager cache = LocalCacheManager.getInstance(this);
        UserProfile cached = cache.getUserProfile(uid);

        if (cached != null) {
            Log.d(TAG, "Cache hit — auto-login as " + cached.getRole());
            AuthRepository.getInstance().setLoggedInUser(cached);

            // Use cached setup/theme flags so we don't need the network
            Boolean setupDone = cache.getSetupDone(uid);
            if (setupDone != null && setupDone) {
                onRoleSelected(savedRole);
            } else if (setupDone != null) {
                startSetup(savedRole);
            } else {
                // No setup flag cached — go straight to main (safe default)
                onRoleSelected(savedRole);
            }
        } else {
            Log.d(TAG, "No cache found — showing login screen");
            showLoginFragment();
        }
    }

    // ── Navigation helpers ─────────────────────────────────────────────────────

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