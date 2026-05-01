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

public class SecretaryProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_secretary_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Expand green header to cover status bar
        View headerBg = view.findViewById(R.id.sec_profile_header_bg);
        if (headerBg != null) {
            ViewGroup.LayoutParams lp = headerBg.getLayoutParams();
            lp.height += MainActivity.statusBarHeight;
            headerBg.setLayoutParams(lp);
        }

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        ((TextView) view.findViewById(R.id.tv_sec_profile_name)).setText(user.getFullName());

        // "Secretary  •  IT-203"
        String section = user.getSection();
        String roleLabel = "Secretary" + (section != null ? "  \u2022  " + section : "");
        ((TextView) view.findViewById(R.id.tv_sec_profile_role)).setText(roleLabel);

        view.findViewById(R.id.btn_sec_logout).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).logout();
        });
    }
}