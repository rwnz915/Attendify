package com.example.attendify.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.R;

import java.util.List;

public class MonthHistoryAdapter extends RecyclerView.Adapter<MonthHistoryAdapter.ViewHolder> {

    public static class MonthSummary {
        public String monthYear;
        public String subtitle; // e.g. section name (IT-203) or subject name (Programming)
        public String subjectId; // for teacher: used to pass to detail
        public String section;   // for teacher: section this entry belongs to
        public String subjectName; // for student/secretary
        public int present;
        public int late;
        public int absent;

        public int getTotal() { return present + late + absent; }
    }

    private final List<MonthSummary> summaries;
    private final Context context;
    private OnMonthClickListener listener;

    public interface OnMonthClickListener {
        void onMonthClick(MonthSummary summary);
    }

    public MonthHistoryAdapter(Context context, List<MonthSummary> summaries) {
        this.context = context;
        this.summaries = summaries;
    }

    public void setOnMonthClickListener(OnMonthClickListener l) {
        this.listener = l;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_month_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MonthSummary s = summaries.get(position);

        // Show "May 2026 - IT-203" or "May 2026 - Programming" if subtitle exists
        if (s.subtitle != null && !s.subtitle.isEmpty()) {
            holder.tvName.setText(s.monthYear + " - " + s.subtitle);
        } else {
            holder.tvName.setText(s.monthYear);
        }

        holder.tvStats.setText(s.present + " Present \u2022 " + s.late + " Late \u2022 " + s.absent + " Absent");

        int total = s.getTotal();
        if (total > 0) {
            setWeight(holder.progressPresent, s.present, total);
            setWeight(holder.progressLate,    s.late,    total);
            setWeight(holder.progressAbsent,  s.absent,  total);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onMonthClick(s);
        });
    }

    private void setWeight(View view, int value, int total) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.weight = (float) value / total;
        params.width = 0;
        view.setLayoutParams(params);
    }

    @Override
    public int getItemCount() { return summaries.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvStats;
        View progressPresent, progressLate, progressAbsent;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName           = itemView.findViewById(R.id.tv_month_name);
            tvStats          = itemView.findViewById(R.id.tv_month_stats);
            progressPresent  = itemView.findViewById(R.id.progress_present);
            progressLate     = itemView.findViewById(R.id.progress_late);
            progressAbsent   = itemView.findViewById(R.id.progress_absent);
        }
    }
}