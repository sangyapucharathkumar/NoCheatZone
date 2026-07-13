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

public class joined_activity extends InternetCheckActivity {

    private RecyclerView recyclerView;
    private TextView emptyText;
    private ExamHistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_joined);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_joined_exams);
        emptyText = findViewById(R.id.text_no_joined);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadJoinedExams();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadJoinedExams();
    }

    private void loadJoinedExams() {
        // Now using our specific joined list
        List<Exam> records = ExamRepository.getInstance().getJoinedExams();
        
        if (records == null || records.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
            adapter = new ExamHistoryAdapter(records, true);
            recyclerView.setAdapter(adapter);
        }
    }
}