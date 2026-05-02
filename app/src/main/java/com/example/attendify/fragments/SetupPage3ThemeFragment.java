package com.example.attendify.fragments.setup;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.R;
import com.example.attendify.SetupActivity;
import com.example.attendify.ThemeManager;

/**
 * SetupPage3ThemeFragment
 *
 * NEW page inserted between Notifications (page 2) and Done (page 3).
 * Lets the user pick a color theme; saves it via ThemeManager.
 * Default selection = role's default theme.
 */
public class SetupPage3ThemeFragment extends Fragment {

    private String role = "teacher";
    private String selectedTheme;

    // View refs for selected row
    private View lastSelectedRow = null;

    public static SetupPage3ThemeFragment newInstance(String role) {
        SetupPage3ThemeFragment f = new SetupPage3ThemeFragment();
        Bundle b = new Bundle();
        b.putString("role", role);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) role = getArguments().getString("role", "teacher");
        // Start with role's default theme (or any already-saved preference)
        selectedTheme = ThemeManager.getSavedTheme(requireContext(), role);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setup_page3_theme, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Icon container — gradient from role default theme
        applyIconGradient(view);
        updateCtaColor(view, selectedTheme);

        // Role badge subtitle
        TextView badge = view.findViewById(R.id.tv_role_default_badge);
        if (badge != null) {
            String defaultLabel = ThemeManager.getThemeLabel(ThemeManager.getDefaultTheme(role));
            badge.setText("Default for " + capitalize(role) + ": " + defaultLabel);
        }

        // Build theme rows
        buildThemeList(view);

        // Dots owned by SetupActivity — hide fragment's own placeholder
        View dotsPlaceholder = view.findViewById(R.id.ll_dots);
        if (dotsPlaceholder != null) dotsPlaceholder.setVisibility(View.GONE);

        // CTA
        view.findViewById(R.id.btn_apply_theme).setOnClickListener(v -> {
            ThemeManager.saveTheme(requireContext(), role, selectedTheme);
            advance();
        });

        view.post(() -> runEntranceAnimation(view));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ICON GRADIENT
    // ─────────────────────────────────────────────────────────────────────────

    private void applyIconGradient(View view) {
        View iconContainer = view.findViewById(R.id.iv_page_icon_container);
        if (iconContainer == null) return;
        int primary   = ThemeManager.getPrimaryColorForTheme(selectedTheme);
        int secondary = ThemeManager.getSecondaryColorForTheme(selectedTheme);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{primary, secondary});
        gd.setShape(GradientDrawable.OVAL);
        iconContainer.setBackground(gd);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // THEME LIST
    // ─────────────────────────────────────────────────────────────────────────

    private void buildThemeList(View root) {
        LinearLayout container = root.findViewById(R.id.ll_theme_list);
        if (container == null) return;
        container.removeAllViews();

        String defaultTheme = ThemeManager.getDefaultTheme(role);

        for (String themeKey : ThemeManager.ALL_THEMES) {
            View row = buildThemeRow(themeKey, defaultTheme, container);
            container.addView(row);
        }
    }

    private View buildThemeRow(String themeKey, String defaultTheme, LinearLayout container) {
        // Inflate item_theme_row
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_theme_row, container, false);

        // Color swatches
        int[] swatches = ThemeManager.getSwatchColors(themeKey);
        setSwatchColor(row, R.id.swatch_1, swatches[0]);
        setSwatchColor(row, R.id.swatch_2, swatches[1]);
        setSwatchColor(row, R.id.swatch_3, swatches[2]);

        // Labels
        TextView tvLabel = row.findViewById(R.id.tv_theme_label);
        TextView tvSub   = row.findViewById(R.id.tv_theme_subtitle);
        if (tvLabel != null) tvLabel.setText(ThemeManager.getThemeLabel(themeKey));
        if (tvSub   != null) {
            String sub = ThemeManager.getThemeSubtitle(themeKey);
            if (themeKey.equals(defaultTheme)) sub += " ★ Role default";
            tvSub.setText(sub);
        }

