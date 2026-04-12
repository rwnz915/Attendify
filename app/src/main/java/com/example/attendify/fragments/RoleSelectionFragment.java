package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.R;
import com.example.attendify.RoleSelectionActivity;

public class RoleSelectionFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_role_selection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!(getActivity() instanceof RoleSelectionActivity)) return;
        RoleSelectionActivity activity = (RoleSelectionActivity) getActivity();

        view.findViewById(R.id.card_teacher).setOnClickListener(v ->
                activity.onRoleSelected("teacher"));

        view.findViewById(R.id.card_student).setOnClickListener(v ->
                activity.onRoleSelected("student"));

        view.findViewById(R.id.card_secretary).setOnClickListener(v ->
                activity.onRoleSelected("secretary"));
    }
}