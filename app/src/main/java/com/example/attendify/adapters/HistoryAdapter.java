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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(AttendanceRecord record);
    }

    private final List<AttendanceRecord> records;
    private final Context context;
    private OnItemClickListener clickListener;
    private final boolean useArrowLayout;

    // Original constructor — keeps existing behavior unchanged
    public HistoryAdapter(Context context, List<AttendanceRecord> records) {
        this(context, records, false);
    }

    // New constructor — pass true to use arrow layout
    public HistoryAdapter(Context context, List<AttendanceRecord> records, boolean useArrowLayout) {
        this.context        = context;
        this.records        = records;
        this.useArrowLayout = useArrowLayout;
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.clickListener = l;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = useArrowLayout
                ? R.layout.item_history_record_arrow
                : R.layout.item_history_record;
        View v = LayoutInflater.from(context).inflate(layout, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceRecord rec = records.get(position);

        // Format: "May 2 - Subject Name"
        String dateLabel = formatDate(rec.getDate());
        String subject = rec.getSubject();
        if (subject != null && !subject.isEmpty()) {
            dateLabel = dateLabel + " - " + subject;
        }
        holder.tvDate.setText(dateLabel);

        holder.tvLegendPresent.setText(rec.getPresent() + " Present");
        holder.tvLegendLate.setText(rec.getLate() + " Late");
        holder.tvLegendAbsent.setText(rec.getAbsent() + " Absent");

        int total = rec.getTotal();
        if (total > 0) {
            setWeight(holder.progressPresent, rec.getPresent(), total);
            setWeight(holder.progressLate,    rec.getLate(),    total);
            setWeight(holder.progressAbsent,  rec.getAbsent(),  total);
        }

        if (clickListener != null) {
            holder.itemView.setOnClickListener(v -> clickListener.onItemClick(rec));
            holder.itemView.setClickable(true);
            holder.itemView.setFocusable(true);
        }
    }

    private String formatDate(String dateStr) {
        try {
            SimpleDateFormat sdfIn  = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            SimpleDateFormat sdfOut = new SimpleDateFormat("MMMM d",     Locale.ENGLISH);
            Date d = sdfIn.parse(dateStr);
            return sdfOut.format(d);
        } catch (ParseException | NullPointerException e) {
            return dateStr != null ? dateStr : "";
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
            tvDate          = itemView.findViewById(R.id.tv_history_date);
            tvLegendPresent = itemView.findViewById(R.id.tv_legend_present);
            tvLegendLate    = itemView.findViewById(R.id.tv_legend_late);
            tvLegendAbsent  = itemView.findViewById(R.id.tv_legend_absent);
            progressPresent = itemView.findViewById(R.id.progress_present);
            progressLate    = itemView.findViewById(R.id.progress_late);
            progressAbsent  = itemView.findViewById(R.id.progress_absent);
        }
    }
}