        // Initial check visibility
        ImageView check = row.findViewById(R.id.iv_check);
        boolean isSelected = themeKey.equals(selectedTheme);
        if (check != null) check.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);

        if (isSelected) {
            highlightRow(row, themeKey);
            lastSelectedRow = row;
        }

        // Click
        row.setOnClickListener(v -> {
            // Deselect previous
            if (lastSelectedRow != null && lastSelectedRow != row) {
                clearRowHighlight(lastSelectedRow);
                ImageView oldCheck = lastSelectedRow.findViewById(R.id.iv_check);
                if (oldCheck != null) oldCheck.setVisibility(View.INVISIBLE);
            }

            selectedTheme = themeKey;
            lastSelectedRow = row;

            highlightRow(row, themeKey);
            if (check != null) check.setVisibility(View.VISIBLE);

            // Update icon gradient + CTA button to reflect new theme
            View rootView = getView();
            if (rootView != null) {
                applyIconGradient(rootView);
                updateCtaColor(rootView, themeKey);
            }
        });

        return row;
    }

    private void setSwatchColor(View row, int viewId, int color) {
        View swatch = row.findViewById(viewId);
        if (swatch == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dpToPx(6));
        swatch.setBackground(gd);
    }

    private void highlightRow(View row, String themeKey) {
        int lightTint = ThemeManager.getLightTintForTheme(themeKey);
        int primary   = ThemeManager.getPrimaryColorForTheme(themeKey);
        View rowBg = row.findViewById(R.id.row_bg);
        if (rowBg != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(lightTint);
            gd.setCornerRadius(dpToPx(14));
            gd.setStroke(dpToPx(2), primary);
            rowBg.setBackground(gd);
        }
    }

    private void clearRowHighlight(View row) {
        View rowBg = row.findViewById(R.id.row_bg);
        if (rowBg == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFFFFFFFF);
        gd.setCornerRadius(dpToPx(14));
        gd.setStroke(dpToPx(1), 0xFFE5E7EB);
        rowBg.setBackground(gd);
    }

    private void updateCtaColor(View root, String themeKey) {
        View btn = root.findViewById(R.id.btn_apply_theme);
        if (btn == null) return;
        int primary   = ThemeManager.getPrimaryColorForTheme(themeKey);
        int secondary = ThemeManager.getSecondaryColorForTheme(themeKey);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{primary, secondary});
        gd.setCornerRadius(dpToPx(16));
        btn.setBackground(gd);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DOTS
    // ─────────────────────────────────────────────────────────────────────────

    private void buildDots(View view, int currentPage) {
        LinearLayout ll = view.findViewById(R.id.ll_dots);
        if (ll == null) return;

        SetupActivity activity = (SetupActivity) requireActivity();
        int total   = activity.getTotalPages();
        int primary = ThemeManager.getPrimaryColorForTheme(selectedTheme);

        ll.removeAllViews();
        for (int i = 0; i < total; i++) {
            View dot = new View(requireContext());
            int w = dpToPx(i == currentPage ? 20 : 8);
            int h = dpToPx(8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.setMarginEnd(dpToPx(4));
            dot.setLayoutParams(lp);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(i == currentPage ? primary : 0xFFCBD5E1);
            gd.setCornerRadius(dpToPx(4));
            dot.setBackground(gd);
            ll.addView(dot);
        }
        ll.setAlpha(1f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANIMATION
    // ─────────────────────────────────────────────────────────────────────────

    private void runEntranceAnimation(View view) {
        OvershootInterpolator overshoot = new OvershootInterpolator(1.4f);

        View iconContainer = view.findViewById(R.id.iv_page_icon_container);
        View headline      = view.findViewById(R.id.tv_page_headline);
        View sub           = view.findViewById(R.id.tv_page_sub);
        View badge         = view.findViewById(R.id.tv_role_default_badge);
        View themeList     = view.findViewById(R.id.ll_theme_list);
        View btn           = view.findViewById(R.id.btn_apply_theme);

        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(
                anim(iconContainer, "scaleX", 400, 100, 0.4f, 1f),
                anim(iconContainer, "scaleY", 400, 100, 0.4f, 1f));
        logoSet.getChildAnimations().get(0).setInterpolator(overshoot);
        logoSet.getChildAnimations().get(1).setInterpolator(overshoot);
        logoSet.start();

        slideUp(headline,  300, 350, 16f).start();
        slideUp(sub,       280, 470, 16f).start();
        slideUp(badge,     260, 560, 10f).start();
        slideUp(themeList, 320, 640, 24f).start();
        slideUp(btn,       300, 870, 20f).start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void advance() {
        if (getActivity() instanceof SetupActivity) {
            ((SetupActivity) getActivity()).advancePage();
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private ObjectAnimator anim(View v, String prop, long dur, long delay, float from, float to) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, prop, from, to);
        a.setDuration(dur);
        a.setStartDelay(delay);
        a.setInterpolator(new DecelerateInterpolator(2f));
        return a;
    }

    private AnimatorSet slideUp(View v, long dur, long delay, float fromY) {
        AnimatorSet s = new AnimatorSet();
        s.playTogether(
                anim(v, "alpha", dur, delay, 0f, 1f),
                anim(v, "translationY", dur, delay, fromY, 0f));
        return s;
    }

    private int dpToPx(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density);
    }
}