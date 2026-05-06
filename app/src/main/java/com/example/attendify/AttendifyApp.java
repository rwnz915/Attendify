package com.example.attendify;

import android.app.Application;

import com.example.attendify.notifications.ClassNotificationScheduler;
import com.example.attendify.notifications.NotificationHelper;
import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.StudentRepository;
import com.example.attendify.repository.SubjectRepository;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;

public class AttendifyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Enable Firestore disk cache (10 MB default, increase for attendance-heavy apps)
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                                .setSizeBytes(50 * 1024 * 1024L) // 50 MB
                                .build())
                .build();
        db.setFirestoreSettings(settings);

        AuthRepository.getInstance().init(this);
        AttendanceRepository.getInstance().init(this);
        SubjectRepository.getInstance().init(this);
        StudentRepository.getInstance().init(this);

        // Create notification channels (required on API 26+)
        NotificationHelper.createChannels(this);
    }
}