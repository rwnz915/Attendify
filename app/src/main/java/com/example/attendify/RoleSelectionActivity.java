package com.example.attendify;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.attendify.fragments.RoleSelectionFragment;

public class RoleSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.role_container, new RoleSelectionFragment())
                    .commit();
        }
    }

    /**
     * Called by RoleSelectionFragment when a role card is tapped.
     * Sets the mock session via AuthRepository, then navigates to MainActivity.
     *
     * When real auth is ready: replace loginAsRole() with an actual login call
     * (show a credentials screen first, call your API, then navigate on success).
     */
    public void onRoleSelected(String role) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("userRole", role);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.anim_slide_up, R.anim.anim_fade_out);
        finish();
    }
}