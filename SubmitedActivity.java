package com.example.nocheatzone;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SubmitedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_submited);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView title = findViewById(R.id.title_after_submition);
        String examId = getIntent().getStringExtra("EXAM_ID");
        if (title != null) {
            if (examId == null || examId.trim().isEmpty()) {
                title.setText("Exam submitted successfully");
            } else {
                com.example.nocheatzone.model.Exam exam = ExamRepository.getInstance().getExamById(examId);
                if (exam != null && exam.getTitle() != null && !exam.getTitle().trim().isEmpty()) {
                    title.setText("Exam: " + exam.getTitle());
                } else {
                    title.setText("Exam submitted successfully");
                }
            }
        }

        android.view.View home = findViewById(R.id.btn_home);
        if (home != null) {
            home.setOnClickListener(v -> {
                Intent intent = new Intent(SubmitedActivity.this, Main_Activity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        }
    }
}
