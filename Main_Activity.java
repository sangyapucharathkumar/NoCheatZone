package com.example.nocheatzone;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

public class Main_Activity extends InternetCheckActivity {

    private ExamHistoryAdapter examHistoryAdapter;
    private SessionManager sessionManager;

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Initialize SessionManager
        sessionManager = new SessionManager(this);

        // 2. Check if user is NOT logged in
        com.google.firebase.auth.FirebaseUser currentUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (!sessionManager.isLoggedIn() || currentUser == null || !currentUser.isEmailVerified()) {
            Intent intent = new Intent(Main_Activity.this, Login_Activity.class);
            startActivity(intent);
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // Intelligently hook up the Root background to Dark Mode
        findViewById(android.R.id.content).setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.main_bg));

        // Dynamically toggle Status Bar Battery/Clock icons depending on the Dark Theme!
        android.content.SharedPreferences prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        androidx.core.view.WindowInsetsControllerCompat insetsController = 
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(!isDarkMode);
        }

        // Motivational Slogans
        TextView txtSlogan = findViewById(R.id.txt_slogan);
        if (txtSlogan != null) {
            String[] slogans = {
                "Success favors the prepared mind.",
                "Fair play is the best way to shine.",
                "True knowledge is your greatest asset.",
                "Empowering fair students, deterring the rest.",
                "Your integrity is your true score.",
                "Honesty is the shortest path to success.",
                "Real learning happens without shortcuts."
            };
            int randomIndex = new java.util.Random().nextInt(slogans.length);
            txtSlogan.setText(slogans[randomIndex]);
        }

        // Bottom Navigation
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }

        // Card click listeners
        MaterialCardView createExamCard = findViewById(R.id.card_create_exam);
        if (createExamCard != null) {
            createExamCard.setOnClickListener(view ->
                    startActivity(new Intent(Main_Activity.this, Host_Activity.class)));
        }

        MaterialCardView joinExamCard = findViewById(R.id.card_join_exam);
        if (joinExamCard != null) {
            joinExamCard.setOnClickListener(view ->
                    startActivity(new Intent(Main_Activity.this, Join_Exam_Activity.class)));
        }

        TextView seeall = findViewById(R.id.seeall);
        if (seeall != null) {
            seeall.setOnClickListener(view -> {
                Intent intent = new Intent(Main_Activity.this, Exams_Activity.class);
                startActivity(intent);
            });
        }

        // Initialize adapter with data from ExamRepository
        java.util.List<com.example.nocheatzone.model.Exam> initialExams =
                ExamRepository.getInstance().getAllExams();
        if (initialExams == null) {
            initialExams = new java.util.ArrayList<>();
        }
        examHistoryAdapter = new ExamHistoryAdapter(initialExams);

        // RecyclerView wired to ExamRepository
        RecyclerView examRecyclerView = findViewById(R.id.recycler_exams);
        if (examRecyclerView != null) {
            examRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            examRecyclerView.setAdapter(examHistoryAdapter);
        }

        if (bottomNavigationView != null) {
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    return true;
                } else if (itemId == R.id.nav_exams) {
                    startActivity(new Intent(Main_Activity.this, Exams_Activity.class));
                    overridePendingTransition(0, 0);
                    return true;
                } else if (itemId == R.id.nav_settings) {
                    startActivity(new Intent(Main_Activity.this, Settings_Activity.class));
                    overridePendingTransition(0, 0);
                    return true;
                }
                return false;
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (examHistoryAdapter != null) {
            java.util.List<com.example.nocheatzone.model.Exam> latestData =
                    ExamRepository.getInstance().getAllExams();
            if (latestData == null) {
                latestData = new java.util.ArrayList<>();
            }
            examHistoryAdapter.updateData(latestData);
        }
    }
}
