package com.example.attendify;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

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
}