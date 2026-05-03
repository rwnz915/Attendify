package com.example.attendify.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * TeacherContactParentFragment
 *
 * Shows all students in the teacher's sections with their parent contact info.
 * Data is loaded from:
 *   - users  collection  → students whose section matches teacher's sections
 *   - parents/{studentUid} → contact, parent, student fields
 *
 * UI mirrors SecretaryClassListFragment (header + back button + RecyclerView).
 */
public class TeacherContactParentFragment extends Fragment {

    // ── View refs ─────────────────────────────────────────────────────────────
    private ProgressBar progressContact;
    private RecyclerView rvContact;
    private TextView tvContactEmpty;
    private TextView tvContactSection;
    private TextView tvContactCount;
    private EditText etContactSearch;

    // ── Data ──────────────────────────────────────────────────────────────────
    private final List<ParentInfo> allItems = new ArrayList<>();
    private ContactAdapter adapter;

    // ── Simple data holder ────────────────────────────────────────────────────
    static class ParentInfo {
        String studentName;  // from parents doc — field "student"
        String parentName;   // from parents doc — field "parent"
        String contact;      // from parents doc — field "contact"
        String studentUid;   // document ID = student's Firebase UID
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_teacher_contact_parent, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Bind views ────────────────────────────────────────────────────────
        progressContact  = view.findViewById(R.id.progress_contact);
        rvContact        = view.findViewById(R.id.rv_contact);
        tvContactEmpty   = view.findViewById(R.id.tv_contact_empty);
        tvContactSection = view.findViewById(R.id.tv_contact_section);
        tvContactCount   = view.findViewById(R.id.tv_contact_count);
        etContactSearch  = view.findViewById(R.id.et_contact_search);

        // ── Back button ───────────────────────────────────────────────────────
        view.findViewById(R.id.btn_contact_back).setOnClickListener(v -> {
            if (getActivity() != null)
                getActivity().getSupportFragmentManager().popBackStack();
        });

        // ── Apply theme to header ─────────────────────────────────────────────
        UserProfile me = AuthRepository.getInstance().getLoggedInUser();
        if (me != null) {
            ThemeApplier.applyHeader(requireContext(), me.getRole(),
                    view.findViewById(R.id.teacher_contact_header));
        }

        // ── RecyclerView ──────────────────────────────────────────────────────
        rvContact.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ContactAdapter(new ArrayList<>());
        rvContact.setAdapter(adapter);

        // ── Section label set dynamically after subjects are fetched ──────────


        // ── Search filter ─────────────────────────────────────────────────────
        etContactSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filterList(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        // ── Load data ─────────────────────────────────────────────────────────
        loadParents(me);
    }

    // ── Fetch parents from Firestore ──────────────────────────────────────────

    private void loadParents(UserProfile teacher) {
        if (teacher == null || teacher.getId() == null) {
            showEmpty();
            return;
        }

        showLoading(true);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Step 1: Get all subjects where this teacher is the instructor.
        //         Each subject doc has a "section" field — that's the source of truth.
        //         (teacher.getSections() on UserProfile is often null for teachers)
        db.collection("subjects")
                .whereEqualTo("teacherId", teacher.getId())
                .get()
                .addOnSuccessListener(subjectDocs -> {
                    if (subjectDocs.isEmpty()) {
                        safeRunOnUiThread(this::showEmpty);
                        return;
                    }

                    // Collect unique sections from all the teacher's subjects
                    List<String> sections = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : subjectDocs) {
                        String sec = doc.getString("section");
                        if (sec != null && !sec.isEmpty() && !sections.contains(sec)) {
                            sections.add(sec);
                        }
                    }

                    if (sections.isEmpty()) {
                        safeRunOnUiThread(this::showEmpty);
                        return;
                    }

                    // Update the header subtitle with actual sections found
                    safeRunOnUiThread(() ->
                            tvContactSection.setText("Sections: " + String.join(", ", sections)));

                    // Step 2: Get students in those sections.
                    //         Firestore whereIn supports max 10 values.
                    List<String> batch = sections.size() > 10 ? sections.subList(0, 10) : sections;

                    db.collection("users")
                            .whereEqualTo("role", "student")
                            .whereIn("section", batch)
                            .get()
                            .addOnSuccessListener(studentDocs -> {
                                if (studentDocs.isEmpty()) {
                                    safeRunOnUiThread(this::showEmpty);
                                    return;
                                }

                                // Step 3: For each student UID, fetch parents/{uid}
                                int total = studentDocs.size();
                                List<ParentInfo> results = new ArrayList<>();
                                int[] fetched = {0};

                                for (QueryDocumentSnapshot studentDoc : studentDocs) {
                                    String uid = studentDoc.getId();
                                    String fn  = studentDoc.getString("firstname");
                                    String ln  = studentDoc.getString("lastname");

                                    db.collection("parents").document(uid).get()
                                            .addOnSuccessListener(parentDoc -> {
                                                if (parentDoc.exists()) {
                                                    ParentInfo info = new ParentInfo();
                                                    info.studentUid  = uid;
                                                    info.studentName = parentDoc.getString("student");
                                                    info.parentName  = parentDoc.getString("parent");
                                                    info.contact     = parentDoc.getString("contact");
                                                    // Fallback to users doc if parent doc missing name
                                                    if (info.studentName == null || info.studentName.isEmpty()) {
                                                        info.studentName = (ln != null && fn != null)
                                                                ? ln + ", " + fn
                                                                : (fn != null ? fn : "");
                                                    }
                                                    synchronized (results) { results.add(info); }
                                                }
                                                checkDone(results, total, fetched);
                                            })
                                            .addOnFailureListener(e -> checkDone(results, total, fetched));
                                }
                            })
                            .addOnFailureListener(e -> safeRunOnUiThread(this::showEmpty));
                })
                .addOnFailureListener(e -> safeRunOnUiThread(this::showEmpty));
    }

