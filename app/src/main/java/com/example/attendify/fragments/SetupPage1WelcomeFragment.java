package com.example.attendify.fragments.setup;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
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
 * SetupPage1WelcomeFragment
 *
 * Page 0 of the setup flow.
 * Shows a role-specific icon, welcome headline, 3 feature highlight cards,
 * a "Get Started" CTA, and a 4-dot page indicator.
 */
public class SetupPage1WelcomeFragment extends Fragment {

    private String role = "teacher";

    public static SetupPage1WelcomeFragment newInstance(String role) {
        SetupPage1WelcomeFragment f = new SetupPage1WelcomeFragment();
        Bundle b = new Bundle();
        b.putString("role", role);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) role = getArguments().getString("role", "teacher");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setup_page1_welcome, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyRoleContent(view);
        applyThemeColors(view);
        // Dots owned by SetupActivity — hide fragment placeholder
        View setupDotsPlaceholder = view.findViewById(R.id.ll_dots);
        if (setupDotsPlaceholder != null) setupDotsPlaceholder.setVisibility(View.GONE);

        // Dots are owned by SetupActivity — hide the in-fragment placeholder

        view.findViewById(R.id.btn_get_started).setOnClickListener(v -> advance());

        view.post(() -> runEntranceAnimation(view));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ROLE CONTENT
    // ─────────────────────────────────────────────────────────────────────────

