package com.example.attendify.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.attendify.MainActivity;
import com.example.attendify.R;
import com.example.attendify.ThemeApplier;
import com.example.attendify.models.UserProfile;
import com.example.attendify.notifications.NotificationStore;
import com.example.attendify.notifications.NotificationStore.NotifItem;
import com.example.attendify.repository.AuthRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * NotificationsFragment
 *
 * Displays all stored notifications for the current user.
 * - Searchable by title/body
 * - Clearable (clears all from Firestore + cache)
 * - Same header style as all other pages (themed by role)
 * - Uses NotificationStore for Firestore + local cache reads
 */
public class NotificationsFragment extends Fragment {

    private List<NotifItem> allItems = new ArrayList<>();
    private LinearLayout container;
    private TextView tvEmpty;
    private ProgressBar progressBar;
    private EditText etSearch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserProfile user = AuthRepository.getInstance().getLoggedInUser();
        String role = user != null ? user.getRole() : "student";

        // ── Header theming ───────────────────────────────────────────────────
        View header = view.findViewById(R.id.notif_header);
        if (header != null) {
            ThemeApplier.applyHeader(requireContext(), role, header);
            header.setPadding(
                    header.getPaddingLeft(),
                    header.getPaddingTop() + MainActivity.statusBarHeight,
                    header.getPaddingRight(),
                    header.getPaddingBottom());
        }

        // ── Back button ──────────────────────────────────────────────────────
        ImageView btnBack = view.findViewById(R.id.btn_notif_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getActivity() != null)
                    getActivity().getOnBackPressedDispatcher().onBackPressed();
            });
        }

        // ── Clear all ────────────────────────────────────────────────────────
        TextView btnClear = view.findViewById(R.id.btn_notif_clear);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                if (user == null) return;
                NotificationStore.getInstance().clearAll(requireContext(), user.getId());
                allItems.clear();
                renderList(allItems);
                Toast.makeText(getContext(), "Notifications cleared", Toast.LENGTH_SHORT).show();
            });
        }

        // ── Search ───────────────────────────────────────────────────────────
        etSearch = view.findViewById(R.id.et_notif_search);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { filterAndRender(s.toString()); }
            });
        }

        container   = view.findViewById(R.id.notif_list_container);
        tvEmpty     = view.findViewById(R.id.tv_notif_empty);
        progressBar = view.findViewById(R.id.notif_progress);

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        if (user == null) {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            return;
        }

        NotificationStore.getInstance().load(requireContext(), user.getId(), items -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                allItems = items;
                renderList(allItems);
                // Mark all as read now that the user has seen them
                NotificationStore.getInstance().markAllRead(requireContext(), user.getId());
            });
        });
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void filterAndRender(String query) {
        if (query == null || query.isEmpty()) {
            renderList(allItems);
            return;
        }
        String q = query.toLowerCase(Locale.ENGLISH);
        List<NotifItem> filtered = new ArrayList<>();
        for (NotifItem ni : allItems) {
            if ((ni.title != null && ni.title.toLowerCase(Locale.ENGLISH).contains(q))
                    || (ni.body != null && ni.body.toLowerCase(Locale.ENGLISH).contains(q))) {
                filtered.add(ni);
            }
        }
        renderList(filtered);
    }

    private void renderList(List<NotifItem> items) {
        if (container == null) return;
        container.removeAllViews();

        if (items.isEmpty()) {
            if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);

        LayoutInflater li = LayoutInflater.from(requireContext());
        for (NotifItem ni : items) {
            View card = li.inflate(R.layout.item_notification, container, false);

            TextView tvTitle = card.findViewById(R.id.tv_notif_title);
            TextView tvBody  = card.findViewById(R.id.tv_notif_body);
            TextView tvTime  = card.findViewById(R.id.tv_notif_time);
            View unreadDot   = card.findViewById(R.id.view_unread_dot);

            if (tvTitle != null) tvTitle.setText(ni.title != null ? ni.title : "");
            if (tvBody  != null) tvBody.setText(ni.body != null ? ni.body : "");
            if (tvTime  != null) tvTime.setText(formatTs(ni.timestamp));
            if (unreadDot != null) unreadDot.setVisibility(ni.read ? View.GONE : View.VISIBLE);

            container.addView(card);
        }
    }

    private String formatTs(String ts) {
        if (ts == null || ts.isEmpty()) return "";
        try {
            SimpleDateFormat inFmt  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
            SimpleDateFormat outFmt = new SimpleDateFormat("MMM d, h:mm a", Locale.ENGLISH);
            Date d = inFmt.parse(ts);
            return d != null ? outFmt.format(d) : ts;
        } catch (ParseException e) {
            return ts;
        }
    }
}