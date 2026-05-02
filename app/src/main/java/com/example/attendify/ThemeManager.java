package com.example.attendify;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class ThemeManager {

    // ── Theme keys ────────────────────────────────────────────────────────────
    public static final String THEME_PROFESSIONAL_SLATE = "professional_slate";
    public static final String THEME_ACADEMIC_BLUE      = "academic_blue";
    public static final String THEME_SUCCESS_GREEN      = "success_green";
    public static final String THEME_MODERN_INDIGO      = "modern_indigo";
    public static final String THEME_ROYAL_PURPLE       = "royal_purple";
    public static final String THEME_ELEGANT_ROSE       = "elegant_rose";

    private static final String PREFS_NAME   = "attendify_theme_prefs";
    private static final String KEY_PREFIX   = "theme_role_";
    private static final String FS_COLLECTION = "settings";
    private static final String FS_FIELD      = "theme";

    // ── Firestore callback ────────────────────────────────────────────────────

    public interface ThemeCallback {
        void onLoaded(String themeKey);
    }

    // ── Default themes per role ───────────────────────────────────────────────

    public static String getDefaultTheme(String role) {
        if (role == null) return THEME_ACADEMIC_BLUE;
        switch (role) {
            case "student":   return THEME_MODERN_INDIGO;
            case "secretary": return THEME_SUCCESS_GREEN;
            default:          return THEME_ACADEMIC_BLUE; // teacher
        }
    }

    // ── Save: SharedPreferences + Firestore ───────────────────────────────────

    public static void saveTheme(Context ctx, String role, String themeKey) {
        // Always save locally
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PREFIX + role, themeKey)
                .apply();

        // Save to Firestore only if user is already authenticated
        String userId = getCurrentUserId();
        if (userId == null) return; // will be pushed up by loadThemeFromFirestore on next login

        Map<String, Object> data = new HashMap<>();
        data.put(FS_FIELD, themeKey);
        FirebaseFirestore.getInstance()
                .collection(FS_COLLECTION)
                .document(userId)
                .set(data, com.google.firebase.firestore.SetOptions.merge());
    }

    // ── Load from Firestore (async) ───────────────────────────────────────────
    // Call this once after login to sync remote → local cache.

    public static void loadThemeFromFirestore(Context ctx, String role,
                                              ThemeCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onLoaded(getSavedTheme(ctx, role));
            return;
        }

        FirebaseFirestore.getInstance()
                .collection(FS_COLLECTION)
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.contains(FS_FIELD)) {
                        String remote = doc.getString(FS_FIELD);
                        if (remote != null && !remote.isEmpty()) {
                            // Remote exists — sync into local cache
                            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                    .edit()
                                    .putString(KEY_PREFIX + role, remote)
                                    .apply();
                            callback.onLoaded(remote);
                            return;
                        }
                    }

                    // No remote doc — push local/default value UP to Firestore
                    String local = getSavedTheme(ctx, role);
                    Map<String, Object> data = new HashMap<>();
                    data.put(FS_FIELD, local);
                    FirebaseFirestore.getInstance()
                            .collection(FS_COLLECTION)
                            .document(userId)
                            .set(data, com.google.firebase.firestore.SetOptions.merge())
                            .addOnCompleteListener(task -> callback.onLoaded(local));
                })
                .addOnFailureListener(e ->
                        callback.onLoaded(getSavedTheme(ctx, role)));
    }

    // ── Read: local cache (synchronous) ──────────────────────────────────────

    public static String getSavedTheme(Context ctx, String role) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PREFIX + role, getDefaultTheme(role));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static String getCurrentUserId() {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // ── Color resolution ──────────────────────────────────────────────────────

    public static int getPrimaryColor(Context ctx, String role) {
        return getPrimaryColorForTheme(getSavedTheme(ctx, role));
    }

    public static int getPrimaryColorForTheme(String themeKey) {
        switch (themeKey) {
            case THEME_PROFESSIONAL_SLATE: return 0xFF334155;
            case THEME_SUCCESS_GREEN:      return 0xFF15803D;
            case THEME_MODERN_INDIGO:      return 0xFF4338CA;
            case THEME_ROYAL_PURPLE:       return 0xFF7C3AED;
            case THEME_ELEGANT_ROSE:       return 0xFFBE123C;
            default:                       return 0xFF1D4ED8;
        }
    }

    public static int getSecondaryColor(Context ctx, String role) {
        return getSecondaryColorForTheme(getSavedTheme(ctx, role));
    }

    public static int getSecondaryColorForTheme(String themeKey) {
        switch (themeKey) {
            case THEME_PROFESSIONAL_SLATE: return 0xFF64748B;
            case THEME_SUCCESS_GREEN:      return 0xFF22C55E;
            case THEME_MODERN_INDIGO:      return 0xFF6366F1;
            case THEME_ROYAL_PURPLE:       return 0xFFA855F7;
            case THEME_ELEGANT_ROSE:       return 0xFFE11D48;
            default:                       return 0xFF3B82F6;
        }
    }

    public static int getAccentColor(Context ctx, String role) {
        return getPrimaryColor(ctx, role);
    }

    public static int getLightTintColor(Context ctx, String role) {
        return getLightTintForTheme(getSavedTheme(ctx, role));
    }

    public static int getLightTintForTheme(String themeKey) {
        switch (themeKey) {
            case THEME_PROFESSIONAL_SLATE: return 0xFFF1F5F9;
            case THEME_SUCCESS_GREEN:      return 0xFFDCFCE7;
            case THEME_MODERN_INDIGO:      return 0xFFE0E7FF;
            case THEME_ROYAL_PURPLE:       return 0xFFF3E8FF;
            case THEME_ELEGANT_ROSE:       return 0xFFFFE4E6;
            default:                       return 0xFFDBEAFE;
        }
    }

    public static int[] getSwatchColors(String themeKey) {
        switch (themeKey) {
            case THEME_PROFESSIONAL_SLATE:
                return new int[]{0xFF1E293B, 0xFF475569, 0xFF94A3B8};
            case THEME_SUCCESS_GREEN:
                return new int[]{0xFF14532D, 0xFF16A34A, 0xFF4ADE80};
            case THEME_MODERN_INDIGO:
                return new int[]{0xFF312E81, 0xFF4F46E5, 0xFF818CF8};
            case THEME_ROYAL_PURPLE:
                return new int[]{0xFF581C87, 0xFF9333EA, 0xFFC084FC};
            case THEME_ELEGANT_ROSE:
                return new int[]{0xFF881337, 0xFFE11D48, 0xFFFB7185};
            default:
                return new int[]{0xFF1E3A8A, 0xFF2563EB, 0xFF93C5FD};
        }
    }

    public static String getThemeLabel(String themeKey) {
        switch (themeKey) {
            case THEME_PROFESSIONAL_SLATE: return "Professional Slate";
            case THEME_SUCCESS_GREEN:      return "Success Green";
            case THEME_MODERN_INDIGO:      return "Modern Indigo";
            case THEME_ROYAL_PURPLE:       return "Royal Purple";
            case THEME_ELEGANT_ROSE:       return "Elegant Rose";
            default:                       return "Academic Blue";
        }
    }

    public static String getThemeSubtitle(String themeKey) {
        switch (themeKey) {
            case THEME_PROFESSIONAL_SLATE: return "Classic institutional look";
            case THEME_SUCCESS_GREEN:      return "Achievement-focused theme";
            case THEME_MODERN_INDIGO:      return "Contemporary academic theme";
            case THEME_ROYAL_PURPLE:       return "Distinguished professional theme";
            case THEME_ELEGANT_ROSE:       return "Refined institutional theme";
            default:                       return "Traditional educational theme";
        }
    }

    public static String[] getSubjectPresetColors(Context ctx, String role) {
        String theme = getSavedTheme(ctx, role);
        switch (theme) {
            case THEME_PROFESSIONAL_SLATE:
                return new String[]{"#334155","#475569","#1E293B","#0F172A",
                        "#64748B","#374151","#1D4ED8","#0369A1"};
            case THEME_SUCCESS_GREEN:
                return new String[]{"#15803D","#0D9488","#16A34A","#059669",
                        "#0891B2","#047857","#0F766E","#166534"};
            case THEME_MODERN_INDIGO:
                return new String[]{"#4338CA","#6366F1","#4F46E5","#3730A3",
                        "#7C3AED","#0D9488","#6D28D9","#0891B2"};
            case THEME_ROYAL_PURPLE:
                return new String[]{"#7C3AED","#9333EA","#6D28D9","#A855F7",
                        "#BE185D","#7C3AED","#4338CA","#C026D3"};
            case THEME_ELEGANT_ROSE:
                return new String[]{"#BE123C","#E11D48","#9F1239","#F43F5E",
                        "#C026D3","#BE123C","#9333EA","#DB2777"};
            default:
                return new String[]{"#1D4ED8","#2563EB","#1E40AF","#3B82F6",
                        "#0369A1","#0891B2","#4F46E5","#0D9488"};
        }
    }

    public static final String[] ALL_THEMES = {
            THEME_ACADEMIC_BLUE,
            THEME_PROFESSIONAL_SLATE,
            THEME_SUCCESS_GREEN,
            THEME_MODERN_INDIGO,
            THEME_ROYAL_PURPLE,
            THEME_ELEGANT_ROSE
    };

    // ── Setup done ────────────────────────────────────────────────────────────

    private static final String FS_FIELD_SETUP = "setupDone";
    private static final String PREF_SETUP_DONE = "setup_done";

    public static void markSetupDone(Context ctx) {
        // Local
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_SETUP_DONE, true)
                .apply();

        // Firestore
        String userId = getCurrentUserId();
        if (userId == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put(FS_FIELD_SETUP, true);
        FirebaseFirestore.getInstance()
                .collection(FS_COLLECTION)
                .document(userId)
                .set(data, com.google.firebase.firestore.SetOptions.merge());
    }

    public static boolean isSetupDoneLocally(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_SETUP_DONE, false);
    }

    public interface SetupCheckCallback {
        void onResult(boolean isDone);
    }

    public static void checkSetupDoneFromFirestore(Context ctx, SetupCheckCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onResult(isSetupDoneLocally(ctx));
            return;
        }

        FirebaseFirestore.getInstance()
                .collection(FS_COLLECTION)
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    boolean done = doc.exists()
                            && Boolean.TRUE.equals(doc.getBoolean(FS_FIELD_SETUP));
                    // Sync to local
                    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean(PREF_SETUP_DONE, done)
                            .apply();
                    callback.onResult(done);
                })
                .addOnFailureListener(e ->
                        callback.onResult(isSetupDoneLocally(ctx)));
    }
}