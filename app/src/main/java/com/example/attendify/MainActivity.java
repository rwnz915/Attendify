package com.example.attendify;

import android.Manifest;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.attendify.fragments.*;
import com.example.attendify.fragments.SecretaryHomeFragment;
import com.example.attendify.fragments.SecretaryQrFragment;
import com.example.attendify.geofence.GeofenceReceiver;
import com.example.attendify.repository.AuthRepository;
import com.google.android.gms.location.*;

import android.location.Location;

public class MainActivity extends AppCompatActivity {

    private LinearLayout tabHome, tabSubject, tabAttendance, tabHistory, tabProfile, tabQR;
    private LinearLayout bottomNav;
    private FrameLayout fragmentContainer;

    public int currentTab = -1;
    private int previousTab = -1;
    public int navSourceTab = -1;
    private String userRole = "";

    private boolean geofenceAdded = false;

    // ── Geofence state tracking ──────────────────────────────────────────────
    // null  = unknown (first fix not yet received)
    // true  = currently INSIDE the geofence
    // false = currently OUTSIDE the geofence
    private Boolean isInsideGeofence = null;

    // Throttle distance toasts: only show a new one every TOAST_INTERVAL_MS
    private static final long TOAST_INTERVAL_MS = 10_000; // 10 seconds
    private long lastDistanceToastTime = 0;

    // ── GPS smoothing ────────────────────────────────────────────────────────
    // Discard any fix whose reported accuracy is worse than this (in metres).
    private static final float MAX_ACCURACY_METERS = 150f;

    // Rolling average window — keeps the last N valid distance readings
    private static final int SMOOTH_WINDOW = 5;
    private final java.util.ArrayDeque<Integer> distanceWindow = new java.util.ArrayDeque<>(SMOOTH_WINDOW);

    private static final int REQ_FINE = 1001;
    private static final int REQ_BG   = 1002;

    //14.707776, 121.050512

    private static final double GEOFENCE_LAT = 14.704375; //14.704375;
    private static final double GEOFENCE_LNG = 121.036763; //121.036763;

