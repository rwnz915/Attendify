package com.example.attendify.fragments;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.ColorDrawable;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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

/**
 * Form where students write and submit an excuse letter.
 *
 * Changes (v2):
 *  - Subject selector (Spinner-like chip row) — required
 *  - Image attachment — required
 *  - Stores subjectId, subjectName, section, teacherId in Firestore
 */
public class SubmitExcuseLetterFragment extends Fragment {

    private TextInputEditText  etMessage;
    private LinearLayout       layoutImagePreview;
    private ImageView          ivPreview;
    private TextView           tvFileName;
    private LinearLayout       btnAttach;
    private TextView           tvAttachLabel, btnSubmit, btnRemoveImage;
    private ProgressBar        progressBar;
    private TextView           tvSubmitting;

    // Subject selector views
    private LinearLayout       layoutSubjectTrigger;
    private LinearLayout       layoutSubjectDropdown;
    private TextView           tvSubjectSelected;
    private ImageView          ivDropdownArrow;
    private TextView           tvSubjectError;
    private boolean            dropdownOpen = false;

    private Uri      selectedImageUri = null;
    private AppwriteManager appwriteManager;

    // Loaded subjects
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_submit_excuse_letter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Apply saved theme
        UserProfile subUser = AuthRepository.getInstance().getLoggedInUser();
        if (subUser != null) {
            String role = subUser.getRole();

            ThemeApplier.applyHeader(requireContext(), role, view.findViewById(R.id.excuse_header_bg));

            TextView tvSubtitle = view.findViewById(R.id.tv_header_subtitle);
            if (tvSubtitle != null) {
                tvSubtitle.setTextColor(ThemeManager.getLightTintColor(requireContext(), role));
            }

            ThemeApplier.applyButton(requireContext(), role, view.findViewById(R.id.btn_submit));

            ImageView ivUpload = view.findViewById(R.id.iv_upload_icon);
            if (ivUpload != null) {
                ivUpload.setColorFilter(ThemeManager.getPrimaryColor(requireContext(), role));
            }

            // ← add this
            applyUploadZoneBorder(view.findViewById(R.id.btn_attach_image),
                    ThemeManager.getPrimaryColor(requireContext(), role));
        }

        appwriteManager = new AppwriteManager(requireContext());

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        etMessage          = view.findViewById(R.id.et_message);
        layoutImagePreview = view.findViewById(R.id.layout_image_preview);
        ivPreview          = view.findViewById(R.id.iv_preview);
        tvFileName         = view.findViewById(R.id.tv_file_name);
        btnAttach          = view.findViewById(R.id.btn_attach_image);
        tvAttachLabel      = view.findViewById(R.id.tv_attach_label);
        btnSubmit          = view.findViewById(R.id.btn_submit);
        // Apply theme to submit button
        UserProfile subBtnUser = AuthRepository.getInstance().getLoggedInUser();
        if (subBtnUser != null) {
            ThemeApplier.applyButton(requireContext(), subBtnUser.getRole(), btnSubmit);
        }
        btnRemoveImage     = view.findViewById(R.id.btn_remove_image);
        progressBar        = view.findViewById(R.id.progress_bar);
        tvSubmitting       = view.findViewById(R.id.tv_submitting);
        layoutSubjectTrigger   = view.findViewById(R.id.layout_subject_trigger);
        layoutSubjectDropdown  = view.findViewById(R.id.layout_subject_chips);
        tvSubjectSelected      = view.findViewById(R.id.tv_subject_selected);
        ivDropdownArrow        = view.findViewById(R.id.iv_dropdown_arrow);
        tvSubjectError         = view.findViewById(R.id.tv_subject_error);

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

    // ── Load student's subjects from Firestore ────────────────────────────────

    private void loadSubjects() {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;

        SubjectRepository.getInstance().getStudentSubjects(
                user.getSection(),
                new SubjectRepository.SubjectsCallback() {
                    @Override
                    public void onSuccess(List<SubjectRepository.SubjectItem> subjects) {
                        if (getActivity() == null) return;
                        subjectList.clear();
                        subjectList.addAll(subjects);
                        getActivity().runOnUiThread(() -> buildSubjectChips());
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(),
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
            TextView item = new TextView(requireContext());
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

            // Divider between items (skip after last)
            if (subjectList.indexOf(subj) < subjectList.size() - 1) {
                View divider = new View(requireContext());
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
        // Close dropdown
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
        try (Cursor cursor = requireContext().getContentResolver()
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
            Toast.makeText(requireContext(),
                    "Please attach a supporting image (required)",
                    Toast.LENGTH_SHORT).show();
            valid = false;
        }

        if (!valid) return;

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Session expired. Please log in again.",
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
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                saveToFirestore(user, message, imageUrl, fileId));
                    }
                }

                @Override
                public void onError(Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            setLoading(false);
                            Toast.makeText(requireContext(),
                                    "Image upload failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                }
            });
        } catch (Exception e) {
            setLoading(false);
            Toast.makeText(requireContext(),
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
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            setLoading(false);
                            Toast.makeText(requireContext(),
                                    "Excuse letter submitted!", Toast.LENGTH_SHORT).show();
                            requireActivity().getSupportFragmentManager().popBackStack();
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            setLoading(false);
                            Toast.makeText(requireContext(),
                                    "Submit failed: " + errorMessage,
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    // ── URI → File ────────────────────────────────────────────────────────────

    private File uriToFile(Uri uri) throws Exception {
        String name = getFileName(uri);
        File file = new File(requireContext().getCacheDir(), name);
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
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