package com.example.attendify.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.activities.AboutActivity;
import com.example.attendify.activities.AppSettingsActivity;
import com.example.attendify.activities.NotificationSettingsActivity;
import com.example.attendify.activities.PersonalInfoActivity;
import com.example.attendify.activities.SecuritySettingsActivity;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.ThemeApplier;

public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Apply saved theme to header
        UserProfile profileUser = AuthRepository.getInstance().getLoggedInUser();
        String profileRole = profileUser != null ? profileUser.getRole() : "teacher";
        ThemeApplier.applyHeader(requireContext(), profileRole, view.findViewById(R.id.profile_header_bg));

        // Apply theme to avatar circle
        ThemeApplier.applyOval(requireContext(), profileRole, view.findViewById(R.id.iv_profile_avatar));

        // Apply theme tint to profile card icons
        int primary = com.example.attendify.ThemeManager.getPrimaryColor(requireContext(), profileRole);
        int lightTint = com.example.attendify.ThemeManager.getLightTintColor(requireContext(), profileRole);
        int[] iconIds = {R.id.iv_profile_icon_1, R.id.iv_profile_icon_2, R.id.iv_profile_icon_3,
                R.id.iv_profile_icon_4, R.id.iv_profile_icon_5};
        for (int id : iconIds) {
            ImageView iv = view.findViewById(id);
            if (iv != null) {
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                gd.setCornerRadius(50f * requireContext().getResources().getDisplayMetrics().density);
                gd.setColor(lightTint);
                iv.setBackground(gd);
                iv.setColorFilter(primary);
            }
        }

        // Expand blue header to cover status bar
        View headerBg = view.findViewById(R.id.profile_header_bg);
        ViewGroup.LayoutParams lp = headerBg.getLayoutParams();
        lp.height = lp.height + MainActivity.statusBarHeight;
        headerBg.setLayoutParams(lp);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        // Full name from Firestore firstname + lastname
        ((TextView) view.findViewById(R.id.tv_profile_name)).setText(user.getFullName());

        // Role label
        ((TextView) view.findViewById(R.id.tv_profile_role)).setText(
                user.isTeacher() ? "Teacher" : "Secretary");

        // Set labels for menu items
        ((TextView) view.findViewById(R.id.tv_label_5)).setText("About");
        ((ImageView) view.findViewById(R.id.iv_profile_icon_5)).setImageResource(android.R.drawable.ic_dialog_info);

        // Click listeners for menu items
        view.findViewById(R.id.card_personal_info).setOnClickListener(v -> startActivity(new Intent(requireContext(), PersonalInfoActivity.class)));
        view.findViewById(R.id.card_notifications).setOnClickListener(v -> startActivity(new Intent(requireContext(), NotificationSettingsActivity.class)));
        view.findViewById(R.id.card_privacy_security).setOnClickListener(v -> startActivity(new Intent(requireContext(), SecuritySettingsActivity.class)));
        view.findViewById(R.id.card_app_settings).setOnClickListener(v -> startActivity(new Intent(requireContext(), AppSettingsActivity.class)));
        view.findViewById(R.id.card_about).setOnClickListener(v -> startActivity(new Intent(requireContext(), AboutActivity.class)));

        // Show sections the teacher handles if available
        TextView tvSections = view.findViewById(R.id.tv_profile_section);
        if (tvSections != null && user.getSections() != null && !user.getSections().isEmpty()) {
            tvSections.setVisibility(View.VISIBLE);
            tvSections.setText(String.join(", ", user.getSections()));
        } else if (tvSections != null) {
            tvSections.setVisibility(View.GONE);
        }

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).logout();
        });
    }

    // Call this to freeze all interactive elements
    public void setInteractionEnabled(boolean enabled) {
        View view = getView();
        if (view == null) return;
        int[] cards = {
                R.id.card_personal_info,
                R.id.card_notifications,
                R.id.card_privacy_security,
                R.id.card_app_settings,
                R.id.card_about,
                R.id.btn_logout
        };
        for (int id : cards) {
            View v = view.findViewById(id);
            if (v != null) {
                v.setClickable(enabled);
                v.setFocusable(enabled);
            }
        }
        // Block the root view too
        view.setOnTouchListener(enabled ? null : (v, e) -> true);
    }

    @Override
    public void onResume() {
        super.onResume();
        reapplyTheme();
    }

    private void reapplyTheme() {
        View view = getView();
        if (view == null) return;
        UserProfile profileUser = AuthRepository.getInstance().getLoggedInUser();
        String profileRole = profileUser != null ? profileUser.getRole() : "teacher";
        ThemeApplier.applyHeader(requireContext(), profileRole, view.findViewById(R.id.profile_header_bg));
        ThemeApplier.applyOval(requireContext(), profileRole, view.findViewById(R.id.iv_profile_avatar));
        int primary   = com.example.attendify.ThemeManager.getPrimaryColor(requireContext(), profileRole);
        int lightTint = com.example.attendify.ThemeManager.getLightTintColor(requireContext(), profileRole);
        int[] iconIds = {R.id.iv_profile_icon_1, R.id.iv_profile_icon_2, R.id.iv_profile_icon_3,
                R.id.iv_profile_icon_4, R.id.iv_profile_icon_5};
        for (int id : iconIds) {
            ImageView iv = view.findViewById(id);
            if (iv != null) {
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                gd.setCornerRadius(50f * requireContext().getResources().getDisplayMetrics().density);
                gd.setColor(lightTint);
                iv.setBackground(gd);
                iv.setColorFilter(primary);
            }
        }
    }
}