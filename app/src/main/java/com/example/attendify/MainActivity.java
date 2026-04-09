package com.example.attendify;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
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

public class MainActivity extends AppCompatActivity {

    private LinearLayout tabHome, tabSubject, tabAttendance, tabHistory, tabProfile;
    private LinearLayout bottomNav;
    private FrameLayout fragmentContainer;
    private int currentTab = -1;

    public static int statusBarHeight = 0;
    public static int navBarHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        bottomNav         = findViewById(R.id.bottom_nav);
        fragmentContainer = findViewById(R.id.fragment_container);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            statusBarHeight = bars.top;
            navBarHeight    = bars.bottom;

            bottomNav.setPadding(8, 0, 8, bars.bottom);

            int tabHeightPx = (int)(64 * getResources().getDisplayMetrics().density);
            fragmentContainer.setPadding(0, 0, 0, tabHeightPx + bars.bottom);

            return insets;
        });

        // Tabs
        tabHome       = findViewById(R.id.tab_home);
        tabSubject    = findViewById(R.id.tab_subject);
        tabAttendance = findViewById(R.id.tab_attendance);
        tabHistory    = findViewById(R.id.tab_history);
        tabProfile    = findViewById(R.id.tab_profile);

        // Click listeners (UPDATED INDEXES)
        tabHome.setOnClickListener(v       -> selectTab(0));
        tabSubject.setOnClickListener(v    -> selectTab(1));
        tabAttendance.setOnClickListener(v -> selectTab(2));
        tabHistory.setOnClickListener(v    -> selectTab(3));
        tabProfile.setOnClickListener(v    -> selectTab(4));

        if (savedInstanceState == null) {
            selectTab(0);
        }
    }

    public void selectTab(int index) {
        if (currentTab == index) return;
        currentTab = index;

        Fragment fragment;
        switch (index) {
            case 1: fragment = new SubjectFragment();    break;
            case 2: fragment = new AttendanceFragment(); break;
            case 3: fragment = new HistoryFragment();    break;
            case 4: fragment = new ProfileFragment();    break;
            default: fragment = new HomeFragment();      break;
        }

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        ft.replace(R.id.fragment_container, fragment);
        ft.commit();

        updateNavUI(index);
    }

    private void updateNavUI(int activeIndex) {
        LinearLayout[] tabs = {
                tabHome,
                tabSubject,
                tabAttendance,
                tabHistory,
                tabProfile
        };

        int[] icons = {
                R.id.icon_home,
                R.id.icon_subject,
                R.id.icon_attendance,
                R.id.icon_history,
                R.id.icon_profile
        };

        int[] labels = {
                R.id.label_home,
                R.id.label_subject,
                R.id.label_attendance,
                R.id.label_history,
                R.id.label_profile
        };

        for (int i = 0; i < tabs.length; i++) {
            ImageView icon  = tabs[i].findViewById(icons[i]);
            TextView  label = tabs[i].findViewById(labels[i]);

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
}