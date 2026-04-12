package com.example.attendify.repository;

import com.example.attendify.models.MockAuthData;
import com.example.attendify.models.UserProfile;

/**
 * Single access point for auth/session data across all fragments and activities.
 *
 * Fragments should call AuthRepository.getInstance().getLoggedInUser()
 * instead of touching MockAuthData directly.
 *
 * HOW TO SWAP IN REAL AUTH:
 *   Replace getLoggedInUser() with SessionManager.getUser() or a call to
 *   SharedPreferences/Room that persists the login response.
 */
public class AuthRepository {

    private static AuthRepository instance;

    private AuthRepository() {}

    public static AuthRepository getInstance() {
        if (instance == null) instance = new AuthRepository();
        return instance;
    }

    /** Returns the currently logged-in user across all roles. */
    public UserProfile getLoggedInUser() {
        return MockAuthData.getLoggedInUser(); // ← swap: SessionManager.getUser()
    }

    /**
     * Called when a role card is tapped on the role-selection screen.
     * Sets the mock logged-in user for that role.
     *
     * Replace with a real login API call when auth is ready:
     *   ApiService.login(role, credentials, callback)
     */
    public void loginAsRole(String role) {
        MockAuthData.setLoggedInUserForRole(role); // ← swap: ApiService.login(...)
    }

    /**
     * Clears the current session. Called by MainActivity.logout().
     * Replace with SessionManager.clear() or token revocation when real
     * auth is ready.
     */
    public void logout() {
        MockAuthData.clearLoggedInUser(); // ← swap: SessionManager.clear()
    }
}