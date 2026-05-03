package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;

public class NotificationSettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notification_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        // Apply theme color to switch
        int primary = com.example.attendify.ThemeManager.getPrimaryColor(requireContext(), user.getRole());

        SwitchCompat switchNotifications = view.findViewById(R.id.switch_notifications);

        // Thumb tint
        android.content.res.ColorStateList thumbStates = new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_checked}, // unchecked
                        new int[]{android.R.attr.state_checked}   // checked
                },
                new int[]{
                        0xFFFFFFFF, // white when off
                        primary     // theme color when on
                }
        );

        // Track tint (slightly transparent)
        android.content.res.ColorStateList trackStates = new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_checked},
                        new int[]{android.R.attr.state_checked}
                },
                new int[]{
                        0xFFCBD5E1, // gray when off
                        (primary & 0x00FFFFFF) | 0x66000000 // 40% opacity theme color when on
                }
        );

        switchNotifications.setThumbTintList(thumbStates);
        switchNotifications.setTrackTintList(trackStates);

        // Apply theme
        ThemeApplier.applyHeader(requireContext(), user.getRole(), view.findViewById(R.id.notifications_header));

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

        // Switch logic
        //SwitchCompat switchNotifications = view.findViewById(R.id.switch_notifications);
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String status = isChecked ? "Enabled" : "Disabled";
            //Toast.makeText(requireContext(), "Notifications " + status, Toast.LENGTH_SHORT).show();
            // TODO: Persist this preference if needed (e.g., SharedPreferences or Firestore)
        });
    }

    private void navigateBackToProfile() {
        requireActivity().getSupportFragmentManager().popBackStack();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity main = (MainActivity) getActivity();
                main.currentTab = -1;
                main.selectTab(4);
            }
        }, 200);
    }
}
