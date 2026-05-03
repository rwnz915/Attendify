package com.example.attendify;

import android.app.Application;

import com.example.attendify.repository.AttendanceRepository;
import com.example.attendify.repository.AuthRepository;
import com.example.attendify.repository.StudentRepository;
import com.example.attendify.repository.SubjectRepository;

public class AttendifyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AuthRepository.getInstance().init(this);
        AttendanceRepository.getInstance().init(this);
        SubjectRepository.getInstance().init(this);
        StudentRepository.getInstance().init(this);
    }
}