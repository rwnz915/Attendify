package com.example.attendify.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.ColorStateList;

import com.example.attendify.ThemeManager;
import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.SubjectRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.example.attendify.ThemeApplier;

/**
 * Secretary QR scanner tab.
 *
 * Matches the existing SecretaryFragment scan logic but with a proper
 * polished UI: green header, scan viewfinder, result card.
 */
public class SecretaryQrFragment extends Fragment {

    private static final int CAMERA_REQUEST_CODE    = 100;
    private static final int LATE_THRESHOLD_MINUTES = 15;

    private TextView tvActiveClass, tvMessage;
    private TextView tvResultStatus, tvResultName, tvResultSubject, tvResultTime;
    private CardView cardResult;
    private MaterialButton btnScan;

    private SubjectRepository.SubjectItem activeSubject = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_secretary_qr, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserProfile secQrUser = AuthRepository.getInstance().getLoggedInUser();

        // Apply saved theme to header
        if (secQrUser != null) {
            ThemeApplier.applyHeader(requireContext(), secQrUser.getRole(),
                    view.findViewById(R.id.sec_qr_header));
        }

        // Expand header over status bar
        View header = view.findViewById(R.id.sec_qr_header);
        if (header != null) {
            header.setPadding(
                    header.getPaddingLeft(),
                    header.getPaddingTop() + MainActivity.statusBarHeight,
                    header.getPaddingRight(),
                    header.getPaddingBottom());
        }

        tvActiveClass   = view.findViewById(R.id.tv_qr_active_class);
        tvMessage       = view.findViewById(R.id.tv_qr_message);
        cardResult      = view.findViewById(R.id.card_scan_result);
        tvResultStatus  = view.findViewById(R.id.tv_result_status_label);
        tvResultName    = view.findViewById(R.id.tv_result_student_name);
        tvResultSubject = view.findViewById(R.id.tv_result_subject);
        tvResultTime    = view.findViewById(R.id.tv_result_time);

        btnScan = view.findViewById(R.id.btn_open_scanner);
        btnScan.setOnClickListener(v -> {
            if (activeSubject == null) {
                showMessage("❌ No class is ongoing right now. Scanning is disabled.");
                return;
            }
            startQRScanner();
        });

        // Apply theme accent to QR corners + button
        if (secQrUser != null) {
            applyThemeAccent(view, secQrUser.getRole());
        }

