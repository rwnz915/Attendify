package com.example.attendify.activities;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.attendify.AppwriteManager;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.ThemeManager;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.ExcuseLetterRepository;
import com.example.attendify.repository.SubjectRepository;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SubmitExcuseLetterActivity extends AppCompatActivity {

    private TextInputEditText  etMessage;
    private LinearLayout       layoutImagePreview;
    private ImageView          ivPreview;
    private TextView           tvFileName;
    private LinearLayout       btnAttach;
    private TextView           tvAttachLabel, btnSubmit, btnRemoveImage;
    private ProgressBar        progressBar;
    private TextView           tvSubmitting;

    private LinearLayout       layoutSubjectTrigger;
    private LinearLayout       layoutSubjectDropdown;
    private TextView           tvSubjectSelected;
    private ImageView          ivDropdownArrow;
    private TextView           tvSubjectError;
    private boolean            dropdownOpen = false;

    private Uri      selectedImageUri = null;
    private AppwriteManager appwriteManager;

    private final List<SubjectRepository.SubjectItem> subjectList = new ArrayList<>();
    private SubjectRepository.SubjectItem selectedSubject = null;

    // ── Image picker launcher ─────────────────────────────────────────────────

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK
                                && result.getData() != null
                                && result.getData().getData() != null) {
                            selectedImageUri = result.getData().getData();
                            showImagePreview(selectedImageUri);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_submit_excuse_letter);

        androidx.activity.EdgeToEdge.enable(this);

        UserProfile subUser = AuthRepository.getInstance().getLoggedInUser();
        if (subUser != null) {
            String role = subUser.getRole();

            ThemeApplier.applyHeader(this, role, findViewById(R.id.excuse_header_bg));

            TextView tvSubtitle = findViewById(R.id.tv_header_subtitle);
            if (tvSubtitle != null) {
                tvSubtitle.setTextColor(ThemeManager.getLightTintColor(this, role));
            }

            ThemeApplier.applyButton(this, role, findViewById(R.id.btn_submit));

            ImageView ivUpload = findViewById(R.id.iv_upload_icon);
            if (ivUpload != null) {
                ivUpload.setColorFilter(ThemeManager.getPrimaryColor(this, role));
            }

            applyUploadZoneBorder(findViewById(R.id.btn_attach_image),
                    ThemeManager.getPrimaryColor(this, role));
        }

        appwriteManager = new AppwriteManager(this);

        // Back button
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        etMessage          = findViewById(R.id.et_message);
        layoutImagePreview = findViewById(R.id.layout_image_preview);
        ivPreview          = findViewById(R.id.iv_preview);
        tvFileName         = findViewById(R.id.tv_file_name);
        btnAttach          = findViewById(R.id.btn_attach_image);
        tvAttachLabel      = findViewById(R.id.tv_attach_label);
        btnSubmit          = findViewById(R.id.btn_submit);

        UserProfile subBtnUser = AuthRepository.getInstance().getLoggedInUser();
        if (subBtnUser != null) {
            ThemeApplier.applyButton(this, subBtnUser.getRole(), btnSubmit);
        }

        btnRemoveImage     = findViewById(R.id.btn_remove_image);
        progressBar        = findViewById(R.id.progress_bar);
        tvSubmitting       = findViewById(R.id.tv_submitting);
        layoutSubjectTrigger   = findViewById(R.id.layout_subject_trigger);
        layoutSubjectDropdown  = findViewById(R.id.layout_subject_chips);
        tvSubjectSelected      = findViewById(R.id.tv_subject_selected);
        ivDropdownArrow        = findViewById(R.id.iv_dropdown_arrow);
        tvSubjectError         = findViewById(R.id.tv_subject_error);

        layoutSubjectTrigger.setOnClickListener(v -> toggleDropdown());

        btnAttach.setOnClickListener(v -> openImagePicker());
        btnRemoveImage.setOnClickListener(v -> {
            selectedImageUri = null;
            layoutImagePreview.setVisibility(View.GONE);
            tvAttachLabel.setText("Attach Supporting Image *");
        });
        btnSubmit.setOnClickListener(v -> handleSubmit());

        loadSubjects();
    }

    private void applyUploadZoneBorder(View uploadZone, int color) {
        android.graphics.drawable.ShapeDrawable sd = new android.graphics.drawable.ShapeDrawable(
                new android.graphics.drawable.shapes.RoundRectShape(
                        new float[]{24f, 24f, 24f, 24f, 24f, 24f, 24f, 24f}, null, null));

        float density = getResources().getDisplayMetrics().density;
        android.graphics.Paint paint = sd.getPaint();
        paint.setColor(color);
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(2f * density);
        paint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{8f * density, 4f * density}, 0f));

        android.graphics.drawable.Drawable[] layers = {
                new android.graphics.drawable.ColorDrawable(0xFFFAFAFA),
                sd
        };
        uploadZone.setBackground(new android.graphics.drawable.LayerDrawable(layers));
    }

    // ── Load subjects ─────────────────────────────────────────────────────────

    private void loadSubjects() {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        SubjectRepository.getInstance().getStudentSubjects(
                user.getSection(),
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                        subjectList.clear();
                        subjectList.addAll(subjects);
                        runOnUiThread(() -> buildSubjectChips());
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() ->
                                Toast.makeText(SubmitExcuseLetterActivity.this,
                                        "Could not load subjects: " + errorMessage,
                                        Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void toggleDropdown() {
        dropdownOpen = !dropdownOpen;
        layoutSubjectDropdown.setVisibility(dropdownOpen ? View.VISIBLE : View.GONE);
        ivDropdownArrow.setRotation(dropdownOpen ? 180f : 0f);
    }

    private void buildSubjectChips() {
        layoutSubjectDropdown.removeAllViews();
        for (SubjectRepository.SubjectItem subj : subjectList) {
            TextView item = new TextView(this);
            item.setText(subj.name);
            item.setTag(subj);
            item.setTextColor(0xFF111827);
            item.setTextSize(14f);
            item.setPadding(dp(14), dp(14), dp(14), dp(14));
            item.setBackgroundResource(R.drawable.bg_dropdown_item);
            item.setSingleLine(true);
            item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.setOnClickListener(v -> selectSubject(subj));
            layoutSubjectDropdown.addView(item);

            if (subjectList.indexOf(subj) < subjectList.size() - 1) {
                View divider = new View(this);
                android.widget.LinearLayout.LayoutParams lp =
                        new android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                lp.setMarginStart(dp(14));
                lp.setMarginEnd(dp(14));
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(0xFFE5E7EB);
                layoutSubjectDropdown.addView(divider);
            }
        }
    }

    private void selectSubject(SubjectRepository.SubjectItem subj) {
        selectedSubject = subj;
        tvSubjectSelected.setText(subj.name);
        tvSubjectSelected.setTextColor(0xFF111827);
        tvSubjectError.setVisibility(View.GONE);
        dropdownOpen = false;
        layoutSubjectDropdown.setVisibility(View.GONE);
        ivDropdownArrow.setRotation(0f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ── Image picker ──────────────────────────────────────────────────────────

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Supporting Image"));
    }

    private void showImagePreview(Uri uri) {
        layoutImagePreview.setVisibility(View.VISIBLE);
        ivPreview.setImageURI(uri);
        tvFileName.setText(getFileName(uri));
        tvAttachLabel.setText("Change Image");
    }

    private String getFileName(Uri uri) {
        String name = "image.jpg";
        try (Cursor cursor = getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return name;
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    private void handleSubmit() {
        String message = etMessage.getText() != null
                ? etMessage.getText().toString().trim() : "";

        boolean valid = true;

        if (selectedSubject == null) {
            tvSubjectError.setVisibility(View.VISIBLE);
            valid = false;
        }

        if (message.isEmpty()) {
            etMessage.setError("Please write your excuse message");
            etMessage.requestFocus();
            valid = false;
        }

        if (selectedImageUri == null) {
            Toast.makeText(this,
                    "Please attach a supporting image (required)",
                    Toast.LENGTH_SHORT).show();
            valid = false;
        }

        if (!valid) return;

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) {
            Toast.makeText(this, "Session expired. Please log in again.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        uploadImageThenSave(user, message, selectedImageUri);
    }

    // ── Upload image → save Firestore ─────────────────────────────────────────

    private void uploadImageThenSave(UserProfile user, String message, Uri imageUri) {
        try {
            File imageFile = uriToFile(imageUri);
            appwriteManager.uploadImage(imageFile, new AppwriteManager.UploadCallback() {
                @Override
                public void onSuccess(String fileId) {
                    String imageUrl = appwriteManager.getFileUrl(fileId);
                    runOnUiThread(() -> saveToFirestore(user, message, imageUrl, fileId));
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(SubmitExcuseLetterActivity.this,
                                "Image upload failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (Exception e) {
            setLoading(false);
            Toast.makeText(this,
                    "Could not read image: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToFirestore(UserProfile user, String message,
                                 String imageUrl, String fileId) {
        ExcuseLetterRepository.getInstance().submitExcuseLetter(
                user.getId(),
                user.getFullName(),
                user.getStudentID(),
                user.getSection(),
                selectedSubject.id,
                selectedSubject.name,
                selectedSubject.teacherId,
                message,
                imageUrl,
                fileId,
                new ExcuseLetterRepository.SubmitCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            setLoading(false);
                            Toast.makeText(SubmitExcuseLetterActivity.this,
                                    "Excuse letter submitted!", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            Toast.makeText(SubmitExcuseLetterActivity.this,
                                    "Submit failed: " + errorMessage,
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    // ── URI → File ────────────────────────────────────────────────────────────

    private File uriToFile(Uri uri) throws Exception {
        String name = getFileName(uri);
        File file = new File(getCacheDir(), name);
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(file)) {
            if (in == null) throw new Exception("Cannot open input stream");
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
        return file;
    }

    // ── Loading state ─────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        tvSubmitting.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!loading);
        btnAttach.setEnabled(!loading);
        etMessage.setEnabled(!loading);
    }
}