    private void applyRoleContent(View view) {
        TextView tvHeadline = view.findViewById(R.id.tv_headline);
        TextView tvSub      = view.findViewById(R.id.tv_subheadline);
        ImageView ivIcon    = view.findViewById(R.id.iv_role_icon);

        TextView tv1Title = view.findViewById(R.id.tv_feat1_title);
        TextView tv1Desc  = view.findViewById(R.id.tv_feat1_desc);
        TextView tv2Title = view.findViewById(R.id.tv_feat2_title);
        TextView tv2Desc  = view.findViewById(R.id.tv_feat2_desc);
        TextView tv3Title = view.findViewById(R.id.tv_feat3_title);
        TextView tv3Desc  = view.findViewById(R.id.tv_feat3_desc);

        ImageView iv1 = view.findViewById(R.id.iv_feat1_icon);
        ImageView iv2 = view.findViewById(R.id.iv_feat2_icon);
        ImageView iv3 = view.findViewById(R.id.iv_feat3_icon);

        switch (role) {
            case "student":
                tvHeadline.setText("Welcome to Attendify");
                tvSub.setText("Your student portal is set up and\nready to use");
                ivIcon.setImageResource(R.drawable.ic_user);
                tv1Title.setText("My Attendance"); tv1Desc.setText("View your present, late and absent records in real time");
                tv2Title.setText("My Subjects");   tv2Desc.setText("See all enrolled subjects and class schedules");
                tv3Title.setText("Attendance History"); tv3Desc.setText("Review full attendance history and trends");
                iv1.setImageResource(R.drawable.ic_clock);
                iv2.setImageResource(R.drawable.ic_book);
                iv3.setImageResource(R.drawable.ic_document);
                break;

            case "secretary":
                tvHeadline.setText("Welcome to Attendify");
                tvSub.setText("Your secretary dashboard is ready.\nManage your section efficiently");
                ivIcon.setImageResource(R.drawable.ic_secretary);
                tv1Title.setText("Class Management"); tv1Desc.setText("Manage your section's student roster and details");
                tv2Title.setText("QR Check-In");      tv2Desc.setText("Scan QR codes to quickly mark student attendance");
                tv3Title.setText("Attendance Records"); tv3Desc.setText("View and monitor daily attendance for your section");
                iv1.setImageResource(R.drawable.ic_person24);
                iv2.setImageResource(R.drawable.qr_code);
                iv3.setImageResource(R.drawable.ic_clock);
                break;

            default: // teacher
                tvHeadline.setText("Welcome to Attendify");
                tvSub.setText("Professional attendance management\nfor academic institutions");
                ivIcon.setImageResource(R.drawable.ic_teacher);
                tv1Title.setText("Accurate Tracking"); tv1Desc.setText("Real-time attendance monitoring with geofencing");
                tv2Title.setText("Secure & Reliable"); tv2Desc.setText("Enterprise-grade security for academic records");
                tv3Title.setText("Performance Insights"); tv3Desc.setText("Track and analyze attendance patterns over time");
                iv1.setImageResource(R.drawable.ic_clock);
                iv2.setImageResource(R.drawable.ic_book);
                iv3.setImageResource(R.drawable.ic_document);
                break;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // THEME
    // ─────────────────────────────────────────────────────────────────────────

    private void applyThemeColors(View view) {
        String theme  = ThemeManager.getSavedTheme(requireContext(), role);
        int primary   = ThemeManager.getPrimaryColorForTheme(theme);
        int secondary = ThemeManager.getSecondaryColorForTheme(theme);
        int lightTint = ThemeManager.getLightTintForTheme(theme);

        // Icon circle — gradient
        View iconContainer = view.findViewById(R.id.iv_logo_container);
        GradientDrawable circle = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{primary, secondary});
        circle.setShape(GradientDrawable.OVAL);
        iconContainer.setBackground(circle);

        // Feature card icon tints + bg
        int[] iconViewIds = {R.id.iv_feat1_icon, R.id.iv_feat2_icon, R.id.iv_feat3_icon};
        int[] bgViewIds   = {R.id.iv_feat1_bg,   R.id.iv_feat2_bg,   R.id.iv_feat3_bg};
        for (int i = 0; i < 3; i++) {
            ImageView iv = view.findViewById(iconViewIds[i]);
            View bg      = view.findViewById(bgViewIds[i]);
            if (iv != null) iv.setColorFilter(primary);
            if (bg != null) setRoundedColor(bg, lightTint, 12);
        }

        // CTA button
        View btn = view.findViewById(R.id.btn_get_started);
        if (btn != null) setRoundedGradient(btn, primary, secondary, 16);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DOTS
    // ─────────────────────────────────────────────────────────────────────────

    private void buildDots(View view) {
        LinearLayout ll = view.findViewById(R.id.ll_dots);
        if (ll == null) return;

        SetupActivity activity = (SetupActivity) requireActivity();
        int total   = activity.getTotalPages();
        int current = 0; // always page 0

        String theme  = ThemeManager.getSavedTheme(requireContext(), role);
        int primary   = ThemeManager.getPrimaryColorForTheme(theme);

        ll.removeAllViews();
        for (int i = 0; i < total; i++) {
            View dot = new View(requireContext());
            int size = dpToPx(i == current ? 20 : 8);
            int h    = dpToPx(8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, h);
            lp.setMarginEnd(dpToPx(4));
            dot.setLayoutParams(lp);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(i == current ? primary : 0xFFCBD5E1);
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

        View logoCircle  = view.findViewById(R.id.iv_logo_container);
        View headline    = view.findViewById(R.id.tv_headline);
        View sub         = view.findViewById(R.id.tv_subheadline);
        View card1       = view.findViewById(R.id.card_feat_1);
        View card2       = view.findViewById(R.id.card_feat_2);
        View card3       = view.findViewById(R.id.card_feat_3);
        View btn         = view.findViewById(R.id.btn_get_started);
        View dots        = view.findViewById(R.id.ll_dots);

        // Logo pop
        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(
                anim(logoCircle, "scaleX", 400, 100, 0.4f, 1f),
                anim(logoCircle, "scaleY", 400, 100, 0.4f, 1f));
        logoSet.getChildAnimations().get(0).setInterpolator(overshoot);
        logoSet.getChildAnimations().get(1).setInterpolator(overshoot);
        logoSet.start();

        slideUp(headline, 300, 350, 16f).start();
        slideUp(sub,      280, 470, 16f).start();

        View[] cards = {card1, card2, card3};
        for (int i = 0; i < cards.length; i++) {
            long delay = 580 + i * 90L;
            AnimatorSet cs = new AnimatorSet();
            cs.playTogether(
                    anim(cards[i], "alpha", 280, delay, 0f, 1f),
                    anim(cards[i], "translationY", 280, delay, 20f, 0f));
            cs.start();
        }

        slideUp(btn,  300, 870, 20f).start();

    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void advance() {
        if (getActivity() instanceof SetupActivity) {
            ((SetupActivity) getActivity()).advancePage();
        }
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

    private void setRoundedColor(View v, int color, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dpToPx(radiusDp));
        v.setBackground(gd);
    }

    private void setRoundedGradient(View v, int start, int end, int radiusDp) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        gd.setCornerRadius(dpToPx(radiusDp));
        v.setBackground(gd);
    }

    private int dpToPx(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density);
    }
}