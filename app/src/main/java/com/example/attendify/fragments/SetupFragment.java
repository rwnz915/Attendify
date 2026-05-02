package com.example.attendify.fragments;

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
import com.example.attendify.ThemeManager;

public class SetupFragment extends Fragment {

    public interface OnSetupCompleteListener {
        void onSetupComplete();
    }

    private OnSetupCompleteListener listener;
    private String role = "teacher";
    private String selectedTheme;

    // Theme picker views
    private View[] themeRows;
    private String[] themeKeys;

    public static SetupFragment newInstance(String role) {
        SetupFragment f = new SetupFragment();
        Bundle b = new Bundle();
        b.putString("role", role);
        f.setArguments(b);
        return f;
    }

    public void setOnSetupCompleteListener(OnSetupCompleteListener l) {
        this.listener = l;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) role = getArguments().getString("role", "teacher");
        selectedTheme = ThemeManager.getDefaultTheme(role);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Apply initial role header colors ──────────────────────────────────
        applyHeaderGradient(view, selectedTheme);

        // ── Role-specific content ─────────────────────────────────────────────
        setupRoleContent(view);

        // ── Theme picker ──────────────────────────────────────────────────────
        buildThemePicker(view);

        // ── CTA ───────────────────────────────────────────────────────────────
        view.findViewById(R.id.btn_go_to_dashboard).setOnClickListener(v -> finish());
        view.findViewById(R.id.tv_skip).setOnClickListener(v -> finish());

        // ── Entrance animation ────────────────────────────────────────────────
        view.post(() -> runEntranceAnimation(view));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ROLE CONTENT
    // ─────────────────────────────────────────────────────────────────────────

    private void setupRoleContent(View view) {
        TextView tvBadge      = view.findViewById(R.id.tv_ready_badge);
        TextView tvHeadline   = view.findViewById(R.id.tv_headline);
        TextView tvSubheadline = view.findViewById(R.id.tv_subheadline);
        ImageView ivIcon      = view.findViewById(R.id.iv_role_icon);

        // Feature card holders
        View[] cards = {
                view.findViewById(R.id.card_feature_1),
                view.findViewById(R.id.card_feature_2),
                view.findViewById(R.id.card_feature_3),
                view.findViewById(R.id.card_feature_4)
        };
        int[] iconViews  = {R.id.iv_feat1, R.id.iv_feat2, R.id.iv_feat3, R.id.iv_feat4};
        int[] titleViews = {R.id.tv_feat1_title, R.id.tv_feat2_title, R.id.tv_feat3_title, R.id.tv_feat4_title};
        int[] descViews  = {R.id.tv_feat1_desc, R.id.tv_feat2_desc, R.id.tv_feat3_desc, R.id.tv_feat4_desc};

        String badge, headline, sub;
        int iconRes;
        int[][] iconTints;   // [icon drawable res, bg color hex]
        String[] featTitles, featDescs;
        int[] featIconRes;

        switch (role) {
            case "student":
                badge    = "✦  Student Account Ready";
                headline = "Welcome, Student!";
                sub      = "Your Attendify student portal is all set.\nHere's what you have access to.";
                iconRes  = R.drawable.ic_user;
                featTitles = new String[]{"My Attendance", "My Subjects", "Excuse Letters", "Attendance History"};
                featDescs  = new String[]{
                        "View your present, late, and absent records in real time",
                        "See all your enrolled subjects and class schedules",
                        "Submit and track excuse letter requests easily",
                        "Review your full attendance history and trends"
                };
                featIconRes = new int[]{R.drawable.ic_clock, R.drawable.ic_book, R.drawable.ic_document, R.drawable.ic_chevron_right};
                break;

            case "secretary":
                badge    = "✦  Secretary Account Ready";
                headline = "Welcome, Secretary!";
                sub      = "Your Attendify secretary dashboard is ready.\nManage your section efficiently.";
                iconRes  = R.drawable.ic_secretary;
                featTitles = new String[]{"Class Management", "QR Check-In", "Attendance Records", "Subject Overview"};
                featDescs  = new String[]{
                        "Manage your section's student roster and details",
                        "Scan QR codes to quickly mark student attendance",
                        "View and monitor daily attendance for your section",
                        "Track all subjects assigned to your section"
                };
                featIconRes = new int[]{R.drawable.ic_person24, R.drawable.qr_code, R.drawable.ic_clock, R.drawable.ic_book};
                break;

            default: // teacher
                badge    = "✦  Teacher Account Ready";
                headline = "You're All Set!";
                sub      = "Your Attendify teacher dashboard is ready.\nHere's what's waiting for you.";
                iconRes  = R.drawable.ic_teacher;
                featTitles = new String[]{"Real-time Attendance", "Manage Subjects", "Insights & History", "Excuse Approvals"};
                featDescs  = new String[]{
                        "Track student presence with geofencing technology",
                        "Organize classes, schedules, and student rosters",
                        "View trends, generate reports, spot at-risk students",
                        "Review and approve student absence requests easily"
                };
                featIconRes = new int[]{R.drawable.ic_clock, R.drawable.ic_book, R.drawable.ic_chevron_right, R.drawable.ic_document};
                break;
        }

        tvBadge.setText(badge);
        tvHeadline.setText(headline);
        tvSubheadline.setText(sub);
        ivIcon.setImageResource(iconRes);

        // Feature cards
        int primary = ThemeManager.getPrimaryColorForTheme(selectedTheme);
        int lightTint = ThemeManager.getLightTintForTheme(selectedTheme);

        for (int i = 0; i < 4; i++) {
            ((TextView) cards[i].findViewById(titleViews[i])).setText(featTitles[i]);
            ((TextView) cards[i].findViewById(descViews[i])).setText(featDescs[i]);
            ImageView iv = cards[i].findViewById(iconViews[i]);
            iv.setImageResource(featIconRes[i]);
            iv.setColorFilter(primary);
            View iconBg = cards[i].findViewById(getIconBgId(i));
            if (iconBg != null) setRoundedColor(iconBg, lightTint, 14);
        }
    }

