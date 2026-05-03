package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

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

        // Apply theme
        ThemeApplier.applyHeader(requireContext(), user.getRole(), view.findViewById(R.id.notifications_header));

        // Back button
        view.findViewById(R.id.btn_back).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        // Switch logic
        SwitchCompat switchNotifications = view.findViewById(R.id.switch_notifications);
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String status = isChecked ? "Enabled" : "Disabled";
            Toast.makeText(requireContext(), "Notifications " + status, Toast.LENGTH_SHORT).show();
            // TODO: Persist this preference if needed (e.g., SharedPreferences or Firestore)
        });
    }
}
