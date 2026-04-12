package com.example.attendify;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.attendify.fragments.AttendanceFragment;
import com.example.attendify.fragments.HistoryFragment;
import com.example.attendify.fragments.HomeFragment;
import com.example.attendify.fragments.ProfileFragment;
import com.example.attendify.fragments.SubjectFragment;
import com.example.attendify.fragments.StudentHomeFragment;
import com.example.attendify.fragments.StudentProfileFragment;
import com.example.attendify.repository.AuthRepository;

public class MainActivity extends AppCompatActivity {

    private LinearLayout tabHome, tabSubject, tabAttendance, tabHistory, tabProfile;
    private LinearLayout bottomNav;
    private FrameLayout fragmentContainer;
    private int currentTab = -1;
    private String userRole = "";

    public static int statusBarHeight = 0;
    public static int navBarHeight    = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        bottomNav         = findViewById(R.id.bottom_nav);
        fragmentContainer = findViewById(R.id.fragment_container);

        tabHome       = findViewById(R.id.tab_home);
        tabSubject    = findViewById(R.id.tab_subject);
        tabAttendance = findViewById(R.id.tab_attendance);
        tabHistory    = findViewById(R.id.tab_history);
        tabProfile    = findViewById(R.id.tab_profile);

        tabHome.setOnClickListener(v       -> selectTab(0));
        tabSubject.setOnClickListener(v    -> selectTab(1));
        tabAttendance.setOnClickListener(v -> selectTab(2));
        tabHistory.setOnClickListener(v    -> selectTab(3));
        tabProfile.setOnClickListener(v    -> selectTab(4));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            statusBarHeight = bars.top;
            navBarHeight    = bars.bottom;

            bottomNav.setPadding(8, 0, 8, bars.bottom);

            int tabHeightPx = (int)(64 * getResources().getDisplayMetrics().density);
            if (bottomNav.getVisibility() == View.VISIBLE) {
                fragmentContainer.setPadding(0, 0, 0, tabHeightPx + bars.bottom);
            } else {
                fragmentContainer.setPadding(0, 0, 0, 0);
            }

            return insets;
        });

        String roleFromIntent = getIntent().getStringExtra("userRole");
        if (roleFromIntent != null) {
            userRole = roleFromIntent;
        }

        if (savedInstanceState != null) {
            userRole   = savedInstanceState.getString("userRole", userRole);
            currentTab = savedInstanceState.getInt("currentTab", -1);
        }

        if (userRole == null || userRole.isEmpty()) {
            logout();
        } else {
            setupUIForRole(userRole);
        }
    }

    private void setupUIForRole(String role) {
        switch (role) {
            case "teacher":
                bottomNav.setVisibility(View.VISIBLE);
                tabHome.setVisibility(View.VISIBLE);
                tabSubject.setVisibility(View.VISIBLE);
                tabAttendance.setVisibility(View.VISIBLE);
                tabHistory.setVisibility(View.VISIBLE);
                tabProfile.setVisibility(View.VISIBLE);
                selectTab(currentTab != -1 ? currentTab : 0);
                break;

            case "student":
                bottomNav.setVisibility(View.VISIBLE);
                tabHome.setVisibility(View.VISIBLE);
                tabSubject.setVisibility(View.VISIBLE);
                tabAttendance.setVisibility(View.GONE);
                tabHistory.setVisibility(View.VISIBLE);
                tabProfile.setVisibility(View.VISIBLE);
                selectTab(currentTab != -1 ? currentTab : 0);
                break;

            case "secretary":
                bottomNav.setVisibility(View.VISIBLE);
                tabHome.setVisibility(View.VISIBLE);
                tabSubject.setVisibility(View.GONE);
                tabAttendance.setVisibility(View.VISIBLE);
                tabHistory.setVisibility(View.VISIBLE);
                tabProfile.setVisibility(View.VISIBLE);
                selectTab(currentTab != -1 ? currentTab : 0);
                break;

            default:
                logout();
                break;
        }
    }

    public void selectTab(int index) {
        if (currentTab == index) return;
        currentTab = index;

        Fragment fragment;

        switch (userRole) {
            case "teacher":
                switch (index) {
                    case 1:  fragment = new SubjectFragment();    break;
                    case 2:  fragment = new AttendanceFragment(); break;
                    case 3:  fragment = new HistoryFragment();    break;
                    case 4:  fragment = new ProfileFragment();    break;
                    default: fragment = new HomeFragment();       break;
                }
                break;

            case "student":
                switch (index) {
                    case 4:  fragment = new StudentProfileFragment(); break;
                    // case 1: fragment = new StudentSubjectFragment(); break;
                    // case 3: fragment = new StudentHistoryFragment(); break;
                    default: fragment = new StudentHomeFragment();    break;
                }
                break;

            case "secretary":
                switch (index) {
                    // case 2: fragment = new SecretaryAttendanceFragment(); break;
                    // case 3: fragment = new SecretaryHistoryFragment();    break;
                    // case 4: fragment = new SecretaryProfileFragment();    break;
                    default: fragment = new HomeFragment(); break;
                }
                break;

            default:
                fragment = new HomeFragment();
                break;
        }

        loadFragment(fragment, false);
        updateNavUI(index);
    }

    private void loadFragment(Fragment fragment, boolean addToBackStack) {
        Bundle bundle = new Bundle();
        bundle.putString("userRole", userRole);
        fragment.setArguments(bundle);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        ft.replace(R.id.fragment_container, fragment);
        if (addToBackStack) ft.addToBackStack(null);
        ft.commit();
    }

    private void updateNavUI(int activeIndex) {
        LinearLayout[] tabs = {
                tabHome, tabSubject, tabAttendance, tabHistory, tabProfile
        };
        int[] icons = {
                R.id.icon_home, R.id.icon_subject, R.id.icon_attendance,
                R.id.icon_history, R.id.icon_profile
        };
        int[] labels = {
                R.id.label_home, R.id.label_subject, R.id.label_attendance,
                R.id.label_history, R.id.label_profile
        };

        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] == null || tabs[i].getVisibility() == View.GONE) continue;

            ImageView icon  = tabs[i].findViewById(icons[i]);
            TextView  label = tabs[i].findViewById(labels[i]);
            if (icon == null || label == null) continue;

            if (i == activeIndex) {
                icon.setColorFilter(getResources().getColor(R.color.blue_600, getTheme()));
                icon.setBackgroundResource(R.drawable.bg_nav_icon_active);
                label.setVisibility(View.VISIBLE);
            } else {
                icon.setColorFilter(getResources().getColor(R.color.gray_400, getTheme()));
                icon.setBackgroundResource(0);
                label.setVisibility(View.GONE);
            }
        }
    }

    public void logout() {
        // Clear the mock session before navigating back to role selection
        AuthRepository.getInstance().logout();

        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("userRole", userRole);
        outState.putInt("currentTab", currentTab);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}