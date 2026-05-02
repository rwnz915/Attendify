package com.example.attendify.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.example.attendify.ThemeApplier;

public class SecretaryHomeFragment extends Fragment {

    private static final int ABSENCE_THRESHOLD = 3;

    private TextView tvName, tvPresent, tvLate, tvAbsent;
    private LinearLayout llAttentionStudents;
    private TextView tvAttentionEmpty;
    private ProgressBar progressAttention;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_secretary_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Apply saved theme to header
        UserProfile secThemeUser = AuthRepository.getInstance().getLoggedInUser();
        if (secThemeUser != null) {
            ThemeApplier.applyHeader(requireContext(), secThemeUser.getRole(), view.findViewById(R.id.sec_header_bg));

            // Apply theme to the Class List quick action card
            android.view.View btnClassList = view.findViewById(R.id.btn_quick_class_list);
            if (btnClassList != null) {
                ThemeApplier.applyLightTint(requireContext(), secThemeUser.getRole(), btnClassList, 20);
                // Tint the icon inside it
                android.widget.ImageView clIcon = btnClassList.findViewWithTag("class_list_icon");
                if (clIcon == null) {
                    // find first ImageView child
                    if (btnClassList instanceof android.view.ViewGroup) {
                        android.view.ViewGroup vg = (android.view.ViewGroup) btnClassList;
                        for (int ci = 0; ci < vg.getChildCount(); ci++) {
                            android.view.View child = vg.getChildAt(ci);
                            if (child instanceof android.widget.ImageView) {
                                ((android.widget.ImageView) child).setColorFilter(
                                        com.example.attendify.ThemeManager.getPrimaryColor(requireContext(), secThemeUser.getRole()));
                            } else if (child instanceof android.widget.TextView) {
                                ((android.widget.TextView) child).setTextColor(
                                        com.example.attendify.ThemeManager.getPrimaryColor(requireContext(), secThemeUser.getRole()));
                            }
                        }
                    }
                }
            }
        }

        // FIX: set paddingTop absolutely (base dp + statusBarHeight) instead of
        // reading getPaddingTop() which causes accumulation on every tab switch.
        // sec_header_bg is constrained to sec_header_spacer so no height tweak needed.
        /*View headerContent = view.findViewById(R.id.sec_home_header);
        if (headerContent != null) {
            int basePx = (int) (56 * view.getResources().getDisplayMetrics().density);
            headerContent.setPadding(
                    headerContent.getPaddingLeft(),
                    basePx + MainActivity.statusBarHeight,
                    headerContent.getPaddingRight(),
                    headerContent.getPaddingBottom());
        }*/

        tvName              = view.findViewById(R.id.tv_sec_name);
        tvPresent           = view.findViewById(R.id.tv_overview_present);
        tvLate              = view.findViewById(R.id.tv_overview_late);
        tvAbsent            = view.findViewById(R.id.tv_overview_absent);
        llAttentionStudents = view.findViewById(R.id.ll_attention_students);
        tvAttentionEmpty    = view.findViewById(R.id.tv_attention_empty);
        progressAttention   = view.findViewById(R.id.progress_attention);

