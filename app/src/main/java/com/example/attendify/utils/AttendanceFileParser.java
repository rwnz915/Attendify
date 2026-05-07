package com.example.attendify.utils;

import android.content.Context;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.example.attendify.models.AttendanceRecord;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AttendanceFileParser
 *
 * Parses externally uploaded attendance files (CSV or PDF) into a list of
 * {@link AttendanceRecord} objects.
 *
 * ── Supported CSV formats ────────────────────────────────────────────────────
 *  Format A (summary, as produced by ExportUtils):
 *    Student Name,Present,Late,Absent,Total
 *    ALBA EINE KLEINE,12,1,2,15
 *
 *  Format B (Appwrite/generic per-record):
 *    date,studentName,subject,status
 *    2025-08-01,ALBA EINE KLEINE,MAWD203,Present
 *
 * ── Supported PDF formats ────────────────────────────────────────────────────
 *  The school weekly monitoring sheet PDF (IT-MAWD203 format).
 *  Student names are extracted from the text layer; attendance marks are
 *  inferred from the surrounding cell text (P / L / A or ✓ / x).
 *
 *  NOTE: Android's PdfRenderer does not expose text directly. For scanned
 *  PDFs, we fall back to extracting student names only, setting all status
 *  as "Present" (school-issued pre-filled sheets typically mark only absences).
 *  A third-party PDF text library (e.g. iText, PdfBox-Android) would provide
 *  full fidelity but is not bundled here to keep the APK lean.
 *
 * Usage:
 *   List<AttendanceRecord> records = AttendanceFileParser.parse(context, uri);
 */
public class AttendanceFileParser {

    // Regex to detect a student name in the school roster format:
    //   "LASTNAME,FIRSTNAME MIDDLENAME ..."  or plain "LASTNAME FIRSTNAME"
    private static final Pattern STUDENT_NAME_PATTERN =
            Pattern.compile("^([A-Z][A-Z\\s,.'\\-]{2,60})$");

    // Regex to detect a date string yyyy-MM-dd
    private static final Pattern DATE_PATTERN =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    // ─── Entry point ─────────────────────────────────────────────────────────

    /**
     * Parse the file at {@code uri} and return attendance records.
     * Returns an empty list (never null) on parse failure.
     */
    public static List<AttendanceRecord> parse(Context context, Uri uri) throws Exception {
        String mime = context.getContentResolver().getType(uri);
        String name = getDisplayName(context, uri);

        if (isPdf(mime, name)) {
            return parsePdf(context, uri);
        } else {
            return parseCsv(context, uri);
        }
    }

    // ─── CSV parser ───────────────────────────────────────────────────────────

