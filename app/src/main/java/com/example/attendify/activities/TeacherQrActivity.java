package com.example.attendify.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.ThemeManager;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.SubjectRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Teacher QR scanner activity.
 *
 * Teachers can scan a student's QR code to record attendance for
 * whichever of the teacher's own subjects is currently active.
 * UI mirrors SecretaryQrFragment with the teacher's theme applied.
 */
public class TeacherQrActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE    = 100;
    private static final int LATE_THRESHOLD_MINUTES = 15;

    private TextView     tvActiveClass;
    private TextView     tvMessage;
    private TextView     tvResultStatus, tvResultName, tvResultSubject, tvResultTime;
    private CardView     cardResult;
    private MaterialButton btnScan;

    private SubjectRepository.SubjectItem activeSubject = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_qr);

        androidx.activity.EdgeToEdge.enable(this);

        // Apply teacher theme to header
        UserProfile me = AuthRepository.getInstance().getLoggedInUser();
        String role = me != null ? me.getRole() : "teacher";
        ThemeApplier.applyHeader(this, role, findViewById(R.id.teacher_qr_header));

        // Apply themed corner brackets and scan button
        applyThemeAccent(role);

        // Back button
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Wire views
        tvActiveClass   = findViewById(R.id.tv_qr_active_class);
        tvMessage       = findViewById(R.id.tv_qr_message);
        cardResult      = findViewById(R.id.card_scan_result);
        tvResultStatus  = findViewById(R.id.tv_result_status_label);
        tvResultName    = findViewById(R.id.tv_result_student_name);
        tvResultSubject = findViewById(R.id.tv_result_subject);
        tvResultTime    = findViewById(R.id.tv_result_time);

        btnScan = findViewById(R.id.btn_open_scanner);
        btnScan.setOnClickListener(v -> {
            if (activeSubject == null) {
                showMessage("No class is ongoing right now. Scanning is disabled.");
                return;
            }
            startQRScanner();
        });

        resolveActiveSubject();
    }

    // ── Active subject ────────────────────────────────────────────────────────

    private void resolveActiveSubject() {
        UserProfile teacher = AuthRepository.getInstance().getLoggedInUser();
        if (teacher == null) {
            updateClassLabel(null);
            return;
        }

        SubjectRepository.getInstance().getTeacherSubjects(teacher.getId(),
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                        SubjectRepository.SubjectItem found = findActiveSubject(subjects);
                        runOnUiThread(() -> {
                            activeSubject = found;
                            updateClassLabel(found);
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() -> updateClassLabel(null));
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

    // ── Schedule helpers ──────────────────────────────────────────────────────

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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        } else {
            openScanner();
        }
    }

    private void openScanner() {
        hideResult();
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Scan Student QR Code");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(true);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            processScannedQr(result.getContents());
        }
    }

    // ── Process QR ────────────────────────────────────────────────────────────

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

        UserProfile teacher = AuthRepository.getInstance().getLoggedInUser();
        if (teacher == null) {
            showMessage("Session expired. Please log in again.");
            return;
        }

        // Re-resolve active subject synchronously (best effort — it was loaded in onCreate)
        if (activeSubject == null) {
            showMessage("No class is ongoing right now.\nScanning is disabled.");
            return;
        }

        // Verify the scanned subject belongs to this teacher's active class
        if (!activeSubject.id.equals(scannedSubjectId)) {
            showMessage("Wrong subject.\nActive: " + activeSubject.name
                    + "\nScanned: " + scannedSubjectName);
            return;
        }

        // Verify section matches
        if (activeSubject.section != null && !activeSubject.section.equals(scannedSection)) {
            showMessage("Section mismatch.\nActive: " + activeSubject.section
                    + "\nStudent: " + scannedSection);
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
                    if (!snapshot.isEmpty()) {
                        DocumentSnapshot existing = snapshot.getDocuments().get(0);
                        String existingTime = existing.getString("time");
                        runOnUiThread(() ->
                                showResultCard("⚠️ Already Recorded",
                                        scannedStudentName, scannedSubjectName,
                                        existingTime, false));
                        return;
                    }
                    runOnUiThread(() ->
                            saveAttendance(scannedUid, scannedStudentName,
                                    scannedSubjectId, scannedSubjectName, today));
                })
                .addOnFailureListener(e ->
                        runOnUiThread(() ->
                                showMessage("❌ Could not verify duplicate. Try again.")));
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
                        boolean isPresent = "Present".equals(status);
                        runOnUiThread(() ->
                                showResultCard(
                                        isPresent ? "PRESENT" : "LATE",
                                        name, subjectName, timeFormatted, isPresent));
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() ->
                                showMessage("Failed to save: " + errorMessage));
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
        if (tvMessage  != null) tvMessage.setVisibility(View.GONE);
        if (cardResult != null) cardResult.setVisibility(View.VISIBLE);

        int color = isGreen
                ? getColor(R.color.green_700)
                : getColor(R.color.orange_600);

        if (tvResultStatus  != null) {
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

    private void applyThemeAccent(String role) {
        int color = ThemeManager.getPrimaryColor(this, role);

        int[] cornerIds = {
                R.id.qr_corner_tl_h, R.id.qr_corner_tl_v,
                R.id.qr_corner_tr_h, R.id.qr_corner_tr_v,
                R.id.qr_corner_bl_h, R.id.qr_corner_bl_v,
                R.id.qr_corner_br_h, R.id.qr_corner_br_v
        };
        for (int id : cornerIds) {
            View corner = findViewById(id);
            if (corner != null) corner.setBackgroundColor(color);
        }

        MaterialButton btn = findViewById(R.id.btn_open_scanner);
        if (btn != null) {
            btn.setBackgroundTintList(null);
            ThemeApplier.applyButton(this, role, btn);
        }
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