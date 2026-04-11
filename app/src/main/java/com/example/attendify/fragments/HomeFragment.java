package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.MockData;

import java.util.List;

public class HomeFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Shift header down so content starts below the status bar
        View header = view.findViewById(R.id.home_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop(),
                header.getPaddingRight(),
                header.getPaddingBottom());

        view.findViewById(R.id.btn_start).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).selectTab(1);
        });

        LinearLayout container2 = view.findViewById(R.id.recent_activity_container);
        List<AttendanceRecord> records = MockData.getHistory();
        LayoutInflater li = LayoutInflater.from(requireContext());
        for (AttendanceRecord record : records) {
            View card = li.inflate(R.layout.item_activity_record, container2, false);
            ((TextView) card.findViewById(R.id.tv_record_date)).setText(record.getDate());
            ((TextView) card.findViewById(R.id.tv_record_present)).setText(record.getPresent() + " P");
            ((TextView) card.findViewById(R.id.tv_record_absent)).setText(record.getAbsent() + " A");
            container2.addView(card);
        }
    }
}
