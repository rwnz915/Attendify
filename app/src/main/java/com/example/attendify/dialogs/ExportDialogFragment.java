package com.example.attendify.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import com.google.android.material.button.MaterialButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.ThemeManager;
import com.example.attendify.models.AttendanceRecord;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.SubjectRepository;
import com.example.attendify.utils.AttendanceFileParser;
import com.example.attendify.utils.ExportUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * ExportDialogFragment
 *
 * A bottom-sheet-style dialog for configuring and triggering attendance exports.
 * Supports:
 *  - Period type: Day / Weekly / Monthly / Whole
 *  - Role-aware filters: Teacher (subject + section), Secretary (subject only)
 *  - Import external attendance files (CSV or PDF matching the school's format)
 *  - Export the configured data to CSV
 *
 * Usage:
 *   ExportDialogFragment.newInstance().show(getSupportFragmentManager(), "export");
 */
public class ExportDialogFragment extends DialogFragment {

    // ─── Period type constants ──────────────────────────────────────────────
    public static final String PERIOD_DAY     = "Day";
    public static final String PERIOD_WEEKLY  = "Weekly";
    public static final String PERIOD_MONTHLY = "Monthly";
    public static final String PERIOD_WHOLE   = "Whole";

    // ─── File pick request code ─────────────────────────────────────────────
    private static final int REQUEST_PICK_FILE = 4201;

    // ─── State ──────────────────────────────────────────────────────────────
    private String selectedPeriod   = PERIOD_MONTHLY;
    private String selectedMonth    = null;   // e.g. "August"
    private String selectedDay      = null;   // e.g. "15"
    private String selectedWeek     = null;   // e.g. "Week 1"
    private String selectedSubject  = "All Subjects";
    private String selectedSection  = "All Sections";

    private List<SubjectRepository.SubjectItem> teacherSubjects = new ArrayList<>();
    private List<AttendanceRecord> importedRecords = null;  // from uploaded file

    // ─── Views ──────────────────────────────────────────────────────────────
    private ChipGroup  chipGroupPeriod;
    private ChipGroup  chipGroupFormat;
    private LinearLayout layoutUploadSection;
    private LinearLayout rowMonth, rowDay, rowWeek;
    private Spinner  spinnerMonth, spinnerDay, spinnerWeek;
    private Spinner  spinnerSubject, spinnerSection;
    private LinearLayout rowSection;  // hidden for secretary
    private MaterialButton btnImport, btnExport;
    private TextView tvImportStatus, tvFileName;
    private ProgressBar progressBar;
    private ImageView ivClearImport;

    // ─── Factory ────────────────────────────────────────────────────────────
    public static ExportDialogFragment newInstance() {
        return new ExportDialogFragment();
    }

    // ─── Lifecycle ──────────────────────────────────────────────────────────
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.ExportDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_export, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        applyTheme(view);
        populateDateSpinners();
        loadSubjectsForRole();
        setupPeriodChips();
        setupImportButton();
        setupExportButton();
        updatePeriodVisibility();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d != null && d.getWindow() != null) {
            android.view.Window window = d.getWindow();
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();

            // 90% width, wrap height — same as dialog_excuse_action
            int width    = (int) (dm.widthPixels  * 0.90f);
            int maxHeight = (int) (dm.heightPixels * 0.85f);

            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);

            // Rounded white background on the window itself — same technique as
            // ApprovalRequestsActivity dialog so corners actually clip correctly
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFFF9FAFB); // gray_50 to match layout background
            bg.setCornerRadius(24 * dm.density);
            window.setBackgroundDrawable(bg);

            // Cap height so content is scrollable when it overflows
            if (getView() != null) {
                getView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                    if (getView().getHeight() > maxHeight) {
                        android.view.ViewGroup.LayoutParams lp = getView().getLayoutParams();
                        lp.height = maxHeight;
                        getView().setLayoutParams(lp);
                    }
                });
            }
        }
    }

    // ─── View binding ────────────────────────────────────────────────────────
    private void bindViews(View v) {
        chipGroupPeriod  = v.findViewById(R.id.chip_group_period);
        rowMonth         = v.findViewById(R.id.row_month);
        rowDay           = v.findViewById(R.id.row_day);
        rowWeek          = v.findViewById(R.id.row_week);
        spinnerMonth     = v.findViewById(R.id.spinner_export_month);
        spinnerDay       = v.findViewById(R.id.spinner_export_day);
        spinnerWeek      = v.findViewById(R.id.spinner_export_week);
        spinnerSubject   = v.findViewById(R.id.spinner_export_subject);
        spinnerSection   = v.findViewById(R.id.spinner_export_section);
        rowSection       = v.findViewById(R.id.row_section);
        btnImport        = v.findViewById(R.id.btn_import_file);
        btnExport        = v.findViewById(R.id.btn_export_confirm);
        tvImportStatus   = v.findViewById(R.id.tv_import_status);
        tvFileName       = v.findViewById(R.id.tv_file_name);
        ivClearImport    = v.findViewById(R.id.iv_clear_import);
        progressBar      = v.findViewById(R.id.progress_export);

        chipGroupFormat      = v.findViewById(R.id.chip_group_format);
        layoutUploadSection  = v.findViewById(R.id.layout_upload_section);

        v.findViewById(R.id.btn_close_dialog).setOnClickListener(x -> dismiss());

        ivClearImport.setOnClickListener(x -> clearImport());

        // Toggle upload section & export button label based on format chip selection
        chipGroupFormat.setOnCheckedStateChangeListener((group, checkedIds) -> {
            boolean isOwn = checkedIds.contains(R.id.chip_format_own);
            layoutUploadSection.setVisibility(isOwn ? View.VISIBLE : View.GONE);
            if (btnExport != null) {
                btnExport.setText(isOwn ? "Export as School Sheet (.xlsx)" : "Export to CSV");
            }
        });
    }

    // ─── Theme ───────────────────────────────────────────────────────────────
    private void applyTheme(View view) {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;
        String role = user.getRole();

        // Header gradient — same as every other fragment
        ThemeApplier.applyHeader(requireContext(), role,
                view.findViewById(R.id.export_dialog_header));

        // Export button gradient
        ThemeApplier.applyButton(requireContext(), role, btnExport);

        // Import button tint
        int primary = ThemeManager.getPrimaryColor(requireContext(), role);
        btnImport.setTextColor(primary);
        btnImport.setStrokeColor(android.content.res.ColorStateList.valueOf(primary));
        btnImport.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ThemeManager.getLightTintColor(requireContext(), role)));
    }

    // ─── Period chips ────────────────────────────────────────────────────────
    private void setupPeriodChips() {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        String role = user != null ? user.getRole() : "teacher";
        int primaryColor = ThemeManager.getPrimaryColor(requireContext(), role);
        int lightTint    = ThemeManager.getLightTintColor(requireContext(), role);

        String[] periods = { PERIOD_DAY, PERIOD_WEEKLY, PERIOD_MONTHLY, PERIOD_WHOLE };
        for (String period : periods) {
            Chip chip = new Chip(requireContext());
            chip.setText(period);
            chip.setCheckable(true);
            chip.setChecked(period.equals(selectedPeriod));
            chip.setCheckedIconVisible(false);

            // Use theme colors — active = primary fill + white text,
            // inactive = light tint fill + primary text (matches existing chip style)
            android.content.res.ColorStateList bgStates = new android.content.res.ColorStateList(
                    new int[][]{ new int[]{ android.R.attr.state_checked }, new int[]{} },
                    new int[]{ primaryColor, lightTint }
            );
            android.content.res.ColorStateList textStates = new android.content.res.ColorStateList(
                    new int[][]{ new int[]{ android.R.attr.state_checked }, new int[]{} },
                    new int[]{ 0xFFFFFFFF, primaryColor }
            );
            chip.setChipBackgroundColor(bgStates);
            chip.setTextColor(textStates);
            chip.setChipStrokeWidth(0f);

            chip.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) {
                    selectedPeriod = period;
                    updatePeriodVisibility();
                }
            });
            chipGroupPeriod.addView(chip);
        }
        chipGroupPeriod.setSingleSelection(true);
    }

    private void updatePeriodVisibility() {
        rowMonth.setVisibility(View.GONE);
        rowDay.setVisibility(View.GONE);
        rowWeek.setVisibility(View.GONE);

        switch (selectedPeriod) {
            case PERIOD_DAY:
                rowMonth.setVisibility(View.VISIBLE);
                rowDay.setVisibility(View.VISIBLE);
                break;
            case PERIOD_WEEKLY:
                rowMonth.setVisibility(View.VISIBLE);
                rowWeek.setVisibility(View.VISIBLE);
                break;
            case PERIOD_MONTHLY:
                rowMonth.setVisibility(View.VISIBLE);
                break;
            case PERIOD_WHOLE:
                // no extra rows
                break;
        }
    }

    // ─── Date spinners ────────────────────────────────────────────────────────
    private void populateDateSpinners() {
        // Months
        String[] months = { "January","February","March","April","May","June",
                "July","August","September","October","November","December" };
        setSpinner(spinnerMonth, months, val -> selectedMonth = val);

        // Pre-select current month
        int currentMonth = Calendar.getInstance().get(Calendar.MONTH);
        spinnerMonth.setSelection(currentMonth);
        selectedMonth = months[currentMonth];

        // Days 1-31
        String[] days = new String[31];
        for (int i = 0; i < 31; i++) days[i] = String.valueOf(i + 1);
        setSpinner(spinnerDay, days, val -> selectedDay = val);

        // Weeks
        String[] weeks = { "Week 1", "Week 2", "Week 3", "Week 4", "Week 5" };
        setSpinner(spinnerWeek, weeks, val -> selectedWeek = val);
    }

    // ─── Subjects / Sections from Firebase ───────────────────────────────────
    private void loadSubjectsForRole() {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        String role = user.getRole();

        // Secretary: hide section spinner, show subject spinner
        if ("secretary".equals(role)) {
            rowSection.setVisibility(View.GONE);
            loadSecretarySubjects(user);
        } else {
            // Teacher
            rowSection.setVisibility(View.VISIBLE);
            loadTeacherSubjects(user);
        }
    }

    private void loadTeacherSubjects(UserProfile user) {
        SubjectRepository.getInstance().getTeacherSubjects(user.getId(),
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            teacherSubjects = subjects;
                            populateSubjectSpinner(subjects);
                            populateSectionSpinner(subjects);
                        });
                    }
                    @Override
                    public void onFailure(String errorMessage) {}
                });
    }

    private void loadSecretarySubjects(UserProfile user) {
        // Secretary sees all subjects in their section via Firestore
        String section = user.getSection();
        if (section == null || section.isEmpty()) return;

        FirebaseFirestore.getInstance()
                .collection("subjects")
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener(snap -> {
                    if (getActivity() == null) return;
                    List<SubjectRepository.SubjectItem> items = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        SubjectRepository.SubjectItem si = doc.toObject(SubjectRepository.SubjectItem.class);
                        if (si != null) { si.id = doc.getId(); items.add(si); }
                    }
                    teacherSubjects = items;
                    getActivity().runOnUiThread(() -> populateSubjectSpinner(items));
                });
    }

    private void populateSubjectSpinner(List<SubjectRepository.SubjectItem> subjects) {
        List<String> names = new ArrayList<>();
        names.add("All Subjects");
        for (SubjectRepository.SubjectItem s : subjects) {
            if (s.name != null && !names.contains(s.name)) names.add(s.name);
        }
        setSpinner(spinnerSubject, names.toArray(new String[0]), val -> selectedSubject = val);
    }

    private void populateSectionSpinner(List<SubjectRepository.SubjectItem> subjects) {
        List<String> sections = new ArrayList<>();
        sections.add("All Sections");
        for (SubjectRepository.SubjectItem s : subjects) {
            if (s.section != null && !sections.contains(s.section)) sections.add(s.section);
        }
        setSpinner(spinnerSection, sections.toArray(new String[0]), val -> selectedSection = val);
    }

    // ─── Import file ──────────────────────────────────────────────────────────
    private void setupImportButton() {
        btnImport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{ "text/csv", "text/comma-separated-values",
                            "application/pdf", "application/vnd.ms-excel" });
            startActivityForResult(Intent.createChooser(intent, "Select attendance file"), REQUEST_PICK_FILE);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE
                && resultCode == android.app.Activity.RESULT_OK
                && data != null && data.getData() != null) {
            handleSelectedFile(data.getData());
        }
    }

    private void handleSelectedFile(Uri uri) {
        progressBar.setVisibility(View.VISIBLE);
        tvImportStatus.setVisibility(View.GONE);

        String fileName = getFileName(uri);
        tvFileName.setText(fileName != null ? fileName : "Selected file");

        new Thread(() -> {
            try {
                List<AttendanceRecord> parsed = AttendanceFileParser.parse(requireContext(), uri);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (parsed != null && !parsed.isEmpty()) {
                        importedRecords = parsed;
                        tvImportStatus.setVisibility(View.VISIBLE);
                        tvImportStatus.setText("✓ " + parsed.size() + " records imported");
                        tvImportStatus.setTextColor(getResources().getColor(R.color.green_700, null));
                        ivClearImport.setVisibility(View.VISIBLE);
                    } else {
                        tvImportStatus.setVisibility(View.VISIBLE);
                        tvImportStatus.setText("⚠ No records found in file");
                        tvImportStatus.setTextColor(getResources().getColor(R.color.yellow_700, null));
                    }
                });
            } catch (Exception e) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvImportStatus.setVisibility(View.VISIBLE);
                    tvImportStatus.setText("✗ Could not read file: " + e.getMessage());
                    tvImportStatus.setTextColor(getResources().getColor(R.color.red_600, null));
                });
            }
        }).start();
    }

    private void clearImport() {
        importedRecords = null;
        tvImportStatus.setVisibility(View.GONE);
        tvFileName.setText("No file selected");
        ivClearImport.setVisibility(View.GONE);
    }

    // ─── Export ───────────────────────────────────────────────────────────────
    private void setupExportButton() {
        btnExport.setOnClickListener(v -> doExport());
    }

    /** Returns true when the "Own Format" chip is currently checked. */
    private boolean isOwnFormatSelected() {
        List<Integer> checked = chipGroupFormat.getCheckedChipIds();
        return !checked.isEmpty() && checked.get(0) == R.id.chip_format_own;
    }

    private void doExport() {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();

        // ── Own Format path ───────────────────────────────────────────────────
        if (isOwnFormatSelected()) {
            if (importedRecords != null && !importedRecords.isEmpty()) {
                // Export imported records into the school monitoring sheet (.xlsx)
                exportAsSchoolSheet(importedRecords, user);
                return;
            }
            // No file uploaded — fetch from Firestore, then export as school sheet
            fetchAndExport(user, true);
            return;
        }

        // ── Default CSV path ──────────────────────────────────────────────────
        if (importedRecords != null && !importedRecords.isEmpty()) {
            ExportUtils.exportToCsv(requireContext(), buildFileName(), importedRecords);
            dismiss();
            return;
        }
        fetchAndExport(user, false);
    }

    private void fetchAndExport(UserProfile user, boolean schoolFormat) {
        if (user == null) return;
        progressBar.setVisibility(View.VISIBLE);
        btnExport.setEnabled(false);

        List<String> subjectIds = new ArrayList<>();
        for (SubjectRepository.SubjectItem s : teacherSubjects) {
            if (selectedSubject.equals("All Subjects") || selectedSubject.equals(s.name)) {
                if (selectedSection.equals("All Sections") || selectedSection.equals(s.section)) {
                    subjectIds.add(s.id);
                }
            }
        }

        AttendanceRepository.getInstance().getHistoryForSubjects(subjectIds,
                new AttendanceRepository.AttendanceCallback() {
                    @Override
                    public void onSuccess(List<AttendanceRecord> records) {
                        if (getActivity() == null) return;
                        List<AttendanceRecord> filtered = filterByPeriod(records);
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnExport.setEnabled(true);
                            if (filtered.isEmpty()) {
                                Toast.makeText(getContext(),
                                        "No records for selected period", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (schoolFormat) {
                                exportAsSchoolSheet(filtered, user);
                            } else {
                                ExportUtils.exportToCsv(requireContext(), buildFileName(), filtered);
                                dismiss();
                            }
                        });
                    }
                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnExport.setEnabled(true);
                            Toast.makeText(getContext(), "Error: " + errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void exportAsSchoolSheet(List<AttendanceRecord> records, UserProfile user) {
        // Gather metadata for the sheet header
        String section    = !"All Sections".equals(selectedSection) ? selectedSection
                : (user != null && user.getSection() != null ? user.getSection() : "");
        String subject    = !"All Subjects".equals(selectedSubject) ? selectedSubject : "";
        String teacherName = user != null && user.getFullName() != null ? user.getFullName() : "";
        String sy         = "2025-2026 TERM 1"; // could be pulled from user profile if available
        String weekLabel  = PERIOD_WEEKLY.equals(selectedPeriod) ? selectedWeek : "";

        ExportUtils.exportToSchoolSheet(
                requireContext(),
                buildFileName(),
                records,
                section,
                subject,
                selectedMonth,
                weekLabel,
                teacherName,
                sy
        );
        dismiss();
    }

    /**
     * Filter records to the selected period.
     * Uses simple string matching on the date field (yyyy-MM-dd).
     */
    private List<AttendanceRecord> filterByPeriod(List<AttendanceRecord> all) {
        if (selectedPeriod.equals(PERIOD_WHOLE)) return all;

        List<AttendanceRecord> out = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

        for (AttendanceRecord rec : all) {
            try {
                Calendar cal = Calendar.getInstance();
                cal.setTime(sdf.parse(rec.getDate()));

                int recMonth = cal.get(Calendar.MONTH);    // 0-based
                int recDay   = cal.get(Calendar.DAY_OF_MONTH);
                int recWeek  = cal.get(Calendar.WEEK_OF_MONTH);

                String[] months = { "January","February","March","April","May","June",
                        "July","August","September","October","November","December" };
                int targetMonthIdx = selectedMonth != null
                        ? Arrays.asList(months).indexOf(selectedMonth) : -1;

                boolean monthMatch = targetMonthIdx < 0 || recMonth == targetMonthIdx;

                switch (selectedPeriod) {
                    case PERIOD_MONTHLY:
                        if (monthMatch) out.add(rec);
                        break;
                    case PERIOD_DAY:
                        int targetDay = selectedDay != null ? Integer.parseInt(selectedDay) : -1;
                        if (monthMatch && (targetDay < 0 || recDay == targetDay)) out.add(rec);
                        break;
                    case PERIOD_WEEKLY:
                        int targetWeek = selectedWeek != null
                                ? Integer.parseInt(selectedWeek.replace("Week ", "")) : -1;
                        if (monthMatch && (targetWeek < 0 || recWeek == targetWeek)) out.add(rec);
                        break;
                }
            } catch (Exception ignored) {}
        }
        return out;
    }

    private String buildFileName() {
        StringBuilder sb = new StringBuilder("Attendance");
        sb.append("_").append(selectedPeriod);
        if (!PERIOD_WHOLE.equals(selectedPeriod) && selectedMonth != null) {
            sb.append("_").append(selectedMonth);
        }
        if (PERIOD_DAY.equals(selectedPeriod) && selectedDay != null) {
            sb.append("_Day").append(selectedDay);
        }
        if (PERIOD_WEEKLY.equals(selectedPeriod) && selectedWeek != null) {
            sb.append("_").append(selectedWeek.replace(" ", ""));
        }
        if (!"All Subjects".equals(selectedSubject)) {
            sb.append("_").append(selectedSubject.replace(" ", "_"));
        }
        if (!"All Sections".equals(selectedSection)) {
            sb.append("_").append(selectedSection.replace(" ", "_"));
        }
        return sb.toString();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private void setSpinner(Spinner spinner, String[] items, OnSpinnerSelected listener) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                listener.onSelected(items[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = requireContext().getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    interface OnSpinnerSelected { void onSelected(String value); }
}