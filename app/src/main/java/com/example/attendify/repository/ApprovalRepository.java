package com.example.attendify.repository;

import com.example.attendify.models.ApprovalRequest;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches approval/excuse requests from Firestore.
 *
 * Firestore collection: "approvals"
 * Fields per document:
 *   studentId   (string) — Firebase Auth UID of the student
 *   studentName (string) — full name e.g. "Morandarte, Renz"
 *   initial     (string) — first letter of last name e.g. "M"
 *   dateRange   (string) — e.g. "April 9–10, 2026"
 *   reason      (string) — excuse text
 *   status      (string) — "pending" | "approved" | "rejected"
 */
public class ApprovalRepository {

    private static ApprovalRepository instance;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private ApprovalRepository() {}

    public static ApprovalRepository getInstance() {
        if (instance == null) instance = new ApprovalRepository();
        return instance;
    }

    // ── Callback ──────────────────────────────────────────────────────────────

    public interface ApprovalsCallback {
        void onSuccess(List<ApprovalRequest> approvals);
        void onFailure(String errorMessage);
    }

    // ── Get all pending approvals (used by teacher/secretary) ─────────────────

    public void getPendingApprovals(ApprovalsCallback callback) {
        db.collection("approvals")
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ApprovalRequest> list = new ArrayList<>();
                    int i = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ApprovalRequest request = new ApprovalRequest(
                                i++,
                                doc.getString("studentName"),
                                doc.getString("initial"),
                                doc.getString("dateRange"),
                                doc.getString("reason")
                        );
                        list.add(request);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Submit a new approval request (used by student) ───────────────────────

    public interface SubmitCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public void submitApprovalRequest(String studentId, String studentName,
                                      String dateRange, String reason,
                                      SubmitCallback callback) {
        // Derive initial from last name (first character)
        String initial = studentName != null && !studentName.isEmpty()
                ? String.valueOf(studentName.charAt(0)).toUpperCase()
                : "?";

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("studentId",   studentId);
        data.put("studentName", studentName);
        data.put("initial",     initial);
        data.put("dateRange",   dateRange);
        data.put("reason",      reason);
        data.put("status",      "pending");
        data.put("submittedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("approvals").add(data)
                .addOnSuccessListener(ref -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Approve or reject a request ───────────────────────────────────────────

    public void updateStatus(String docId, String newStatus, SubmitCallback callback) {
        db.collection("approvals").document(docId)
                .update("status", newStatus)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}