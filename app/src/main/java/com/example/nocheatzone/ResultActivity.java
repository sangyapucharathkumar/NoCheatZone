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

import com.google.android.material.button.MaterialButton;

public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_result);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        int score = getIntent().getIntExtra("SCORE", 0);
        int total = getIntent().getIntExtra("TOTAL", 0);

        TextView txtScore = findViewById(R.id.txt_score);
        TextView txtPercentage = findViewById(R.id.txt_percentage);
        MaterialButton btnHome = findViewById(R.id.btn_home);

        txtScore.setText(score + " / " + total);

        int percentage = (total > 0) ? (score * 100 / total) : 0;
        txtPercentage.setText("Score: " + percentage + "%");

        // Pass/Fail indicator
        TextView txtResult = findViewById(R.id.txt_result);
        if (txtResult != null) {
            if (percentage >= 50) {
                txtResult.setText("✅ PASSED");
                txtResult.setTextColor(ContextCompat.getColor(this, R.color.success));
                txtResult.setBackgroundResource(R.drawable.bg_badge_green);
            } else {
                txtResult.setText("❌ FAILED");
                txtResult.setTextColor(ContextCompat.getColor(this, R.color.danger));
                txtResult.setBackgroundResource(R.drawable.bg_badge_red);
            }
        }

        MaterialButton btnShare = findViewById(R.id.btn_share_result);
        MaterialButton btnDownload = findViewById(R.id.btn_download_result);

        String examId = getIntent().getStringExtra("EXAM_ID");
        com.example.nocheatzone.model.Exam exam = null;
        if (examId != null) {
            exam = ExamRepository.getInstance().getExamById(examId);
        }

        com.example.nocheatzone.model.Exam finalExam = exam;
        btnShare.setOnClickListener(v -> shareResult(score, total, percentage, finalExam));
        btnDownload.setOnClickListener(v -> downloadResult(score, total, percentage, finalExam));

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, Main_Activity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private String generateStudentReport(int score, int total, int percentage, com.example.nocheatzone.model.Exam exam) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- NO CHEAT ZONE EXAM RESULTS ---\n\n");
        sb.append("Date: ").append(new java.util.Date().toString()).append("\n");
        sb.append("Score: ").append(score).append(" / ").append(total).append("\n");
        sb.append("Percentage: ").append(percentage).append("%\n");
        sb.append("Result: ").append(percentage >= 50 ? "PASSED ✅" : "FAILED ❌").append("\n\n");
        
        if (exam != null) {
            sb.append("Exam: ").append(exam.getTitle()).append("\n");
            sb.append("Code: ").append(exam.getAccessCode()).append("\n\n");
            sb.append("--- QUESTIONS ---\n");
            int idx = 1;
            java.util.List<com.example.nocheatzone.model.Question> questions = exam.getQuestions();
            if (questions != null) {
                for (com.example.nocheatzone.model.Question q : questions) {
                    if (q == null) continue;
                    sb.append("Q").append(idx++).append(". ").append(q.getQuestionText()).append("\n");
                    if (q.getType() == com.example.nocheatzone.model.Question.QuestionType.MULTIPLE_CHOICE) {
                        java.util.List<String> opts = q.getOptions();
                        if (opts != null) {
                            for (int i = 0; i < opts.size(); i++) {
                                sb.append("  ").append((char) ('A' + i)).append(") ").append(opts.get(i)).append("\n");
                            }
                        }
                        sb.append("  Correct Answer: ").append((char) ('A' + q.getCorrectAnswerIndex())).append("\n\n");
                    } else {
                        sb.append("  Correct Answer: ").append(q.getCorrectAnswer()).append("\n\n");
                    }
                }
            }
        }
        sb.append("This is a permanent record of your exam progress.");
        return sb.toString();
    }

    private void shareResult(int score, int total, int percentage, com.example.nocheatzone.model.Exam exam) {
        String msg = generateStudentReport(score, total, percentage, exam);
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(android.content.Intent.EXTRA_TEXT, msg);
        startActivity(android.content.Intent.createChooser(intent, "Share Result"));
    }

    private void downloadResult(int score, int total, int percentage, com.example.nocheatzone.model.Exam exam) {
        String content = generateStudentReport(score, total, percentage, exam);
        try {
            java.io.File path = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
            if (path == null) path = getExternalFilesDir(null);
            java.io.File file = new java.io.File(path, "ExamResult_" + System.currentTimeMillis() + ".txt");
            java.io.FileOutputStream stream = new java.io.FileOutputStream(file);
            stream.write(content.getBytes());
            stream.close();
            android.widget.Toast.makeText(this, "Result saved to: " + file.getName(), android.widget.Toast.LENGTH_LONG).show();
        } catch (java.io.IOException e) {
            android.widget.Toast.makeText(this, "Save Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