    private int getIconBgId(int i) {
        switch (i) {
            case 0: return R.id.iv_feat1_bg;
            case 1: return R.id.iv_feat2_bg;
            case 2: return R.id.iv_feat3_bg;
            default: return R.id.iv_feat4_bg;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // THEME PICKER
    // ─────────────────────────────────────────────────────────────────────────

    private void buildThemePicker(View view) {
        themeKeys = ThemeManager.ALL_THEMES;
        LinearLayout container = view.findViewById(R.id.ll_theme_container);
        themeRows = new View[themeKeys.length];

        LayoutInflater li = LayoutInflater.from(requireContext());

        for (int i = 0; i < themeKeys.length; i++) {
            final String key = themeKeys[i];
            View row = li.inflate(R.layout.item_theme_row, container, false);

            // Swatches
            int[] swatches = ThemeManager.getSwatchColors(key);
            setSwatch(row, R.id.swatch_1, swatches[0]);
            setSwatch(row, R.id.swatch_2, swatches[1]);
            setSwatch(row, R.id.swatch_3, swatches[2]);

            // Labels
            ((TextView) row.findViewById(R.id.tv_theme_label)).setText(ThemeManager.getThemeLabel(key));
            ((TextView) row.findViewById(R.id.tv_theme_subtitle)).setText(ThemeManager.getThemeSubtitle(key));

            final int index = i;
            row.setOnClickListener(v -> selectTheme(view, key, index));

            container.addView(row);
            themeRows[i] = row;
        }

        // Pre-select default
        int defaultIndex = indexOf(themeKeys, selectedTheme);
        highlightSelectedRow(view, defaultIndex >= 0 ? defaultIndex : 0);
    }

    private void selectTheme(View rootView, String themeKey, int index) {
        selectedTheme = themeKey;
        ThemeManager.saveTheme(requireContext(), role, themeKey);

        // Update header gradient live
        applyHeaderGradient(rootView, themeKey);

        // Update feature card icon tints live
        refreshCardTints(rootView, themeKey);

        // Update CTA button color live
        View btn = rootView.findViewById(R.id.btn_go_to_dashboard);
        setRoundedGradient(btn,
                ThemeManager.getPrimaryColorForTheme(themeKey),
                ThemeManager.getSecondaryColorForTheme(themeKey), 16);

        // Animate selection highlight
        highlightSelectedRow(rootView, index);
    }

    private void highlightSelectedRow(View rootView, int selectedIndex) {
        if (themeRows == null) return;
        for (int i = 0; i < themeRows.length; i++) {
            View row = themeRows[i];
            View checkIcon = row.findViewById(R.id.iv_check);
            View rowBg     = row.findViewById(R.id.row_bg);

            boolean isSelected = i == selectedIndex;
            checkIcon.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);

            if (rowBg != null) {
                int bgColor = isSelected
                        ? ThemeManager.getLightTintForTheme(themeKeys[i])
                        : 0xFFFFFFFF;
                int strokeColor = isSelected
                        ? ThemeManager.getPrimaryColorForTheme(themeKeys[i])
                        : 0xFFE2E8F0;
                setRoundedColorWithStroke(rowBg, bgColor, strokeColor, 14, 2);
            }
        }
    }

    private void refreshCardTints(View rootView, String themeKey) {
        int primary   = ThemeManager.getPrimaryColorForTheme(themeKey);
        int lightTint = ThemeManager.getLightTintForTheme(themeKey);
        int[] iconViewIds = {R.id.iv_feat1, R.id.iv_feat2, R.id.iv_feat3, R.id.iv_feat4};
        int[] bgViewIds   = {R.id.iv_feat1_bg, R.id.iv_feat2_bg, R.id.iv_feat3_bg, R.id.iv_feat4_bg};
        for (int i = 0; i < 4; i++) {
            View iv = rootView.findViewById(iconViewIds[i]);
            View bg = rootView.findViewById(bgViewIds[i]);
            if (iv instanceof ImageView) ((ImageView) iv).setColorFilter(primary);
            if (bg != null) setRoundedColor(bg, lightTint, 14);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HEADER GRADIENT
    // ─────────────────────────────────────────────────────────────────────────

    private void applyHeaderGradient(View rootView, String themeKey) {
        View headerBg = rootView.findViewById(R.id.setup_header_bg);
        if (headerBg == null) return;
        int start = ThemeManager.getPrimaryColorForTheme(themeKey);
        int end   = ThemeManager.getSecondaryColorForTheme(themeKey);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{start, end}
        );
        gd.setCornerRadii(new float[]{0, 0, 0, 0, 36, 36, 36, 36});
        headerBg.setBackground(gd);

        // Badge tint
        View badge = rootView.findViewById(R.id.tv_ready_badge);
        if (badge != null) {
            int light = ThemeManager.getLightTintForTheme(themeKey);
            int primary = ThemeManager.getPrimaryColorForTheme(themeKey);
            GradientDrawable bd = new GradientDrawable();
            bd.setColor(light);
            bd.setStroke(2, withAlpha(primary, 80));
            bd.setCornerRadius(dpToPx(24));
            badge.setBackground(bd);
            if (badge instanceof TextView) ((TextView) badge).setTextColor(primary);
        }

        // CTA button
        View btn = rootView.findViewById(R.id.btn_go_to_dashboard);
        if (btn != null) setRoundedGradient(btn, start, end, 16);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ENTRANCE ANIMATION
    // ─────────────────────────────────────────────────────────────────────────

    private void runEntranceAnimation(View view) {
        DecelerateInterpolator decel = new DecelerateInterpolator(2f);
        OvershootInterpolator overshoot = new OvershootInterpolator(1.4f);

        View llHeader    = view.findViewById(R.id.ll_header_content);
        View logoCircle  = view.findViewById(R.id.iv_logo_container);
        View badge       = view.findViewById(R.id.tv_ready_badge);
        View headline    = view.findViewById(R.id.tv_headline);
        View subheadline = view.findViewById(R.id.tv_subheadline);
        View divider     = view.findViewById(R.id.v_divider);
        View card1 = view.findViewById(R.id.card_feature_1);
        View card2 = view.findViewById(R.id.card_feature_2);
        View card3 = view.findViewById(R.id.card_feature_3);
        View card4 = view.findViewById(R.id.card_feature_4);
        View themeSection = view.findViewById(R.id.ll_theme_section);
        View btn   = view.findViewById(R.id.btn_go_to_dashboard);
        View skip  = view.findViewById(R.id.tv_skip);

        // --- 1. Header block fade (0ms, 280ms) ---
        anim(llHeader, "alpha", 280, 0, 0f, 1f).start();

        // --- 2. Logo pop (200ms delay, 400ms) ---
        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(
                anim(logoCircle, "scaleX", 400, 200, 0.4f, 1f),
                anim(logoCircle, "scaleY", 400, 200, 0.4f, 1f)
        );
        logoSet.getChildAnimations().get(0).setInterpolator(overshoot);
        logoSet.getChildAnimations().get(1).setInterpolator(overshoot);
        logoSet.start();

        // --- 3. Badge slide up (400ms delay) ---
        animSlideUp(badge, 300, 400, 10f).start();

        // --- 4. Headline (550ms) ---
        animSlideUp(headline, 300, 550, 16f).start();

        // --- 5. Subheadline (670ms) ---
        animSlideUp(subheadline, 280, 670, 16f).start();

        // --- 6. Divider scale (780ms) ---
        AnimatorSet divSet = new AnimatorSet();
        divSet.playTogether(
                anim(divider, "alpha", 250, 780, 0f, 1f),
                anim(divider, "scaleX", 250, 780, 0f, 1f)
        );
        divSet.start();

        // --- 7. Cards stagger from left (880ms base, +80ms each) ---
        View[] cards = {card1, card2, card3, card4};
        for (int i = 0; i < cards.length; i++) {
            long delay = 880 + (i * 80L);
            AnimatorSet cs = new AnimatorSet();
            cs.playTogether(
                    anim(cards[i], "alpha", 300, delay, 0f, 1f),
                    anim(cards[i], "translationX", 300, delay, -40f, 0f)
            );
            cs.start();
        }

        // --- 8. Theme section (1200ms) ---
        if (themeSection != null) animSlideUp(themeSection, 300, 1200, 20f).start();

        // --- 9. Button + skip (1350ms) ---
        animSlideUp(btn, 300, 1350, 20f).start();
        anim(skip, "alpha", 250, 1480, 0f, 1f).start();
    }

    private ObjectAnimator anim(View v, String prop, long dur, long delay, float from, float to) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, prop, from, to);
        a.setDuration(dur);
        a.setStartDelay(delay);
        a.setInterpolator(new DecelerateInterpolator(2f));
        return a;
    }

    private AnimatorSet animSlideUp(View v, long dur, long delay, float fromY) {
        AnimatorSet s = new AnimatorSet();
        s.playTogether(
                anim(v, "alpha", dur, delay, 0f, 1f),
                anim(v, "translationY", dur, delay, fromY, 0f)
        );
        return s;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void finish() {
        if (listener != null) listener.onSetupComplete();
    }

    private void setSwatch(View parent, int viewId, int color) {
        View swatch = parent.findViewById(viewId);
        if (swatch == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dpToPx(6));
        swatch.setBackground(gd);
    }

    private void setRoundedColor(View v, int color, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dpToPx(radiusDp));
        v.setBackground(gd);
    }

    private void setRoundedColorWithStroke(View v, int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setStroke(dpToPx(strokeDp), stroke);
        gd.setCornerRadius(dpToPx(radiusDp));
        v.setBackground(gd);
    }

    private void setRoundedGradient(View v, int start, int end, int radiusDp) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{start, end}
        );
        gd.setCornerRadius(dpToPx(radiusDp));
        v.setBackground(gd);
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int dpToPx(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density);
    }

    private int indexOf(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(val)) return i;
        return -1;
    }
}