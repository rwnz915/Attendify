package com.example.attendify.activities;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.ThemeManager;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;

public class AboutActivity extends AppCompatActivity {

    private static final String EMAIL_SUPPORT = "presentime.attendify@gmail.com";
    private static final String PHONE_NUMBER  = "+639566540246";
    private static final String URL_FACEBOOK  = "https://www.facebook.com/profile.php?id=61574349402092&mibextid=wwXIfr&rdid=LJELMGKSY2HsbuIQ&share_url=https%3A%2F%2Fwww.facebook.com%2Fshare%2F1HeVa4nQUW%2F#";
    private static final String URL_PRIVACY   = "https://one-attendify.vercel.app/privacy-policy";
    private static final String URL_TERMS     = "https://one-attendify.vercel.app/terms-of-service";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_about);

        androidx.activity.EdgeToEdge.enable(this);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) { finish(); return; }

        String role = user.getRole();
        int primary   = ThemeManager.getPrimaryColor(this, role);
        int lightTint = ThemeManager.getLightTintColor(this, role);

        // ── Header ─────────────────────────────────────────────────────────
        View header = findViewById(R.id.about_header);
        ThemeApplier.applyHeader(this, role, header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        // ── Back ────────────────────────────────────────────────────────────
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // ── App name color ──────────────────────────────────────────────────
        TextView tvAppName = findViewById(R.id.tv_app_name);
        if (tvAppName != null) tvAppName.setTextColor(primary);

        // ── Apply theme to all tagged views ─────────────────────────────────
        View root = findViewById(android.R.id.content);
        applyThemeToTaggedViews((ViewGroup) root, primary, lightTint);

        // ── Contact text views ──────────────────────────────────────────────
        ((TextView) findViewById(R.id.tv_support_contact)).setTextColor(primary);
        ((TextView) findViewById(R.id.tv_support_contact)).setText(EMAIL_SUPPORT);

        ((TextView) findViewById(R.id.tv_phone_number)).setTextColor(primary);
        ((TextView) findViewById(R.id.tv_phone_number)).setText("+63 956 654 0246");

        ((TextView) findViewById(R.id.tv_facebook_page)).setTextColor(primary);
        ((TextView) findViewById(R.id.tv_facebook_page)).setText("facebook.com/attendify");

        // ── Legal rows ──────────────────────────────────────────────────────
        findViewById(R.id.row_privacy_policy).setOnClickListener(v -> openUrl(URL_PRIVACY));
        findViewById(R.id.row_terms).setOnClickListener(v -> openUrl(URL_TERMS));

        // ── Help & Support rows ─────────────────────────────────────────────
        findViewById(R.id.row_email).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:" + EMAIL_SUPPORT));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Attendify Support Request");
            startActivity(Intent.createChooser(intent, "Send Email"));
        });

        findViewById(R.id.row_phone).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + PHONE_NUMBER));
            startActivity(intent);
        });

        findViewById(R.id.row_facebook).setOnClickListener(v -> openUrl(URL_FACEBOOK));
    }

    // ── Recursive theme applicator ────────────────────────────────────────────
    // Walks the entire view tree and applies theme colors based on android:tag.
    //   "icon_circle"   → oval lightTint background + primary color filter
    //   "section_label" → primary text color

    private void applyThemeToTaggedViews(ViewGroup parent, int primary, int lightTint) {
        if (parent == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            Object tag = child.getTag();

            if ("icon_circle".equals(tag) && child instanceof ImageView) {
                // Oval light-tint background
                GradientDrawable oval = new GradientDrawable();
                oval.setShape(GradientDrawable.OVAL);
                oval.setColor(lightTint);
                child.setBackground(oval);
                // Primary tint on the icon drawable
                ((ImageView) child).setColorFilter(primary);

            } else if ("section_label".equals(tag) && child instanceof TextView) {
                ((TextView) child).setTextColor(primary);
            }

            // Recurse into ViewGroups
            if (child instanceof ViewGroup) {
                applyThemeToTaggedViews((ViewGroup) child, primary, lightTint);
            }
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}