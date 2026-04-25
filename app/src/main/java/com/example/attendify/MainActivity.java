package com.example.attendify;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.attendify.fragments.*;
import com.example.attendify.geofence.GeofenceReceiver;
import com.example.attendify.repository.AuthRepository;
import com.google.android.gms.location.*;

public class MainActivity extends AppCompatActivity {

    private LinearLayout tabHome, tabSubject, tabAttendance, tabHistory, tabProfile, tabQR;
    private LinearLayout bottomNav;
    private FrameLayout fragmentContainer;

    private int currentTab = -1;
    private String userRole = "";

    private boolean geofenceAdded = false;

    private static final int REQ_FINE = 1001;
    private static final int REQ_BG = 1002;

    public static int statusBarHeight = 0;
    public static int navBarHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        fragmentContainer = findViewById(R.id.fragment_container);

        tabHome = findViewById(R.id.tab_home);
        tabSubject = findViewById(R.id.tab_subject);
        tabAttendance = findViewById(R.id.tab_attendance);
        tabHistory = findViewById(R.id.tab_history);
        tabProfile = findViewById(R.id.tab_profile);
        tabQR = findViewById(R.id.tab_qr);

        tabHome.setOnClickListener(v -> selectTab(0));
        tabSubject.setOnClickListener(v -> selectTab(1));
        tabAttendance.setOnClickListener(v -> selectTab(2));
        tabHistory.setOnClickListener(v -> selectTab(3));
        tabProfile.setOnClickListener(v -> selectTab(4));
        tabQR.setOnClickListener(v -> selectTab(5));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            statusBarHeight = bars.top;
            navBarHeight = bars.bottom;

            bottomNav.setPadding(8, 0, 8, bars.bottom);

            int tabHeight = (int) (64 * getResources().getDisplayMetrics().density);

            fragmentContainer.setPadding(0, 0, 0,
                    bottomNav.getVisibility() == View.VISIBLE ? tabHeight + bars.bottom : 0);

            return insets;
        });

        userRole = getIntent().getStringExtra("userRole");

        if (savedInstanceState != null) {
            userRole = savedInstanceState.getString("userRole", userRole);
            currentTab = savedInstanceState.getInt("currentTab", -1);
        }

        if (userRole == null || userRole.isEmpty()) {
            logout();
        } else {
            setupUIForRole(userRole);
        }

        requestLocationPermissions();
    }

    // ---------------- ROLE UI ----------------
    private void setupUIForRole(String role) {
        bottomNav.setVisibility(View.VISIBLE);

        if (!"student".equals(role)) {
            tabQR.setVisibility(View.GONE);
        } else {
            tabQR.setVisibility(View.VISIBLE);
        }

        selectTab(currentTab != -1 ? currentTab : 0);
    }

    // ---------------- NAVIGATION ----------------
    public void selectTab(int index) {
        if (currentTab == index) return;
        currentTab = index;

        Fragment fragment;

        switch (userRole) {

            case "teacher":
                switch (index) {
                    case 1:
                        fragment = new com.example.attendify.fragments.SubjectFragment();
                        break;
                    case 2:
                        fragment = new com.example.attendify.fragments.AttendanceFragment();
                        break;
                    case 3:
                        fragment = new com.example.attendify.fragments.HistoryFragment();
                        break;
                    case 4:
                        fragment = new com.example.attendify.fragments.ProfileFragment();
                        break;
                    default:
                        fragment = new com.example.attendify.fragments.HomeFragment();
                        break;
                }
                break;

            case "student":
                switch (index) {
                    case 1:
                        fragment = new com.example.attendify.fragments.StudentSubjectFragment();
                        break;
                    case 4:
                        fragment = new com.example.attendify.fragments.StudentProfileFragment();
                        break;
                    case 5:
                        fragment = new com.example.attendify.fragments.StudentQrFragment();
                        break;
                    default:
                        fragment = new com.example.attendify.fragments.StudentHomeFragment();
                        break;
                }
                break;

            case "secretary":
                fragment = new com.example.attendify.fragments.SecretaryFragment();
                break;

            default:
                fragment = new com.example.attendify.fragments.HomeFragment();
                break;
        }

        loadFragment(fragment);
        updateNavUI(index);
    }

    private void loadFragment(Fragment fragment) {
        Bundle b = new Bundle();
        b.putString("userRole", userRole);
        fragment.setArguments(b);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        ft.replace(R.id.fragment_container, fragment);
        ft.commit();
    }

    // ---------------- NAV UI ----------------
    private void updateNavUI(int activeIndex) {

        LinearLayout[] tabs = {
                tabHome, tabSubject, tabAttendance,
                tabHistory, tabProfile, tabQR
        };

        int[] icons = {
                R.id.icon_home, R.id.icon_subject, R.id.icon_attendance,
                R.id.icon_history, R.id.icon_profile, R.id.icon_qr
        };

        int[] labels = {
                R.id.label_home, R.id.label_subject, R.id.label_attendance,
                R.id.label_history, R.id.label_profile, R.id.label_qr
        };

        for (int i = 0; i < tabs.length; i++) {

            if (tabs[i].getVisibility() == View.GONE) continue;

            ImageView icon = tabs[i].findViewById(icons[i]);
            TextView label = tabs[i].findViewById(labels[i]);

            if (icon == null || label == null) continue;

            if (i == activeIndex) {
                icon.setColorFilter(getColor(R.color.blue_600));
                label.setVisibility(View.VISIBLE);
            } else {
                icon.setColorFilter(getColor(R.color.gray_400));
                label.setVisibility(View.GONE);
            }
        }
    }

    // ---------------- LOGOUT ----------------
    public void logout() {
        AuthRepository.getInstance().logout();

        Intent i = new Intent(this, RoleSelectionActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    // ---------------- PERMISSIONS ----------------
    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQ_FINE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_FINE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQ_BG);
        }

        else if (requestCode == REQ_BG &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            setupGeofence();
        }
    }

    // ---------------- GEOFENCE ----------------
    private PendingIntent getGeofencePendingIntent() {
        Intent intent = new Intent(this, GeofenceReceiver.class);

        return PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );
    }

    private void setupGeofence() {

        if (geofenceAdded) return;

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        Geofence geofence = new Geofence.Builder()
                .setRequestId("CLASSROOM_101")
                .setCircularRegion(14.707507, 121.050279, 50)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER |
                                Geofence.GEOFENCE_TRANSITION_EXIT
                )
                .build();

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();

        GeofencingClient client = LocationServices.getGeofencingClient(this);

        client.addGeofences(request, getGeofencePendingIntent())
                .addOnSuccessListener(aVoid -> {
                    geofenceAdded = true;
                    Log.d("GEOFENCE", "Added");
                })
                .addOnFailureListener(e ->
                        Log.e("GEOFENCE", "Failed: " + e.getMessage()));
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