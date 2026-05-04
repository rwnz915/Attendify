package com.example.attendify.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.R;
import com.example.attendify.notifications.ClassNotificationScheduler;
import com.example.attendify.models.Student;

import java.util.List;

public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.ViewHolder> {

    private List<Student> students;
    private final Context context;
    private OnStudentClickListener listener;

    public interface OnStudentClickListener {
        void onStudentClick(int position);
    }

    public StudentAdapter(Context context, List<Student> students) {
        this.context = context;
        this.students = students;
    }

    public void setOnStudentClickListener(OnStudentClickListener l) {
        this.listener = l;
    }

    public void updateList(List<Student> newList) {
        this.students = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_student, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Student student = students.get(position);
        holder.tvName.setText(student.getName());
        holder.tvTime.setText(student.getTime());
        holder.tvBadge.setText(student.getStatusLabel());

        // Reset in-school label — will be shown for any status that has a geofence time-in
        if (holder.tvInSchoolTime != null) {
            holder.tvInSchoolTime.setVisibility(android.view.View.GONE);
            holder.tvInSchoolTime.setText("");
        }

        int dotBg, badgeBg, badgeColor;
        switch (student.getStatus()) {
            case Student.STATUS_LATE:
                dotBg      = R.drawable.bg_dot_yellow;
                badgeBg    = R.drawable.bg_badge_late;
                badgeColor = context.getResources().getColor(R.color.yellow_700, context.getTheme());
                break;
            case Student.STATUS_IN_SCHOOL:
                dotBg      = R.drawable.bg_dot_green;
                badgeBg    = R.drawable.bg_badge_present;
                badgeColor = context.getResources().getColor(R.color.green_700, context.getTheme());
                break;
            case Student.STATUS_ABSENT:
                dotBg      = R.drawable.bg_dot_red;
                badgeBg    = R.drawable.bg_badge_absent;
                badgeColor = context.getResources().getColor(R.color.red_700, context.getTheme());
                break;
            default: // PRESENT
                dotBg      = R.drawable.bg_dot_green;
                badgeBg    = R.drawable.bg_badge_present;
                badgeColor = context.getResources().getColor(R.color.green_700, context.getTheme());
                break;
        }

        // Show "In school since H:MM AM" for Present, Late, and In-school statuses —
        // the geofence time-in is always preserved even after QR scan records attendance.
        if (student.getStatus() != Student.STATUS_ABSENT
                && holder.tvInSchoolTime != null
                && student.getStudentId() != null) {
            final int boundStatus = student.getStatus();
            ClassNotificationScheduler.getInstance().getEarliestTimeInToday(
                    context, student.getStudentId(), "",
                    (timeIn, subId) -> {
                        if (timeIn != null && !timeIn.isEmpty()) {
                            String label = "In school since " + formatIso(timeIn);
                            android.os.Handler mainHandler =
                                    new android.os.Handler(android.os.Looper.getMainLooper());
                            mainHandler.post(() -> {
                                int cur = holder.getAdapterPosition();
                                if (cur != androidx.recyclerview.widget.RecyclerView.NO_ID
                                        && cur < students.size()
                                        && students.get(cur).getStatus() == boundStatus) {
                                    holder.tvInSchoolTime.setText(label);
                                    holder.tvInSchoolTime.setVisibility(android.view.View.VISIBLE);
                                }
                            });
                        }
                    });
        }

        holder.statusDot.setBackgroundResource(dotBg);
        holder.tvBadge.setBackgroundResource(badgeBg);
        holder.tvBadge.setTextColor(badgeColor);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onStudentClick(holder.getAdapterPosition());
        });
    }

    private String formatIso(String iso) {
        try {
            java.text.SimpleDateFormat inFmt  = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ENGLISH);
            java.text.SimpleDateFormat outFmt = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH);
            java.util.Date d = inFmt.parse(iso);
            return d != null ? outFmt.format(d) : iso;
        } catch (Exception e) {
            return iso;
        }
    }

    @Override
    public int getItemCount() { return students.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTime, tvInSchoolTime, tvBadge;
        View statusDot;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName       = itemView.findViewById(R.id.tv_student_name);
            tvTime       = itemView.findViewById(R.id.tv_student_time);
            tvInSchoolTime = itemView.findViewById(R.id.tv_in_school_time);
            tvBadge      = itemView.findViewById(R.id.tv_status_badge);
            statusDot    = itemView.findViewById(R.id.status_dot);
        }
    }
}