    // ── Hysteresis thresholds ────────────────────────────────────────────────
    // Two thresholds instead of one to prevent bouncing at the boundary.
    // Must go BELOW ENTER_RADIUS to be considered inside,
    // must go ABOVE EXIT_RADIUS  to be considered outside.
    // The gap between them (45–60 m) is a dead zone where state never changes.
    private static final int GEOFENCE_ENTER_RADIUS = 80;  // cross inward  at 45 m
    private static final int GEOFENCE_EXIT_RADIUS  = 130;  // cross outward at 60 m

    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    // Launcher to re-check location after returning from Settings
    private final ActivityResultLauncher<Intent> locationSettingsLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        // User returned from Settings — check again
                        if (!isLocationEnabled()) {
                            // Still off — show the dialog again
                            showLocationOffDialog();
                        }
                        // If now on, tracking will start / geofence will be set up
                        // via the normal flow already triggered earlier
                    }
            );

    public static int statusBarHeight = 0;
    public static int navBarHeight    = 0;

    // ─────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        bottomNav         = findViewById(R.id.bottom_nav);
        fragmentContainer = findViewById(R.id.fragment_container);

        tabHome       = findViewById(R.id.tab_home);
        tabSubject    = findViewById(R.id.tab_subject);
        tabAttendance = findViewById(R.id.tab_attendance);
        tabHistory    = findViewById(R.id.tab_history);
        tabProfile    = findViewById(R.id.tab_profile);
        tabQR         = findViewById(R.id.tab_qr);

        tabHome.setOnClickListener(v       -> selectTab(0));
        tabSubject.setOnClickListener(v    -> selectTab(1));
        tabAttendance.setOnClickListener(v -> selectTab(2));
        tabHistory.setOnClickListener(v    -> selectTab(3));
        tabProfile.setOnClickListener(v    -> selectTab(4));
        tabQR.setOnClickListener(v         -> selectTab(5));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            statusBarHeight = bars.top;
            navBarHeight    = bars.bottom;

            bottomNav.setPadding(8, 0, 8, bars.bottom);

            int tabHeight = (int) (64 * getResources().getDisplayMetrics().density);
            fragmentContainer.setPadding(0, 0, 0,
                    bottomNav.getVisibility() == View.VISIBLE ? tabHeight + bars.bottom : 0);

            return insets;
        });

        userRole = getIntent().getStringExtra("userRole");

        if (savedInstanceState != null) {
            userRole    = savedInstanceState.getString("userRole", userRole);
            currentTab  = savedInstanceState.getInt("currentTab", -1);
            previousTab = savedInstanceState.getInt("previousTab", -1);
        }

        if (userRole == null || userRole.isEmpty()) {
            logout();
        } else {
            setupUIForRole(userRole);
        }

        requestPermissionsFlow();
    }

    // ─────────────────────────────────────────
    // Called every time the app comes to the foreground so we always re-check
    @Override
    protected void onResume() {
        super.onResume();
        checkLocationServices();
        registerNetworkCallback();
    }

    // ─────────────────────────────────────────
    // LOCATION SERVICES CHECK
    // ─────────────────────────────────────────

    /** Returns true when GPS or Network provider is enabled. */
    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return false;

        boolean gpsOn     = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkOn = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return gpsOn || networkOn;
    }

    /** Check and prompt if location is disabled. */
    private void checkLocationServices() {
        if (!isLocationEnabled()) {
            showLocationOffDialog();
        }
    }

    /** Dialog that opens Location Settings when dismissed with "Enable". */
    private void showLocationOffDialog() {
        // Avoid stacking multiple dialogs
        if (isFinishing() || isDestroyed()) return;

        new AlertDialog.Builder(this)
                .setTitle("Location Required")
                .setMessage(
                        "Attendify needs your device location to verify attendance. " +
                                "Please enable Location Services to continue."
                )
                .setCancelable(false)           // user must make a choice
                .setPositiveButton("Enable", (dialog, which) -> openLocationSettings())
                .setNegativeButton("Exit App",  (dialog, which) -> finishAffinity())
                .show();
    }

    /** Opens the system Location Settings screen. */
    private void openLocationSettings() {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        locationSettingsLauncher.launch(intent);
    }

    // ─────────────────────────────────────────
    // ROLE
    // ─────────────────────────────────────────
    private void setupUIForRole(String role) {
        bottomNav.setVisibility(View.VISIBLE);

        // Secretary gets Home, Subject (section-filtered), History, QR, Profile
        boolean isSecretary = "secretary".equals(role);
        boolean isStudent   = "student".equals(role);

        tabQR.setVisibility((isStudent || isSecretary) ? View.VISIBLE : View.GONE);
        tabAttendance.setVisibility((isStudent || isSecretary) ? View.GONE : View.VISIBLE);
        // Secretary now has a Subject tab showing their section's subjects only
        tabSubject.setVisibility(View.VISIBLE);

        selectTab(currentTab != -1 ? currentTab : 0);
        // startDistanceTracking() is called from onRequestPermissionsResult
        // after ACCESS_FINE_LOCATION is confirmed — NOT here — so the
        // fusedClient.requestLocationUpdates() call is never silently skipped.
    }

    // ─────────────────────────────────────────
    // TABS
    // ─────────────────────────────────────────
    public void selectTab(int index) {
        if (currentTab == index) return;
        previousTab = currentTab;
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
                    case 1:  fragment = new StudentSubjectFragment(); break;
                    case 4:  fragment = new StudentProfileFragment(); break;
                    case 3:  fragment = new StudentHistoryFragment();    break;
                    case 5:  fragment = new StudentQrFragment();      break;
                    default: fragment = new StudentHomeFragment();    break;
                }
                break;

            case "secretary":
                switch (index) {
                    case 1:  fragment = new SecretarySubjectFragment();      break; // Subjects
                    case 3:  fragment = new SecretaryHistoryFragment();      break; // History
                    case 4:  fragment = new SecretaryProfileFragment();      break; // Profile
                    case 5:  fragment = new SecretaryQrFragment();           break; // QR Scanner
                    default: fragment = new SecretaryHomeFragment();         break; // Home (0)
                }
                break;

            default:
                fragment = new HomeFragment();
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

        ft.replace(R.id.fragment_container, fragment);
        ft.commit();
    }

    public void navigateTo(Fragment fragment) {
        navSourceTab = currentTab;
        bottomNav.setVisibility(View.GONE);

        // Disable interaction on ProfileFragment if it's underneath
        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container);
        if (current instanceof ProfileFragment) {
            ((ProfileFragment) current).setInteractionEnabled(false);
        }

        getSupportFragmentManager().addOnBackStackChangedListener(
                new androidx.fragment.app.FragmentManager.OnBackStackChangedListener() {
                    @Override
                    public void onBackStackChanged() {
                        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                            getSupportFragmentManager().removeOnBackStackChangedListener(this);
                            bottomNav.setVisibility(View.VISIBLE);

                            // Re-enable ProfileFragment interaction
                            Fragment restored = getSupportFragmentManager()
                                    .findFragmentById(R.id.fragment_container);
                            if (restored instanceof ProfileFragment) {
                                ((ProfileFragment) restored).setInteractionEnabled(true);
                            }
                        }
                    }
                });

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.anim.nav_slide_in_right,
                        0,
                        0,
                        R.anim.nav_slide_out_right)
                .add(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

// ── Touch helpers ─────────────────────────────────────────────────────────

    private void disableAllFragmentTouches() {
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f != null && f.getView() != null) {
                f.getView().setOnTouchListener((v, event) -> true);
            }
        }
    }

    private void enableAllFragmentTouches() {
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f != null && f.getView() != null) {
                f.getView().setOnTouchListener(null);
            }
        }
    }

    private void enableTopFragmentTouches() {
        java.util.List<Fragment> fragments = getSupportFragmentManager().getFragments();
        if (fragments.isEmpty()) return;
        // The last fragment in the list is the top one
        Fragment top = fragments.get(fragments.size() - 1);
        if (top != null && top.getView() != null) {
            top.getView().setOnTouchListener(null);
        }
    }

    // Reusable helper — put this in a utility class if you have many fragments
    public void applyNavBarPadding(View bottomBar) {
        int extra = (int)(20 * getResources().getDisplayMetrics().density);
        bottomBar.setPadding(
                bottomBar.getPaddingLeft(),
                bottomBar.getPaddingTop(),
                bottomBar.getPaddingRight(),
                extra + MainActivity.navBarHeight
        );
    }

    // ─────────────────────────────────────────
    // NAV UI
    // ─────────────────────────────────────────
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

            ImageView icon  = tabs[i].findViewById(icons[i]);
            TextView  label = tabs[i].findViewById(labels[i]);

            if (icon == null || label == null) continue;

            if (i == activeIndex) {
                icon.setColorFilter(com.example.attendify.ThemeManager.getAccentColor(this, userRole));
                label.setVisibility(View.VISIBLE);
            } else {
                icon.setColorFilter(getColor(R.color.gray_400));
                label.setVisibility(View.GONE);
            }
        }
    }

    // ─────────────────────────────────────────
    // PERMISSIONS
    // ─────────────────────────────────────────
    private void requestPermissionsFlow() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                REQ_FINE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_FINE) {

            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                // ✅ Fine location granted — safe to start tracking NOW
                startDistanceTracking();

                // Then request background location for geofencing
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        REQ_BG);

            } else {
                Log.e("PERMISSIONS", "ACCESS_FINE_LOCATION denied — toasts will not appear");
                Toast.makeText(this,
                        "Location permission required for attendance tracking",
                        Toast.LENGTH_LONG).show();
            }

        } else if (requestCode == REQ_BG) {

            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                setupGeofence();

            } else {
                Log.w("PERMISSIONS", "ACCESS_BACKGROUND_LOCATION denied — geofence unavailable");
            }
        }
    }

    // ─────────────────────────────────────────
    // GEOFENCE
    // ─────────────────────────────────────────
    private void setupGeofence() {

        if (geofenceAdded) return;

        Geofence geofence = new Geofence.Builder()
                .setRequestId("CLASSROOM_101")
                .setCircularRegion(GEOFENCE_LAT, GEOFENCE_LNG, 50f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER |
                                Geofence.GEOFENCE_TRANSITION_EXIT
                )
                .build();

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(0)
                .addGeofence(geofence)
                .build();

        GeofencingClient client = LocationServices.getGeofencingClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e("GEOFENCE", "Missing FINE location permission");
            return;
        }

        try {
            client.addGeofences(request, getPendingIntent())
                    .addOnSuccessListener(aVoid -> {
                        geofenceAdded = true;
                        Log.d("GEOFENCE", "Added");
                    })
                    .addOnFailureListener(e ->
                            Log.e("GEOFENCE", "FAILED: " + e.getMessage()));

        } catch (SecurityException e) {
            Log.e("GEOFENCE", "SecurityException: " + e.getMessage());
        }
    }

    private PendingIntent getPendingIntent() {
        Intent intent = new Intent(this, GeofenceReceiver.class);
        return PendingIntent.getBroadcast(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );
    }

    // ─────────────────────────────────────────
    // DISTANCE TRACKING
    // ─────────────────────────────────────────
    private void startDistanceTracking() {

        if (locationCallback != null) return;

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 10_000
        )
                .setMinUpdateIntervalMillis(5_000)
                .setMinUpdateDistanceMeters(2f)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {

                Location loc = result.getLastLocation();
                if (loc == null) return;

                float accuracy = loc.getAccuracy();

                float[] dist = new float[1];
                Location.distanceBetween(
                        loc.getLatitude(),
                        loc.getLongitude(),
                        GEOFENCE_LAT,
                        GEOFENCE_LNG,
                        dist
                );

                int rawMeters = (int) dist[0];

                // ── Smooth GPS noise ─────────────────────────────
                if (distanceWindow.size() >= SMOOTH_WINDOW) distanceWindow.poll();
                distanceWindow.add(rawMeters);

                int smoothed = 0;
                for (int d : distanceWindow) smoothed += d;
                smoothed /= distanceWindow.size();

                // ── Confidence check (DO NOT BLOCK UPDATES) ───────
                boolean highConfidence = accuracy <= MAX_ACCURACY_METERS;

                // Slight correction for low accuracy (prevents “false closer” readings)
                if (!highConfidence) {
                    smoothed += (int) (accuracy * 0.25f);
                }

                // ── Adaptive geofence thresholds ─────────────────
                int enterRadius = highConfidence ? GEOFENCE_ENTER_RADIUS : 80;
                int exitRadius  = highConfidence ? GEOFENCE_EXIT_RADIUS  : 150;

                boolean nowInside;

                if (isInsideGeofence == null) {
                    nowInside = smoothed <= enterRadius;
                } else if (isInsideGeofence) {
                    nowInside = smoothed < exitRadius;
                } else {
                    nowInside = smoothed <= enterRadius;
                }

                // ── State change (ENTER / EXIT) ──────────────────
                if (isInsideGeofence == null || isInsideGeofence != nowInside) {
                    isInsideGeofence = nowInside;

                    showGeofenceToast(
                            (nowInside ? "✅ Inside" : "❌ Outside")
                                    + " (" + (highConfidence ? "HIGH" : "LOW") + " accuracy)"
                    );

                    if (nowInside) {
                        // ── ENTER: resolve active subject then record time-in
                        com.example.attendify.models.UserProfile loggedIn =
                                AuthRepository.getInstance().getLoggedInUser();
                        if (loggedIn != null) {
                            final String _userId  = loggedIn.getId();
                            final String _section = loggedIn.getSection();
                            if (_section != null && !_section.isEmpty()) {
                                com.example.attendify.repository.SubjectRepository
                                        .getInstance()
                                        .getStudentSubjects(_section,
                                                new com.example.attendify.repository.SubjectRepository.SubjectsCallback() {
                                                    @Override
                                                    public void onSuccess(java.util.List<com.example.attendify.repository.SubjectRepository.SubjectItem> subjects) {
                                                        String activeId = findActiveSubjectId(subjects);
                                                        com.example.attendify.repository.GeofenceRepository
                                                                .getInstance()
                                                                .recordTimeIn(MainActivity.this, _userId, activeId);
                                                    }
                                                    @Override
                                                    public void onFailure(String err) {
                                                        com.example.attendify.repository.GeofenceRepository
                                                                .getInstance()
                                                                .recordTimeIn(MainActivity.this, _userId, "");
                                                    }
                                                });
                            } else {
                                com.example.attendify.repository.GeofenceRepository
                                        .getInstance()
                                        .recordTimeIn(MainActivity.this, _userId, "");
                            }
                        }
                    }
                }

                // ── Throttled distance updates ───────────────────
                long nowMs = System.currentTimeMillis();
                if (nowMs - lastDistanceToastTime >= TOAST_INTERVAL_MS) {
                    lastDistanceToastTime = nowMs;

                    showDistanceToast(
                            "📍 " + smoothed +
                                    "m (±" + (int) accuracy + "m)"
                    );
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
        );
    }

    // ─────────────────────────────────────────

    // ─────────────────────────────────────────
    // SUBJECT SCHEDULE HELPERS  (used by distance-tracking ENTER event)
    // ─────────────────────────────────────────

    /** Returns the Firestore doc ID of the subject active right now, or "". */
    private String findActiveSubjectId(
            java.util.List<com.example.attendify.repository.SubjectRepository.SubjectItem> subjects) {
        if (subjects == null) return "";
        java.util.Calendar now = java.util.Calendar.getInstance();
        int calDow = now.get(java.util.Calendar.DAY_OF_WEEK);
        int nowMin = now.get(java.util.Calendar.HOUR_OF_DAY) * 60
                + now.get(java.util.Calendar.MINUTE);
        for (com.example.attendify.repository.SubjectRepository.SubjectItem item : subjects) {
            if (item.schedule == null || item.schedule.isEmpty()) continue;
            String[] parts = item.schedule.trim().split("\\s+", 2);
            if (parts.length < 2) continue;
            if (!subjectDayMatchesToday(parts[0], calDow)) continue;
            String[] times = parts[1].split("-", 2);
            if (times.length < 2) continue;
            int s = parseSubjectTime(times[0].trim());
            int e = parseSubjectTime(times[1].trim());
            if (s < 0 || e < 0) continue;
            if (nowMin >= s && nowMin <= e) {
                Log.d("MainActivity", "Active subject: " + item.id);
                return item.id != null ? item.id : "";
            }
        }
        return "";
    }

    private boolean subjectDayMatchesToday(String d, int calDow) {
        d = d.toUpperCase(java.util.Locale.ENGLISH);
        boolean thu = d.contains("TH");
        boolean tue = d.replace("TH","").contains("T");
        boolean sun = d.contains("SU");
        boolean sat = !sun && d.contains("S");
        switch (calDow) {
            case java.util.Calendar.MONDAY:    return d.contains("M");
            case java.util.Calendar.TUESDAY:   return tue;
            case java.util.Calendar.WEDNESDAY: return d.contains("W");
            case java.util.Calendar.THURSDAY:  return thu;
            case java.util.Calendar.FRIDAY:    return d.contains("F");
            case java.util.Calendar.SATURDAY:  return sat;
            case java.util.Calendar.SUNDAY:    return sun;
            default: return false;
        }
    }

    private int parseSubjectTime(String t) {
        try {
            t = t.trim().toLowerCase(java.util.Locale.ENGLISH);
            boolean pm = t.contains("pm"), am = t.contains("am");
            t = t.replace("pm","").replace("am","").trim();
            String[] hm = t.split(":");
            int h = Integer.parseInt(hm[0].trim()), m = Integer.parseInt(hm[1].trim());
            if (pm && h != 12) h += 12;
            if (am && h == 12) h = 0;
            return h * 60 + m;
        } catch (Exception ex) { return -1; }
    }

    // TOAST HELPERS
    // ─────────────────────────────────────────

    /**
     * Bold, prominent toast for geofence ENTER / EXIT transitions.
     * Shown at the TOP of the screen so it doesn't collide with the bottom nav.
     */
    private void showGeofenceToast(String message) {
       /* runOnUiThread(() -> {
            Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                    0, statusBarHeight + 16);
            toast.show();
        });*/
    }

    /**
     * Standard bottom toast for the periodic distance update.
     */
    private void showDistanceToast(String message) {
        /*runOnUiThread(() ->
                Toast.makeText(this, "📍 " + message, Toast.LENGTH_SHORT).show()
        );*/
    }

    // ─────────────────────────────────────────
    // LOGOUT
    // ─────────────────────────────────────────
    public void logout() {
        AuthRepository.getInstance().logout();

        Intent i = new Intent(this, RoleSelectionActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    // ─────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("userRole", userRole);
        outState.putInt("currentTab", currentTab);
        outState.putInt("previousTab", previousTab);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedClient != null && locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);
        }
        unregisterNetworkCallback();
    }

    // ── Network callback: flush cached geofence entries when back online ────
    private void registerNetworkCallback() {
        connectivityManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                // Back online — push any cached geofence time-in records
                com.example.attendify.models.UserProfile loggedIn =
                        AuthRepository.getInstance().getLoggedInUser();
                if (loggedIn != null) {
                    com.example.attendify.repository.GeofenceRepository
                            .getInstance()
                            .flushPendingEntries(MainActivity.this, loggedIn.getId());
                }
            }
        };

        NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivityManager.registerNetworkCallback(req, networkCallback);
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
    }
}