package com.example.attendify.notifications;

import android.content.Context;
import android.util.Log;

import com.example.attendify.LocalCacheManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NotificationStore
 *
 * Saves notifications to Firestore under:
 *   notifications / {userId} / items / {autoId}  {title, body, timestamp, read}
 *
 * Also caches them locally so the list works offline.
 * Users can clear (delete) all their notifications.
 */
public class NotificationStore {

    private static final String TAG = "NotificationStore";
    private static final String COL_NOTIFS = "notifications";
    private static final String CACHE_KEY  = "notif_list";

    private static NotificationStore instance;
    private NotificationStore() {}

    public static NotificationStore getInstance() {
        if (instance == null) instance = new NotificationStore();
        return instance;
    }

    // ── Model ─────────────────────────────────────────────────────────────────

    public static class NotifItem {
        public String id;
        public String title;
        public String body;
        public String timestamp;
        public boolean read;

        public NotifItem() {}
        public NotifItem(String id, String title, String body, String timestamp, boolean read) {
            this.id = id; this.title = title; this.body = body;
            this.timestamp = timestamp; this.read = read;
        }
    }

    public interface NotifCallback {
        void onLoaded(List<NotifItem> items);
    }

    // ── Save a notification ───────────────────────────────────────────────────

    public void save(Context ctx, String userId, String title, String body) {
        if (userId == null || userId.isEmpty()) return;

        String ts = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).format(new Date());

        Map<String, Object> data = new HashMap<>();
        data.put("title",     title);
        data.put("body",      body);
        data.put("timestamp", ts);
        data.put("read",      false);

        // Save to Firestore if online; cache regardless for offline reading
        if (LocalCacheManager.isOnline(ctx)) {
            FirebaseFirestore.getInstance()
                    .collection(COL_NOTIFS)
                    .document(userId)
                    .collection("items")
                    .add(data)
                    .addOnSuccessListener(ref -> Log.d(TAG, "Notif saved: " + ref.getId()))
                    .addOnFailureListener(e -> Log.w(TAG, "Notif save failed", e));
        }

        // Always persist locally
        appendToCache(ctx, userId, new NotifItem(ts, title, body, ts, false));
    }

    // ── Load notifications ────────────────────────────────────────────────────

    /** Loads from Firestore (newest first), falls back to local cache if offline. */
    public void load(Context ctx, String userId, NotifCallback cb) {
        if (!LocalCacheManager.isOnline(ctx)) {
            cb.onLoaded(loadFromCache(ctx, userId));
            return;
        }

        FirebaseFirestore.getInstance()
                .collection(COL_NOTIFS)
                .document(userId)
                .collection("items")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(snap -> {
                    List<NotifItem> items = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        NotifItem ni = new NotifItem();
                        ni.id        = doc.getId();
                        ni.title     = doc.getString("title");
                        ni.body      = doc.getString("body");
                        ni.timestamp = doc.getString("timestamp");
                        Boolean r    = doc.getBoolean("read");
                        ni.read      = r != null && r;
                        items.add(ni);
                    }
                    saveListToCache(ctx, userId, items);
                    cb.onLoaded(items);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Firestore load failed — using cache", e);
                    cb.onLoaded(loadFromCache(ctx, userId));
                });
    }

    // ── Unread helpers ────────────────────────────────────────────────────────

    /** Returns true if there is at least one unread notification in the local cache. */
    public boolean hasUnread(Context ctx, String userId) {
        for (NotifItem ni : loadFromCache(ctx, userId)) {
            if (!ni.read) return true;
        }
        return false;
    }

    /**
     * Marks every notification as read in Firestore and in the local cache.
     * Call this when the user opens the Notifications screen.
     */
    public void markAllRead(Context ctx, String userId) {
        List<NotifItem> items = loadFromCache(ctx, userId);
        boolean anyUnread = false;
        for (NotifItem ni : items) {
            if (!ni.read) { ni.read = true; anyUnread = true; }
        }
        if (!anyUnread) return;
        saveListToCache(ctx, userId, items);

        if (!LocalCacheManager.isOnline(ctx)) return;

        FirebaseFirestore.getInstance()
                .collection(COL_NOTIFS)
                .document(userId)
                .collection("items")
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot doc : snap) {
                        doc.getReference().update("read", true);
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "markAllRead failed", e));
    }

    // ── Clear all notifications ───────────────────────────────────────────────

    public void clearAll(Context ctx, String userId) {
        // Clear local cache
        LocalCacheManager.getInstance(ctx).putRaw(userId, CACHE_KEY, "[]");

        if (!LocalCacheManager.isOnline(ctx)) return;

        // Delete from Firestore
        FirebaseFirestore.getInstance()
                .collection(COL_NOTIFS)
                .document(userId)
                .collection("items")
                .get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot doc : snap) doc.getReference().delete();
                })
                .addOnFailureListener(e -> Log.w(TAG, "clearAll failed", e));
    }

    // ── Cache helpers ─────────────────────────────────────────────────────────

    private void appendToCache(Context ctx, String userId, NotifItem item) {
        List<NotifItem> list = loadFromCache(ctx, userId);
        list.add(0, item); // newest first
        if (list.size() > 100) list = list.subList(0, 100);
        saveListToCache(ctx, userId, list);
    }

    private List<NotifItem> loadFromCache(Context ctx, String userId) {
        String json = LocalCacheManager.getInstance(ctx).getRaw(userId, CACHE_KEY);
        List<NotifItem> items = new ArrayList<>();
        if (json == null || json.isEmpty()) return items;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                items.add(new NotifItem(
                        o.optString("id", ""),
                        o.optString("title", ""),
                        o.optString("body", ""),
                        o.optString("timestamp", ""),
                        o.optBoolean("read", false)));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Cache parse error", e);
        }
        return items;
    }

    private void saveListToCache(Context ctx, String userId, List<NotifItem> items) {
        try {
            JSONArray arr = new JSONArray();
            for (NotifItem ni : items) {
                JSONObject o = new JSONObject();
                o.put("id",        ni.id != null ? ni.id : "");
                o.put("title",     ni.title != null ? ni.title : "");
                o.put("body",      ni.body != null ? ni.body : "");
                o.put("timestamp", ni.timestamp != null ? ni.timestamp : "");
                o.put("read",      ni.read);
                arr.put(o);
            }
            LocalCacheManager.getInstance(ctx).putRaw(userId, CACHE_KEY, arr.toString());
        } catch (JSONException e) {
            Log.w(TAG, "saveListToCache error", e);
        }
    }
}