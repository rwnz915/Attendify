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

public class StudentProfileFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Expand blue header to cover status bar
        View headerBg = view.findViewById(R.id.profile_header_bg);
        ViewGroup.LayoutParams lp = headerBg.getLayoutParams();
        lp.height = lp.height + MainActivity.statusBarHeight;
        headerBg.setLayoutParams(lp);

        // Populate from the logged-in student
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        ((TextView) view.findViewById(R.id.tv_profile_name)).setText(user.getName());
        ((TextView) view.findViewById(R.id.tv_profile_section)).setText(user.getSection());

        // Student ID is only present for students
        TextView tvStudentId = view.findViewById(R.id.tv_profile_student_id);
        if (user.getStudentId() != null) {
            tvStudentId.setVisibility(View.VISIBLE);
            tvStudentId.setText(user.getStudentId());
        } else {
            tvStudentId.setVisibility(View.GONE);
        }

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).logout();
        });
    }
}