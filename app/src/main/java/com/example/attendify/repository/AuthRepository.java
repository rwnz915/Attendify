package com.example.attendify.repository;

import android.content.Context;
import android.util.Log;

import com.example.attendify.LocalCacheManager;
import com.example.attendify.models.UserProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

/**
 * Handles Firebase Auth login and fetches the user profile from Firestore.
 * Keeps a static in-memory session so fragments can call getLoggedInUser() anywhere.
 *
 * OFFLINE SUPPORT
 * ───────────────
 * • On successful login / profile fetch → profile is cached to LocalCacheManager.
 * • If the device is offline and fetchUserProfile is called, the cached profile
 *   is restored into the in-memory session so auto-login still works.
 * • saveSession() persists uid + role globally so RoleSelectionActivity can
 *   restore the session without touching Firestore.
 */
public class AuthRepository {

    private static final String TAG = "AuthRepository";

    private static AuthRepository instance;
    private static UserProfile loggedInUser = null;

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private Context appContext; // set once via init()

    private AuthRepository() {}

    public static AuthRepository getInstance() {
        if (instance == null) instance = new AuthRepository();
        return instance;
    }

    /**
     * Call this once from Application or the first Activity so repositories
     * can access the cache without needing to pass Context everywhere.
     */
    public void init(Context ctx) {
        this.appContext = ctx.getApplicationContext();
    }

    // ── Callback ──────────────────────────────────────────────────────────────

    public interface LoginCallback {
        void onSuccess(UserProfile user);
        void onFailure(String errorMessage);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Signs in with Firebase Auth, then loads the user profile from Firestore.
     * Validates that the role in Firestore matches the role card the user tapped.
     * On success, the profile is cached locally for offline use.
     */
    public void login(String email, String password, String expectedRole,
                      LoginCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    String uid = result.getUser().getUid();

                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                if (!doc.exists()) {
                                    callback.onFailure(
                                            "Account not found. Contact your administrator.");
                                    return;
                                }

                                String role = doc.getString("role");

                                if (!expectedRole.equals(role)) {
                                    auth.signOut();
                                    callback.onFailure(
                                            "This account is not registered as a "
                                                    + expectedRole + ".");
                                    return;
                                }

                                UserProfile user = buildUserProfile(uid, doc);
                                loggedInUser = user;

                                // ── Cache everything ──────────────────────────
                                cacheProfile(uid, role, user);

                                callback.onSuccess(user);
                            })
                            .addOnFailureListener(e ->
                                    callback.onFailure("Failed to load profile: " + e.getMessage()));
                })
                .addOnFailureListener(e -> {
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("password")) {
                        callback.onFailure("Incorrect password. Please try again.");
                    } else if (msg != null && msg.contains("no user")) {
                        callback.onFailure("No account found with that email.");
                    } else {
                        callback.onFailure("Login failed. Please check your credentials.");
                    }
                });
    }

    // ── Auto-login (called from RoleSelectionActivity on app start) ───────────

    /**
     * Fetches the user profile from Firestore (online) and falls back to the
     * local cache when offline. Either way the in-memory session is restored
     * so the app can navigate straight to MainActivity.
     */
    public void fetchUserProfile(String uid, String savedRole, LoginCallback callback) {

        // ── Offline fast-path ─────────────────────────────────────────────────
        if (appContext != null && !LocalCacheManager.isOnline(appContext)) {
            Log.d(TAG, "fetchUserProfile: OFFLINE — returning cached profile");
            UserProfile cached = getCachedProfile(uid);
            if (cached != null) {
                loggedInUser = cached;
                callback.onSuccess(cached);
            } else {
                callback.onFailure("No cached profile. Please connect to the internet.");
            }
            return;
        }

        // ── Online path ───────────────────────────────────────────────────────
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        // Firestore miss — try cache before failing
                        UserProfile cached = getCachedProfile(uid);
                        if (cached != null) {
                            loggedInUser = cached;
                            callback.onSuccess(cached);
                            return;
                        }
                        callback.onFailure("Profile not found.");
                        return;
                    }

                    String role = doc.getString("role");

                    if (!savedRole.equals(role)) {
                        auth.signOut();
                        callback.onFailure("Role mismatch. Please log in again.");
                        return;
                    }

                    UserProfile user = buildUserProfile(uid, doc);
                    loggedInUser = user;

                    // Refresh cache with latest Firestore data
                    cacheProfile(uid, role, user);

                    callback.onSuccess(user);
                })
                .addOnFailureListener(e -> {
                    // Network error — fall back to cache
                    Log.w(TAG, "fetchUserProfile network error — trying cache", e);
                    UserProfile cached = getCachedProfile(uid);
                    if (cached != null) {
                        loggedInUser = cached;
                        callback.onSuccess(cached);
                    } else {
                        callback.onFailure("Failed to load profile: " + e.getMessage());
                    }
                });
    }

    // ── Build UserProfile from Firestore document ─────────────────────────────

    @SuppressWarnings("unchecked")
    private UserProfile buildUserProfile(String uid, DocumentSnapshot doc) {
        UserProfile user = new UserProfile();
        user.setId(uid);
        user.setFirstname(doc.getString("firstname"));
        user.setLastname(doc.getString("lastname"));
        user.setEmail(doc.getString("email"));
        user.setRole(doc.getString("role"));
        user.setSection(doc.getString("section"));
        user.setStudentID(doc.getString("studentID"));

        List<String> sections = (List<String>) doc.get("sections");
        user.setSections(sections);

        return user;
    }

    // ── Cache helpers ──────────────────────────────────────────────────────────

    private void cacheProfile(String uid, String role, UserProfile user) {
        if (appContext == null) return;
        LocalCacheManager cache = LocalCacheManager.getInstance(appContext);
        cache.saveSession(uid, role);
        cache.saveUserProfile(uid, user);
    }

    private UserProfile getCachedProfile(String uid) {
        if (appContext == null) return null;
        return LocalCacheManager.getInstance(appContext).getUserProfile(uid);
    }

    // ── Session ───────────────────────────────────────────────────────────────

    /** Returns the currently logged-in user. Available to all fragments. */
    public UserProfile getLoggedInUser() {
        return loggedInUser;
    }

    /** Sets the in-memory user (used when restoring from cache). */
    public void setLoggedInUser(UserProfile user) {
        loggedInUser = user;
    }

    /** Clears session and signs out from Firebase Auth. */
    public void logout() {
        loggedInUser = null;
        auth.signOut();
        if (appContext != null) {
            LocalCacheManager.getInstance(appContext).clearSession();
        }
    }

    // ── Legacy stub ───────────────────────────────────────────────────────────
    @Deprecated
    public void loginAsRole(String role) {}
}