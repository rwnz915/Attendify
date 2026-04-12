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

import com.google.android.material.progressindicator.CircularProgressIndicator;

import com.example.attendify.R;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.StudentRepository;

import java.util.List;

public class StudentHomeFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── 1. Get the logged-in student ──────────────────────────────────────
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        String studentName = user.getName();

        ((TextView) view.findViewById(R.id.tv_student_name)).setText(studentName);

        // ── 2. Load this student's session history ────────────────────────────
        List<AttendanceRecord> history =
                StudentRepository.getInstance().getStudentHistory(studentName);

        int totalSessions = history.size();
        int totalPresent = 0, totalLate = 0, totalAbsent = 0;
        for (AttendanceRecord r : history) {
            totalPresent += r.getPresent();
            totalLate    += r.getLate();
            totalAbsent  += r.getAbsent();
        }
        int attendanceRate = totalSessions > 0
                ? Math.round((totalPresent * 100f) / totalSessions) : 0;

        // ── 3. Bind header ────────────────────────────────────────────────────
        ((TextView) view.findViewById(R.id.tv_attendance_rate))
                .setText(attendanceRate + "%");
        ((TextView) view.findViewById(R.id.tv_days_present))
                .setText(totalPresent + " days present");
        ((TextView) view.findViewById(R.id.tv_progress_label))
                .setText(totalPresent + "/" + totalSessions);

        CircularProgressIndicator progress = view.findViewById(R.id.progress_attendance);
        progress.setProgress(attendanceRate);

        // ── 4. Bind stat cards ────────────────────────────────────────────────
        ((TextView) view.findViewById(R.id.tv_present_count)).setText(String.valueOf(totalPresent));
        ((TextView) view.findViewById(R.id.tv_late_count)).setText(String.valueOf(totalLate));
        ((TextView) view.findViewById(R.id.tv_absent_count)).setText(String.valueOf(totalAbsent));

        // ── 5. Recent attendance list ─────────────────────────────────────────
        LinearLayout attendanceList = view.findViewById(R.id.attendance_list);
        attendanceList.removeAllViews();

        LayoutInflater li = LayoutInflater.from(requireContext());
        for (AttendanceRecord record : history) {
            View item = li.inflate(R.layout.item_student_attendance_record, attendanceList, false);
            ((TextView) item.findViewById(R.id.tv_record_date)).setText(record.getDate());
            ((TextView) item.findViewById(R.id.tv_record_subject)).setText(record.getSubject());
            ((TextView) item.findViewById(R.id.tv_record_time)).setText(record.getTime());

            TextView tvStatus = item.findViewById(R.id.tv_record_status);
            tvStatus.setText(record.getStatusLabel());
            switch (record.getStatusLabel()) {
                case "Present":
                    tvStatus.setTextColor(0xFF2E7D32);
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_present);
                    break;
                case "Late":
                    tvStatus.setTextColor(0xFFF57F17);
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_late);
                    break;
                case "Absent":
                    tvStatus.setTextColor(0xFFC62828);
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_absent);
                    break;
            }
            attendanceList.addView(item);
        }
    }
}