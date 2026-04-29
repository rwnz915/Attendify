package com.example.attendify.repository;

import com.example.attendify.models.ExcuseLetter;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore CRUD for the "excuse_letters" collection.
 *
 * v2 changes:
 *  - submitExcuseLetter now takes subjectId, subjectName, section, teacherId
 *  - getPendingByTeacher(teacherId) — teachers only see letters for their subjects
 *  - getAllByTeacher(teacherId)     — all-status view, filtered same way
 */
public class ExcuseLetterRepository {

    private static ExcuseLetterRepository instance;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private static final String COLLECTION = "excuse_letters";

    private ExcuseLetterRepository() {}

    public static ExcuseLetterRepository getInstance() {
        if (instance == null) instance = new ExcuseLetterRepository();
        return instance;
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public interface SubmitCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface ListCallback {
        void onSuccess(List<ExcuseLetter> letters);
        void onFailure(String errorMessage);
    }

    // ── Student: submit excuse letter (v2 — image required, subject required) ─

    public void submitExcuseLetter(String studentId, String studentName,
                                   String studentNumber, String section,
                                   String subjectId, String subjectName, String teacherId,
                                   String message,
                                   String imageUrl, String fileId,
                                   SubmitCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("studentId",     studentId);
        data.put("studentName",   studentName);
        data.put("studentNumber", studentNumber);
        data.put("section",       section != null ? section : "");
        data.put("subjectId",     subjectId != null ? subjectId : "");
        data.put("subjectName",   subjectName != null ? subjectName : "");
        data.put("teacherId",     teacherId != null ? teacherId : "");
        data.put("message",       message);
        data.put("imageUrl",      imageUrl != null ? imageUrl : "");
        data.put("fileId",        fileId != null ? fileId : "");
        data.put("status",        "pending");
        data.put("submittedAt",   FieldValue.serverTimestamp());

        db.collection(COLLECTION)
                .add(data)
                .addOnSuccessListener(ref -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Student: get own excuse letters (ordered newest first) ────────────────

    public void getStudentExcuseLetters(String studentId, ListCallback callback) {
        db.collection(COLLECTION)
                .whereEqualTo("studentId", studentId)
                .orderBy("submittedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ExcuseLetter> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ExcuseLetter letter = doc.toObject(ExcuseLetter.class);
                        if (letter != null) {
                            letter.setDocId(doc.getId());
                            list.add(letter);
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Teacher: get PENDING letters for this teacher's subjects only ──────────

    public void getPendingByTeacher(String teacherId, ListCallback callback) {
        db.collection(COLLECTION)
                .whereEqualTo("teacherId", teacherId)
                .whereEqualTo("status", "pending")
                .orderBy("submittedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ExcuseLetter> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ExcuseLetter letter = doc.toObject(ExcuseLetter.class);
                        if (letter != null) {
                            letter.setDocId(doc.getId());
                            list.add(letter);
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Teacher: get ALL letters (all statuses) for this teacher's subjects ────

    public void getAllByTeacher(String teacherId, ListCallback callback) {
        db.collection(COLLECTION)
                .whereEqualTo("teacherId", teacherId)
                .orderBy("submittedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ExcuseLetter> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ExcuseLetter letter = doc.toObject(ExcuseLetter.class);
                        if (letter != null) {
                            letter.setDocId(doc.getId());
                            list.add(letter);
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Legacy — kept for backward compat (secretary view) ───────────────────

    public void getPendingExcuseLetters(ListCallback callback) {
        db.collection(COLLECTION)
                .whereEqualTo("status", "pending")
                .orderBy("submittedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ExcuseLetter> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ExcuseLetter letter = doc.toObject(ExcuseLetter.class);
                        if (letter != null) {
                            letter.setDocId(doc.getId());
                            list.add(letter);
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void getAllExcuseLetters(ListCallback callback) {
        db.collection(COLLECTION)
                .orderBy("submittedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ExcuseLetter> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ExcuseLetter letter = doc.toObject(ExcuseLetter.class);
                        if (letter != null) {
                            letter.setDocId(doc.getId());
                            list.add(letter);
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Teacher: approve or reject (records timestamp of decision) ───────────

    public void updateStatus(String docId, String newStatus, SubmitCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        updates.put("reviewedAt", FieldValue.serverTimestamp());

        db.collection(COLLECTION)
                .document(docId)
                .update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ── Teacher: change a previous decision (approved <-> rejected) ───────────
    //    Alias for updateStatus; kept separate so call sites are self-documenting.

    public void changeDecision(String docId, String newStatus, SubmitCallback callback) {
        updateStatus(docId, newStatus, callback);
    }
}