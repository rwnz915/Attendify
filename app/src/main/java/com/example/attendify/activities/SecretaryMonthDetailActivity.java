package com.example.attendify.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.adapters.HistoryAdapter;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.ThemeApplier;
import com.example.attendify.utils.ExportUtils;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Secretary's month detail screen.
 * Shows dates for a given month+subject. Clicking a date opens DateStudentListActivity.
 */
public class SecretaryMonthDetailActivity extends AppCompatActivity {

    private String monthYear;
    private String subjectFilter;  // fixed subject from SecretaryHistoryFragment
    private String userSection;

    private List<AttendanceRecord> allMonthRecords = new ArrayList<>();

    private RecyclerView rvDetails;
    private TextView tvTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_secretary_month_detail);

        androidx.activity.EdgeToEdge.enable(this);

        monthYear    = getIntent().getStringExtra("MONTH_YEAR");
        subjectFilter = getIntent().getStringExtra("SUBJECT");
        userSection  = getIntent().getStringExtra("SECTION");

        tvTitle = findViewById(R.id.tv_month_title);
        String titleText = monthYear;
        if (subjectFilter != null && !subjectFilter.isEmpty()) {
            titleText += " - " + subjectFilter;
        }
        tvTitle.setText(titleText);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_export_month).setOnClickListener(v -> exportMonthData());

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user != null) {
            ThemeApplier.applyHeader(this, user.getRole(), findViewById(R.id.detail_header));
        }

        View header = findViewById(R.id.detail_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        // Hide subject spinner - subject is fixed
        View spinnerSubject = findViewById(R.id.spinner_subject_detail);
        if (spinnerSubject != null) spinnerSubject.setVisibility(View.GONE);

        rvDetails = findViewById(R.id.rv_month_details);
        rvDetails.setLayoutManager(new LinearLayoutManager(this));

        loadData();
    }

    private void loadData() {
        if (userSection == null || userSection.isEmpty()) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("section", userSection)
                .get()
                .addOnSuccessListener(userSnap -> {
                    List<DocumentSnapshot> studentDocs = userSnap.getDocuments();
                    if (studentDocs.isEmpty()) return;

                    List<String> uids = new ArrayList<>();
                    for (DocumentSnapshot d : studentDocs) uids.add(d.getId());

                    db.collection("attendance")
                            .whereIn("studentId", uids.subList(0, Math.min(uids.size(), 30)))
                            .get()
                            .addOnSuccessListener(attSnap -> {
                                List<AttendanceRecord> records = new ArrayList<>();
                                for (DocumentSnapshot doc : attSnap.getDocuments()) {
                                    records.add(new AttendanceRecord(
                                            doc.getString("date"),
                                            doc.getString("subjectName"),
                                            doc.getString("subjectId"),
                                            doc.getString("time"),
                                            doc.getString("status"),
                                            doc.getString("studentId"),
                                            doc.getString("studentName")));
                                }
                                runOnUiThread(() -> {
                                    filterByMonthAndSubject(records);
                                    buildDateList();
                                });
                            });
                });
    }

    private void filterByMonthAndSubject(List<AttendanceRecord> records) {
        SimpleDateFormat sdfInput  = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        SimpleDateFormat sdfOutput = new SimpleDateFormat("MMMM yyyy",  Locale.ENGLISH);

        allMonthRecords.clear();
        for (AttendanceRecord rec : records) {
            try {
                Date d = sdfInput.parse(rec.getDate());
                boolean monthOk   = sdfOutput.format(d).equals(monthYear);
                boolean subjectOk = subjectFilter == null || subjectFilter.isEmpty()
                        || subjectFilter.equals(rec.getSubject());
                if (monthOk && subjectOk) {
                    allMonthRecords.add(rec);
                }
            } catch (ParseException e) {}
        }
    }

    private void buildDateList() {
        // Group by date — each date = one card showing aggregate stats
        Map<String, List<AttendanceRecord>> byDate = new TreeMap<>(Collections.reverseOrder());
        for (AttendanceRecord rec : allMonthRecords) {
            byDate.computeIfAbsent(rec.getDate(), k -> new ArrayList<>()).add(rec);
        }

        List<AttendanceRecord> summaries = new ArrayList<>();
        for (Map.Entry<String, List<AttendanceRecord>> e : byDate.entrySet()) {
            int p = 0, l = 0, a = 0;
            for (AttendanceRecord r : e.getValue()) {
                p += r.getPresent(); l += r.getLate(); a += r.getAbsent();
            }
            summaries.add(new AttendanceRecord(e.getKey(), p, a, l));
        }

        HistoryAdapter adapter = new HistoryAdapter(this, summaries, true);
        adapter.setOnItemClickListener(record -> {
            String timeDisplay = getTimeForDate(record.getDate());
            Intent intent = new Intent(this, DateStudentListActivity.class);
            intent.putExtra("DATE",    record.getDate());
            intent.putExtra("TIME",    timeDisplay);
            intent.putExtra("SECTION", userSection);
            intent.putExtra("SUBJECT", subjectFilter != null ? subjectFilter : "");
            intent.putExtra("MODE",    "secretary");
            startActivity(intent);
        });
        rvDetails.setAdapter(adapter);
    }

    private String getTimeForDate(String date) {
        Set<String> times = new LinkedHashSet<>();
        for (AttendanceRecord rec : allMonthRecords) {
            if (rec.getDate().equals(date)) {
                if (rec.getTime() != null && !rec.getTime().isEmpty() && !"--:--".equals(rec.getTime())) {
                    times.add(rec.getTime());
                }
            }
        }
        return String.join(" - ", times);
    }

    private void exportMonthData() {
        String fileName = "Section_Attendance_" + userSection.replace(" ", "_")
                + "_" + monthYear.replace(" ", "_");
        ExportUtils.exportToCsv(this, fileName, allMonthRecords);
    }
}