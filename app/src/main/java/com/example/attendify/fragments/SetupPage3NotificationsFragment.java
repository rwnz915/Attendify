package com.example.attendify.fragments.setup;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.R;
import com.example.attendify.SetupActivity;
import com.example.attendify.ThemeManager;

public class SetupPage3NotificationsFragment extends Fragment {

    private String role = "teacher";

    // Declared as field but registered in onCreate() — required for FragmentTransaction replace() compatibility
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    public static SetupPage3NotificationsFragment newInstance(String role) {
        SetupPage3NotificationsFragment f = new SetupPage3NotificationsFragment();
        Bundle b = new Bundle();
        b.putString("role", role);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) role = getArguments().getString("role", "teacher");

        // Register here — inside onCreate — so it works correctly with replace() transactions
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> advance()
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setup_page3_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyThemeColors(view);

        // Dots owned by SetupActivity — hide fragment placeholder
        View dotsPlaceholder = view.findViewById(R.id.ll_dots);
        if (dotsPlaceholder != null) dotsPlaceholder.setVisibility(View.GONE);

        view.findViewById(R.id.btn_enable_notifications).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                advance();
            }
        });
        view.findViewById(R.id.tv_skip_notifications).setOnClickListener(v -> advance());

        view.post(() -> runEntranceAnimation(view));
    }

    private void applyThemeColors(View view) {
        String theme  = ThemeManager.getSavedTheme(requireContext(), role);
        int primary   = ThemeManager.getPrimaryColorForTheme(theme);
        int secondary = ThemeManager.getSecondaryColorForTheme(theme);
        int lightTint = ThemeManager.getLightTintForTheme(theme);

        View iconContainer = view.findViewById(R.id.iv_page_icon_container);
        GradientDrawable iconBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{primary, secondary});
        iconBg.setShape(GradientDrawable.OVAL);
        if (iconContainer != null) iconContainer.setBackground(iconBg);

        if (iconContainer instanceof android.widget.FrameLayout) {
            android.widget.FrameLayout fl = (android.widget.FrameLayout) iconContainer;
            for (int i = 0; i < fl.getChildCount(); i++) {
                if (fl.getChildAt(i) instanceof ImageView)
                    ((ImageView) fl.getChildAt(i)).setColorFilter(0xFFFFFFFF);
            }
        }

        View bg1 = view.findViewById(R.id.iv_notif1_bg);
        View bg2 = view.findViewById(R.id.iv_notif2_bg);
        if (bg1 != null) setRoundedColor(bg1, lightTint, 10);
        if (bg2 != null) setRoundedColor(bg2, lightTint, 10);

        ImageView iv1 = view.findViewById(R.id.iv_notif1_icon);
        ImageView iv2 = view.findViewById(R.id.iv_notif2_icon);
        if (iv1 != null) iv1.setColorFilter(primary);
        if (iv2 != null) iv2.setColorFilter(primary);

        View btn = view.findViewById(R.id.btn_enable_notifications);
        if (btn != null) setRoundedGradient(btn, primary, secondary, 16);
    }

    private void runEntranceAnimation(View view) {
        OvershootInterpolator overshoot = new OvershootInterpolator(1.4f);

        View iconContainer = view.findViewById(R.id.iv_page_icon_container);
        View headline      = view.findViewById(R.id.tv_page_headline);
        View sub           = view.findViewById(R.id.tv_page_sub);
        View card1         = view.findViewById(R.id.card_notif_1);
        View card2         = view.findViewById(R.id.card_notif_2);
        View card3         = view.findViewById(R.id.card_notif_3);
        View btn           = view.findViewById(R.id.btn_enable_notifications);
        View skip          = view.findViewById(R.id.tv_skip_notifications);

        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(
                anim(iconContainer, "scaleX", 400, 100, 0.4f, 1f),
                anim(iconContainer, "scaleY", 400, 100, 0.4f, 1f));
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
        anim(skip, "alpha", 250, 960, 0f, 1f).start();
    }

    private void advance() {
        if (getActivity() instanceof SetupActivity)
            ((SetupActivity) getActivity()).advancePage();
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