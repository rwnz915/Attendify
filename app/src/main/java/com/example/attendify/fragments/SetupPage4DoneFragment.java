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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.R;
import com.example.attendify.SetupActivity;
import com.example.attendify.ThemeManager;

public class SetupPage4DoneFragment extends Fragment {

    private String role = "teacher";

    public static SetupPage4DoneFragment newInstance(String role) {
        SetupPage4DoneFragment f = new SetupPage4DoneFragment();
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
        return inflater.inflate(R.layout.fragment_setup_page4_done, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        buildDots(view, 4);

        view.findViewById(R.id.btn_start).setOnClickListener(v -> {
            if (getActivity() instanceof SetupActivity) {
                ThemeManager.markSetupDone(requireContext());
                ((SetupActivity) getActivity()).finishSetup();
            }
        });

        view.post(() -> runEntranceAnimation(view));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Always re-read theme here — user may have just changed it on the previous page
        View view = getView();
        if (view != null) {
            applyThemeColors(view);
            // Dots owned by SetupActivity — hide fragment placeholder
            View setupDotsPlaceholder = view.findViewById(R.id.ll_dots);
            if (setupDotsPlaceholder != null) setupDotsPlaceholder.setVisibility(View.GONE);

            buildDots(view, 4);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void applyThemeColors(View view) {
        String theme    = ThemeManager.getSavedTheme(requireContext(), role);
        int primary     = ThemeManager.getPrimaryColorForTheme(theme);
        int secondary   = ThemeManager.getSecondaryColorForTheme(theme);

        // Circle background — theme gradient
        View circle = view.findViewById(R.id.iv_done_circle);
        if (circle != null) {
            GradientDrawable circleBg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, new int[]{primary, secondary});
            circleBg.setShape(GradientDrawable.OVAL);
            circle.setBackground(circleBg);
        }

        // Check icon — white on themed circle
        ImageView checkIcon = view.findViewById(R.id.iv_done_check);
        if (checkIcon != null) checkIcon.setColorFilter(0xFFFFFFFF);

        // CTA button — theme gradient
        View btn = view.findViewById(R.id.btn_start);
        if (btn != null) setRoundedGradient(btn, primary, secondary, 16);
    }

    private void buildDots(View view, int currentPage) {
        LinearLayout ll = view.findViewById(R.id.ll_dots);
        if (ll == null) return;

        SetupActivity activity = (SetupActivity) requireActivity();
        int total = activity.getTotalPages();

        String theme  = ThemeManager.getSavedTheme(requireContext(), role);
        int primary   = ThemeManager.getPrimaryColorForTheme(theme);

        ll.removeAllViews();
        for (int i = 0; i < total; i++) {
            View dot = new View(requireContext());
            int w    = dpToPx(i == currentPage ? 20 : 8);
            int h    = dpToPx(8);
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

    private void runEntranceAnimation(View view) {
        OvershootInterpolator overshoot = new OvershootInterpolator(1.4f);

        View doneContainer = view.findViewById(R.id.iv_done_container);
        View headline      = view.findViewById(R.id.tv_done_headline);
        View sub           = view.findViewById(R.id.tv_done_sub);
        View card          = view.findViewById(R.id.card_done_summary);
        View btn           = view.findViewById(R.id.btn_start);
        View dots          = view.findViewById(R.id.ll_dots);

        AnimatorSet checkSet = new AnimatorSet();
        checkSet.playTogether(
                anim(doneContainer, "scaleX", 500, 100, 0.4f, 1f),
                anim(doneContainer, "scaleY", 500, 100, 0.4f, 1f));
        checkSet.getChildAnimations().get(0).setInterpolator(overshoot);
        checkSet.getChildAnimations().get(1).setInterpolator(overshoot);
        checkSet.start();

        slideUp(headline, 300, 400, 16f).start();
        slideUp(sub,      280, 520, 16f).start();
        slideUp(card,     320, 640, 20f).start();
        slideUp(btn,      300, 820, 20f).start();
    }

    // ─────────────────────────────────────────────────────────────────────────

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