    /** Called after each parent doc fetch; renders once all are done. */
    private void checkDone(List<ParentInfo> results, int total, int[] fetched) {
        synchronized (fetched) { fetched[0]++; }
        if (fetched[0] >= total) {
            allItems.clear();
            allItems.addAll(results);
            safeRunOnUiThread(() -> {
                showLoading(false);
                if (allItems.isEmpty()) {
                    showEmpty();
                } else {
                    showList(allItems);
                }
            });
        }
    }

    // ── Search filter ─────────────────────────────────────────────────────────

    private void filterList(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.updateList(allItems);
            tvContactCount.setText(String.valueOf(allItems.size()));
            return;
        }
        String q = query.trim().toLowerCase();
        List<ParentInfo> filtered = new ArrayList<>();
        for (ParentInfo p : allItems) {
            if ((p.studentName != null && p.studentName.toLowerCase().contains(q))
                    || (p.parentName != null && p.parentName.toLowerCase().contains(q))
                    || (p.contact    != null && p.contact.contains(q))) {
                filtered.add(p);
            }
        }
        adapter.updateList(filtered);
        tvContactCount.setText(String.valueOf(filtered.size()));
    }

    // ── UI state helpers ──────────────────────────────────────────────────────

    private void showLoading(boolean loading) {
        progressContact.setVisibility(loading ? View.VISIBLE : View.GONE);
        rvContact.setVisibility(View.GONE);
        tvContactEmpty.setVisibility(View.GONE);
    }

    private void showEmpty() {
        progressContact.setVisibility(View.GONE);
        rvContact.setVisibility(View.GONE);
        tvContactEmpty.setVisibility(View.VISIBLE);
        if (tvContactCount != null) tvContactCount.setText("0");
    }

    private void showList(List<ParentInfo> list) {
        progressContact.setVisibility(View.GONE);
        tvContactEmpty.setVisibility(View.GONE);
        rvContact.setVisibility(View.VISIBLE);
        tvContactCount.setText(String.valueOf(list.size()));
        adapter.updateList(list);
    }

    private void safeRunOnUiThread(Runnable r) {
        if (getActivity() != null) getActivity().runOnUiThread(r);
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.VH> {

        private List<ParentInfo> list;

        ContactAdapter(List<ParentInfo> list) {
            this.list = new ArrayList<>(list);
        }

        void updateList(List<ParentInfo> newList) {
            list = new ArrayList<>(newList);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_parent_contact, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ParentInfo p = list.get(position);

            h.tvStudentName.setText(p.studentName != null ? p.studentName : "—");
            h.tvParentName.setText(p.parentName   != null ? p.parentName  : "Parent");
            h.tvContact.setText(p.contact         != null ? p.contact     : "No contact number");

            // Apply theme — replaces hardcoded blue on both the call button and icon circle
            String role = AuthRepository.getInstance().getLoggedInUser() != null
                    ? AuthRepository.getInstance().getLoggedInUser().getRole() : "teacher";
            ThemeApplier.applyButton(h.itemView.getContext(), role, h.btnCall);
            ThemeApplier.applyOval(h.itemView.getContext(), role, h.ivPersonIcon);

            h.btnCall.setOnClickListener(v -> {
                if (p.contact == null || p.contact.trim().isEmpty()) {
                    Toast.makeText(requireContext(),
                            "No contact number available", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Dial intent — opens phone app with number pre-filled
                Intent dial = new Intent(Intent.ACTION_DIAL,
                        Uri.parse("tel:" + p.contact.trim()));
                startActivity(dial);
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView  tvStudentName, tvParentName, tvContact;
            View      btnCall;
            ImageView ivPersonIcon;

            VH(View v) {
                super(v);
                tvStudentName = v.findViewById(R.id.tv_parent_student_name);
                tvParentName  = v.findViewById(R.id.tv_parent_name);
                tvContact     = v.findViewById(R.id.tv_parent_contact);
                btnCall       = v.findViewById(R.id.btn_call_parent);
                ivPersonIcon  = v.findViewById(R.id.iv_person_icon);
            }
        }
    }
}