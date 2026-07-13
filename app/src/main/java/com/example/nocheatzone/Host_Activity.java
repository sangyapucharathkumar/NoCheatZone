package com.example.nocheatzone;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Host_Activity extends InternetCheckActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        android.view.View createCard = findViewById(R.id.card_create_exam);
        if (createCard != null) {
            createCard.setOnClickListener(v ->
                    startActivity(new Intent(Host_Activity.this, CreateExamActivity.class)));
        }

        android.view.View back = findViewById(R.id.back);
        if (back != null) {
            back.setOnClickListener(view ->
                    startActivity(new Intent(Host_Activity.this, Main_Activity.class)));
        }

        android.view.View monitorCard = findViewById(R.id.card_monitor_students);
        if (monitorCard != null) {
            monitorCard.setOnClickListener(v -> {
                Intent intent = new Intent(Host_Activity.this, Hosted_Activity.class);
                intent.putExtra(Hosted_Activity.EXTRA_MODE, Hosted_Activity.MODE_MONITOR);
                startActivity(intent);
            });
        }

        android.view.View resultCard = findViewById(R.id.card_view_results);
        if (resultCard != null) {
            resultCard.setOnClickListener(v -> {
                Intent intent = new Intent(Host_Activity.this, Hosted_Activity.class);
                intent.putExtra(Hosted_Activity.EXTRA_MODE, Hosted_Activity.MODE_RESULTS);
                startActivity(intent);
            });
        }
        // Correctly color the status bar gap with the start color of the gradient_purple
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        // Use light icons since the deep purple is very dark
        androidx.core.view.WindowInsetsControllerCompat insetsController = 
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        // Initialize Hosting History RecyclerView (newly added to layout)
        androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.recycler_hosted_exams);
        android.widget.TextView emptyText = findViewById(R.id.text_no_hosted_dashboard);
        
        java.util.List<com.example.nocheatzone.model.Exam> hostedExams = ExamRepository.getInstance().getHostedExams();
        hostedExams.sort((a, b) -> Long.compare(b.getCreationTimestamp(), a.getCreationTimestamp()));
        
        if (rv != null && emptyText != null) {
            if (hostedExams.isEmpty()) {
                rv.setVisibility(android.view.View.GONE);
                emptyText.setVisibility(android.view.View.VISIBLE);
            } else {
                rv.setVisibility(android.view.View.VISIBLE);
                emptyText.setVisibility(android.view.View.GONE);
                rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
                rv.setAdapter(new ExamHistoryAdapter(hostedExams, exam -> {
                    Intent intent = new Intent(
                            Host_Activity.this,
                            exam.isEnded() ? HostResultActivity.class : MonitoringActivity.class
                    );
                    intent.putExtra("EXAM_ID", exam.getId());
                    startActivity(intent);
                }));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.recycler_hosted_exams);
        android.widget.TextView emptyText = findViewById(R.id.text_no_hosted_dashboard);
        
        if (rv != null && emptyText != null) {
            java.util.List<com.example.nocheatzone.model.Exam> hostedExams = ExamRepository.getInstance().getHostedExams();
            hostedExams.sort((a, b) -> Long.compare(b.getCreationTimestamp(), a.getCreationTimestamp()));
            
            if (hostedExams.isEmpty()) {
                rv.setVisibility(android.view.View.GONE);
                emptyText.setVisibility(android.view.View.VISIBLE);
            } else {
                rv.setVisibility(android.view.View.VISIBLE);
                emptyText.setVisibility(android.view.View.GONE);
                if (rv.getLayoutManager() == null) {
                    rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
                }
                rv.setAdapter(new ExamHistoryAdapter(hostedExams, exam -> {
                    Intent intent = new Intent(
                            Host_Activity.this,
                            exam.isEnded() ? HostResultActivity.class : MonitoringActivity.class
                    );
                    intent.putExtra("EXAM_ID", exam.getId());
                    startActivity(intent);
                }));
            }
        }
    }
}
