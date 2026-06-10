package com.example.nocheatzone;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nocheatzone.model.StudentStatus;
import com.google.android.material.appbar.MaterialToolbar;

import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MonitoringActivity extends InternetCheckActivity {
    private RecyclerView recyclerView;
    private StudentStatusAdapter adapter;
    private String currentExamId;
    
    // Firebase references for Realtime Status Updates
    private com.google.firebase.database.DatabaseReference sessionRef;
    private com.google.firebase.database.ValueEventListener sessionListener;

    @Override
    protected boolean shouldBlockScreenshots() {
        return true;
    }

    @Override
    protected boolean shouldEnforceDeveloperOptionsCheck() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_monitoring);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Setup Toolbar
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Setup RecyclerView
        recyclerView = findViewById(R.id.recycler_monitoring);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));

            // To monitor, we need to know WHICH exam we're monitoring.
            currentExamId = getIntent().getStringExtra("EXAM_ID");
            if (currentExamId == null || currentExamId.trim().isEmpty()) {
                Toast.makeText(this, "Missing exam ID. Open monitoring from your hosted exam list.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            com.example.nocheatzone.model.Exam exam = ExamRepository.getInstance().getExamById(currentExamId);
            if (exam == null) {
                Toast.makeText(this, "Exam not found. It may still be syncing — try again in a moment.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            // Display Access Code
            android.widget.TextView codeText = findViewById(R.id.txt_access_code);
            if (codeText != null && exam != null) {
                if (exam.isEnded()) {
                    codeText.setText("ENDED");
                    codeText.setTextColor(0xFFFF0000); // Red
                } else {
                    String rawCode = exam.getAccessCode();
                    if (rawCode != null && rawCode.length() == 6) {
                        String formatted = rawCode.substring(0, 3) + " " + rawCode.substring(3);
                        codeText.setText(formatted);
                    } else {
                        codeText.setText(rawCode);
                    }
                }

                codeText.setTextIsSelectable(true);
                codeText.setOnClickListener(v -> copyAccessCodeToClipboard(codeText.getText() != null
                        ? codeText.getText().toString()
                        : ""));
                codeText.setOnLongClickListener(v -> {
                    copyAccessCodeToClipboard(codeText.getText() != null
                            ? codeText.getText().toString()
                            : "");
                    return true;
                });
            }

            // Stats Update
            if (exam != null) {
                android.widget.TextView totalText = findViewById(R.id.txt_total_count);
                android.widget.TextView activeText = findViewById(R.id.txt_online_count);
                android.widget.TextView flaggedText = findViewById(R.id.txt_flagged_count);
                int flaggedCount = 0;
                java.util.List<StudentStatus> joined = exam.getJoinedStudents();
                if (joined == null) joined = new java.util.ArrayList<>();
                for (StudentStatus st : joined) {
                    if (st != null && st.isFlagged()) flaggedCount++;
                }
                if (totalText != null) totalText.setText(String.valueOf(joined.size()));
                if (activeText != null) activeText.setText(String.valueOf(Math.max(0, joined.size() - flaggedCount)));
                if (flaggedText != null) flaggedText.setText(String.valueOf(flaggedCount));

                com.google.android.material.switchmaterial.SwitchMaterial hideResultSwitch =
                        findViewById(R.id.switch_hide_result_monitoring);
                if (hideResultSwitch != null) {
                    hideResultSwitch.setChecked(exam.isStudentResultHidden());
                    hideResultSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        exam.setStudentResultHidden(isChecked);
                        ExamRepository.getInstance().saveExam(exam);
                        Toast.makeText(
                                this,
                                isChecked ? "Student result hidden enabled" : "Student result visible to students",
                                Toast.LENGTH_SHORT
                        ).show();
                    });
                }
            }

            // Share Button
            com.google.android.material.button.MaterialButton btnShare = findViewById(R.id.btn_share_code);
            if (btnShare != null && exam != null) {
                String finalCode = exam.getAccessCode();
                String finalTitle = exam.getTitle();
                btnShare.setOnClickListener(v -> {
                    android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    String shareMessage = "Join my Exam on NoCheatZone!\n\n" +
                            "Exam: " + finalTitle + "\n" +
                            "Access Code: " + finalCode + "\n\n" +
                            "Download the app to join.";
                    shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareMessage);
                    startActivity(android.content.Intent.createChooser(shareIntent, "Share Exam Code"));
                });
            }

            com.google.android.material.button.MaterialButton btnEnd = findViewById(R.id.btn_end_exam);
            if (btnEnd != null && exam != null) {
                com.example.nocheatzone.model.Exam finalExam = exam;
                btnEnd.setOnClickListener(v -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("End Exam?")
                            .setMessage("Are you sure you want to end the exam for all students?")
                            .setPositiveButton("End Now", (d, w) -> {
                                finalExam.setEnded(true);
                                ExamRepository.getInstance().saveExam(finalExam);
                                Toast.makeText(this, "Exam Ended Successfully", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            }

            java.util.List<StudentStatus> students = exam.getJoinedStudents();
            if (students == null) students = new ArrayList<>();
            adapter = new StudentStatusAdapter(students, this::removeStudent);
            recyclerView.setAdapter(adapter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentExamId != null) {
            try {
                sessionRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("exam_sessions")
                        .child(currentExamId)
                        .child("participants");
                        
                sessionListener = new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                        com.example.nocheatzone.model.Exam latest = ExamRepository.getInstance().getExamById(currentExamId);
                        if (latest != null) {
                            List<com.example.nocheatzone.model.StudentStatus> activeStudents = new ArrayList<>();
                            for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                                String name = child.child("name").getValue(String.class);
                                String studentId = child.child("studentId").getValue(String.class);
                                String status = child.child("status").getValue(String.class);
                                Boolean flagged = child.child("flagged").getValue(Boolean.class);
                                Long ts = child.child("flagTimestamp").getValue(Long.class);
                                 
                                String email = child.child("email").getValue(String.class);
                                String phone = child.child("phone").getValue(String.class);
                                 
                                com.example.nocheatzone.model.StudentStatus st = 
                                        new com.example.nocheatzone.model.StudentStatus(
                                                name != null ? name : "Unknown", 
                                                studentId != null ? studentId : "", 
                                                      "Joined",
                                                false
                                        );
                                if (status != null) st.setStatus(status);
                                if (flagged != null) st.setFlagged(flagged);
                                if (ts != null) st.setFlagTimestamp(ts);
                                if (email != null) st.setEmail(email);
                                if (phone != null) st.setPhone(phone);
                                
                                activeStudents.add(st);
                            }
                            latest.setJoinedStudents(activeStudents);
                            // Only update local memory without re-triggering save cascades
                            refreshMonitoringData();
                        }
                    }

                    @Override
                    public void onCancelled(com.google.firebase.database.DatabaseError error) {
                        android.util.Log.e("MonitoringActivity", "Firebase listener failed", error.toException());
                    }
                };
                sessionRef.addValueEventListener(sessionListener);
            } catch (Exception e) {
                // Firebase not initialized
            }
        }
        refreshMonitoringData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sessionRef != null && sessionListener != null) {
            sessionRef.removeEventListener(sessionListener);
        }
    }

    private void copyAccessCodeToClipboard(String codeText) {
        if (codeText == null || codeText.trim().isEmpty() || "ENDED".equalsIgnoreCase(codeText.trim())) {
            Toast.makeText(this, "No active code to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        String normalized = codeText.replace(" ", "").trim();
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Exam Access Code", normalized));
            Toast.makeText(this, "Access code copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshMonitoringData() {
        if (currentExamId == null) return;
        com.example.nocheatzone.model.Exam latest = ExamRepository.getInstance().getExamById(currentExamId);
        if (latest == null) return;

        android.widget.TextView totalText = findViewById(R.id.txt_total_count);
        android.widget.TextView activeText = findViewById(R.id.txt_online_count);
        android.widget.TextView flaggedText = findViewById(R.id.txt_flagged_count);

        int total = latest.getJoinedStudents() != null ? latest.getJoinedStudents().size() : 0;
        int flagged = 0;
        if (latest.getJoinedStudents() != null) {
            for (StudentStatus st : latest.getJoinedStudents()) {
                if (st != null && st.isFlagged()) flagged++;
            }
        }
        if (totalText != null) totalText.setText(String.valueOf(total));
        if (activeText != null) activeText.setText(String.valueOf(Math.max(0, total - flagged)));
        if (flaggedText != null) flaggedText.setText(String.valueOf(flagged));

        if (recyclerView != null) {
            if (adapter == null) {
                adapter = new StudentStatusAdapter(latest.getJoinedStudents() != null ? latest.getJoinedStudents() : new ArrayList<>(), this::removeStudent);
                recyclerView.setAdapter(adapter);
            } else {
                adapter = new StudentStatusAdapter(latest.getJoinedStudents() != null ? latest.getJoinedStudents() : new ArrayList<>(), this::removeStudent);
                recyclerView.setAdapter(adapter);
            }
        }
    }

    private void removeStudent(StudentStatus student) {
        if (currentExamId == null || student == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Remove Student")
                .setMessage("Remove " + student.getName() + " from the exam? They will not be able to rejoin.")
                .setPositiveButton("Remove", (d, w) -> {
                    try {
                        com.google.firebase.database.FirebaseDatabase.getInstance()
                                .getReference("exam_sessions")
                                .child(currentExamId)
                                .child("participants")
                                .child(student.getStudentId())
                                .child("removed").setValue(true);
                        Toast.makeText(this, student.getName() + " removed successfully.", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        android.util.Log.e("MonitoringActivity", "Remove student failed", e);
                        Toast.makeText(this, "Could not remove student. Check network.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
