package com.example.attendify.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.adapters.StudentPresentAdapter;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.utils.ExportUtils;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.attendify.adapters.StudentAdapter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Shows all students present on a specific date/time.
 * Used by both Teacher (MonthDetailActivity) and Secretary (SecretaryMonthDetailActivity).
 *
 * Extras:
 *   DATE        - the date string (yyyy-MM-dd)
 *   TIME        - the formatted time (e.g. "1:00pm - 2:30pm")
 *   SECTION     - filter students by section
 *   SUBJECT     - optional subject filter
 *   SUBJECT_ID  - optional subject ID filter
 *   MODE        - "teacher" or "secretary"
 */
public class DateStudentListActivity extends AppCompatActivity {

    private String date, time, section, subject, subjectId, mode;

    private List<AttendanceRecord> allStudents = new ArrayList<>();
    private List<AttendanceRecord> filteredStudents = new ArrayList<>();

    private RecyclerView rvStudents;
    private EditText etSearch;
    private TextView tvTitle, tvSubtitle, tvEmpty;
    private ProgressBar progressBar;

    private StudentPresentAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_date_student_list);

        androidx.activity.EdgeToEdge.enable(this);

        date      = getIntent().getStringExtra("DATE");
        time      = getIntent().getStringExtra("TIME");
        section   = getIntent().getStringExtra("SECTION");
        subject   = getIntent().getStringExtra("SUBJECT");
        subjectId = getIntent().getStringExtra("SUBJECT_ID");
        mode      = getIntent().getStringExtra("MODE");
        if (mode == null) mode = "teacher";

        // Apply theme
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user != null) {
            ThemeApplier.applyHeader(this, user.getRole(), findViewById(R.id.date_list_header));
        }

        View header = findViewById(R.id.date_list_header);
        header.setPadding(
                header.getPaddingLeft(),
                header.getPaddingTop() + MainActivity.statusBarHeight,
                header.getPaddingRight(),
                header.getPaddingBottom());

        // Format display date: "May 2" style
        tvTitle    = findViewById(R.id.tv_date_list_title);
        tvSubtitle = findViewById(R.id.tv_date_list_subtitle);
        tvEmpty    = findViewById(R.id.tv_date_list_empty);
        rvStudents = findViewById(R.id.rv_date_students);
        etSearch   = findViewById(R.id.et_search_students);
        progressBar = findViewById(R.id.progress_date_list);

        tvTitle.setText(formatDisplayDate(date));
        String sub = (time != null && !time.isEmpty()) ? time : "";
        if (subject != null && !subject.isEmpty()) {
            sub = sub.isEmpty() ? subject : sub + " • " + subject;
        }
        tvSubtitle.setText(sub);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_export_date).setOnClickListener(v -> exportData());

        rvStudents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StudentPresentAdapter(this, filteredStudents);
        rvStudents.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterBySearch(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadStudents();
    }

    private void loadStudents() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get students in this section
        db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener(userSnap -> {
                    List<String> uids = new ArrayList<>();
                    for (DocumentSnapshot d : userSnap.getDocuments()) uids.add(d.getId());

                    if (uids.isEmpty()) {
                        runOnUiThread(() -> {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            tvEmpty.setVisibility(View.VISIBLE);
                        });
                        return;
                    }

                    // Query attendance for the date
                    com.google.firebase.firestore.Query query = db.collection("attendance")
                            .whereIn("studentId", uids.subList(0, Math.min(uids.size(), 30)))
                            .whereEqualTo("date", date);

                    if (subjectId != null && !subjectId.isEmpty()) {
                        query = query.whereEqualTo("subjectId", subjectId);
                    }

                    query.get().addOnSuccessListener(attSnap -> {
                        List<AttendanceRecord> records = new ArrayList<>();
                        for (DocumentSnapshot doc : attSnap.getDocuments()) {
                            String status = doc.getString("status");
                            if ("Present".equals(status) || "Late".equals(status)) {
                                records.add(new AttendanceRecord(
                                        doc.getString("date"),
                                        doc.getString("subjectName"),
                                        doc.getString("subjectId"),
                                        doc.getString("time"),
                                        status,
                                        doc.getString("studentId"),
                                        doc.getString("studentName")));
                            }
                        }

                        // Sort by last name (studentName format: "Last, First")
                        Collections.sort(records, (a, b) -> {
                            String na = a.getStudentName() != null ? a.getStudentName() : "";
                            String nb = b.getStudentName() != null ? b.getStudentName() : "";
                            return na.compareToIgnoreCase(nb);
                        });

                        runOnUiThread(() -> {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            allStudents = records;
                            filterBySearch(etSearch.getText().toString());
                        });
                    }).addOnFailureListener(e -> runOnUiThread(() -> {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }));
                })
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }));
    }

    private void filterBySearch(String query) {
        filteredStudents.clear();
        String q = query.trim().toLowerCase();
        for (AttendanceRecord rec : allStudents) {
            String name = rec.getStudentName() != null ? rec.getStudentName().toLowerCase() : "";
            if (q.isEmpty() || name.contains(q)) {
                filteredStudents.add(rec);
            }
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(filteredStudents.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void exportData() {
        String fileName = "Attendance_" + date + "_" + (section != null ? section : "");
        ExportUtils.exportToCsv(this, fileName, filteredStudents);
    }

    private String formatDisplayDate(String dateStr) {
        try {
            SimpleDateFormat sdfIn  = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            SimpleDateFormat sdfOut = new SimpleDateFormat("MMMM d",     Locale.ENGLISH);
            Date d = sdfIn.parse(dateStr);
            return sdfOut.format(d);
        } catch (ParseException e) {
            return dateStr != null ? dateStr : "";
        }
    }
}