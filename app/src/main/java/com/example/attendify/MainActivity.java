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

public class MainActivity extends AppCompatActivity {

    private LinearLayout tabHome, tabAttendance, tabHistory, tabProfile;
    private LinearLayout bottomNav;
    private FrameLayout fragmentContainer;
    private int currentTab = -1;

    /** Status bar height in px — fragments read this to offset their headers */
    public static int statusBarHeight = 0;
    /** Full bottom inset (gesture nav bar) in px */
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

            // Bottom nav: pad bottom so icons sit above the gesture bar
            bottomNav.setPadding(8, 0, 8, bars.bottom);

            // Fragment container: pad bottom so content never hides under bottom nav
            // 64dp tab area + gesture nav bar
            int tabHeightPx = (int)(64 * getResources().getDisplayMetrics().density);
            fragmentContainer.setPadding(0, 0, 0, tabHeightPx + bars.bottom);

            return insets;
        });

        tabHome       = findViewById(R.id.tab_home);
        tabAttendance = findViewById(R.id.tab_attendance);
        tabHistory    = findViewById(R.id.tab_history);
        tabProfile    = findViewById(R.id.tab_profile);

        tabHome.setOnClickListener(v       -> selectTab(0));
        tabAttendance.setOnClickListener(v -> selectTab(1));
        tabHistory.setOnClickListener(v    -> selectTab(2));
        tabProfile.setOnClickListener(v    -> selectTab(3));

        if (savedInstanceState == null) {
            selectTab(0);
        }
    }

    public void selectTab(int index) {
        if (currentTab == index) return;
        currentTab = index;

        Fragment fragment;
        switch (index) {
            case 1: fragment = new AttendanceFragment(); break;
            case 2: fragment = new HistoryFragment();    break;
            case 3: fragment = new ProfileFragment();    break;
            default: fragment = new HomeFragment();      break;
        }

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        ft.replace(R.id.fragment_container, fragment);
        ft.commit();

        updateNavUI(index);
    }

    private void updateNavUI(int activeIndex) {
        LinearLayout[] tabs   = {tabHome, tabAttendance, tabHistory, tabProfile};
        int[]          icons  = {R.id.icon_home, R.id.icon_attendance, R.id.icon_history, R.id.icon_profile};
        int[]          labels = {R.id.label_home, R.id.label_attendance, R.id.label_history, R.id.label_profile};

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
