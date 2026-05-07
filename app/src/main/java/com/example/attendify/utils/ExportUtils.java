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
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ExportUtils
 *
 * Provides two export modes:
 *  1. exportToCsv()         — simple CSV summary (existing behaviour, unchanged)
 *  2. exportToSchoolSheet() — school "Class Attendance Monitoring Sheet" as .xlsx
 *
 * The .xlsx is generated as a raw Open XML zip — no Apache POI OOXML,
 * no java.awt.Color, fully Android-compatible.
 */
public class ExportUtils {

    // ── Style index constants — order must match <cellXfs> in buildStylesXml() ─
    private static final int S_DEFAULT   = 0;
    private static final int S_HEADER    = 1;  // dark-blue bg, white bold, center
    private static final int S_SUBHEADER = 2;  // grey bg, black bold, center
    private static final int S_LABEL     = 3;  // white bg, black bold, left
    private static final int S_NAME      = 4;  // white bg, black normal, left
    private static final int S_DATA      = 5;  // white bg, black normal, center
    private static final int S_PRESENT   = 6;  // green bg, center
    private static final int S_ABSENT    = 7;  // red bg, center
    private static final int S_LATE      = 8;  // yellow bg, center
    private static final int S_SUM       = 9;  // grey bg, black bold, center
    private static final int S_TOTAL_ROW = 10; // blue bg, black bold, center

    // ── Default CSV export ────────────────────────────────────────────────────