        view.findViewById(R.id.btn_quick_subjects).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).selectTab(1);
        });

        view.findViewById(R.id.btn_quick_class_list).setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new SecretaryClassListFragment())
                    .addToBackStack(null)
                    .commit();
        });

        loadSecretaryInfo();
        loadTodayOverview();
        loadAttentionStudents();
    }

    private void loadSecretaryInfo() {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;
        if (tvName != null) tvName.setText(user.getFullName());

        TextView tvRole = getView() != null ? getView().findViewById(R.id.tv_sec_role) : null;
        if (tvRole != null) {
            String section = user.getSection();
            tvRole.setText(section != null ? "Class Secretary  \u2022  " + section : "Class Secretary");
        }

        TextView tvLabel = getView() != null ? getView().findViewById(R.id.tv_overview_label) : null;
        if (tvLabel != null && user.getSection() != null)
            tvLabel.setText("Today's Overview  \u2022  " + user.getSection());
    }

    private void loadTodayOverview() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date());
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null || user.getSection() == null) return;
        final String section = user.getSection();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener(userSnap -> {
                    if (getActivity() == null) return;
                    List<DocumentSnapshot> docs = userSnap.getDocuments();
                    if (docs.isEmpty()) return;
                    java.util.List<String> uids = new java.util.ArrayList<>();
                    for (DocumentSnapshot d : docs) uids.add(d.getId());

                    db.collection("attendance")
                            .whereEqualTo("date", today)
                            .whereIn("studentId", uids.subList(0, Math.min(uids.size(), 30)))
                            .get()
                            .addOnSuccessListener(attSnap -> {
                                if (getActivity() == null) return;
                                int present = 0, late = 0, absent = 0;
                                for (DocumentSnapshot doc : attSnap.getDocuments()) {
                                    String status = doc.getString("status");
                                    if (status == null) continue;
                                    switch (status) {
                                        case "Present": present++; break;
                                        case "Late":    late++;    break;
                                        case "Absent":  absent++;  break;
                                    }
                                }
                                final int fp = present, fl = late, fa = absent;
                                getActivity().runOnUiThread(() -> {
                                    if (tvPresent != null) tvPresent.setText(String.valueOf(fp));
                                    if (tvLate    != null) tvLate.setText(String.valueOf(fl));
                                    if (tvAbsent  != null) tvAbsent.setText(String.valueOf(fa));
                                });
                            });
                });
    }

    private void loadAttentionStudents() {
        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        if (user == null) return;
        String section = user.getSection();
        if (section == null || section.isEmpty()) { showEmptyAttention(); return; }
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener(userSnap -> {
                    if (getActivity() == null) return;
                    List<DocumentSnapshot> students = userSnap.getDocuments();
                    if (students.isEmpty()) {
                        getActivity().runOnUiThread(this::showEmptyAttention);
                        return;
                    }
                    final int[] processed = {0};
                    final int total = students.size();

                    for (DocumentSnapshot studentDoc : students) {
                        String uid       = studentDoc.getId();
                        String firstname = studentDoc.getString("firstname");
                        String lastname  = studentDoc.getString("lastname");
                        String schoolId  = studentDoc.getString("studentID");
                        String fullName  = (lastname != null ? lastname : "")
                                + ", " + (firstname != null ? firstname : "");

                        db.collection("attendance")
                                .whereEqualTo("studentId", uid)
                                .whereEqualTo("status", "Absent")
                                .get()
                                .addOnSuccessListener(absSnap -> {
                                    if (getActivity() == null) return;
                                    int absences = absSnap.size();
                                    if (absences >= ABSENCE_THRESHOLD)
                                        getActivity().runOnUiThread(() ->
                                                addAttentionStudent(fullName, schoolId, absences));
                                    synchronized (processed) {
                                        processed[0]++;
                                        if (processed[0] == total)
                                            getActivity().runOnUiThread(this::finishLoadingAttention);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    synchronized (processed) {
                                        processed[0]++;
                                        if (processed[0] == total)
                                            getActivity().runOnUiThread(this::finishLoadingAttention);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (getActivity() != null) getActivity().runOnUiThread(this::showEmptyAttention);
                });
    }

    private void addAttentionStudent(String name, String schoolId, int absences) {
        if (llAttentionStudents == null || getContext() == null) return;
        if (progressAttention != null) progressAttention.setVisibility(View.GONE);
        if (tvAttentionEmpty  != null) tvAttentionEmpty.setVisibility(View.GONE);

        View item = LayoutInflater.from(getContext())
                .inflate(R.layout.item_attention_student, llAttentionStudents, false);
        ((TextView) item.findViewById(R.id.tv_attention_student_name)).setText(name);
        ((TextView) item.findViewById(R.id.tv_attention_student_id))
                .setText(schoolId != null ? schoolId : "—");
        ((TextView) item.findViewById(R.id.tv_absence_count)).setText(String.valueOf(absences));
        llAttentionStudents.addView(item);
    }

    private void finishLoadingAttention() {
        if (progressAttention != null) progressAttention.setVisibility(View.GONE);
        if (llAttentionStudents != null && llAttentionStudents.getChildCount() <= 2)
            showEmptyAttention();
    }

    private void showEmptyAttention() {
        if (progressAttention != null) progressAttention.setVisibility(View.GONE);
        if (tvAttentionEmpty  != null) tvAttentionEmpty.setVisibility(View.VISIBLE);
    }
}