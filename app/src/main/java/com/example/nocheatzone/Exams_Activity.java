package com.example.nocheatzone;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nocheatzone.model.Exam;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class Exams_Activity extends InternetCheckActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exams);
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        MaterialCardView createdExamCard = findViewById(R.id.card_created_exam);
        if (createdExamCard != null) {
            createdExamCard.setOnClickListener(view ->
                    startActivity(new Intent(Exams_Activity.this, Hosted_Activity.class)));
        }
        MaterialCardView joinedExamCard = findViewById(R.id.card_joined_exam);
        if (joinedExamCard != null) {
            joinedExamCard.setOnClickListener(view ->
                    startActivity(new Intent(Exams_Activity.this, joined_activity.class)));
        }

        // Bottom Navigation
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_exams);
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    startActivity(new android.content.Intent(getApplicationContext(), Main_Activity.class));
                    overridePendingTransition(0, 0);
                    return true;
                } else if (itemId == R.id.nav_exams) {
                    return true;
                } else if (itemId == R.id.nav_settings) {
                    startActivity(new android.content.Intent(getApplicationContext(), Settings_Activity.class));
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
        RecyclerView recyclerView = findViewById(R.id.recycler_exams_history);
        if (recyclerView != null) {
            loadExamsFromFirebase(recyclerView);
        }
    }

    private void loadExamsFromFirebase(RecyclerView recyclerView) {
        // Load from repository instead of Firebase for now to ensure local work done
        List<Exam> exams = ExamRepository.getInstance().getAllExams();
        ExamHistoryAdapter adapter = new ExamHistoryAdapter(exams);
        recyclerView.setAdapter(adapter);
    }
}