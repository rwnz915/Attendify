package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;

public class AboutFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        // Apply theme
        ThemeApplier.applyHeader(requireContext(), user.getRole(), view.findViewById(R.id.about_header));

        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).applyNavBarPadding(view.findViewById(R.id.bottom_bar));

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

        // Fill support contact
        ((TextView) view.findViewById(R.id.tv_support_contact)).setText("support@attendify.com");
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
