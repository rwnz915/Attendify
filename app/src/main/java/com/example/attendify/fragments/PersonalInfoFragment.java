package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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

        // Back button
        view.findViewById(R.id.btn_back).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

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
            ((TextView) view.findViewById(R.id.tv_parent_name)).setText(
                    user.getParentName() != null ? user.getParentName() : "Not provided");
            ((TextView) view.findViewById(R.id.tv_parent_contact)).setText(
                    user.getParentContact() != null ? user.getParentContact() : "Not provided");
        } else {
            parentLayout.setVisibility(View.GONE);
            parentDivider.setVisibility(View.GONE);
        }
    }
}
