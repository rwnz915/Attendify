package com.example.attendify.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.attendify.models.AttendanceRecord;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ExportUtils {

    public static void exportToCsv(Context context, String fileName, List<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            Toast.makeText(context, "No records to export", Toast.LENGTH_SHORT).show();
            return;
        }

        // Aggregate by student: Student Name -> {Present, Late, Absent}
        Map<String, int[]> studentStats = new TreeMap<>();
        for (AttendanceRecord rec : records) {
            String name = rec.getStudentName();
            if (name == null || name.isEmpty()) continue;
            
            int[] stats = studentStats.getOrDefault(name, new int[3]);
            stats[0] += rec.getPresent();
            stats[1] += rec.getLate();
            stats[2] += rec.getAbsent();
            studentStats.put(name, stats);
        }

        StringBuilder csvContent = new StringBuilder();
        csvContent.append("Student Name,Present,Late,Absent,Total\n");

        for (Map.Entry<String, int[]> entry : studentStats.entrySet()) {
            int[] s = entry.getValue();
            int total = s[0] + s[1] + s[2];
            csvContent.append(entry.getKey()).append(",")
                    .append(s[0]).append(",")
                    .append(s[1]).append(",")
                    .append(s[2]).append(",")
                    .append(total).append("\n");
        }

        try {
            File file = new File(context.getCacheDir(), fileName + ".csv");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(csvContent.toString().getBytes());
            fos.close();

            shareFile(context, file);
        } catch (IOException e) {
            Toast.makeText(context, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void shareFile(Context context, File file) {
        Uri contentUri = FileProvider.getUriForFile(context, "com.example.attendify.fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_STREAM, contentUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "Export Report"));
    }

    public static void exportToCsvStudent(Context context, String fileName, List<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            Toast.makeText(context, "No records to export", Toast.LENGTH_SHORT).show();
            return;
        }

        // Aggregate by subject: Subject -> {Present, Late, Absent}
        Map<String, int[]> subjectStats = new TreeMap<>();
        for (AttendanceRecord rec : records) {
            String subject = rec.getSubject();
            if (subject == null || subject.isEmpty()) subject = "Unknown Subject";
            
            int[] stats = subjectStats.getOrDefault(subject, new int[3]);
            stats[0] += rec.getPresent();
            stats[1] += rec.getLate();
            stats[2] += rec.getAbsent();
            subjectStats.put(subject, stats);
        }

        StringBuilder csvContent = new StringBuilder();
        csvContent.append("Subject,Present,Late,Absent,Total\n");

        for (Map.Entry<String, int[]> entry : subjectStats.entrySet()) {
            int[] s = entry.getValue();
            int total = s[0] + s[1] + s[2];
            csvContent.append(entry.getKey()).append(",")
                    .append(s[0]).append(",")
                    .append(s[1]).append(",")
                    .append(s[2]).append(",")
                    .append(total).append("\n");
        }

        try {
            File file = new File(context.getCacheDir(), fileName + ".csv");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(csvContent.toString().getBytes());
            fos.close();

            shareFile(context, file);
        } catch (IOException e) {
            Toast.makeText(context, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
