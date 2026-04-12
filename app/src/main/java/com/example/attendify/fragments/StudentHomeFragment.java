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
import com.example.attendify.models.MockData;
import com.example.attendify.models.Student;

import java.util.List;

public class StudentHomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── 1. Student name (hardcoded to the logged-in student for now) ──────
        String studentName = "Morandarte, Renz";
        ((TextView) view.findViewById(R.id.tv_student_name)).setText(studentName);

        // ── 2. Compute this student's stats from MockData ─────────────────────
        //    Filter records that belong to this student
        List<Student> allStudents = MockData.getStudents();

        // Find this student's status in today's list
        int presentCount = 0, lateCount = 0, absentCount = 0;
        for (Student s : allStudents) {
            if (s.getName().equals(studentName)) {
                switch (s.getStatus()) {
                    case Student.STATUS_PRESENT: presentCount++; break;
                    case Student.STATUS_LATE:    lateCount++;    break;
                    case Student.STATUS_ABSENT:  absentCount++;  break;
                }
            }
        }

        // Use history to compute cumulative stats for this student
        // (MockData.getHistory() gives class-wide records; we derive the
        //  student's personal rate from the history list size as total sessions)
        List<AttendanceRecord> history = MockData.getStudentHistory(studentName);
        int totalSessions = history.size();
        int totalPresent  = 0, totalLate = 0, totalAbsent = 0;
        for (AttendanceRecord r : history) {
            totalPresent += r.getPresent();  // reused as "was present" flag (1 or 0)
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
        ((TextView) view.findViewById(R.id.tv_present_count))
                .setText(String.valueOf(totalPresent));

        ((TextView) view.findViewById(R.id.tv_late_count))
                .setText(String.valueOf(totalLate));

        ((TextView) view.findViewById(R.id.tv_absent_count))
                .setText(String.valueOf(totalAbsent));

        // ── 5. Recent attendance list (same pattern as HomeFragment) ──────────
        LinearLayout attendanceList = view.findViewById(R.id.attendance_list);
        attendanceList.removeAllViews();

        LayoutInflater li = LayoutInflater.from(requireContext());
        for (AttendanceRecord record : history) {
            View item = li.inflate(R.layout.item_student_attendance_record, attendanceList, false);
            ((TextView) item.findViewById(R.id.tv_record_date))
                    .setText(record.getDate());
            ((TextView) item.findViewById(R.id.tv_record_subject))
                    .setText(record.getSubject());
            ((TextView) item.findViewById(R.id.tv_record_time))
                    .setText(record.getTime());
            ((TextView) item.findViewById(R.id.tv_record_status))
                    .setText(record.getStatusLabel());

            // Color the status badge
            TextView tvStatus = item.findViewById(R.id.tv_record_status);
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