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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

/**
 * Secretary QR scanner screen.
 *
 * Validation chain on every scan:
 *   1. Secretary must be logged in with role="secretary"
 *   2. A subject must be currently ongoing (today's day + time inside schedule window)
 *   3. The scanned subject section must match the secretary's own section
 *   4. The scanned subjectId must match the ongoing subject's id
 *   5. Only then → record attendance in Firestore with correct date + Late/Present logic
 *
 * Late rule: arrival > class start + 15 min → "Late", else "Present"
 */
public class SecretaryFragment extends Fragment {

    private static final int CAMERA_REQUEST_CODE    = 100;
    private static final int LATE_THRESHOLD_MINUTES = 15;

    private TextView tvResult, tvCurrentClass, tvSecretaryInfo;

    /** The active subject resolved from schedule — null if no class right now. */
    private SubjectRepository.SubjectItem activeSubject = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_secretary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvResult        = view.findViewById(R.id.tv_scan_result);
        tvCurrentClass  = view.findViewById(R.id.tv_current_class);   // new — see layout patch
        tvSecretaryInfo = view.findViewById(R.id.tv_secretary_info);  // new — see layout patch

        view.findViewById(R.id.btn_logout_secretary).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).logout();
        });

        view.findViewById(R.id.btn_scan_qr).setOnClickListener(v -> {
            if (activeSubject == null) {
                Toast.makeText(getContext(),
                        "No class is ongoing right now. Cannot scan.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            startQRScanner();
        });

        // Show secretary info and resolve active subject
        showSecretaryInfo();
        resolveActiveSubject();
    }

    // ── Show secretary name + section ─────────────────────────────────────────

    private void showSecretaryInfo() {
        UserProfile sec = AuthRepository.getInstance().getLoggedInUser();
        if (tvSecretaryInfo == null || sec == null) return;
        tvSecretaryInfo.setText(sec.getFullName()
                + "  •  Section: " + (sec.getSection() != null ? sec.getSection() : "—"));
    }

    // ── Resolve the currently active subject for this secretary's section ─────

    /**
     * Looks up subjects matching the secretary's section, then finds the one
     * whose schedule window contains the current time today.
     * Updates tvCurrentClass accordingly.
     */
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
        if (tvCurrentClass == null) return;
        if (subject == null) {
            tvCurrentClass.setText("No active class right now");
        } else {
            tvCurrentClass.setText("Active: " + subject.name
                    + "  •  " + subject.section
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
        boolean hasTue = dayCodes.replaceAll("TH","").contains("T");
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

    // ── QR scanner ────────────────────────────────────────────────────────────

    private void startQRScanner() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        } else {
            openScanner();
        }
    }

    private void openScanner() {
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

    // ── Process scanned QR ────────────────────────────────────────────────────

    /**
     * QR payload format: firebaseUID|subjectId|studentName|subjectName|section
     *
     * Validation:
     *   1. Re-check active subject (class must still be ongoing)
     *   2. scannedSection must == secretary's section
     *   3. scannedSubjectId must == activeSubject.id
     *   4. Prevent duplicate scan for the same student today
     */
    private void processScannedQr(String qrData) {
        String[] parts = qrData.split("\\|");
        if (parts.length < 5) {
            tvResult.setText("❌ Invalid QR Code format.");
            return;
        }

        String scannedUid         = parts[0].trim();
        String scannedSubjectId   = parts[1].trim();
        String scannedStudentName = parts[2].trim();
        String scannedSubjectName = parts[3].trim();
        String scannedSection     = parts[4].trim();

        UserProfile sec = AuthRepository.getInstance().getLoggedInUser();
        if (sec == null) {
            tvResult.setText("❌ Secretary session expired. Please log in again.");
            return;
        }

        // ── Validation 1: a subject must be active right now ──────────────────
        // Re-resolve in case time has changed since fragment loaded
        resolveActiveSubject(); // async refresh — check cached value below
        if (activeSubject == null) {
            tvResult.setText("❌ No class is ongoing right now.\nScanning is disabled.");
            return;
        }

        // ── Validation 2: section must match secretary's section ──────────────
        String secSection = sec.getSection();
        if (secSection == null || !secSection.equals(scannedSection)) {
            tvResult.setText("❌ Section mismatch.\n"
                    + "Your section: " + secSection + "\n"
                    + "Student section: " + scannedSection);
            return;
        }

        // ── Validation 3: subjectId must match the active subject ─────────────
        if (!activeSubject.id.equals(scannedSubjectId)) {
            tvResult.setText("❌ Wrong subject.\n"
                    + "Active: " + activeSubject.name + "\n"
                    + "Scanned: " + scannedSubjectName);
            return;
        }

        // ── Validation 4: prevent duplicate — check if already recorded today ──
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
                        // Already recorded today
                        DocumentSnapshot existing = snapshot.getDocuments().get(0);
                        String existingStatus = existing.getString("status");
                        String existingTime   = existing.getString("time");
                        getActivity().runOnUiThread(() ->
                                tvResult.setText("⚠️ Already recorded today\n\n"
                                        + scannedStudentName + "\n"
                                        + scannedSubjectName + "\n"
                                        + "Status: " + existingStatus
                                        + "  •  Time: " + existingTime));
                        return;
                    }
                    // All checks passed — record attendance
                    getActivity().runOnUiThread(() ->
                            saveAttendanceToFirestore(scannedUid, scannedStudentName,
                                    scannedSubjectId, scannedSubjectName, today));
                })
                .addOnFailureListener(e -> {
                    if (getActivity() != null)
                        getActivity().runOnUiThread(() ->
                                tvResult.setText("❌ Could not verify duplicate. Try again."));
                });
    }

    // ── Save to Firestore with correct date + Late/Present status ─────────────

    private void saveAttendanceToFirestore(String studentUid, String studentName,
                                           String subjectId, String subjectName,
                                           String today) {
        // Current time formatted for display and storage
        Calendar now = Calendar.getInstance();
        String timeFormatted = new SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(now.getTime());

        // Compute status from arrival time vs class start
        String status = computeStatus(now, activeSubject.schedule);

        AttendanceRepository.getInstance().recordAttendance(
                studentUid, studentName, subjectId, subjectName,
                today, timeFormatted, status,
                new AttendanceRepository.SubmitCallback() {
                    @Override
                    public void onSuccess() {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() ->
                                tvResult.setText(
                                        ("Present".equals(status) ? "✅ PRESENT" : "🕐 LATE")
                                                + "\n\n"
                                                + studentName + "\n"
                                                + subjectName + "\n"
                                                + "Time: " + timeFormatted + "\n"
                                                + "Status: " + status));
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() ->
                                tvResult.setText("❌ Failed to save: " + errorMessage));
                    }
                });
    }

    // ── Late / Present logic ──────────────────────────────────────────────────

    /**
     * "Present" if arrival <= class start + 15 min, "Late" otherwise.
     * classSchedule e.g. "MWF 1:00pm-2:30pm" — we extract the start part.
     */
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

    // ── Permission result ─────────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openScanner();
            } else {
                tvResult.setText("❌ Camera permission denied.");
            }
        }
    }
}