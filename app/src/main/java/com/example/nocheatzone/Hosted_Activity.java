package com.example.nocheatzone;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import com.example.nocheatzone.model.Exam;
import com.google.android.material.appbar.MaterialToolbar;

public class Hosted_Activity extends InternetCheckActivity {
    public static final String EXTRA_MODE = "HOSTED_MODE";
    public static final String MODE_MONITOR = "monitor";
    public static final String MODE_RESULTS = "results";

    private RecyclerView recyclerView;
    private TextView emptyText;
    private ExamHistoryAdapter adapter;
    private String mode = MODE_MONITOR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hosted);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (!MODE_RESULTS.equals(mode)) {
            mode = MODE_MONITOR;
        }
        toolbar.setTitle(MODE_RESULTS.equals(mode) ? "Completed Exams" : "Live Exams");
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_hosted_exams);
        emptyText = findViewById(R.id.text_no_hosted);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadHostedExams();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHostedExams();
    }

    private void loadHostedExams() {
        List<Exam> source = ExamRepository.getInstance().getHostedExams();
        List<Exam> records = new java.util.ArrayList<>();
        if (source != null) {
            for (Exam exam : source) {
                if (exam == null) continue;
                if (MODE_RESULTS.equals(mode)) {
                    if (exam.isEnded()) records.add(exam);
                } else {
                    if (!exam.isEnded()) records.add(exam);
                }
            }
        }
        records.sort((a, b) -> Long.compare(b.getCreationTimestamp(), a.getCreationTimestamp()));
        
        if (records == null || records.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText(MODE_RESULTS.equals(mode)
                    ? "No completed exams found."
                    : "No live hosted exams found.");
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
            adapter = new ExamHistoryAdapter(records, exam -> {
                if (exam == null || exam.getId() == null || exam.getId().trim().isEmpty()) {
                    android.widget.Toast.makeText(
                            Hosted_Activity.this,
                            "This exam record is invalid. Please refresh the list.",
                            android.widget.Toast.LENGTH_SHORT
                    ).show();
                    return;
                }
                if (MODE_RESULTS.equals(mode)) {
                    android.content.Intent intent = new android.content.Intent(Hosted_Activity.this, HostResultActivity.class);
                    intent.putExtra("EXAM_ID", exam.getId());
                    startActivity(intent);
                } else {
                    android.content.Intent intent = new android.content.Intent(Hosted_Activity.this, MonitoringActivity.class);
                    intent.putExtra("EXAM_ID", exam.getId());
                    startActivity(intent);
                }
            });
            recyclerView.setAdapter(adapter);
        }
    }
}