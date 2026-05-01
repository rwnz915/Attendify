package com.example.attendify.fragments;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.R;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.SubjectRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Shown to the logged-in STUDENT.
 *
 * QR payload format (pipe-separated):
 *   <firebaseAuthUID>|<subjectId>|<studentName>|<subjectName>|<section>
 *
 * Rules:
 *  - QR only generates when there is an active subject right now
 *    (today's day + current time falls inside a subject's schedule window).
 *  - If no subject is ongoing, shows a "No class right now" message instead.
 *  - The student's Firebase Auth UID is embedded — SecretaryFragment uses it
 *    to write the attendance record, so the studentId in Firestore matches
 *    exactly what AttendanceFragment queries.
 */
public class StudentQrFragment extends Fragment {

    private ImageView imgQr;
    private TextView  tvSubject, tvStudent, tvStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_qr, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imgQr     = view.findViewById(R.id.img_qr);
        tvSubject = view.findViewById(R.id.tv_subject);
        tvStudent = view.findViewById(R.id.tv_student);
        tvStatus  = view.findViewById(R.id.tv_qr_status);   // new view — see layout patch

        UserProfile student = AuthRepository.getInstance().getLoggedInUser();
        if (student == null) {
            showError("Not logged in.");
            return;
        }

        // Display student info
        tvStudent.setText(student.getFullName()
                + (student.getStudentID() != null ? "  •  " + student.getStudentID() : ""));

        // Find the active subject for this student's section then generate QR
        String section = student.getSection();
        if (section == null || section.isEmpty()) {
            showError("No section assigned to your account.");
            return;
        }

        SubjectRepository.getInstance().getStudentSubjects(section,
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                        if (getActivity() == null) return;
                        SubjectRepository.SubjectItem active = findActiveSubject(subjects);
                        getActivity().runOnUiThread(() -> {
                            if (active == null) {
                                showError("No class is ongoing right now.");
                            } else {
                                displayQr(student, active);
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() != null)
                            getActivity().runOnUiThread(() -> showError("Failed to load subjects."));
                    }
                });
    }

    // ── Find the currently active subject ─────────────────────────────────────

    private SubjectRepository.SubjectItem findActiveSubject(
            List<SubjectRepository.SubjectItem> subjects) {

        Calendar now = Calendar.getInstance();
        int dow = now.get(Calendar.DAY_OF_WEEK);
        int min = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        for (SubjectRepository.SubjectItem item : subjects) {
            int[] w = parseScheduleWindow(item.schedule, dow);
            if (w != null && min >= w[0] && min <= w[1]) return item;
        }
        return null;
    }

    private int[] parseScheduleWindow(String schedule, int calDow) {
        if (schedule == null || schedule.isEmpty()) return null;
        try {
            String[] parts = schedule.trim().split("\\s+", 2);
            if (parts.length < 2) return null;
            if (!dayMatchesToday(parts[0], calDow)) return null;
            String[] times = parts[1].split("-", 2);
            if (times.length < 2) return null;
            int start = parseTimeToMinutes(times[0].trim());
            int end   = parseTimeToMinutes(times[1].trim());
            if (start < 0 || end < 0) return null;
            return new int[]{start, end};
        } catch (Exception e) { return null; }
    }

    private boolean dayMatchesToday(String dayCodes, int calDow) {
        dayCodes = dayCodes.toUpperCase(Locale.ENGLISH);
        boolean hasThu = dayCodes.contains("TH");
        boolean hasTue = dayCodes.replaceAll("TH", "").contains("T");
        boolean hasSun = dayCodes.contains("SU");
        switch (calDow) {
            case Calendar.MONDAY:    return dayCodes.contains("M");
            case Calendar.TUESDAY:   return hasTue;
            case Calendar.WEDNESDAY: return dayCodes.contains("W");
            case Calendar.THURSDAY:  return hasThu;
            case Calendar.FRIDAY:    return dayCodes.contains("F");
            case Calendar.SATURDAY:  return !hasSun && dayCodes.contains("S");
            case Calendar.SUNDAY:    return hasSun;
            default:                 return false;
        }
    }

    private int parseTimeToMinutes(String t) {
        if (t == null) return -1;
        try {
            t = t.trim().toLowerCase(Locale.ENGLISH);
            boolean pm = t.contains("pm"), am = t.contains("am");
            t = t.replace("pm","").replace("am","").trim();
            String[] hm = t.split(":");
            int h = Integer.parseInt(hm[0].trim());
            int m = Integer.parseInt(hm[1].trim());
            if (pm && h != 12) h += 12;
            if (am && h == 12) h = 0;
            return h * 60 + m;
        } catch (Exception e) { return -1; }
    }

    // ── Generate and show QR ──────────────────────────────────────────────────

    /**
     * QR payload: firebaseUID|subjectId|studentFullName|subjectName|section
     *
     * SecretaryFragment decodes this to:
     *   [0] firebaseUID   → stored as studentId in attendance collection
     *   [1] subjectId     → verified against secretary's active subject
     *   [2] studentName   → stored as studentName
     *   [3] subjectName   → stored as subjectName
     *   [4] section       → verified against secretary's own section
     */
    private void displayQr(UserProfile student, SubjectRepository.SubjectItem subject) {
        String qrPayload = student.getId()
                + "|" + subject.id
                + "|" + student.getFullName()
                + "|" + subject.name
                + "|" + (subject.section != null ? subject.section : "");

        tvSubject.setText(subject.name
                + "  •  " + (subject.section != null ? subject.section : "")
                + "  •  " + (subject.schedule != null ? subject.schedule : ""));

        if (tvStatus != null) tvStatus.setText("Class is ongoing — QR is active");

        // Hide overlay
        View overlay = getView() == null ? null : getView().findViewById(R.id.qr_overlay);
        if (overlay != null) overlay.setVisibility(View.GONE);

        try {
            BitMatrix matrix = new MultiFormatWriter()
                    .encode(qrPayload, BarcodeFormat.QR_CODE, 500, 500);
            Bitmap bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.RGB_565);
            for (int x = 0; x < 500; x++)
                for (int y = 0; y < 500; y++)
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            imgQr.setImageBitmap(bitmap);
        } catch (Exception e) {
            showError("Failed to generate QR.");
        }
    }

    private void showError(String msg) {
        if (tvStatus  != null) tvStatus.setText(msg);
        if (tvSubject != null) tvSubject.setText("");

        // Show overlay over the QR instead of hiding it
        View overlay = getView() == null ? null : getView().findViewById(R.id.qr_overlay);
        if (overlay != null) overlay.setVisibility(View.VISIBLE);
    }
}