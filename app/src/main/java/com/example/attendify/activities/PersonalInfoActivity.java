package com.example.attendify.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;

public class PersonalInfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_personal_info);

        androidx.activity.EdgeToEdge.enable(this);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) { finish(); return; }

        // Apply theme to header
        ThemeApplier.applyHeader(this, user.getRole(), findViewById(R.id.personal_info_header));

        // Status bar padding
        android.view.View header = findViewById(R.id.personal_info_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Fill data
        ((TextView) findViewById(R.id.tv_full_name)).setText(user.getFullName());
        ((TextView) findViewById(R.id.tv_email)).setText(user.getEmail());

        String sectionText = "N/A";
        if (user.isStudent() || user.isSecretary()) {
            sectionText = user.getSection() != null ? user.getSection() : "N/A";
        } else if (user.isTeacher() && user.getSections() != null && !user.getSections().isEmpty()) {
            sectionText = String.join(", ", user.getSections());
        }
        ((TextView) findViewById(R.id.tv_section)).setText(sectionText);

        // Parent Info (Student only)
        View parentLayout = findViewById(R.id.layout_parent_info);
        if (user.isStudent()) {
            parentLayout.setVisibility(View.VISIBLE);

            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("parents")
                    .document(user.getId())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String parentName    = doc.getString("parent");
                            String parentContact = doc.getString("contact");

                            TextView tvParentName    = findViewById(R.id.tv_parent_name);
                            TextView tvParentContact = findViewById(R.id.tv_parent_contact);

                            if (tvParentName != null)
                                tvParentName.setText(parentName != null ? parentName : "Not provided");
                            if (tvParentContact != null)
                                tvParentContact.setText(parentContact != null ? parentContact : "Not provided");
                        }
                    })
                    .addOnFailureListener(e -> { /* leave "Not provided" as default */ });

            ((TextView) findViewById(R.id.tv_parent_name)).setText("Loading...");
            ((TextView) findViewById(R.id.tv_parent_contact)).setText("Loading...");

        } else {
            parentLayout.setVisibility(View.GONE);
        }
    }
}