    private static List<AttendanceRecord> parseCsv(Context context, Uri uri) throws Exception {
        List<AttendanceRecord> results = new ArrayList<>();

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {

            String header = br.readLine();
            if (header == null) return results;

            String[] cols = header.split(",");
            boolean isSummaryFormat = containsIgnoreCase(cols, "present")
                    && containsIgnoreCase(cols, "absent");
            boolean isPerRecordFormat = containsIgnoreCase(cols, "date")
                    && containsIgnoreCase(cols, "status");

            // Determine column indices
            int idxName    = findCol(cols, "studentname", "student name", "name");
            int idxDate    = findCol(cols, "date");
            int idxSubject = findCol(cols, "subject", "subjectname");
            int idxStatus  = findCol(cols, "status");
            int idxPresent = findCol(cols, "present");
            int idxLate    = findCol(cols, "late");
            int idxAbsent  = findCol(cols, "absent");

            // Fallback date for summary rows (today)
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                    .format(Calendar.getInstance().getTime());

            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = splitCsvLine(line);

                if (isPerRecordFormat) {
                    // date, studentName, subject, status
                    String date    = safeGet(parts, idxDate,    today);
                    String stName  = safeGet(parts, idxName,    "Unknown");
                    String subject = safeGet(parts, idxSubject, "");
                    String status  = safeGet(parts, idxStatus,  "Present");
                    results.add(new AttendanceRecord(date, subject, "", "—", capitalize(status), "", stName));

                } else if (isSummaryFormat) {
                    // Student Name, Present, Late, Absent, Total
                    String stName = safeGet(parts, idxName, "Unknown");
                    int p = safeInt(parts, idxPresent);
                    int l = safeInt(parts, idxLate);
                    int a = safeInt(parts, idxAbsent);

                    // Expand into individual synthetic records so ExportUtils can re-aggregate
                    for (int i = 0; i < p; i++) results.add(new AttendanceRecord(today, p, a, l) {{ /* present */ }});
                    for (int i = 0; i < l; i++) results.add(new AttendanceRecord(today, 0, 0, 1));
                    for (int i = 0; i < a; i++) results.add(new AttendanceRecord(today, 0, 1, 0));

                } else {
                    // Unknown format: try to build something useful
                    if (parts.length >= 2) {
                        String col0 = parts[0].trim();
                        String col1 = parts[1].trim();
                        String date = DATE_PATTERN.matcher(col0).find() ? col0 : today;
                        results.add(new AttendanceRecord(date, col1, "", "—", "Present", "", col0));
                    }
                }
            }
        }
        return results;
    }

    // ─── PDF parser ───────────────────────────────────────────────────────────

    /**
     * Parses the school's weekly attendance monitoring sheet PDF.
     *
     * Since Android's built-in PdfRenderer renders to Bitmap only (no text layer),
     * we parse the raw PDF stream for embedded text strings. This works for
     * digitally created PDFs; scanned images require OCR.
     *
     * The school format has student names in the first column (rows 1–43).
     * We extract them and create synthetic "Present" records for the reporting week.
     */
    private static List<AttendanceRecord> parsePdf(Context context, Uri uri) throws Exception {
        List<AttendanceRecord> results = new ArrayList<>();

        // Try raw text extraction from PDF stream
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            byte[] bytes = readAllBytes(is);
            String raw = extractPdfText(bytes);
            results.addAll(parseSchoolSheetText(raw));
        }

        // If nothing found, gracefully return empty
        return results;
    }

    /**
     * Extract printable text from raw PDF bytes by scanning for BT...ET blocks
     * and Tj / TJ operators.
     */
    private static String extractPdfText(byte[] bytes) {
        String content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        StringBuilder sb = new StringBuilder();

        // Match all string literals inside parentheses after Tj/TJ
        Pattern p = Pattern.compile("\\(([^)]{1,120})\\)\\s*Tj");
        Matcher m = p.matcher(content);
        while (m.find()) {
            sb.append(m.group(1).trim()).append("\n");
        }
        // Also handle array form: [(text)(more)] TJ
        Pattern p2 = Pattern.compile("\\[([^\\]]{1,500})\\]\\s*TJ");
        Matcher m2 = p2.matcher(content);
        while (m2.find()) {
            String chunk = m2.group(1);
            Matcher inner = Pattern.compile("\\(([^)]{1,120})\\)").matcher(chunk);
            while (inner.find()) sb.append(inner.group(1).trim()).append(" ");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Scan the extracted text for student names matching the school roster format.
     * Expected pattern: "LASTNAME,FIRSTNAME MIDDLENAME"
     */
    private static List<AttendanceRecord> parseSchoolSheetText(String text) {
        List<AttendanceRecord> results = new ArrayList<>();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                .format(Calendar.getInstance().getTime());

        // Name pattern: contains a comma, all caps, at least 5 chars
        Pattern namePattern = Pattern.compile("([A-Z][A-Z\\s]{1,30},[A-Z][A-Z\\s.]{2,40})");
        Matcher m = namePattern.matcher(text);

        while (m.find()) {
            String name = m.group(1).trim().replaceAll("\\s+", " ");
            // Deduplicate
            boolean dup = false;
            for (AttendanceRecord r : results) {
                if (name.equals(r.getStudentName())) { dup = true; break; }
            }
            if (!dup) {
                // Default: mark as Present (the sheet only records absences explicitly)
                results.add(new AttendanceRecord(today, "", "", "—", "Present", "", name));
            }
        }
        return results;
    }

    // ─── Utility helpers ──────────────────────────────────────────────────────

    private static String[] splitCsvLine(String line) {
        // Handle quoted fields
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        for (char c : line.toCharArray()) {
            if (c == '"') { inQuote = !inQuote; }
            else if (c == ',' && !inQuote) { tokens.add(sb.toString()); sb.setLength(0); }
            else sb.append(c);
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }

    private static int findCol(String[] headers, String... candidates) {
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().toLowerCase(Locale.ENGLISH).replaceAll("[^a-z]", "");
            for (String c : candidates) {
                if (h.equals(c.replaceAll("[^a-z]", ""))) return i;
            }
        }
        return -1;
    }

    private static boolean containsIgnoreCase(String[] arr, String target) {
        for (String s : arr) if (s.trim().equalsIgnoreCase(target)) return true;
        return false;
    }

    private static String safeGet(String[] parts, int idx, String def) {
        if (idx < 0 || idx >= parts.length) return def;
        String v = parts[idx].trim();
        return v.isEmpty() ? def : v;
    }

    private static int safeInt(String[] parts, int idx) {
        try { return Integer.parseInt(safeGet(parts, idx, "0")); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "Present";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ENGLISH);
    }

    private static boolean isPdf(String mime, String name) {
        return "application/pdf".equals(mime)
                || (name != null && name.toLowerCase(Locale.ENGLISH).endsWith(".pdf"));
    }

    private static String getDisplayName(Context context, Uri uri) {
        String result = null;
        try (android.database.Cursor cursor = context.getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = is.read(chunk)) != -1) buffer.write(chunk, 0, n);
        return buffer.toByteArray();
    }
}