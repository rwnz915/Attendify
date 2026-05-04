package com.example.attendify.activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_about);

        androidx.activity.EdgeToEdge.enable(this);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) { finish(); return; }

        // Apply theme
        ThemeApplier.applyHeader(this, user.getRole(), findViewById(R.id.about_header));

        // Status bar padding
        android.view.View header = findViewById(R.id.about_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Fill support contact
        ((TextView) findViewById(R.id.tv_support_contact)).setText("support@attendify.com");
    }
}