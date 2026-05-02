package com.example.attendify;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.attendify.fragments.setup.SetupPage3ThemeFragment;
import com.example.attendify.fragments.setup.SetupPage1WelcomeFragment;
import com.example.attendify.fragments.setup.SetupPage2LocationFragment;
import com.example.attendify.fragments.setup.SetupPage3NotificationsFragment;
import com.example.attendify.fragments.setup.SetupPage4DoneFragment;

/**
 * SetupPagerAdapter
 *
 * Drives the 4-page setup flow inside SetupActivity via ViewPager2.
 *
 * Pages:
 *   0 – Welcome         (role-specific hero + 3 feature cards)
 *   1 – Location        (geofence permission request)
 *   2 – Notifications   (notification permission request)
 *   3 – All Done        (dark summary screen + "Start" CTA)
 */
public class SetupPagerAdapter extends FragmentStateAdapter {

    private static final int PAGE_COUNT = 5;
    private final String role;

    public SetupPagerAdapter(@NonNull FragmentActivity activity, String role) {
        super(activity);
        this.role = role;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 1:  return SetupPage2LocationFragment.newInstance(role);
            case 2:  return SetupPage3NotificationsFragment.newInstance(role);
            case 3:  return SetupPage3ThemeFragment.newInstance(role);
            case 4:  return SetupPage4DoneFragment.newInstance(role);
            default: return SetupPage1WelcomeFragment.newInstance(role);
        }
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }
}