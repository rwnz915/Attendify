package com.example.attendify.activities;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.ThemeManager;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;

public class AppSettingsActivity extends AppCompatActivity {

    private String role = "teacher";
    private String selectedTheme;
    private View lastSelectedRow = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_app_settings);

        androidx.activity.EdgeToEdge.enable(this);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) { finish(); return; }
        role = user.getRole();
        selectedTheme = ThemeManager.getSavedTheme(this, role);

        // Apply theme to header
        ThemeApplier.applyHeader(this, role, findViewById(R.id.app_settings_header));

        // Status bar padding
        android.view.View header = findViewById(R.id.app_settings_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        buildThemeList(getWindow().getDecorView().getRootView());
    }

    private void buildThemeList(View root) {
        LinearLayout container = root.findViewById(R.id.ll_theme_list);
        if (container == null) return;
        container.removeAllViews();

        for (String themeKey : ThemeManager.ALL_THEMES) {
            View row = buildThemeRow(themeKey, container);
            container.addView(row);
        }
    }

    private View buildThemeRow(String themeKey, LinearLayout container) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_theme_row, container, false);

        int[] swatches = ThemeManager.getSwatchColors(themeKey);
        setSwatchColor(row, R.id.swatch_1, swatches[0]);
        setSwatchColor(row, R.id.swatch_2, swatches[1]);
        setSwatchColor(row, R.id.swatch_3, swatches[2]);

        TextView tvLabel = row.findViewById(R.id.tv_theme_label);
        TextView tvSub   = row.findViewById(R.id.tv_theme_subtitle);
        tvLabel.setText(ThemeManager.getThemeLabel(themeKey));
        tvSub.setText(ThemeManager.getThemeSubtitle(themeKey));

        ImageView check = row.findViewById(R.id.iv_check);
        boolean isSelected = themeKey.equals(selectedTheme);
        check.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);

        if (isSelected) {
            highlightRow(row, themeKey);
            lastSelectedRow = row;
        }

        row.setOnClickListener(v -> {
            if (lastSelectedRow != null && lastSelectedRow != row) {
                clearRowHighlight(lastSelectedRow);
                lastSelectedRow.findViewById(R.id.iv_check).setVisibility(View.INVISIBLE);
            }

            selectedTheme = themeKey;
            lastSelectedRow = row;
            highlightRow(row, themeKey);
            check.setVisibility(View.VISIBLE);

            // Save and apply immediately to header
            ThemeManager.saveTheme(this, role, themeKey);
            ThemeApplier.applyHeader(this, role, findViewById(R.id.app_settings_header));
        });

        return row;
    }

    private void setSwatchColor(View row, int viewId, int color) {
        View swatch = row.findViewById(viewId);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(6));
        swatch.setBackground(gd);
    }

    private void highlightRow(View row, String themeKey) {
        int lightTint = ThemeManager.getLightTintForTheme(themeKey);
        int primary   = ThemeManager.getPrimaryColorForTheme(themeKey);
        View rowBg = row.findViewById(R.id.row_bg);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(lightTint);
        gd.setCornerRadius(dp(14));
        gd.setStroke(dp(2), primary);
        rowBg.setBackground(gd);
    }

    private void clearRowHighlight(View row) {
        View rowBg = row.findViewById(R.id.row_bg);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFFFFFFFF);
        gd.setCornerRadius(dp(14));
        gd.setStroke(dp(1), 0xFFE5E7EB);
        rowBg.setBackground(gd);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}