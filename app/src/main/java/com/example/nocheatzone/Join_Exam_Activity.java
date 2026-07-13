package com.example.nocheatzone;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class Join_Exam_Activity extends InternetCheckActivity {

    private TextInputEditText examCodeInput;
    private MaterialButton btnJoin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        // Setup Toolbar
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        examCodeInput = findViewById(R.id.exam_code_input);
        btnJoin = findViewById(R.id.btn_join_exam);
        btnJoin.setOnClickListener(view -> codecheck(examCodeInput));
    }
    private void codecheck(TextInputEditText text){
        String code = text.getText() != null ? text.getText().toString().trim() : "";
        if (TextUtils.isEmpty(code)) {
            text.setError("Please enter an exam code");
            return;
        }
        if (!code.matches("\\d{6}")) {
            text.setError("Exam code must be 6 digits");
            return;
        }

        btnJoin.setEnabled(false);
        btnJoin.setText("Searching...");

        com.google.firebase.database.Query query = com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("exams").orderByChild("accessCode").equalTo(code);
        
        query.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                btnJoin.setEnabled(true);
                btnJoin.setText("Join Exam");
                if (snapshot.exists()) {
                    for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                        com.example.nocheatzone.model.Exam exam = child.getValue(com.example.nocheatzone.model.Exam.class);
                        if (exam != null) {
                            processJoinedExam(exam);
                            return; // Join the first match
                        }
                    }
                }
                text.setError("Invalid Exam Code. Please check and try again.");
                Toast.makeText(Join_Exam_Activity.this, "Exam not found on server ❌", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(com.google.firebase.database.DatabaseError error) {
                btnJoin.setEnabled(true);
                btnJoin.setText("Join Exam");
                Toast.makeText(Join_Exam_Activity.this, "Network error. Try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void processJoinedExam(com.example.nocheatzone.model.Exam exam) {
        long currentTime = System.currentTimeMillis();
        if (exam.getId() == null || exam.getId().trim().isEmpty()) {
            Toast.makeText(this, "Invalid exam data. Please ask host to recreate the exam.", Toast.LENGTH_LONG).show();
            return;
        }

        if (exam.getQuestions() == null || exam.getQuestions().isEmpty()) {
            Toast.makeText(this, "This exam is not ready yet. Host has not added questions.", Toast.LENGTH_LONG).show();
            return;
        }
        
        // Check Start Window
        if (exam.getStartTime() > 0 && currentTime < exam.getStartTime()) {
            String startTimeStr = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(exam.getStartTime()));
            Toast.makeText(this, "This exam opens at " + startTimeStr + " ⏳", Toast.LENGTH_LONG).show();
            return;
        }
        
        // Check if Exam is manually ended
        if (exam.isEnded()) {
            Toast.makeText(this, "The host has ended this exam session ❌", Toast.LENGTH_LONG).show();
            return;
        }

        // Check End Window
        if (exam.getEndTime() > 0 && currentTime > exam.getEndTime()) {
            Toast.makeText(this, "This exam session has closed ❌", Toast.LENGTH_LONG).show();
            return;
        }

        // 1. Generate identity
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        String generatedStudentId = (user != null) ? user.getUid() : ("ID_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));

        android.content.SharedPreferences prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        // Prefer profile cached at login/register (same keys as Login_Activity).
        String savedName = prefs.getString("user_name", null);
        String savedEmail = prefs.getString("user_email", "");
        String savedPhone = prefs.getString("user_phone", "");

        if (user != null) {
            if ((savedName == null || savedName.trim().isEmpty())
                    && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                savedName = user.getDisplayName();
            }
            if (savedEmail.isEmpty() && user.getEmail() != null) {
                savedEmail = user.getEmail();
            }
            if (savedPhone.isEmpty() && user.getPhoneNumber() != null) {
                savedPhone = user.getPhoneNumber();
            }
        }

        if (savedName == null || savedName.trim().isEmpty()) {
            savedName = "Student #" + (int) (Math.random() * 10000);
        }
        
        // 2. Check if student already took this exam
        btnJoin.setEnabled(false);
        btnJoin.setText("Verifying...");
        
        String finalSavedName = savedName;
        String finalSavedEmail = savedEmail;
        String finalSavedPhone = savedPhone;

        com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("exam_sessions")
                .child(exam.getId())
                .child("participants")
                .child(generatedStudentId)
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                        btnJoin.setEnabled(true);
                        btnJoin.setText("Join Exam");

                        if (snapshot.exists()) {
                            Boolean isRemoved = snapshot.child("removed").getValue(Boolean.class);
                            if (Boolean.TRUE.equals(isRemoved)) {
                                Toast.makeText(Join_Exam_Activity.this, "You have been removed from this exam by the host. ⛔", Toast.LENGTH_LONG).show();
                                return;
                            }

                            Integer attempts = snapshot.child("attemptsCount").getValue(Integer.class);
                            int currentAttempts = attempts != null ? attempts : 0;
                            
                            String status = snapshot.child("status").getValue(String.class);
                            if (status != null && status.contains("Submitted")) {
                                if (!status.contains("Suspicious")) {
                                    Toast.makeText(Join_Exam_Activity.this, "You have already completed this exam ❌", Toast.LENGTH_LONG).show();
                                    return;
                                } else {
                                    if (currentAttempts >= 2) {
                                        Toast.makeText(Join_Exam_Activity.this, "Maximum exam attempts reached (2/2). ⛔", Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    Toast.makeText(Join_Exam_Activity.this, "Re-joining suspicious submission (Attempt " + (currentAttempts + 1) + "/2)", Toast.LENGTH_LONG).show();
                                }
                            }
                        }

                        Toast.makeText(Join_Exam_Activity.this, "Joined Successfully! ✅", Toast.LENGTH_SHORT).show();

                        // Persist to joined history
                        ExamRepository.getInstance().saveJoinedExam(exam);

                        // Navigate back to the actual proctored exam screen
                        Intent intent = new Intent(Join_Exam_Activity.this, StudentExamActivity.class);
                        intent.putExtra("EXAM_DATA_JSON", new com.google.gson.Gson().toJson(exam));
                        intent.putExtra("EXAM_ID", exam.getId());
                        intent.putExtra("STUDENT_ID", generatedStudentId);
                        intent.putExtra("STUDENT_NAME", finalSavedName);
                        intent.putExtra("STUDENT_EMAIL", finalSavedEmail);
                        intent.putExtra("STUDENT_PHONE", finalSavedPhone);
                        
                        // Pass attempts to increment inside the exam activity
                        Integer attempts = snapshot.child("attemptsCount").getValue(Integer.class);
                        intent.putExtra("ATTEMPT_COUNT", (attempts != null ? attempts : 0) + 1);
                        
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onCancelled(com.google.firebase.database.DatabaseError error) {
                        btnJoin.setEnabled(true);
                        btnJoin.setText("Join Exam");
                        Toast.makeText(Join_Exam_Activity.this, "Network error. Try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
