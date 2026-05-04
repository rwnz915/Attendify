package com.example.attendify;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * ThemeApplier
 *
 * Central utility that applies the saved role theme to any view.
 * Keeps all fragments free of repeated gradient-building boilerplate.
 *
 * Usage (in any fragment's onViewCreated):
 *   String role = AuthRepository.getInstance().getLoggedInUser().getRole();
 *   ThemeApplier.applyHeader(requireContext(), role, view.findViewById(R.id.home_header_bg));
 *   ThemeApplier.applyButton(requireContext(), role, view.findViewById(R.id.btn_start));
 */
public class ThemeApplier {

    // ── Header gradient (full-width, rounded bottom corners) ─────────────────

    public static void applyHeader(Context ctx, String role, View headerBg) {
        if (headerBg == null) return;
        int primary   = ThemeManager.getPrimaryColor(ctx, role);
        int secondary = ThemeManager.getSecondaryColor(ctx, role);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{primary, secondary});
        int r = dp(ctx, 28);
        gd.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        headerBg.setBackground(gd);
    }

    // ── Rounded button gradient ───────────────────────────────────────────────

    public static void applyButton(Context ctx, String role, View btn) {
        if (btn == null) return;
        int primary   = ThemeManager.getPrimaryColor(ctx, role);
        int secondary = ThemeManager.getSecondaryColor(ctx, role);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{primary, secondary});
        gd.setCornerRadius(dp(ctx, 16));
        // MaterialButton's backgroundTint overrides setBackground(), so we must
        // clear it first — otherwise the XML blue_600 tint always wins.
        if (btn instanceof com.google.android.material.button.MaterialButton) {
            ((com.google.android.material.button.MaterialButton) btn)
                    .setBackgroundTintList(null);
        }
        btn.setBackground(gd);
    }

    // ── Oval/circle gradient (avatar, icon containers) ───────────────────────

    public static void applyOval(Context ctx, String role, View view) {
        if (view == null) return;
        int primary   = ThemeManager.getPrimaryColor(ctx, role);
        int secondary = ThemeManager.getSecondaryColor(ctx, role);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{primary, secondary});
        gd.setShape(GradientDrawable.OVAL);
        view.setBackground(gd);
    }

    // ── Solid primary rounded background (chips, pills, badges) ──────────────

    public static void applyChipActive(Context ctx, String role, View chip, int radiusDp) {
        if (chip == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(ThemeManager.getPrimaryColor(ctx, role));
        gd.setCornerRadius(dp(ctx, radiusDp));
        chip.setBackground(gd);
    }

    // ── Light tint background (icon bg circles) ───────────────────────────────

    public static void applyLightTint(Context ctx, String role, View view, int radiusDp) {
        if (view == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(ThemeManager.getLightTintColor(ctx, role));
        gd.setCornerRadius(dp(ctx, radiusDp));
        view.setBackground(gd);
    }

    // ── Quick action card background (light tint, rounded) ────────────────────

    public static void applyQuickActionBg(Context ctx, String role, View view) {
        if (view == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(ThemeManager.getLightTintColor(ctx, role));
        gd.setCornerRadius(dp(ctx, 10));
        view.setBackground(gd);
    }

    // ── Quick action preset colors (4 slots, themed) ──────────────────────────
    // Each index = one button: 0=Subjects, 1=ClassList, 2=History, 3=Settings

    public static int[][] getQuickActionColors(Context ctx, String role) {
        String theme = ThemeManager.getSavedTheme(ctx, role);
        // Each int[2]: { backgroundArgb, iconAndTextArgb }
        switch (theme) {
            case ThemeManager.THEME_PROFESSIONAL_SLATE:
                return new int[][]{
                        {0xFFE2E8F0, 0xFF334155},  // Subjects   – slate blue-gray
                        {0xFFDBEAFE, 0xFF1D4ED8},  // Class List – blue
                        {0xFFDCFCE7, 0xFF15803D},  // History    – green
                        {0xFFF1F5F9, 0xFF475569},  // Settings   – light slate
                };
            case ThemeManager.THEME_SUCCESS_GREEN:
                return new int[][]{
                        {0xFFDCFCE7, 0xFF15803D},  // Subjects   – green
                        {0xFFCCFBF1, 0xFF0F766E},  // Class List – teal
                        {0xFFDBEAFE, 0xFF1D4ED8},  // History    – blue
                        {0xFFF0FDF4, 0xFF166534},  // Settings   – deep green
                };
            case ThemeManager.THEME_MODERN_INDIGO:
                return new int[][]{
                        {0xFFE0E7FF, 0xFF4338CA},  // Subjects   – indigo
                        {0xFFEDE9FE, 0xFF6D28D9},  // Class List – violet
                        {0xFFDBEAFE, 0xFF1D4ED8},  // History    – blue
                        {0xFFCCFBF1, 0xFF0F766E},  // Settings   – teal
                };
            case ThemeManager.THEME_ROYAL_PURPLE:
                return new int[][]{
                        {0xFFF3E8FF, 0xFF7C3AED},  // Subjects   – purple
                        {0xFFFCE7F3, 0xFFBE185D},  // Class List – pink
                        {0xFFEDE9FE, 0xFF6D28D9},  // History    – violet
                        {0xFFDBEAFE, 0xFF1D4ED8},  // Settings   – blue
                };
            case ThemeManager.THEME_ELEGANT_ROSE:
                return new int[][]{
                        {0xFFFFE4E6, 0xFFBE123C},  // Subjects   – rose
                        {0xFFFCE7F3, 0xFFBE185D},  // Class List – pink
                        {0xFFF3E8FF, 0xFF7C3AED},  // History    – purple
                        {0xFFFFF7ED, 0xFFC2410C},  // Settings   – orange
                };
            default: // THEME_ACADEMIC_BLUE
                return new int[][]{
                        {0xFFDBEAFE, 0xFF1D4ED8},  // Subjects   – blue
                        {0xFFEDE9FE, 0xFF6D28D9},  // Class List – violet
                        {0xFFDCFCE7, 0xFF15803D},  // History    – green
                        {0xFFFFEDD5, 0xFFC2410C},  // Settings   – orange
                };
        }
    }

    // ── Apply per-slot quick action color ─────────────────────────────────────

    public static void applyQuickActionColor(Context ctx, String role,
                                             View container, int slot) {
        if (container == null) return;
        int[][] colors = getQuickActionColors(ctx, role);
        if (slot < 0 || slot >= colors.length) return;

        int bg   = colors[slot][0];
        int tint = colors[slot][1];

        // Background
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setCornerRadius(dp(ctx, 10));
        container.setBackground(gd);

        // Tint children (ImageView + TextView)
        if (container instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) container;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof ImageView)
                    ((ImageView) child).setColorFilter(tint);
                else if (child instanceof TextView)
                    ((TextView) child).setTextColor(tint);
            }
        }
    }

    // ── Generate subject card preset colors derived from theme ────────────────
    // Returns 8 hex colors that harmonize with the selected theme primary color.

    public static String[] getSubjectPresetColors(Context ctx, String role) {
        String theme = ThemeManager.getSavedTheme(ctx, role);
        switch (theme) {
            case ThemeManager.THEME_PROFESSIONAL_SLATE:
                return new String[]{"#334155", "#475569", "#1E293B", "#0F172A",
                        "#64748B", "#374151", "#1D4ED8", "#0369A1"};
            case ThemeManager.THEME_SUCCESS_GREEN:
                return new String[]{"#15803D", "#0D9488", "#16A34A", "#059669",
                        "#0891B2", "#047857", "#0F766E", "#166534"};
            case ThemeManager.THEME_MODERN_INDIGO:
                return new String[]{"#4338CA", "#6366F1", "#4F46E5", "#3730A3",
                        "#7C3AED", "#0D9488", "#6D28D9", "#0891B2"};
            case ThemeManager.THEME_ROYAL_PURPLE:
                return new String[]{"#7C3AED", "#9333EA", "#6D28D9", "#A855F7",
                        "#BE185D", "#7C3AED", "#4338CA", "#C026D3"};
            case ThemeManager.THEME_ELEGANT_ROSE:
                return new String[]{"#BE123C", "#E11D48", "#9F1239", "#F43F5E",
                        "#C026D3", "#BE123C", "#9333EA", "#DB2777"};
            default: // academic blue
                return new String[]{"#1D4ED8", "#2563EB", "#1E40AF", "#3B82F6",
                        "#0369A1", "#0891B2", "#4F46E5", "#0D9488"};
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static int primary(Context ctx, String role) {
        return ThemeManager.getPrimaryColor(ctx, role);
    }

    public static int secondary(Context ctx, String role) {
        return ThemeManager.getSecondaryColor(ctx, role);
    }

    public static int lightTint(Context ctx, String role) {
        return ThemeManager.getLightTintColor(ctx, role);
    }

    private static int dp(Context ctx, int value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density);
    }

    // ── Quick action icon/text tint (primary color for icons & labels) ────────

    public static int getQuickActionTint(Context ctx, String role) {
        return ThemeManager.getPrimaryColor(ctx, role);
    }
}