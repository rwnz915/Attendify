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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

public class StudentQrFragment extends Fragment {

    private ImageView imgQr;
    private TextView tvSubject, tvStudent;

    // Example data (replace with real logged-in student info)
    private String studentId = "20241001";
    private String subject = "MATH101";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_student_qr, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imgQr = view.findViewById(R.id.img_qr);
        tvSubject = view.findViewById(R.id.tv_subject);
        tvStudent = view.findViewById(R.id.tv_student);

        tvSubject.setText("Subject: " + subject);
        tvStudent.setText("Student ID: " + studentId);

        generateQR();
    }

    // -----------------------------
    // GENERATE QR CODE
    // -----------------------------
    private void generateQR() {

        try {
            String qrData = studentId + "|" + subject;

            MultiFormatWriter writer = new MultiFormatWriter();

            BitMatrix matrix = writer.encode(qrData,
                    BarcodeFormat.QR_CODE,
                    500,
                    500);

            Bitmap bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.RGB_565);

            for (int x = 0; x < 500; x++) {
                for (int y = 0; y < 500; y++) {
                    bitmap.setPixel(x, y,
                            matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            imgQr.setImageBitmap(bitmap);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}