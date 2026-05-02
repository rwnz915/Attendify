package com.example.attendify;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * ThemeManager
 *
 * Persists a chosen color theme per user-role and provides helpers
 * that convert a theme key into concrete color values used throughout the app.
 *
 * Usage:
 *   // Save (from setup screen)
 *   ThemeManager.saveTheme(context, "teacher", ThemeManager.THEME_PROFESSIONAL_SLATE);
 *
 *   // Read anywhere
 *   int primary = ThemeManager.getPrimaryColor(context, "teacher");
 */
public class ThemeManager {

    // ── Theme keys ────────────────────────────────────────────────────────────
    public static final String THEME_PROFESSIONAL_SLATE = "professional_slate";
    public static final String THEME_ACADEMIC_BLUE      = "academic_blue";
    public static final String THEME_SUCCESS_GREEN      = "success_green";
    public static final String THEME_MODERN_INDIGO      = "modern_indigo";
    public static final String THEME_ROYAL_PURPLE       = "royal_purple";
    public static final String THEME_ELEGANT_ROSE       = "elegant_rose";

    private static final String PREFS_NAME = "attendify_theme_prefs";
    private static final String KEY_PREFIX = "theme_role_";

    // ── Default themes per role ───────────────────────────────────────────────
    public static String getDefaultTheme(String role) {
        switch (role) {
            case "student":   return THEME_MODERN_INDIGO;
            case "secretary": return THEME_SUCCESS_GREEN;
            default:          return THEME_ACADEMIC_BLUE;   // teacher
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────
    public static void saveTheme(Context ctx, String role, String themeKey) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PREFIX + role, themeKey)
                .apply();
    }

    public static String getSavedTheme(Context ctx, String role) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PREFIX + role, getDefaultTheme(role));
    }

    // ── Color resolution ──────────────────────────────────────────────────────

    /**
     * Primary (darker) header gradient start color.
     * Used for: header background start, button background, active nav icon tint.
     */
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
            default:                       return 0xFF1D4ED8; // academic blue
        }
    }

    /**
     * Secondary (lighter) header gradient end color.
     */
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
            default:                       return 0xFF3B82F6; // academic blue
        }
    }

    /**
     * Accent / tint color — used for active tab icons, buttons, text highlights.
     * Usually same as primary but can differ for better contrast.
     */
    public static int getAccentColor(Context ctx, String role) {
        return getPrimaryColor(ctx, role);
    }

    /**
     * Light background tint (for icon backgrounds, badges, selection highlights).
     */
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
            default:                       return 0xFFDBEAFE; // academic blue
        }
    }

    // ── Color swatches for the theme picker UI ────────────────────────────────
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
            default: // academic blue
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

    public static final String[] ALL_THEMES = {
            THEME_ACADEMIC_BLUE,
            THEME_PROFESSIONAL_SLATE,
            THEME_SUCCESS_GREEN,
            THEME_MODERN_INDIGO,
            THEME_ROYAL_PURPLE,
            THEME_ELEGANT_ROSE
    };
}