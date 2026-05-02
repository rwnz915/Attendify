package com.example.attendify.fragments.setup;

import android.Manifest;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.R;
import com.example.attendify.SetupActivity;
import com.example.attendify.ThemeManager;

public class SetupPage2LocationFragment extends Fragment {

    private String role = "teacher";

    // Declared as field but registered in onCreate() — required for FragmentTransaction replace() compatibility
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    public static SetupPage2LocationFragment newInstance(String role) {
        SetupPage2LocationFragment f = new SetupPage2LocationFragment();
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
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> advance()
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setup_page2_location, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyThemeColors(view);

        // Dots owned by SetupActivity — hide fragment placeholder
        View dotsPlaceholder = view.findViewById(R.id.ll_dots);
        if (dotsPlaceholder != null) dotsPlaceholder.setVisibility(View.GONE);

        view.findViewById(R.id.btn_enable_location).setOnClickListener(v ->
                locationPermissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                })
        );
        view.findViewById(R.id.tv_skip_location).setOnClickListener(v -> advance());

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

        int[] bgIds   = {R.id.iv_loc1_bg, R.id.iv_loc2_bg, R.id.iv_loc3_bg};
        int[] iconIds = {R.id.iv_loc1_icon, R.id.iv_loc2_icon, R.id.iv_loc3_icon};
        for (int i = 0; i < 3; i++) {
            View bg = view.findViewById(bgIds[i]);
            ImageView iv = view.findViewById(iconIds[i]);
            if (bg != null) setRoundedColor(bg, lightTint, 10);
            if (iv != null) iv.setColorFilter(primary);
        }

        View btn = view.findViewById(R.id.btn_enable_location);
        if (btn != null) setRoundedGradient(btn, primary, secondary, 16);
    }

    private void runEntranceAnimation(View view) {
        OvershootInterpolator overshoot = new OvershootInterpolator(1.4f);

        View iconContainer = view.findViewById(R.id.iv_page_icon_container);
        View headline      = view.findViewById(R.id.tv_page_headline);
        View sub           = view.findViewById(R.id.tv_page_sub);
        View card1         = view.findViewById(R.id.card_loc_1);
        View card2         = view.findViewById(R.id.card_loc_2);
        View card3         = view.findViewById(R.id.card_loc_3);
        View btn           = view.findViewById(R.id.btn_enable_location);
        View skip          = view.findViewById(R.id.tv_skip_location);

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