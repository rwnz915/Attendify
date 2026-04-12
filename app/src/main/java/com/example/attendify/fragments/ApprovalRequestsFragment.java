package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.R;
import com.example.attendify.adapters.ApprovalsAdapter;
import com.example.attendify.models.ApprovalRequest;
import com.example.attendify.repository.ApprovalRepository;

import java.util.List;

public class ApprovalRequestsFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_approval_requests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack()
        );

        RecyclerView rv       = view.findViewById(R.id.rv_approvals);
        LinearLayout emptyState = view.findViewById(R.id.tv_empty);

        List<ApprovalRequest> requests = ApprovalRepository.getInstance().getPendingApprovals();

        if (requests.isEmpty()) {
            rv.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            rv.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setAdapter(new ApprovalsAdapter(requests));
        }
    }
}