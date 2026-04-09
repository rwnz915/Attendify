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
import com.example.attendify.models.AttendanceRecord;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<AttendanceRecord> records;
    private final Context context;

    public HistoryAdapter(Context context, List<AttendanceRecord> records) {
        this.context = context;
        this.records = records;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_history_record, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceRecord rec = records.get(position);
        holder.tvDate.setText(rec.getDate());
        holder.tvLegendPresent.setText(rec.getPresent() + " Present");
        holder.tvLegendLate.setText(rec.getLate() + " Late");
        holder.tvLegendAbsent.setText(rec.getAbsent() + " Absent");

        // Set progress bar weights
        int total = rec.getTotal();
        if (total > 0) {
            setWeight(holder.progressPresent, rec.getPresent(), total);
            setWeight(holder.progressLate,    rec.getLate(),    total);
            setWeight(holder.progressAbsent,  rec.getAbsent(),  total);
        }
    }

    private void setWeight(View view, int value, int total) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.weight = (float) value / total;
        params.width = 0;
        view.setLayoutParams(params);
    }

    @Override
    public int getItemCount() { return records.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvLegendPresent, tvLegendLate, tvLegendAbsent;
        View progressPresent, progressLate, progressAbsent;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate           = itemView.findViewById(R.id.tv_history_date);
            tvLegendPresent  = itemView.findViewById(R.id.tv_legend_present);
            tvLegendLate     = itemView.findViewById(R.id.tv_legend_late);
            tvLegendAbsent   = itemView.findViewById(R.id.tv_legend_absent);
            progressPresent  = itemView.findViewById(R.id.progress_present);
            progressLate     = itemView.findViewById(R.id.progress_late);
            progressAbsent   = itemView.findViewById(R.id.progress_absent);
        }
    }
}
