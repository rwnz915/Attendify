package com.example.attendify.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.R;
import com.example.attendify.models.AttendanceRecord;

import java.util.List;

/**
 * Adapter for showing students present on a specific date.
 * Shows: Last, First name | time scanned | Present/Late badge
 */
public class StudentPresentAdapter extends RecyclerView.Adapter<StudentPresentAdapter.ViewHolder> {

    private final List<AttendanceRecord> records;
    private final Context context;

    public StudentPresentAdapter(Context context, List<AttendanceRecord> records) {
        this.context = context;
        this.records = records;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_student, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceRecord rec = records.get(position);

        holder.tvName.setText(rec.getStudentName() != null ? rec.getStudentName() : "");

        // Show time scanned
        String timeText = rec.getTime() != null && !rec.getTime().isEmpty() ? rec.getTime() : "--:--";
        holder.tvTime.setText(timeText);

        // Status badge
        String status = rec.getStatusLabel();
        holder.tvBadge.setText(status != null ? status : "");

        int dotBg, badgeBg, badgeColor;
        if ("Late".equals(status)) {
            dotBg      = R.drawable.bg_dot_yellow;
            badgeBg    = R.drawable.bg_badge_late;
            badgeColor = context.getResources().getColor(R.color.yellow_700, context.getTheme());
        } else {
            dotBg      = R.drawable.bg_dot_green;
            badgeBg    = R.drawable.bg_badge_present;
            badgeColor = context.getResources().getColor(R.color.green_700, context.getTheme());
        }

        holder.statusDot.setBackgroundResource(dotBg);
        holder.tvBadge.setBackgroundResource(badgeBg);
        holder.tvBadge.setTextColor(badgeColor);
    }

    @Override
    public int getItemCount() { return records.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTime, tvBadge;
        View statusDot;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName    = itemView.findViewById(R.id.tv_student_name);
            tvTime    = itemView.findViewById(R.id.tv_student_time);
            tvBadge   = itemView.findViewById(R.id.tv_status_badge);
            statusDot = itemView.findViewById(R.id.status_dot);
        }
    }
}