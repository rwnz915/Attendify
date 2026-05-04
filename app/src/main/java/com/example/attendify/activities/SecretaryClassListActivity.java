package com.example.attendify.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.models.Student;
import com.example.attendify.models.UserProfile;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.StudentRepository;

import java.util.List;

public class SecretaryClassListActivity extends AppCompatActivity {

    private ProgressBar   progressClasslist;
    private RecyclerView  rvClasslist;
    private TextView      tvClasslistEmpty;
    private TextView      tvClasslistSection;
    private TextView      tvClasslistCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_secretary_class_list);

        androidx.activity.EdgeToEdge.enable(this);

        // ── Bind views ────────────────────────────────────────────────────────
        progressClasslist   = findViewById(R.id.progress_classlist);
        rvClasslist         = findViewById(R.id.rv_classlist);
        tvClasslistEmpty    = findViewById(R.id.tv_classlist_empty);
        tvClasslistSection  = findViewById(R.id.tv_classlist_section);
        tvClasslistCount    = findViewById(R.id.tv_classlist_count);

        // ── Back button ───────────────────────────────────────────────────────
        View btnBack = findViewById(R.id.btn_classlist_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // ── Apply saved theme to header ───────────────────────────────────────
        UserProfile clThemeUser = AuthRepository.getInstance().getLoggedInUser();
        if (clThemeUser != null) {
            ThemeApplier.applyHeader(this, clThemeUser.getRole(),
                    findViewById(R.id.sec_classlist_header));
        }

        // ── RecyclerView setup ────────────────────────────────────────────────
        rvClasslist.setLayoutManager(new LinearLayoutManager(this));

        // ── Load data ─────────────────────────────────────────────────────────
        UserProfile secretary = AuthRepository.getInstance().getLoggedInUser();
        if (secretary == null) return;

        String section = secretary.getSection();
        if (tvClasslistSection != null) {
            tvClasslistSection.setText(section != null ? "Section: " + section : "");
        }

        loadStudents(section);
    }

    // ── Fetch students by section from Firestore ──────────────────────────────

    private void loadStudents(String section) {
        if (section == null || section.isEmpty()) {
            showEmpty();
            return;
        }

        showLoading(true);

        StudentRepository.getInstance().getStudentsBySection(section,
                new StudentRepository.StudentsCallback() {
                    @Override
                    public void onSuccess(List<Student> students) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            if (students.isEmpty()) {
                                showEmpty();
                            } else {
                                showStudents(students);
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            showEmpty();
                        });
                    }
                });
    }

    // ── UI state helpers ──────────────────────────────────────────────────────

    private void showLoading(boolean loading) {
        progressClasslist.setVisibility(loading ? View.VISIBLE : View.GONE);
        rvClasslist.setVisibility(View.GONE);
        tvClasslistEmpty.setVisibility(View.GONE);
    }

    private void showEmpty() {
        progressClasslist.setVisibility(View.GONE);
        rvClasslist.setVisibility(View.GONE);
        tvClasslistEmpty.setVisibility(View.VISIBLE);
        if (tvClasslistCount != null) tvClasslistCount.setText("0");
    }

    private void showStudents(List<Student> students) {
        progressClasslist.setVisibility(View.GONE);
        tvClasslistEmpty.setVisibility(View.GONE);
        rvClasslist.setVisibility(View.VISIBLE);

        if (tvClasslistCount != null)
            tvClasslistCount.setText(String.valueOf(students.size()));

        rvClasslist.setAdapter(new ClassListAdapter(students));
    }

    // ── Inner RecyclerView Adapter ────────────────────────────────────────────

    private static class ClassListAdapter
            extends RecyclerView.Adapter<ClassListAdapter.VH> {

        private final List<Student> list;

        ClassListAdapter(List<Student> list) { this.list = list; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_student, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Student s = list.get(position);
            if (h.tvName    != null) h.tvName.setText(s.getName());
            if (h.tvTime    != null)
                h.tvTime.setText(s.getSchoolId() != null ? s.getSchoolId() : "");
            if (h.tvStatusBadge != null) h.tvStatusBadge.setVisibility(View.GONE);
        }

        @Override
        public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvTime, tvStatusBadge;

            VH(View v) {
                super(v);
                tvName        = v.findViewById(R.id.tv_student_name);
                tvTime        = v.findViewById(R.id.tv_student_time);
                tvStatusBadge = v.findViewById(R.id.tv_status_badge);
            }
        }
    }
}
