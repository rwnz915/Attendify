package com.example.attendify.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.R;
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

        int dotBg, badgeBg, badgeColor;
        switch (student.getStatus()) {
            case Student.STATUS_LATE:
                dotBg     = R.drawable.bg_dot_yellow;
                badgeBg   = R.drawable.bg_badge_late;
                badgeColor = context.getResources().getColor(R.color.yellow_700, context.getTheme());
                break;
            case Student.STATUS_ABSENT:
                dotBg     = R.drawable.bg_dot_red;
                badgeBg   = R.drawable.bg_badge_absent;
                badgeColor = context.getResources().getColor(R.color.red_700, context.getTheme());
                break;
            default: // PRESENT
                dotBg     = R.drawable.bg_dot_green;
                badgeBg   = R.drawable.bg_badge_present;
                badgeColor = context.getResources().getColor(R.color.green_700, context.getTheme());
                break;
        }

        holder.statusDot.setBackgroundResource(dotBg);
        holder.tvBadge.setBackgroundResource(badgeBg);
        holder.tvBadge.setTextColor(badgeColor);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onStudentClick(holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() { return students.size(); }

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
