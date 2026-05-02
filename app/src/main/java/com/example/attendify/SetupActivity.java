package com.example.attendify;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.attendify.fragments.setup.SetupPage1WelcomeFragment;
import com.example.attendify.fragments.setup.SetupPage2LocationFragment;
import com.example.attendify.fragments.setup.SetupPage3NotificationsFragment;
import com.example.attendify.fragments.setup.SetupPage3ThemeFragment;
import com.example.attendify.fragments.setup.SetupPage4DoneFragment;

/**
 * SetupActivity
 *
 * Single-screen setup flow — content swaps in-place with animated transitions.
 * The dot indicator is persistent (owned by this activity) and animates a sliding
 * pill to indicate the active step. No screen changes, no ViewPager2.
 *
 *   Step 0 — Welcome
 *   Step 1 — Enable Location
 *   Step 2 — Notifications
 *   Step 3 — Theme
 *   Step 4 — All Done
 */
public class SetupActivity extends AppCompatActivity {

    private static final int TOTAL_PAGES = 5;

    private int currentStep = 0;
    private String userRole;

    // Dot views
    private View[] dotViews;
    private LinearLayout dotsContainer;

    // Dot dimensions (dp → px)
    private int dotActivePx;
    private int dotInactivePx;
    private int dotHeightPx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        userRole = getIntent().getStringExtra("userRole");
        if (userRole == null) userRole = "teacher";

        dotsContainer = findViewById(R.id.ll_dots);

        dotActivePx   = dp(20);
        dotInactivePx = dp(8);
        dotHeightPx   = dp(8);

        buildDots();

        // Show first fragment without animation
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.setup_content_container, makeFragment(0))
                    .commit();
        }
    }

    // ── Called by fragments ────────────────────────────────────────────────

    /** Advance to next step with in-place swap animation. */
    public void advancePage() {
        if (currentStep >= TOTAL_PAGES - 1) return;
        int next = currentStep + 1;
        swapToStep(next);
    }

    /** Called from the final step to launch MainActivity. */
    public void finishSetup() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("userRole", userRole);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.anim_slide_up, R.anim.anim_fade_out);
        finish();
    }

    /** For backward compatibility — fragments can ask what page they're on. */
    public int getCurrentPage()  { return currentStep; }
    public int getTotalPages()   { return TOTAL_PAGES; }

    // ── Step swap ──────────────────────────────────────────────────────────

    private void swapToStep(int nextStep) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();

        // Custom animations: current slides & fades out left, new comes in from right
        tx.setCustomAnimations(
                R.anim.setup_enter_right,   // enter
                R.anim.setup_exit_left      // exit
        );

        tx.replace(R.id.setup_content_container, makeFragment(nextStep));
        tx.commit();

        // Animate dot indicator
        animateDots(currentStep, nextStep);

        currentStep = nextStep;
    }

    private Fragment makeFragment(int step) {
        switch (step) {
            case 1:  return SetupPage2LocationFragment.newInstance(userRole);
            case 2:  return SetupPage3NotificationsFragment.newInstance(userRole);
            case 3:  return SetupPage3ThemeFragment.newInstance(userRole);
            case 4:  return SetupPage4DoneFragment.newInstance(userRole);
            default: return SetupPage1WelcomeFragment.newInstance(userRole);
        }
    }

    // ── Dots ───────────────────────────────────────────────────────────────

    private void buildDots() {
        dotsContainer.removeAllViews();
        dotViews = new View[TOTAL_PAGES];

        String theme  = ThemeManager.getSavedTheme(this, userRole);
        int primary   = ThemeManager.getPrimaryColorForTheme(theme);

        for (int i = 0; i < TOTAL_PAGES; i++) {
            View dot = new View(this);
            boolean isActive = (i == 0);
            int width = isActive ? dotActivePx : dotInactivePx;

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, dotHeightPx);
            lp.setMarginEnd(dp(5));
            dot.setLayoutParams(lp);

            GradientDrawable gd = new GradientDrawable();
            gd.setColor(isActive ? primary : 0xFFCBD5E1);
            gd.setCornerRadius(dp(4));
            dot.setBackground(gd);

            dotsContainer.addView(dot);
            dotViews[i] = dot;
        }
    }

    /**
     * Animate the dot indicator:
     * - Old active dot shrinks width + fades to inactive color
     * - New active dot grows width + transitions to primary color
     * Uses a smooth ValueAnimator for width so layout reflows naturally.
     */
    private void animateDots(int fromStep, int toStep) {
        if (dotViews == null) return;

        String theme  = ThemeManager.getSavedTheme(this, userRole);
        int primary   = ThemeManager.getPrimaryColorForTheme(theme);
        int inactive  = 0xFFCBD5E1;

        View fromDot = dotViews[fromStep];
        View toDot   = dotViews[toStep];

        // Shrink outgoing dot
        ValueAnimator shrink = ValueAnimator.ofInt(dotActivePx, dotInactivePx);
        shrink.setDuration(300);
        shrink.setInterpolator(new DecelerateInterpolator(1.5f));
        shrink.addUpdateListener(anim -> {
            int w = (int) anim.getAnimatedValue();
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) fromDot.getLayoutParams();
            lp.width = w;
            fromDot.setLayoutParams(lp);
        });

        // Fade outgoing dot color → inactive
        ObjectAnimator fromAlpha = ObjectAnimator.ofFloat(fromDot, "alpha", 1f, 0.45f);
        fromAlpha.setDuration(200);

        // Grow incoming dot
        ValueAnimator grow = ValueAnimator.ofInt(dotInactivePx, dotActivePx);
        grow.setDuration(360);
        grow.setStartDelay(60);
        grow.setInterpolator(new OvershootInterpolator(1.8f));
        grow.addUpdateListener(anim -> {
            int w = (int) anim.getAnimatedValue();
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) toDot.getLayoutParams();
            lp.width = w;
            toDot.setLayoutParams(lp);
        });

        // Fade incoming dot to full opacity + swap color
        toDot.setAlpha(0.45f);
        ObjectAnimator toAlpha = ObjectAnimator.ofFloat(toDot, "alpha", 0.45f, 1f);
        toAlpha.setDuration(280);
        toAlpha.setStartDelay(80);

        // Swap colors at the start of each
        setDotColor(fromDot, inactive);
        setDotColor(toDot, primary);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(shrink, fromAlpha, grow, toAlpha);
        set.start();
    }

    private void setDotColor(View dot, int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(4));
        dot.setBackground(gd);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}