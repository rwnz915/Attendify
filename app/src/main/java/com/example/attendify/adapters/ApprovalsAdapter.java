package com.example.attendify.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.R;
import com.example.attendify.models.ApprovalRequest;

import java.util.List;

public class ApprovalsAdapter extends RecyclerView.Adapter<ApprovalsAdapter.ViewHolder> {

    private final List<ApprovalRequest> items;

    public ApprovalsAdapter(List<ApprovalRequest> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_approval_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ApprovalRequest request = items.get(position);
        holder.tvAvatar.setText(request.getInitial());
        holder.tvStudentName.setText(request.getStudentName());
        holder.tvDateRange.setText(request.getDateRange());
        holder.tvReason.setText(request.getReason());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvStudentName, tvDateRange, tvReason;
        TextView btnApprove, btnDecline;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar      = itemView.findViewById(R.id.tv_avatar);
            tvStudentName = itemView.findViewById(R.id.tv_student_name);
            tvDateRange   = itemView.findViewById(R.id.tv_date_range);
            tvReason      = itemView.findViewById(R.id.tv_reason);
            btnApprove    = itemView.findViewById(R.id.btn_approve);
            btnDecline    = itemView.findViewById(R.id.btn_decline);
        }
    }
}