    public static void exportToCsv(Context context, String fileName, List<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            Toast.makeText(context, "No records to export", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, int[]> stats = new TreeMap<>();
        for (AttendanceRecord rec : records) {
            String name = rec.getStudentName();
            if (name == null || name.isEmpty()) continue;
            int[] s = stats.getOrDefault(name, new int[3]);
            s[0] += rec.getPresent(); s[1] += rec.getLate(); s[2] += rec.getAbsent();
            stats.put(name, s);
        }
        StringBuilder sb = new StringBuilder("Student Name,Present,Late,Absent,Total\n");
        for (Map.Entry<String, int[]> e : stats.entrySet()) {
            int[] s = e.getValue();
            sb.append(e.getKey()).append(",").append(s[0]).append(",").append(s[1])
                    .append(",").append(s[2]).append(",").append(s[0]+s[1]+s[2]).append("\n");
        }
        writeCsv(context, fileName, sb.toString());
    }

    // ── Student CSV export (by subject) ──────────────────────────────────────

    public static void exportToCsvStudent(Context context, String fileName, List<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            Toast.makeText(context, "No records to export", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, int[]> stats = new TreeMap<>();
        for (AttendanceRecord rec : records) {
            String subj = rec.getSubject();
            if (subj == null || subj.isEmpty()) subj = "Unknown Subject";
            int[] s = stats.getOrDefault(subj, new int[3]);
            s[0] += rec.getPresent(); s[1] += rec.getLate(); s[2] += rec.getAbsent();
            stats.put(subj, s);
        }
        StringBuilder sb = new StringBuilder("Subject,Present,Late,Absent,Total\n");
        for (Map.Entry<String, int[]> e : stats.entrySet()) {
            int[] s = e.getValue();
            sb.append(e.getKey()).append(",").append(s[0]).append(",").append(s[1])
                    .append(",").append(s[2]).append(",").append(s[0]+s[1]+s[2]).append("\n");
        }
        writeCsv(context, fileName, sb.toString());
    }

    private static void writeCsv(Context context, String fileName, String content) {
        try {
            File file = new File(context.getCacheDir(), fileName + ".csv");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes("UTF-8"));
            }
            shareFile(context, file, "text/csv");
        } catch (IOException e) {
            Toast.makeText(context, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ── School monitoring sheet (.xlsx) ───────────────────────────────────────

    public static void exportToSchoolSheet(
            Context context, String fileName, List<AttendanceRecord> records,
            String section, String subject, String month, String week,
            String teacher, String schoolYear) {

        if (records == null || records.isEmpty()) {
            Toast.makeText(context, "No records to export", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File file = new File(context.getCacheDir(), fileName + ".xlsx");
            try (FileOutputStream fos = new FileOutputStream(file);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                writeXlsx(zos, records, section, subject, month, week, teacher, schoolYear);
            }
            shareFile(context, file,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (Exception e) {
            Toast.makeText(context, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ── Raw Open XML writer ───────────────────────────────────────────────────

    private static void writeXlsx(
            ZipOutputStream zos, List<AttendanceRecord> records,
            String section, String subject, String month, String week,
            String teacher, String schoolYear) throws Exception {

        // Collect data
        TreeMap<String, Integer> dateIndex = new TreeMap<>();
        LinkedHashMap<String, Map<String, String>> studentMap = new LinkedHashMap<>();
        for (AttendanceRecord rec : records) {
            String name = rec.getStudentName();
            if (name == null || name.isEmpty()) continue;
            String date   = rec.getDate() != null ? rec.getDate() : "";
            String status = statusCode(rec.getStatusLabel());
            studentMap.putIfAbsent(name, new TreeMap<>());
            studentMap.get(name).putIfAbsent(date, status);
            if (!date.isEmpty()) dateIndex.putIfAbsent(date, dateIndex.size());
        }

        List<String> dates    = new ArrayList<>(dateIndex.keySet());
        List<String> students = new ArrayList<>(studentMap.keySet());
        int numDates  = dates.size();
        int totalCols = 2 + numDates * 2 + 3;
        int sumStart  = 2 + numDates * 2; // 0-based; 1-based = sumStart+1

        putZipEntry(zos, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                        "<Default Extension=\"xml\"  ContentType=\"application/xml\"/>" +
                        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                        "</Types>");

        putZipEntry(zos, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                        "</Relationships>");

        putZipEntry(zos, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                        "</Relationships>");

        putZipEntry(zos, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                        "<sheets><sheet name=\"Attendance\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
                        "</workbook>");

        putZipEntry(zos, "xl/styles.xml", buildStylesXml());

        putZipEntry(zos, "xl/worksheets/sheet1.xml",
                buildSheetXml(students, studentMap, dates, numDates, totalCols, sumStart,
                        section, subject, month, week, teacher, schoolYear));
    }

    // ── Sheet XML ─────────────────────────────────────────────────────────────

    private static String buildSheetXml(
            List<String> students, Map<String, Map<String, String>> studentMap,
            List<String> dates, int numDates, int totalCols, int sumStart,
            String section, String subject, String month, String week,
            String teacher, String schoolYear) {

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");

        // Column widths
        xml.append("<cols>");
        xml.append("<col min=\"1\" max=\"1\" width=\"5\" customWidth=\"1\"/>");
        xml.append("<col min=\"2\" max=\"2\" width=\"32\" customWidth=\"1\"/>");
        for (int i = 0; i < numDates; i++) {
            int c = 3 + i * 2;
            xml.append("<col min=\"").append(c).append("\" max=\"").append(c+1)
                    .append("\" width=\"4.5\" customWidth=\"1\"/>");
        }
        for (int i = 1; i <= 3; i++) {
            xml.append("<col min=\"").append(sumStart+i).append("\" max=\"").append(sumStart+i)
                    .append("\" width=\"8\" customWidth=\"1\"/>");
        }
        xml.append("</cols>");

        // Freeze panes: freeze first 2 columns and 4 rows
        xml.append("<sheetViews><sheetView workbookViewId=\"0\">");
        xml.append("<pane xSplit=\"2\" ySplit=\"4\" topLeftCell=\"C5\" activePane=\"bottomRight\" state=\"frozen\"/>");
        xml.append("</sheetView></sheetViews>");

        xml.append("<sheetData>");

        // Row 1: Title
        xml.append("<row r=\"1\" ht=\"24\" customHeight=\"1\">");
        appendCell(xml, "A1", S_HEADER, "CLASS ATTENDANCE MONITORING SHEET");
        for (int c = 2; c <= totalCols; c++) appendCell(xml, colRef(c)+"1", S_HEADER, "");
        xml.append("</row>");

        // Row 2: Meta info
        int sixth = Math.max(1, totalCols / 6);
        xml.append("<row r=\"2\" ht=\"16\" customHeight=\"1\">");
        appendCell(xml, "A2",                     S_LABEL, "MONTH OF: " + safe(month));
        appendCell(xml, colRef(sixth+1)+"2",       S_LABEL, "WEEK: " + safe(week));
        appendCell(xml, colRef(sixth*2+1)+"2",     S_LABEL, "SECTION: " + safe(section));
        appendCell(xml, colRef(sixth*3+1)+"2",     S_LABEL, "S.Y. & TERM: " + safe(schoolYear));
        appendCell(xml, colRef(sixth*4+1)+"2",     S_LABEL, "TEACHER: " + safe(teacher));
        appendCell(xml, colRef(sixth*5+1)+"2",     S_LABEL, "SUBJECT: " + safe(subject));
        xml.append("</row>");

        // Row 3: Day headers
        xml.append("<row r=\"3\" ht=\"20\" customHeight=\"1\">");
        appendCell(xml, "A3", S_SUBHEADER, "#");
        appendCell(xml, "B3", S_SUBHEADER, "NAME OF STUDENT");
        for (int i = 0; i < numDates; i++) {
            int col = 3 + i*2;
            appendCell(xml, colRef(col)+"3",   S_SUBHEADER, formatDayLabel(dates.get(i)));
            appendCell(xml, colRef(col+1)+"3", S_SUBHEADER, "");
        }
        appendCell(xml, colRef(sumStart+1)+"3", S_SUM, "PRESENT");
        appendCell(xml, colRef(sumStart+2)+"3", S_SUM, "ABSENT");
        appendCell(xml, colRef(sumStart+3)+"3", S_SUM, "TOTAL");
        xml.append("</row>");

        // Row 4: P/A sub-headers
        xml.append("<row r=\"4\" ht=\"14\" customHeight=\"1\">");
        appendCell(xml, "A4", S_SUBHEADER, "");
        appendCell(xml, "B4", S_SUBHEADER, "");
        for (int i = 0; i < numDates; i++) {
            int col = 3 + i*2;
            appendCell(xml, colRef(col)+"4",   S_SUBHEADER, "P");
            appendCell(xml, colRef(col+1)+"4", S_SUBHEADER, "A");
        }
        appendCell(xml, colRef(sumStart+1)+"4", S_SUM, "");
        appendCell(xml, colRef(sumStart+2)+"4", S_SUM, "");
        appendCell(xml, colRef(sumStart+3)+"4", S_SUM, "");
        xml.append("</row>");

        // Student rows
        int rowNum = 5;
        int[] totalsP = new int[numDates];
        int[] totalsA = new int[numDates];

        for (int si = 0; si < students.size(); si++) {
            String name = students.get(si);
            Map<String, String> ds = studentMap.get(name);
            int tp = 0, ta = 0;
            xml.append("<row r=\"").append(rowNum).append("\" ht=\"14\" customHeight=\"1\">");
            appendCellNum(xml, "A"+rowNum, S_DATA, si+1);
            appendCell(xml, "B"+rowNum, S_NAME, name);
            for (int i = 0; i < numDates; i++) {
                int col = 3 + i*2;
                String code = ds != null ? ds.getOrDefault(dates.get(i), "") : "";
                String r1 = colRef(col)+rowNum, r2 = colRef(col+1)+rowNum;
                if ("P".equals(code))      { appendCell(xml,r1,S_PRESENT,"P"); appendCell(xml,r2,S_DATA,""); tp++; totalsP[i]++; }
                else if ("L".equals(code)) { appendCell(xml,r1,S_LATE,"L");    appendCell(xml,r2,S_DATA,""); tp++; totalsP[i]++; }
                else if ("A".equals(code)) { appendCell(xml,r1,S_DATA,"");     appendCell(xml,r2,S_ABSENT,"A"); ta++; totalsA[i]++; }
                else                       { appendCell(xml,r1,S_DATA,"");     appendCell(xml,r2,S_DATA,""); }
            }
            appendCellNum(xml, colRef(sumStart+1)+rowNum, S_SUM, tp);
            appendCellNum(xml, colRef(sumStart+2)+rowNum, S_SUM, ta);
            appendCellNum(xml, colRef(sumStart+3)+rowNum, S_SUM, tp+ta);
            xml.append("</row>");
            rowNum++;
        }

        // Totals row
        int grandP = 0, grandA = 0;
        xml.append("<row r=\"").append(rowNum).append("\" ht=\"14\" customHeight=\"1\">");
        appendCell(xml, "A"+rowNum, S_TOTAL_ROW, "");
        appendCell(xml, "B"+rowNum, S_TOTAL_ROW, "TOTAL");
        for (int i = 0; i < numDates; i++) {
            int col = 3+i*2;
            appendCellNum(xml, colRef(col)+rowNum,   S_TOTAL_ROW, totalsP[i]);
            appendCellNum(xml, colRef(col+1)+rowNum, S_TOTAL_ROW, totalsA[i]);
            grandP += totalsP[i]; grandA += totalsA[i];
        }
        appendCellNum(xml, colRef(sumStart+1)+rowNum, S_TOTAL_ROW, grandP);
        appendCellNum(xml, colRef(sumStart+2)+rowNum, S_TOTAL_ROW, grandA);
        appendCellNum(xml, colRef(sumStart+3)+rowNum, S_TOTAL_ROW, grandP+grandA);
        xml.append("</row>");

        xml.append("</sheetData>");

        // Merge cells
        xml.append("<mergeCells>");
        xml.append("<mergeCell ref=\"A1:").append(colRef(totalCols)).append("1\"/>");
        // Meta row merges
        appendMerge(xml, "A2",                     colRef(sixth)+"2");
        appendMerge(xml, colRef(sixth+1)+"2",       colRef(sixth*2)+"2");
        appendMerge(xml, colRef(sixth*2+1)+"2",     colRef(sixth*3)+"2");
        appendMerge(xml, colRef(sixth*3+1)+"2",     colRef(sixth*4)+"2");
        appendMerge(xml, colRef(sixth*4+1)+"2",     colRef(sixth*5)+"2");
        appendMerge(xml, colRef(sixth*5+1)+"2",     colRef(totalCols)+"2");
        // Name header spans rows 3-4
        xml.append("<mergeCell ref=\"B3:B4\"/>");
        // Day headers span P+A in row 3
        for (int i = 0; i < numDates; i++) {
            int c = 3+i*2;
            xml.append("<mergeCell ref=\"").append(colRef(c)).append("3:").append(colRef(c+1)).append("3\"/>");
        }
        // SUM headers span rows 3-4
        for (int i = 1; i <= 3; i++) {
            String cr = colRef(sumStart+i);
            xml.append("<mergeCell ref=\"").append(cr).append("3:").append(cr).append("4\"/>");
        }
        // Totals label
        xml.append("<mergeCell ref=\"A").append(rowNum).append(":B").append(rowNum).append("\"/>");
        xml.append("</mergeCells>");

        xml.append("</worksheet>");
        return xml.toString();
    }

    // ── Styles XML ────────────────────────────────────────────────────────────

    private static String buildStylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                // Fonts: 0=normal, 1=white-bold-11pt, 2=black-bold-9pt, 3=black-normal-9pt
                "<fonts count=\"4\">" +
                "<font><sz val=\"9\"/><name val=\"Calibri\"/></font>" +
                "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>" +
                "<font><b/><sz val=\"9\"/><name val=\"Calibri\"/></font>" +
                "<font><sz val=\"9\"/><name val=\"Calibri\"/></font>" +
                "</fonts>" +
                // Fills: 0=none,1=gray125(required),2=dark-blue,3=grey,4=white,5=green,6=red,7=yellow,8=blue
                "<fills count=\"9\">" +
                "<fill><patternFill patternType=\"none\"/></fill>" +
                "<fill><patternFill patternType=\"gray125\"/></fill>" +
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F3864\"/></patternFill></fill>" +
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD9D9D9\"/></patternFill></fill>" +
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFFFFF\"/></patternFill></fill>" +
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFC6EFCE\"/></patternFill></fill>" +
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFC7CE\"/></patternFill></fill>" +
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFEB9C\"/></patternFill></fill>" +
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFBDD7EE\"/></patternFill></fill>" +
                "</fills>" +
                // Borders: 0=none, 1=thin-all
                "<borders count=\"2\">" +
                "<border><left/><right/><top/><bottom/></border>" +
                "<border>" +
                "<left style=\"thin\"><color rgb=\"FF000000\"/></left>" +
                "<right style=\"thin\"><color rgb=\"FF000000\"/></right>" +
                "<top style=\"thin\"><color rgb=\"FF000000\"/></top>" +
                "<bottom style=\"thin\"><color rgb=\"FF000000\"/></bottom>" +
                "</border>" +
                "</borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                // cellXfs — order MUST match S_* constants
                "<cellXfs count=\"11\">" +
                xf(0,0,0,"left",  false) +  // 0 S_DEFAULT
                xf(1,2,1,"center",true)  +  // 1 S_HEADER    white-bold, dark-blue bg
                xf(2,3,1,"center",true)  +  // 2 S_SUBHEADER black-bold, grey bg
                xf(2,4,1,"left",  true)  +  // 3 S_LABEL     black-bold, white bg
                xf(3,4,0,"left",  false) +  // 4 S_NAME      black-normal, white bg
                xf(3,4,0,"center",false) +  // 5 S_DATA      black-normal, white bg
                xf(3,5,0,"center",false) +  // 6 S_PRESENT   green bg
                xf(3,6,0,"center",false) +  // 7 S_ABSENT    red bg
                xf(3,7,0,"center",false) +  // 8 S_LATE      yellow bg
                xf(2,3,1,"center",true)  +  // 9 S_SUM       black-bold, grey bg
                xf(2,8,1,"center",true)  +  // 10 S_TOTAL_ROW black-bold, blue bg
                "</cellXfs>" +
                "</styleSheet>";
    }

    private static String xf(int fontId, int fillId, int borderId, String align, boolean bold) {
        return "<xf numFmtId=\"0\" fontId=\"" + fontId + "\" fillId=\"" + fillId +
                "\" borderId=\"" + borderId + "\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">" +
                "<alignment horizontal=\"" + align + "\" vertical=\"center\"/>" +
                "</xf>";
    }

    // ── XML helpers ───────────────────────────────────────────────────────────

    private static void appendCell(StringBuilder x, String ref, int s, String v) {
        x.append("<c r=\"").append(ref).append("\" s=\"").append(s).append("\" t=\"inlineStr\">")
                .append("<is><t>").append(escapeXml(v)).append("</t></is></c>");
    }

    private static void appendCellNum(StringBuilder x, String ref, int s, int v) {
        x.append("<c r=\"").append(ref).append("\" s=\"").append(s).append("\"><v>").append(v).append("</v></c>");
    }

    private static void appendMerge(StringBuilder x, String from, String to) {
        if (!from.equals(to)) x.append("<mergeCell ref=\"").append(from).append(":").append(to).append("\"/>");
    }

    /** 1-based column index → letter(s): 1=A, 26=Z, 27=AA */
    private static String colRef(int col) {
        StringBuilder sb = new StringBuilder();
        while (col > 0) { col--; sb.insert(0,(char)('A'+col%26)); col/=26; }
        return sb.toString();
    }

    private static void putZipEntry(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        byte[] b = content.getBytes("UTF-8");
        zos.write(b, 0, b.length);
        zos.closeEntry();
    }

    // ── Misc helpers ─────────────────────────────────────────────────────────

    private static String statusCode(String label) {
        if (label == null) return "";
        switch (label.trim().toLowerCase()) {
            case "present": return "P";
            case "late":    return "L";
            case "absent":  return "A";
            default: return label.length() == 1 ? label.toUpperCase() : "";
        }
    }

    private static String formatDayLabel(String date) {
        if (date == null || date.length() < 10) return date != null ? date : "";
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(date));
            String[] days = {"SUN","MON","TUE","WED","THU","FRI","SAT"};
            return days[cal.get(Calendar.DAY_OF_WEEK)-1] + " " + cal.get(Calendar.DAY_OF_MONTH);
        } catch (Exception e) { return date.substring(8); }
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&apos;");
    }

    private static String safe(String s) { return s != null ? s : ""; }

    // ── File sharing ──────────────────────────────────────────────────────────

    private static void shareFile(Context context, File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(context, "com.example.attendify.fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "Export Report"));
    }
}