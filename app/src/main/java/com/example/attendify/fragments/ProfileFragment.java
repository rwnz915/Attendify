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

public class ProfileFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Expand blue header to cover status bar
        View headerBg = view.findViewById(R.id.profile_header_bg);
        ViewGroup.LayoutParams lp = headerBg.getLayoutParams();
        lp.height = lp.height + MainActivity.statusBarHeight;
        headerBg.setLayoutParams(lp);

        // Populate name and role from the logged-in user
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        ((TextView) view.findViewById(R.id.tv_profile_name)).setText(user.getName());
        // Teachers/secretaries show their role as subtitle — no student ID needed
        ((TextView) view.findViewById(R.id.tv_profile_role)).setText(
                user.isTeacher() ? "Teacher" : "Secretary"
        );

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).logout();
        });
    }
}