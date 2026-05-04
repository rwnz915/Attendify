package com.example.attendify.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.ThemeManager;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;

public class NotificationSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_notification_settings);

        androidx.activity.EdgeToEdge.enable(this);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) { finish(); return; }

        // Apply theme color to switch
        int primary = ThemeManager.getPrimaryColor(this, user.getRole());

        SwitchCompat switchNotifications = findViewById(R.id.switch_notifications);

        // Thumb tint
        android.content.res.ColorStateList thumbStates = new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_checked},
                        new int[]{android.R.attr.state_checked}
                },
                new int[]{
                        0xFFFFFFFF,
                        primary
                }
        );

        // Track tint (slightly transparent)
        android.content.res.ColorStateList trackStates = new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_checked},
                        new int[]{android.R.attr.state_checked}
                },
                new int[]{
                        0xFFCBD5E1,
                        (primary & 0x00FFFFFF) | 0x66000000
                }
        );

        switchNotifications.setThumbTintList(thumbStates);
        switchNotifications.setTrackTintList(trackStates);

        // Apply theme
        ThemeApplier.applyHeader(this, user.getRole(), findViewById(R.id.notifications_header));

        // Status bar padding
        android.view.View header = findViewById(R.id.notifications_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Switch logic
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // TODO: Persist this preference if needed (e.g., SharedPreferences or Firestore)
        });
    }
}