        resolveActiveSubject();
    }

    // ── Active subject ────────────────────────────────────────────────────────

    private void resolveActiveSubject() {
        UserProfile sec = AuthRepository.getInstance().getLoggedInUser();
        if (sec == null || sec.getSection() == null) {
            updateClassLabel(null);
            return;
        }

        SubjectRepository.getInstance().getStudentSubjects(sec.getSection(),
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                        if (getActivity() == null) return;
                        SubjectRepository.SubjectItem found = findActiveSubject(subjects);
                        getActivity().runOnUiThread(() -> {
                            activeSubject = found;
                            updateClassLabel(found);
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() != null)
                            getActivity().runOnUiThread(() -> updateClassLabel(null));
                    }
                });
    }

    private void updateClassLabel(SubjectRepository.SubjectItem subject) {
        if (tvActiveClass == null) return;
        if (subject == null) {
            tvActiveClass.setText("No active class right now");
        } else {
            tvActiveClass.setText(subject.name + "  •  " + subject.section
                    + "  •  " + subject.schedule);
        }
    }

    // ── Schedule helpers (same as original SecretaryFragment) ─────────────────

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
            t = t.replace("pm", "").replace("am", "").trim();
            String[] hm = t.split(":");
            int h = Integer.parseInt(hm[0].trim());
            int m = Integer.parseInt(hm[1].trim());
            if (pm && h != 12) h += 12;
            if (am && h == 12) h = 0;
            return h * 60 + m;
        } catch (Exception e) { return -1; }
    }

    // ── Scanner ───────────────────────────────────────────────────────────────

    private void startQRScanner() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        } else {
            openScanner();
        }
    }

    private void openScanner() {
        hideResult();
        IntentIntegrator integrator = IntentIntegrator.forSupportFragment(this);
        integrator.setPrompt("Scan Student QR Code");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(true);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.initiateScan();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            processScannedQr(result.getContents());
        }
    }

    // ── Process QR (same validation chain as SecretaryFragment) ──────────────

    private void processScannedQr(String qrData) {
        String[] parts = qrData.split("\\|");
        if (parts.length < 5) {
            showMessage("❌ Invalid QR Code format.");
            return;
        }

        String scannedUid         = parts[0].trim();
        String scannedSubjectId   = parts[1].trim();
        String scannedStudentName = parts[2].trim();
        String scannedSubjectName = parts[3].trim();
        String scannedSection     = parts[4].trim();

        UserProfile sec = AuthRepository.getInstance().getLoggedInUser();
        if (sec == null) {
            showMessage("❌ Secretary session expired. Please log in again.");
            return;
        }

        resolveActiveSubject();
        if (activeSubject == null) {
            showMessage("❌ No class is ongoing right now.\nScanning is disabled.");
            return;
        }

        String secSection = sec.getSection();
        if (secSection == null || !secSection.equals(scannedSection)) {
            showMessage("❌ Section mismatch.\nYour section: " + secSection
                    + "\nStudent section: " + scannedSection);
            return;
        }

        if (!activeSubject.id.equals(scannedSubjectId)) {
            showMessage("❌ Wrong subject.\nActive: " + activeSubject.name
                    + "\nScanned: " + scannedSubjectName);
            return;
        }

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date());
        FirebaseFirestore.getInstance()
                .collection("attendance")
                .whereEqualTo("studentId", scannedUid)
                .whereEqualTo("subjectId", scannedSubjectId)
                .whereEqualTo("date", today)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (getActivity() == null) return;
                    if (!snapshot.isEmpty()) {
                        DocumentSnapshot existing = snapshot.getDocuments().get(0);
                        String existingStatus = existing.getString("status");
                        String existingTime   = existing.getString("time");
                        getActivity().runOnUiThread(() ->
                                showResultCard("⚠️ Already Recorded",
                                        scannedStudentName, scannedSubjectName,
                                        existingTime, false));
                        return;
                    }
                    getActivity().runOnUiThread(() ->
                            saveAttendance(scannedUid, scannedStudentName,
                                    scannedSubjectId, scannedSubjectName, today));
                })
                .addOnFailureListener(e -> {
                    if (getActivity() != null)
                        getActivity().runOnUiThread(() ->
                                showMessage("❌ Could not verify duplicate. Try again."));
                });
    }

    private void saveAttendance(String uid, String name, String subjectId,
                                String subjectName, String today) {
        Calendar now = Calendar.getInstance();
        String timeFormatted = new SimpleDateFormat("hh:mm a", Locale.ENGLISH)
                .format(now.getTime());
        String status = computeStatus(now, activeSubject.schedule);

        AttendanceRepository.getInstance().recordAttendance(
                uid, name, subjectId, subjectName,
                today, timeFormatted, status,
                new AttendanceRepository.SubmitCallback() {
                    @Override
                    public void onSuccess() {
                        if (getActivity() == null) return;
                        boolean isPresent = "Present".equals(status);
                        getActivity().runOnUiThread(() ->
                                showResultCard(
                                        isPresent ? "✅ PRESENT" : "🕐 LATE",
                                        name, subjectName, timeFormatted, isPresent));
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() != null)
                            getActivity().runOnUiThread(() ->
                                    showMessage("❌ Failed to save: " + errorMessage));
                    }
                });
    }

    private String computeStatus(Calendar arrival, String classSchedule) {
        if (classSchedule == null) return "Present";
        try {
            String timePart = classSchedule.trim().split("\\s+", 2)[1];
            int startMin = parseTimeToMinutes(timePart.split("-", 2)[0].trim());
            if (startMin < 0) return "Present";
            int nowMin = arrival.get(Calendar.HOUR_OF_DAY) * 60 + arrival.get(Calendar.MINUTE);
            return (nowMin - startMin) <= LATE_THRESHOLD_MINUTES ? "Present" : "Late";
        } catch (Exception e) {
            return "Present";
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void showResultCard(String status, String name, String subject,
                                String time, boolean isGreen) {
        if (tvMessage != null) tvMessage.setVisibility(View.GONE);
        if (cardResult != null) cardResult.setVisibility(View.VISIBLE);

        int color = isGreen
                ? requireContext().getColor(R.color.green_700)
                : requireContext().getColor(R.color.orange_600);

        if (tvResultStatus != null) {
            tvResultStatus.setText(status);
            tvResultStatus.setTextColor(color);
        }
        if (tvResultName    != null) tvResultName.setText(name);
        if (tvResultSubject != null) tvResultSubject.setText(subject);
        if (tvResultTime    != null) tvResultTime.setText(time != null ? time : "—");
    }

    private void showMessage(String msg) {
        if (cardResult != null) cardResult.setVisibility(View.GONE);
        if (tvMessage  != null) {
            tvMessage.setText(msg);
            tvMessage.setVisibility(View.VISIBLE);
        }
    }

    private void hideResult() {
        if (cardResult != null) cardResult.setVisibility(View.GONE);
        if (tvMessage  != null) tvMessage.setVisibility(View.GONE);
    }

    // ── Theme accent ──────────────────────────────────────────────────────────

    // ── Theme accent ──────────────────────────────────────────────────────────

    private void applyThemeAccent(View root, String role) {
        int color = ThemeManager.getPrimaryColor(requireContext(), role);

        int[] cornerIds = {
                R.id.qr_corner_tl_h, R.id.qr_corner_tl_v,
                R.id.qr_corner_tr_h, R.id.qr_corner_tr_v,
                R.id.qr_corner_bl_h, R.id.qr_corner_bl_v,
                R.id.qr_corner_br_h, R.id.qr_corner_br_v
        };
        for (int id : cornerIds) {
            View corner = root.findViewById(id);
            if (corner != null) corner.setBackgroundColor(color);
        }

        // Clear MaterialButton's built-in tint FIRST, then apply gradient
        btnScan.setBackgroundTintList(null);
        ThemeApplier.applyButton(requireContext(), role, btnScan);
    }

    // ── Permission ────────────────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openScanner();
            } else {
                showMessage("❌ Camera permission denied.");
            }
        }
    }
}