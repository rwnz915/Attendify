package com.example.attendify.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
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
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class SecretaryFragment extends Fragment {

    private static final int CAMERA_REQUEST_CODE = 100;

    private String currentSubject = "MATH101"; // you can make this dynamic later

    private TextView tvResult;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_secretary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvResult = view.findViewById(R.id.tv_scan_result);

        // LOGOUT
        view.findViewById(R.id.btn_logout_secretary).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).logout();
            }
        });

        // SCAN QR
        view.findViewById(R.id.btn_scan_qr).setOnClickListener(v -> {
            startQRScanner();
        });
    }

    // -----------------------------
    // START SCANNER
    // -----------------------------
    private void startQRScanner() {

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_REQUEST_CODE
            );

        } else {
            openScanner();
        }
    }

    // -----------------------------
    // OPEN CAMERA
    // -----------------------------
    private void openScanner() {
        IntentIntegrator integrator = IntentIntegrator.forSupportFragment(this);
        integrator.setPrompt("Scan Student QR Code");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(true);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.initiateScan();
    }

    // -----------------------------
    // SCAN RESULT
    // -----------------------------
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        IntentResult result =
                IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (result != null && result.getContents() != null) {

            String qrData = result.getContents();

            markAttendance(qrData);
        }
    }

    // -----------------------------
    // MARK ATTENDANCE + SUBJECT CHECK
    // -----------------------------
    private void markAttendance(String qrData) {

        // Expected format: STUDENT123|MATH101
        String[] parts = qrData.split("\\|");

        if (parts.length < 2) {
            tvResult.setText("❌ Invalid QR Code");
            return;
        }

        String studentId = parts[0];
        String subject = parts[1];

        // SUBJECT VALIDATION
        if (!subject.equals(currentSubject)) {

            tvResult.setText(
                    "❌ Wrong Subject\n\n" +
                            "Student: " + studentId + "\n" +
                            "Scanned: " + subject + "\n" +
                            "Expected: " + currentSubject
            );

            return;
        }

        // SUCCESS
        String display =
                "✅ PRESENT\n\n" +
                        "Student ID: " + studentId + "\n" +
                        "Subject: " + subject + "\n" +
                        "Status: Marked Successfully";

        tvResult.setText(display);

        saveAttendance(studentId, subject);
    }

    // -----------------------------
    // SAVE LOCALLY (OFFLINE)
    // -----------------------------
    private void saveAttendance(String studentId, String subject) {

        // TODO: Replace with Room DB
        Toast.makeText(getContext(),
                "Saved: " + studentId + " (" + subject + ")",
                Toast.LENGTH_SHORT).show();
    }

    // -----------------------------
    // PERMISSION RESULT
    // -----------------------------
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_REQUEST_CODE) {

            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                openScanner();

            } else {
                tvResult.setText("❌ Camera permission denied");
            }
        }
    }
}