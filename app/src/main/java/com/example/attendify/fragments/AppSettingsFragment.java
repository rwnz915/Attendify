package com.example.attendify.fragments;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.ThemeManager;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;

public class AppSettingsFragment extends Fragment {

    private String role = "teacher";
    private String selectedTheme;
    private View lastSelectedRow = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_app_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;
        role = user.getRole();
        selectedTheme = ThemeManager.getSavedTheme(requireContext(), role);

        // Apply theme to header
        ThemeApplier.applyHeader(requireContext(), role, view.findViewById(R.id.app_settings_header));

        // Handle system back button
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        navigateBackToProfile();
                    }
                });

        // Back button in layout
        view.findViewById(R.id.btn_back).setOnClickListener(v -> navigateBackToProfile());

        buildThemeList(view);
    }

    private void navigateBackToProfile() {
        requireActivity().getSupportFragmentManager().popBackStack();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity main = (MainActivity) getActivity();
                int returnTab = main.navSourceTab != -1 ? main.navSourceTab : 4;
                main.navSourceTab = -1; // reset after use
                main.currentTab = -1;
                main.selectTab(returnTab);
            }
        }, 200);
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
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_theme_row, container, false);

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
            ThemeManager.saveTheme(requireContext(), role, themeKey);
            ThemeApplier.applyHeader(requireContext(), role, getView().findViewById(R.id.app_settings_header));
            //Toast.makeText(requireContext(), "Theme updated!", Toast.LENGTH_SHORT).show();
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
