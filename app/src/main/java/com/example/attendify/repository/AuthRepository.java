package com.example.attendify.repository;

import com.example.attendify.models.UserProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

/**
 * Handles Firebase Auth login and fetches the user profile from Firestore.
 * Keeps a static in-memory session so fragments can call getLoggedInUser() anywhere.
 */
public class AuthRepository {

    private static AuthRepository instance;
    private static UserProfile loggedInUser = null;

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private AuthRepository() {}

    public static AuthRepository getInstance() {
        if (instance == null) instance = new AuthRepository();
        return instance;
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
     */
    public void login(String email, String password, String expectedRole, LoginCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    String uid = result.getUser().getUid();

                    // Fetch profile from Firestore using the UID
                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                if (!doc.exists()) {
                                    callback.onFailure("Account not found. Contact your administrator.");
                                    return;
                                }

                                String role = doc.getString("role");

                                // Make sure the role matches the card they tapped
                                if (!expectedRole.equals(role)) {
                                    auth.signOut();
                                    callback.onFailure("This account is not registered as a " + expectedRole + ".");
                                    return;
                                }

                                // Build the UserProfile from Firestore fields
                                UserProfile user = buildUserProfile(uid, doc);
                                loggedInUser = user;
                                callback.onSuccess(user);
                            })
                            .addOnFailureListener(e -> callback.onFailure("Failed to load profile: " + e.getMessage()));
                })
                .addOnFailureListener(e -> {
                    // Make the Firebase error messages friendlier
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

    // ── Build UserProfile from Firestore document ─────────────────────────────

    @SuppressWarnings("unchecked")
    private UserProfile buildUserProfile(String uid, DocumentSnapshot doc) {
        UserProfile user = new UserProfile();
        user.setId(uid);
        user.setFirstname(doc.getString("firstname"));
        user.setLastname(doc.getString("lastname"));
        user.setEmail(doc.getString("email"));
        user.setRole(doc.getString("role"));
        user.setSection(doc.getString("section"));       // students only
        user.setStudentID(doc.getString("studentID"));   // students only

        // teachers only — sections list
        List<String> sections = (List<String>) doc.get("sections");
        user.setSections(sections);

        return user;
    }

    public void fetchUserProfile(String uid, String savedRole, LoginCallback callback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onFailure("Profile not found.");
                        return;
                    }

                    String role = doc.getString("role");

                    // Make sure the saved role still matches what's in Firestore
                    if (!savedRole.equals(role)) {
                        auth.signOut();
                        callback.onFailure("Role mismatch. Please log in again.");
                        return;
                    }

                    UserProfile user = buildUserProfile(uid, doc);
                    loggedInUser = user;
                    callback.onSuccess(user);
                })
                .addOnFailureListener(e -> callback.onFailure("Failed to load profile: " + e.getMessage()));
    }

    // ── Session ───────────────────────────────────────────────────────────────

    /** Returns the currently logged-in user. Available to all fragments. */
    public UserProfile getLoggedInUser() {
        return loggedInUser;
    }

    /** Clears session and signs out from Firebase Auth. */
    public void logout() {
        loggedInUser = null;
        auth.signOut();
    }

    // ── Legacy stub — kept so existing code doesn't break during migration ────
    @Deprecated
    public void loginAsRole(String role) {
        // No-op — replaced by login(email, password, role, callback)
    }
}