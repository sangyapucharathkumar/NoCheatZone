package com.example.nocheatzone;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nocheatzone.model.StudentResult;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HostResultActivity extends AppCompatActivity {
    private enum ResultFilter { ALL, NORMAL, SUSPICIOUS, PENDING }

    private RecyclerView recyclerView;
    private StudentResultAdapter adapter;
    private List<StudentResult> allResults = new ArrayList<>();
    private ResultFilter activeFilter = ResultFilter.ALL;
    private MaterialButton btnAll;
    private MaterialButton btnNormal;
    private MaterialButton btnSuspicious;
    private MaterialButton btnPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_host_result);
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_student_results);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));

            // Realtime data integration
            String currentExamId = getIntent().getStringExtra("EXAM_ID");
            if (currentExamId == null || currentExamId.trim().isEmpty()) {
                android.widget.Toast.makeText(this, "Missing exam ID. Open results from completed exam list.", android.widget.Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            
            com.example.nocheatzone.model.Exam exam = ExamRepository.getInstance().getExamById(currentExamId);

            // Setup Filter UI first
            setupFilterButtons();

            // Firebase Listener for Results
            try {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("exam_sessions")
                        .child(currentExamId)
                        .child("participants")
                        .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                            @Override
                            public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                                List<StudentResult> results = new ArrayList<>();
                                
                                int totalQuestions = exam != null && exam.getQuestions() != null ? exam.getQuestions().size() : 1;
                                
                                for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                                    String name = child.child("name").getValue(String.class);
                                    String studentId = child.child("studentId").getValue(String.class);
                                    String email = child.child("email").getValue(String.class);
                                    Integer score = child.child("score").getValue(Integer.class);
                                    Integer snapshotTotalQuestions = child.child("totalQuestions").getValue(Integer.class);
                                    Long submittedAtMs = child.child("submittedAtMs").getValue(Long.class);
                                    Boolean suspiciousExit = child.child("suspiciousExit").getValue(Boolean.class);

                                    if (name == null) name = "Unknown";
                                    if (studentId == null) studentId = "unknown";
                                    if (email == null) email = "";
                                    
                                    StudentResult sr;
                                    if (score != null && snapshotTotalQuestions != null && submittedAtMs != null) {
                                        sr = new StudentResult(name, studentId, score, snapshotTotalQuestions, submittedAtMs, suspiciousExit != null ? suspiciousExit : false, email);
                                        // Save cloud result locally immediately for offline persistence
                                        ExamRepository.getInstance().saveStudentResult(currentExamId, sr);
                                    } else {
                                        if (exam != null && exam.isEnded()) {
                                            // Exam officially over but student never submitted their test. Mark as forced zero.
                                            sr = new StudentResult(name + " (Abandoned)", studentId, 0, totalQuestions, exam.getEndTime() > 0 ? exam.getEndTime() : System.currentTimeMillis(), true, email);
                                        } else {
                                            sr = new StudentResult(name + " (Pending)", studentId, 0, totalQuestions, 0L, false, email);
                                        }
                                    }
                                    results.add(sr);
                                }
                                
                                // Update UI
                                allResults = results;
                                setFilter(activeFilter);
                            }

                            @Override
                            public void onCancelled(com.google.firebase.database.DatabaseError error) {
                                android.util.Log.e("HostResultActivity", "Failed to load results from Firebase");
                            }
                        });
            } catch (Exception e) {
                // Ignore initialization failures
            }

            // Immediately load cached results while connecting
            List<StudentResult> cached = ExamRepository.getInstance().getStudentResults(currentExamId);
            if (cached != null) {
                allResults = new ArrayList<>(cached);
                adapter = new StudentResultAdapter(applyFilter(allResults, activeFilter));
                recyclerView.setAdapter(adapter);
                refreshFilterCountsAndState();
            } else {
                adapter = new StudentResultAdapter(new ArrayList<>());
                recyclerView.setAdapter(adapter);
            }
            
            com.google.android.material.button.MaterialButton btnShare = findViewById(R.id.btn_share_host_result);
            com.google.android.material.button.MaterialButton btnDownload = findViewById(R.id.btn_download_host_result);
            
            if (btnShare != null && exam != null) {
                btnShare.setOnClickListener(v -> {
                    List<StudentResult> filteredResults = applyFilter(allResults, activeFilter);
                    shareHostResult(exam, filteredResults);
                });
            }
            if (btnDownload != null && exam != null) {
                btnDownload.setOnClickListener(v -> {
                    List<StudentResult> filteredResults = applyFilter(allResults, activeFilter);
                    downloadHostResult(exam, filteredResults);
                });
            }
        }
    }

    private void setupFilterButtons() {
        android.view.ViewGroup container = findViewById(R.id.summary_card);
        if (container == null) return;
        if (btnAll != null) {
            refreshFilterCountsAndState();
            return;
        }
        btnAll = buildFilterButton("All");
        btnNormal = buildFilterButton("Normal");
        btnSuspicious = buildFilterButton("Suspicious");
        btnPending = buildFilterButton("Pending");

        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setPadding(24, 0, 24, 16);

        row.addView(btnAll);
        row.addView(btnNormal);
        row.addView(btnSuspicious);
        row.addView(btnPending);

        container.addView(row);

        btnAll.setOnClickListener(v -> setFilter(ResultFilter.ALL));
        btnNormal.setOnClickListener(v -> setFilter(ResultFilter.NORMAL));
        btnSuspicious.setOnClickListener(v -> setFilter(ResultFilter.SUSPICIOUS));
        btnPending.setOnClickListener(v -> setFilter(ResultFilter.PENDING));
        refreshFilterCountsAndState();
    }

    private MaterialButton buildFilterButton(String text) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setAllCaps(false);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        lp.setMarginEnd(8);
        button.setLayoutParams(lp);
        return button;
    }

    private void setFilter(ResultFilter filter) {
        activeFilter = filter;
        if (adapter != null) {
            adapter.updateData(applyFilter(allResults, activeFilter));
        }
        refreshFilterCountsAndState();
    }

    private void refreshFilterButtons(
            MaterialButton btnAll,
            MaterialButton btnNormal,
            MaterialButton btnSuspicious,
            MaterialButton btnPending
    ) {
        tintButton(btnAll, activeFilter == ResultFilter.ALL);
        tintButton(btnNormal, activeFilter == ResultFilter.NORMAL);
        tintButton(btnSuspicious, activeFilter == ResultFilter.SUSPICIOUS);
        tintButton(btnPending, activeFilter == ResultFilter.PENDING);
    }

    private void refreshFilterCountsAndState() {
        if (btnAll == null || btnNormal == null || btnSuspicious == null || btnPending == null) return;
        int allCount = applyFilter(allResults, ResultFilter.ALL).size();
        int normalCount = applyFilter(allResults, ResultFilter.NORMAL).size();
        int suspiciousCount = applyFilter(allResults, ResultFilter.SUSPICIOUS).size();
        int pendingCount = applyFilter(allResults, ResultFilter.PENDING).size();

        btnAll.setText("All (" + allCount + ")");
        btnNormal.setText("Normal (" + normalCount + ")");
        btnSuspicious.setText("Suspicious (" + suspiciousCount + ")");
        btnPending.setText("Pending (" + pendingCount + ")");
        refreshFilterButtons(btnAll, btnNormal, btnSuspicious, btnPending);
    }

    private void tintButton(MaterialButton button, boolean selected) {
        if (button == null) return;
        if (selected) {
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.primary_dark)));
            button.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.card_bg)));
            button.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        }
    }

    private List<StudentResult> applyFilter(List<StudentResult> source, ResultFilter filter) {
        List<StudentResult> filtered = new ArrayList<>();
        if (source == null) return filtered;
        for (StudentResult result : source) {
            if (result == null) continue;
            boolean isPending = result.getSubmittedAtMs() <= 0L;
            switch (filter) {
                case NORMAL:
                    if (!isPending && !result.isSuspiciousSubmission()) filtered.add(result);
                    break;
                case SUSPICIOUS:
                    if (!isPending && result.isSuspiciousSubmission()) filtered.add(result);
                    break;
                case PENDING:
                    if (isPending) filtered.add(result);
                    break;
                case ALL:
                default:
                    filtered.add(result);
                    break;
            }
        }
        filtered.sort((a, b) -> Integer.compare(b.getPercentage(), a.getPercentage()));
        return filtered;
    }
    
    private String generateExamReport(com.example.nocheatzone.model.Exam exam, List<StudentResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- NO CHEAT ZONE EXAM REPORT ---\n\n");
        sb.append("Exam: ").append(exam.getTitle()).append("\n");
        sb.append("Code: ").append(exam.getAccessCode()).append("\n");
        sb.append("Date: ").append(new java.util.Date(exam.getCreationTimestamp()).toString()).append("\n\n");
        
        sb.append("--- QUESTIONS ---\n");
        int idx = 1;
        for (com.example.nocheatzone.model.Question q : exam.getQuestions()) {
            sb.append("Q").append(idx++).append(". ").append(q.getQuestionText()).append("\n");
            if (q.getType() == com.example.nocheatzone.model.Question.QuestionType.MULTIPLE_CHOICE) {
                List<String> opts = q.getOptions();
                if (opts != null) {
                    for (int i=0; i<opts.size(); i++) {
                        sb.append("  ").append((char)('A'+i)).append(") ").append(opts.get(i)).append("\n");
                    }
                }
                sb.append("  Correct Answer: ").append((char)('A' + q.getCorrectAnswerIndex())).append("\n\n");
            } else {
                sb.append("  Correct Answer: ").append(q.getCorrectAnswer()).append("\n\n");
            }
        }
        
        sb.append("--- STUDENT RESULTS ---\n");
        if (results.isEmpty()) {
            sb.append("No student results recorded locally yet.\n");
        } else {
            for (StudentResult r : results) {
                sb.append(r.getName())
                        .append(" (ID: ").append(r.getStudentId()).append(")")
                        .append(" [").append(r.getEmail() != null && !r.getEmail().isEmpty() ? r.getEmail() : "No Email").append("]")
                        .append(" - Score: ").append(r.getPercentage()).append("%")
                        .append(" | Status: ").append(r.isSuspiciousSubmission() ? "Auto-Submitted (Suspicious)" : "Normal Submission")
                        .append(" | Time: ").append(formatSubmissionTime(r.getSubmittedAtMs()))
                        .append("\n");
            }
        }
        return sb.toString();
    }

    private String formatSubmissionTime(long submittedAtMs) {
        if (submittedAtMs <= 0L) return "N/A";
        java.text.SimpleDateFormat format =
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
        return format.format(new java.util.Date(submittedAtMs));
    }
    
    private void shareHostResult(com.example.nocheatzone.model.Exam exam, List<StudentResult> results) {
        String msg = generateExamReport(exam, results);
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(android.content.Intent.EXTRA_TEXT, msg);
        startActivity(android.content.Intent.createChooser(intent, "Share Exam Report"));
    }

    private void downloadHostResult(com.example.nocheatzone.model.Exam exam, List<StudentResult> results) {
        String content = generateExamReport(exam, results);
        try {
            java.io.File path = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
            if (path == null) path = getExternalFilesDir(null);
            java.io.File file = new java.io.File(path, "ExamReport_" + exam.getAccessCode() + ".txt");
            java.io.FileOutputStream stream = new java.io.FileOutputStream(file);
            stream.write(content.getBytes());
            stream.close();
            android.widget.Toast.makeText(this, "Report saved to: " + file.getName(), android.widget.Toast.LENGTH_LONG).show();
        } catch (java.io.IOException e) {
            android.widget.Toast.makeText(this, "Save Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // Proper static inner adapter (no memory leak)
    static class StudentResultAdapter
            extends RecyclerView.Adapter<StudentResultAdapter.ResultViewHolder> {

        private final List<StudentResult> resultList;

        public StudentResultAdapter(List<StudentResult> resultList) {
            this.resultList = resultList;
        }

        public void updateData(List<StudentResult> data) {
            resultList.clear();
            if (data != null) resultList.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_student_result, parent, false);
            return new ResultViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
            StudentResult result = resultList.get(position);
            holder.txtName.setText(result.getName());
            
            if (holder.txtEmail != null) {
                if (result.getEmail() != null && !result.getEmail().trim().isEmpty()) {
                    holder.txtEmail.setVisibility(View.VISIBLE);
                    holder.txtEmail.setText(result.getEmail());
                } else {
                    holder.txtEmail.setVisibility(View.GONE);
                }
            }
            
            String statusLabel = result.isSuspiciousSubmission() ? "Auto-Submitted (Suspicious)" : "Normal";
            holder.txtId.setText(
                    "ID: " + result.getStudentId()
                            + " • " + statusLabel
                            + " • " + formatSubmissionTimeShort(result.getSubmittedAtMs())
            );
            
            if (result.getName() != null && !result.getName().isEmpty() && holder.txtLetter != null) {
                holder.txtLetter.setText(
                        String.valueOf(result.getName().charAt(0)).toUpperCase(java.util.Locale.ROOT)
                );
            }

            int pct = result.getPercentage();
            int score = result.getScore();
            int total = result.getTotalQuestions();
            
            holder.txtScore.setText(score + " / " + total + "  (" + pct + "%)");

            if (pct >= 50) {
                holder.txtScore.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.success));
            } else {
                holder.txtScore.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.danger));
            }
        }

        @Override
        public int getItemCount() {
            return resultList.size();
        }

        private String formatSubmissionTimeShort(long submittedAtMs) {
            if (submittedAtMs <= 0L) return "N/A";
            java.text.SimpleDateFormat format =
                    new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault());
            return format.format(new java.util.Date(submittedAtMs));
        }

        static class ResultViewHolder extends RecyclerView.ViewHolder {
            TextView txtName, txtEmail, txtId, txtScore, txtLetter;

            public ResultViewHolder(@NonNull View itemView) {
                super(itemView);
                txtName = itemView.findViewById(R.id.txt_student_name);
                txtEmail = itemView.findViewById(R.id.txt_student_email);
                txtId = itemView.findViewById(R.id.txt_student_id);
                txtScore = itemView.findViewById(R.id.txt_score);
                txtLetter = itemView.findViewById(R.id.txt_avatar_letter);
            }
        }
    }
}
