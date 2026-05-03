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

public class PersonalInfoFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_personal_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        // Apply theme to header
        ThemeApplier.applyHeader(requireContext(), user.getRole(), view.findViewById(R.id.personal_info_header));

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

        // Fill data
        ((TextView) view.findViewById(R.id.tv_full_name)).setText(user.getFullName());
        ((TextView) view.findViewById(R.id.tv_email)).setText(user.getEmail());

        String sectionText = "N/A";
        if (user.isStudent() || user.isSecretary()) {
            sectionText = user.getSection() != null ? user.getSection() : "N/A";
        } else if (user.isTeacher() && user.getSections() != null && !user.getSections().isEmpty()) {
            sectionText = String.join(", ", user.getSections());
        }
        ((TextView) view.findViewById(R.id.tv_section)).setText(sectionText);

        // Parent Info (Student only)
        View parentLayout = view.findViewById(R.id.layout_parent_info);
        View parentDivider = view.findViewById(R.id.divider_parent);
        if (user.isStudent()) {
            parentLayout.setVisibility(View.VISIBLE);
            parentDivider.setVisibility(View.VISIBLE);

            // Fetch from parents/{uid} collection
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("parents")
                    .document(user.getId())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String parentName    = doc.getString("parent");
                            String parentContact = doc.getString("contact");

                            TextView tvParentName    = view.findViewById(R.id.tv_parent_name);
                            TextView tvParentContact = view.findViewById(R.id.tv_parent_contact);

                            if (tvParentName != null)
                                tvParentName.setText(parentName != null ? parentName : "Not provided");
                            if (tvParentContact != null)
                                tvParentContact.setText(parentContact != null ? parentContact : "Not provided");
                        }
                    })
                    .addOnFailureListener(e -> {
                        // leave "Not provided" as default
                    });

            // Set defaults while loading
            ((TextView) view.findViewById(R.id.tv_parent_name)).setText("Loading...");
            ((TextView) view.findViewById(R.id.tv_parent_contact)).setText("Loading...");

        } else {
            parentLayout.setVisibility(View.GONE);
            parentDivider.setVisibility(View.GONE);
        }
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
