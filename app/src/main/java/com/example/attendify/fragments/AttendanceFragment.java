package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.adapters.StudentAdapter;
import com.example.attendify.models.Student;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.StudentRepository;
import com.example.attendify.repository.SubjectRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AttendanceFragment extends Fragment {

    // ── State ─────────────────────────────────────────────────────────────────

    private List<Student> allStudents = new ArrayList<>();
    private StudentAdapter adapter;
    private String currentFilter = "All";

    /** The subject that is currently active based on the real-time clock + schedule. */
    private SubjectRepository.SubjectItem currentSubject = null;

    // ── Views ─────────────────────────────────────────────────────────────────

    private TextView tvSubjectName, tvSubjectInfo;
    private TextView tvPresent, tvLate, tvAbsent;
    private TextView filterAll, filterPresent, filterLate, filterAbsent;
    private TextView tvEmpty;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_attendance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Status-bar padding for the header
        View header = view.findViewById(R.id.attendance_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        // Header subject labels (added via layout update — see fragment_attendance.xml patch)
        tvSubjectName = view.findViewById(R.id.tv_current_subject_name);
        tvSubjectInfo = view.findViewById(R.id.tv_current_subject_info);

        // Stat counts
        tvPresent     = view.findViewById(R.id.tv_present_count);
        tvLate        = view.findViewById(R.id.tv_late_count);
        tvAbsent      = view.findViewById(R.id.tv_absent_count);
        tvEmpty       = view.findViewById(R.id.tv_empty);

        // Filter pills
        filterAll     = view.findViewById(R.id.filter_all);
        filterPresent = view.findViewById(R.id.filter_present);
        filterLate    = view.findViewById(R.id.filter_late);
        filterAbsent  = view.findViewById(R.id.filter_absent);

        // RecyclerView
        recyclerView  = view.findViewById(R.id.rv_students);
        progressBar   = view.findViewById(R.id.progress_bar);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new StudentAdapter(requireContext(), new ArrayList<>());
        adapter.setOnStudentClickListener(position -> {
            List<Student> filtered = getFilteredList();
            if (position >= filtered.size()) return;
            Student tapped = filtered.get(position);
            for (Student s : allStudents) {
                if (s.getId() == tapped.getId()) {
                    s.cycleStatus();
                    break;
                }
            }
            updateStats();
            applyFilter(currentFilter);
        });
        recyclerView.setAdapter(adapter);

        filterAll.setOnClickListener(v     -> applyFilter("All"));
        filterPresent.setOnClickListener(v -> applyFilter("Present"));
        filterLate.setOnClickListener(v    -> applyFilter("Late"));
        filterAbsent.setOnClickListener(v  -> applyFilter("Absent"));

        // Step 1: resolve current subject by time, then load students
        resolveCurrentSubjectAndLoad();
    }

    // ── Step 1: find the subject that matches today's day + current time ──────

    /**
     * Loads all subjects for this teacher, picks the one whose schedule window
     * contains the current time on the current day, then kicks off student loading.
     *
     * Schedule format stored in Firestore: "MWF 8:00-9:30"
     *   • Day codes: M = Monday, T = Tuesday, W = Wednesday, TH = Thursday, F = Friday
     *   • Time range: HH:mm-HH:mm  (24-hour, no AM/PM)
     */
    private void resolveCurrentSubjectAndLoad() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        UserProfile teacher = AuthRepository.getInstance().getLoggedInUser();
        if (teacher == null) {
            showError("Not logged in.");
            return;
        }

        SubjectRepository.getInstance().getTeacherSubjects(teacher.getId(),
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                        if (getActivity() == null) return;

                        SubjectRepository.SubjectItem match = findCurrentSubject(subjects);
                        getActivity().runOnUiThread(() -> {
                            currentSubject = match;
                            updateHeader();
                            if (match == null) {
                                if (progressBar != null) progressBar.setVisibility(View.GONE);
                                showNoCurrentSubject();
                            } else {
                                loadStudentsForSubject(match);
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> showError("Failed to load subjects: " + errorMessage));
                    }
                });
    }

    /**
     * Returns the SubjectItem whose schedule covers the current day and time,
     * or null if none matches.
     *
     * Late threshold: class start + 15 minutes.
     * Being within the window (start to end) = on time or late.
     */
    private SubjectRepository.SubjectItem findCurrentSubject(
            List<SubjectRepository.SubjectItem> subjects) {

        Calendar now = Calendar.getInstance();
        int todayDow  = now.get(Calendar.DAY_OF_WEEK);   // 1=Sun … 7=Sat
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        for (SubjectRepository.SubjectItem item : subjects) {
            if (item.schedule == null || item.schedule.isEmpty()) continue;

            // Split "MWF 8:00-9:30" into ["MWF", "8:00-9:30"]
            String[] parts = item.schedule.trim().split("\\s+", 2);
            if (parts.length < 2) continue;

            String dayCodes = parts[0];   // e.g. "MWF" or "TTH"
            String timeRange = parts[1];  // e.g. "8:00-9:30"

            if (!dayMatchesToday(dayCodes, todayDow)) continue;

            String[] times = timeRange.split("-", 2);
            if (times.length < 2) continue;

            int startMin = parseTimeToMinutes(times[0].trim());
            int endMin   = parseTimeToMinutes(times[1].trim());
            if (startMin < 0 || endMin < 0) continue;

            // Current time must be inside [startMin, endMin]
            if (nowMinutes >= startMin && nowMinutes <= endMin) {
                return item;
            }
        }
        return null;
    }

    /**
     * Maps the day-code string to the current Calendar day-of-week.
     * Supports: M, T, W, TH, F, S, SU  (and their combinations e.g. "MWF", "TTH", "MTWTHFS")
     */
    private boolean dayMatchesToday(String dayCodes, int calDow) {
        // calDow: 1=Sun, 2=Mon, 3=Tue, 4=Wed, 5=Thu, 6=Fri, 7=Sat
        dayCodes = dayCodes.toUpperCase(Locale.ENGLISH);

        // Parse two-letter codes first to avoid ambiguity (TH before T, SU before S)
        boolean hasMon = dayCodes.contains("M");
        boolean hasThu = dayCodes.contains("TH");
        boolean hasTue = !hasThu && dayCodes.contains("T");  // bare T = Tuesday only if no TH
        // Re-check: if string has "TTH" it contains both T and TH
        hasTue = dayCodes.replaceAll("TH", "").contains("T");
        boolean hasWed = dayCodes.contains("W");
        boolean hasFri = dayCodes.contains("F");
        boolean hasSun = dayCodes.contains("SU");
        boolean hasSat = !hasSun && dayCodes.contains("S");

        switch (calDow) {
            case Calendar.MONDAY:    return hasMon;
            case Calendar.TUESDAY:   return hasTue;
            case Calendar.WEDNESDAY: return hasWed;
            case Calendar.THURSDAY:  return hasThu;
            case Calendar.FRIDAY:    return hasFri;
            case Calendar.SATURDAY:  return hasSat;
            case Calendar.SUNDAY:    return hasSun;
            default:                 return false;
        }
    }

    /**
     * Parses "8:00" or "09:30" → minutes from midnight.
     * Returns -1 on parse error.
     */
    /**
     * Parses a time string into minutes-from-midnight.
     * Handles all of these formats:
     *   "8:00"      → 480
     *   "13:00"     → 780
     *   "1:00pm"    → 780
     *   "1:00PM"    → 780
     *   "1:00 PM"   → 780
     *   "8:00am"    → 480
     * Returns -1 on any parse error.
     */
    private int parseTimeToMinutes(String timeStr) {
        try {
            timeStr = timeStr.trim().toLowerCase(Locale.ENGLISH);
            boolean isPm = timeStr.contains("pm");
            boolean isAm = timeStr.contains("am");
            // Strip am/pm suffix so we can parse the digits cleanly
            timeStr = timeStr.replace("pm", "").replace("am", "").trim();

            String[] hm = timeStr.split(":");
            int h = Integer.parseInt(hm[0].trim());
            int m = Integer.parseInt(hm[1].trim());

            // Convert 12-hour → 24-hour
            if (isPm && h != 12) h += 12;
            if (isAm && h == 12) h = 0;

            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    // ── Step 2: update header to show subject name + schedule ────────────────

    private void updateHeader() {
        if (tvSubjectName == null || tvSubjectInfo == null) return;

        if (currentSubject == null) {
            tvSubjectName.setText("No Active Class");
            tvSubjectInfo.setText("No class scheduled right now");
        } else {
            tvSubjectName.setText(currentSubject.name != null ? currentSubject.name : "Attendance");
            String info = (currentSubject.section != null ? currentSubject.section : "")
                    + "  •  "
                    + (currentSubject.schedule != null ? currentSubject.schedule : "");
            tvSubjectInfo.setText(info.trim());
        }
    }

    private void showNoCurrentSubject() {
        allStudents = new ArrayList<>();
        updateStats();
        applyFilter("All");
        tvEmpty.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        tvEmpty.setText("No class is scheduled at this time.");
    }

    // ── Step 3: load the student roster for the subject's section ────────────

    private void loadStudentsForSubject(SubjectRepository.SubjectItem subject) {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        String section = subject.section;
        if (section == null || section.isEmpty()) {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            Toast.makeText(getContext(), "Subject has no section.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Load roster first, then overlay today's attendance records
        StudentRepository.getInstance().getStudentsBySection(section,
                new StudentRepository.StudentsCallback() {
                    @Override
                    public void onSuccess(List<Student> students) {
                        if (getActivity() == null) return;
                        // Step 4: overlay today's real attendance on top of the roster
                        fetchTodayAttendanceAndMerge(students, subject);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> showError("Failed to load students: " + errorMessage));
                    }
                });
    }

    // ── Step 4: fetch today's attendance docs and merge into student list ─────

    /**
     * Queries attendance collection for today + this subjectId.
     * For every record found, updates the matching Student's status + time.
     * Students with no record stay "Absent".
     *
     * Late rule: if the class starts at H:M, any time > H:M+15 is Late.
     * The stored `status` field in Firestore is the source of truth —
     * we use it directly so QR scans / secretary overrides are respected.
     */
    private void fetchTodayAttendanceAndMerge(List<Student> roster,
                                              SubjectRepository.SubjectItem subject) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date());

        FirebaseFirestore.getInstance()
                .collection("attendance")
                .whereEqualTo("subjectId", subject.id)
                .whereEqualTo("date", today)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (getActivity() == null) return;

                    // Build a map: studentId → {status, time} from today's records
                    Map<String, String[]> todayMap = new HashMap<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String studentId = doc.getString("studentId");
                        String status    = doc.getString("status");
                        String time      = doc.getString("time");
                        if (studentId != null && status != null) {
                            todayMap.put(studentId, new String[]{status, time != null ? time : "--:--"});
                        }
                    }

                    // Parse the class start time once (e.g. "1:00pm" from "MWF 1:00pm-2:30pm")
                    int classStartMinutes = parseClassStartMinutes(subject.schedule);

                    // Merge into roster
                    for (Student s : roster) {
                        String sid = s.getStudentId();
                        if (sid != null && todayMap.containsKey(sid)) {
                            String[] entry   = todayMap.get(sid);
                            String dbStatus  = entry[0];
                            String dbTime    = entry[1];

                            // Re-evaluate status from arrival time so wrong stored values
                            // (e.g. "Present" saved when student arrived 19 min late) are corrected.
                            // Only override Present↔Late — keep "Absent" as-is (no arrival time).
                            int statusCode;
                            if ("Absent".equals(dbStatus)) {
                                statusCode = Student.STATUS_ABSENT;
                            } else {
                                // Re-derive from actual arrival time
                                statusCode = resolveStatus(dbTime, classStartMinutes);
                            }
                            s.setStatusFromDb(statusCode, dbTime);
                        }
                        // else: remains STATUS_ABSENT / "--:--" from constructor
                    }

                    getActivity().runOnUiThread(() -> {
                        allStudents = roster;
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        updateStats();
                        applyFilter("All");
                    });
                })
                .addOnFailureListener(e -> {
                    if (getActivity() == null) return;
                    // Even on failure, show the roster (all marked Absent by default)
                    getActivity().runOnUiThread(() -> {
                        allStudents = roster;
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        updateStats();
                        applyFilter("All");
                        Toast.makeText(getContext(),
                                "Could not load today's records. Showing default.", Toast.LENGTH_SHORT).show();
                    });
                });
    }

    // ── Late / Present resolution ─────────────────────────────────────────────

    private static final int LATE_THRESHOLD_MINUTES = 15;

    /**
     * Extracts the class start time from a schedule string like "MWF 1:00pm-2:30pm"
     * and returns it as minutes from midnight. Returns -1 if unparseable.
     */
    private int parseClassStartMinutes(String schedule) {
        if (schedule == null || schedule.isEmpty()) return -1;
        try {
            // e.g. "MWF 1:00pm-2:30pm" → split on space → ["MWF", "1:00pm-2:30pm"]
            String[] parts = schedule.trim().split("\\s+", 2);
            if (parts.length < 2) return -1;
            // take the start half: "1:00pm-2:30pm" → "1:00pm"
            String startPart = parts[1].split("-", 2)[0].trim();
            return parseTimeToMinutes(startPart);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Given a stored arrival time string (e.g. "1:19PM") and the class start in
     * minutes, returns STATUS_PRESENT if within the 15-min grace period, else
     * STATUS_LATE. Falls back to STATUS_PRESENT if the time can't be parsed.
     */
    private int resolveStatus(String arrivalTimeStr, int classStartMinutes) {
        if (classStartMinutes < 0 || arrivalTimeStr == null || "--:--".equals(arrivalTimeStr)) {
            return Student.STATUS_PRESENT; // can't determine — be lenient
        }
        int arrivalMinutes = parseTimeToMinutes(arrivalTimeStr);
        if (arrivalMinutes < 0) return Student.STATUS_PRESENT;

        int minutesLate = arrivalMinutes - classStartMinutes;
        return (minutesLate <= LATE_THRESHOLD_MINUTES)
                ? Student.STATUS_PRESENT
                : Student.STATUS_LATE;
    }

    // ── Filter helpers ────────────────────────────────────────────────────────

    private List<Student> getFilteredList() {
        if ("All".equals(currentFilter)) return new ArrayList<>(allStudents);
        List<Student> result = new ArrayList<>();
        for (Student s : allStudents)
            if (s.getStatusLabel().equals(currentFilter)) result.add(s);
        return result;
    }

    private void applyFilter(String filter) {
        currentFilter = filter;
        TextView[] filters = {filterAll, filterPresent, filterLate, filterAbsent};
        String[]   labels  = {"All", "Present", "Late", "Absent"};
        for (int i = 0; i < filters.length; i++) {
            if (labels[i].equals(filter)) {
                filters[i].setBackgroundResource(R.drawable.bg_filter_active);
                filters[i].setTextColor(requireContext().getResources()
                        .getColor(R.color.white, requireContext().getTheme()));
            } else {
                filters[i].setBackgroundResource(R.drawable.bg_filter_inactive);
                filters[i].setTextColor(requireContext().getResources()
                        .getColor(R.color.gray_600, requireContext().getTheme()));
            }
        }
        List<Student> filtered = getFilteredList();
        adapter.updateList(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        if (filtered.isEmpty() && tvEmpty.getText().toString().isEmpty()) {
            tvEmpty.setText(R.string.no_students);
        }
    }

    private void updateStats() {
        int p = 0, l = 0, a = 0;
        for (Student s : allStudents) {
            switch (s.getStatus()) {
                case Student.STATUS_PRESENT: p++; break;
                case Student.STATUS_LATE:    l++; break;
                case Student.STATUS_ABSENT:  a++; break;
            }
        }
        tvPresent.setText(String.valueOf(p));
        tvLate.setText(String.valueOf(l));
        tvAbsent.setText(String.valueOf(a));
    }

    private void showError(String msg) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    // ── Public accessor (used by HomeFragment to show current subject) ────────

    /** Returns the currently resolved subject, or null if outside class hours. */
    public SubjectRepository.SubjectItem getCurrentSubject() {
        return currentSubject;
    }
}