package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
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
            android.widget.ImageView iv = view.findViewById(id);
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
}