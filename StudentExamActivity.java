package com.example.nocheatzone;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import android.media.Image;

import com.example.nocheatzone.model.Exam;
import com.example.nocheatzone.model.Question;
import com.example.nocheatzone.model.StudentResult;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class StudentExamActivity extends AppCompatActivity {
    private static final String TAG = "StudentExamActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final String PREF_EXAM_SESSION = "exam_session_prefs";
    private static final String KEY_DEADLINE_PREFIX = "deadline_";
    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_EXIT_WARNINGS = "exit_warnings_enabled";
    private static final String KEY_AUTO_SUBMIT_CLOSE = "auto_submit_on_close_enabled";


    private TextView textTimer;
    private android.os.CountDownTimer countDownTimer;

    private TextView questionIndexText;
    private TextView questionTextDisplay;
    private RadioGroup optionsRadioGroup;
    private RadioButton optA, optB, optC, optD;
    private MaterialButton btnNext, btnPrev;
    private com.google.android.material.textfield.TextInputLayout textAnswerContainer;
    private com.google.android.material.textfield.TextInputEditText textAnswerInput;

    private List<Question> examQuestions;
    private int currentQuestionIndex = 0;
    private String[] userAnswers;
    private Exam currentExam;
    private String examId;
    private String studentId;
    private String studentName;
    private String studentEmail;
    private String studentPhone;
    private boolean isExamLocked = false;
    private boolean isSubmitting = false;
    private int exitAttemptCount = 0;
    private boolean pendingHomeWarningDialog = false;
    private long examDeadlineMs = 0L;
    private PreviewView cameraPreview;
    private ProcessCameraProvider cameraProvider;
    
    private FaceDetector faceDetector;
    private TextRecognizer textRecognizer;
    private int faceWarningCount = 0;
    private static final int MAX_FACE_WARNINGS = 5;
    private long lastWarningTime = 0;
    private static final long NO_FACE_THRESHOLD_MS = 3000L;
    private static final long MULTI_FACE_THRESHOLD_MS = 3000L;
    private static final long BAD_POSE_THRESHOLD_MS = 3000L;
    private long noFaceSinceMs = 0L;
    private long multiFaceSinceMs = 0L;
    private long badPoseSinceMs = 0L;
    private static final long BOOK_CHECK_INTERVAL_MS = 2000L;
    private static final long BOOK_VISIBLE_GRACE_MS = 5000L;
    private long lastBookCheckMs = 0L;
    private long lastBookVisibleMs = 0L;
    private long pendingRemainingMs = 0L;
    private boolean examStarted = false;
    private boolean cameraReady = false;
    private boolean preExamDialogShown = false;
    
    private com.google.firebase.database.DatabaseReference examStatusRef;
    private com.google.firebase.database.ValueEventListener examStatusListener;

    private void startTimer(long millis) {
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new android.os.CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = (millisUntilFinished / 1000) / 60;
                long seconds = (millisUntilFinished / 1000) % 60;
                textTimer.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds));
                
                // Alert if less than 1 minute
                if (minutes == 0 && seconds <= 30) {
                    textTimer.setTextColor(android.graphics.Color.RED);
                }
            }

            @Override
            public void onFinish() {
                textTimer.setText("00:00");
                Toast.makeText(StudentExamActivity.this, "Time's up! Auto-submitting...", Toast.LENGTH_LONG).show();
                submitExam();
            }
        }.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Security hardening: exam surface should not allow screenshots/recording.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_exam);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        android.view.View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        com.google.android.material.appbar.MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        if (topAppBar != null) {
            topAppBar.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));
        }

        textTimer = findViewById(R.id.text_timer);
        cameraPreview = findViewById(R.id.camera_preview);
        
        // Setup real questions from Intent
        String examJson = getIntent().getStringExtra("EXAM_DATA_JSON");
        examId = getIntent().getStringExtra("EXAM_ID");
        studentId = getIntent().getStringExtra("STUDENT_ID");
        studentName = getIntent().getStringExtra("STUDENT_NAME");
        studentEmail = getIntent().getStringExtra("STUDENT_EMAIL");
        studentPhone = getIntent().getStringExtra("STUDENT_PHONE");

        if (examJson != null) {
            currentExam = new Gson().fromJson(examJson, Exam.class);
            examQuestions = buildRandomizedQuestionSet(currentExam.getQuestions());
            if ((examId == null || examId.trim().isEmpty()) && currentExam != null) {
                examId = currentExam.getId();
            }
        }

        if (examQuestions == null || examQuestions.isEmpty()) {
            Toast.makeText(this, "Failed to load exam", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Resolve countdown first. Actual timer starts only after student confirmation.
        // even if the app is closed and reopened.
        int duration = currentExam.getDurationMinutes();
        if (duration <= 0) duration = 60; // default safety minutes
        examDeadlineMs = resolveOrCreateExamDeadline(duration);
        long remainingMs = examDeadlineMs - System.currentTimeMillis();
        if (remainingMs <= 0) {
            Toast.makeText(this, "Exam time is over. Auto-submitting...", Toast.LENGTH_LONG).show();
            submitExam(false);
            return;
        }
        pendingRemainingMs = remainingMs;

        // Initialize answer tracking array
        userAnswers = new String[examQuestions.size()];
        for (int i = 0; i < userAnswers.length; i++) {
            userAnswers[i] = ""; // empty means not answered
        }

        // Init Views
        questionIndexText = findViewById(R.id.question_index_text);
        questionTextDisplay = findViewById(R.id.question_text_display);
        optionsRadioGroup = findViewById(R.id.options_radio_group);
        optA = findViewById(R.id.radio_opt_a);
        optB = findViewById(R.id.radio_opt_b);
        optC = findViewById(R.id.radio_opt_c);
        optD = findViewById(R.id.radio_opt_d);
        btnNext = findViewById(R.id.btn_next_question);
        btnPrev = findViewById(R.id.btn_prev_question);
        textAnswerContainer = findViewById(R.id.text_answer_container);
        textAnswerInput = findViewById(R.id.text_answer_input);
        
        com.google.android.material.progressindicator.LinearProgressIndicator progressBar = findViewById(R.id.question_progress);
        if (progressBar != null) {
            progressBar.setMax(examQuestions.size());
        }

        // Wire up navigation buttons
        if (btnNext != null) btnNext.setOnClickListener(v -> nextQuestion());
        if (btnPrev != null) btnPrev.setOnClickListener(v -> previousQuestion());

        // Load the first question
        loadQuestion();
        setExamInteractionEnabled(false);
        
        // Start watching for Host termination via Firebase Realtime Database
        if (examId != null && !examId.trim().isEmpty()) {
            examStatusRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("exams").child(examId).child("ended");
            examStatusListener = new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        Boolean isEnded = snapshot.getValue(Boolean.class);
                        if (Boolean.TRUE.equals(isEnded) && !isExamLocked && !isFinishing()) {
                            Toast.makeText(StudentExamActivity.this, "Host has ended the exam! Auto-submitting...", Toast.LENGTH_LONG).show();
                            submitExam();
                        }
                    }
                }

                @Override
                public void onCancelled(com.google.firebase.database.DatabaseError error) {
                    Log.e(TAG, "Failed to read exam ended status", error.toException());
                }
            };
            examStatusRef.addValueEventListener(examStatusListener);
        }

        if (currentExam != null && currentExam.isCameraMonitoringEnabled()) {
            if (cameraPreview != null) {
                cameraPreview.setVisibility(android.view.View.VISIBLE);
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{android.Manifest.permission.CAMERA},
                        REQUEST_CAMERA_PERMISSION
                );
            }
        } else {
            updateProctoringStatus("🔒 Secure Mode  •  ✅ Ready for confirmation", false);
            showPreExamConfirmationDialog();
        }

        // Push initial live status so host sees student state before exam begins.
        updateLiveStatus("Ready - Awaiting Confirmation", false);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleExitAttempt("back");
            }
        });
    }

    private void updateLiveStatus(String statusText, boolean isFlagged) {
        if (examId != null && studentId != null) {
            try {
                com.google.firebase.database.DatabaseReference ref = 
                        com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("exam_sessions")
                        .child(examId)
                        .child("participants")
                        .child(studentId);
                        
                java.util.Map<String, Object> updates = new java.util.HashMap<>();
                updates.put("status", statusText);
                updates.put("flagged", isFlagged);
                if (isFlagged) {
                    updates.put("flagTimestamp", System.currentTimeMillis());
                }
                
                int attempts = getIntent().getIntExtra("ATTEMPT_COUNT", 1);
                updates.put("attemptsCount", attempts);
                
                updates.put("name", studentName != null ? studentName : "Unknown");
                updates.put("studentId", studentId);
                updates.put("email", studentEmail != null ? studentEmail : "");
                updates.put("phone", studentPhone != null ? studentPhone : "");
                
                ref.updateChildren(updates);
            } catch (Exception e) {
                Log.e(TAG, "Firebase sync failed", e);
            }
        }
    }

    private void loadQuestion() {
        if (examQuestions == null || examQuestions.isEmpty()) return;
        if (currentQuestionIndex >= 0 && currentQuestionIndex < examQuestions.size()) {
            Question q = examQuestions.get(currentQuestionIndex);

            questionIndexText.setText("Question " + (currentQuestionIndex + 1) + " of " + examQuestions.size());
            questionTextDisplay.setText(q.getQuestionText());
            
            com.google.android.material.progressindicator.LinearProgressIndicator progressBar = findViewById(R.id.question_progress);
            if (progressBar != null) {
                progressBar.setProgressCompat(currentQuestionIndex + 1, true);
            }

            if (q.getType() == Question.QuestionType.MULTIPLE_CHOICE) {
                optionsRadioGroup.setVisibility(android.view.View.VISIBLE);
                textAnswerContainer.setVisibility(android.view.View.GONE);

                List<String> cleanedOptions = new ArrayList<>();
                if (q.getOptions() != null) {
                    for (String option : q.getOptions()) {
                        if (option != null && !option.trim().isEmpty()) {
                            cleanedOptions.add(option.trim());
                        }
                    }
                }

                if (cleanedOptions.size() > 0) {
                    optA.setVisibility(android.view.View.VISIBLE);
                    optA.setText(cleanedOptions.get(0));
                }
                if (cleanedOptions.size() > 1) {
                    optB.setVisibility(android.view.View.VISIBLE);
                    optB.setText(cleanedOptions.get(1));
                }
                
                if (cleanedOptions.size() > 2) {
                    optC.setVisibility(android.view.View.VISIBLE);
                    optC.setText(cleanedOptions.get(2));
                } else {
                    optC.setVisibility(android.view.View.GONE);
                }
                
                if (cleanedOptions.size() > 3) {
                    optD.setVisibility(android.view.View.VISIBLE);
                    optD.setText(cleanedOptions.get(3));
                } else {
                    optD.setVisibility(android.view.View.GONE);
                }

                // Restore previously selected answer
                optionsRadioGroup.clearCheck();
                String saved = userAnswers[currentQuestionIndex];
                if ("0".equals(saved)) optionsRadioGroup.check(R.id.radio_opt_a);
                else if ("1".equals(saved)) optionsRadioGroup.check(R.id.radio_opt_b);
                else if ("2".equals(saved)) optionsRadioGroup.check(R.id.radio_opt_c);
                else if ("3".equals(saved)) optionsRadioGroup.check(R.id.radio_opt_d);

            } else {
                optionsRadioGroup.setVisibility(android.view.View.GONE);
                textAnswerContainer.setVisibility(android.view.View.VISIBLE);
                textAnswerInput.setText(userAnswers[currentQuestionIndex]);
                if (q.getType() == Question.QuestionType.FILL_IN_BLANKS) {
                    textAnswerContainer.setHint("Fill in the blank...");
                } else {
                    textAnswerContainer.setHint("Type your answer...");
                }
            }

            btnPrev.setEnabled(currentQuestionIndex > 0);
            btnNext.setText(currentQuestionIndex == examQuestions.size() - 1 ? "Submit Exam" : "Next →");
        }
    }

    private void setExamInteractionEnabled(boolean enabled) {
        if (optionsRadioGroup != null) {
            optionsRadioGroup.setEnabled(enabled);
        }
        if (optA != null) optA.setEnabled(enabled);
        if (optB != null) optB.setEnabled(enabled);
        if (optC != null) optC.setEnabled(enabled);
        if (optD != null) optD.setEnabled(enabled);
        if (textAnswerInput != null) textAnswerInput.setEnabled(enabled);
        if (btnNext != null) btnNext.setEnabled(enabled);
        if (btnPrev != null) btnPrev.setEnabled(enabled && currentQuestionIndex > 0);
    }

    private void showPreExamConfirmationDialog() {
        if (preExamDialogShown || isFinishing() || isDestroyed()) {
            return;
        }
        if (currentExam != null && currentExam.isCameraMonitoringEnabled() && !cameraReady) {
            return;
        }
        preExamDialogShown = true;
        StringBuilder sb = new StringBuilder();
        sb.append("Please review before starting:\n\n");
        sb.append("1) Keep your face clearly visible in front camera.\n");
        sb.append("2) Avoid switching apps or minimizing.\n");
        sb.append("3) Ensure good lighting and clean camera lens.\n");
        if (currentExam != null && currentExam.isCameraMonitoringEnabled()) {
            sb.append("4) AI Proctoring: ML Kit Face Detection (FAST mode).\n");
            sb.append("   Warnings trigger only after continuous non-detection for ~3 seconds.\n");
        }
        sb.append("\nPress \"Start Exam\" only after camera preview looks correct.");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Exam Instructions & Confirmation")
                .setMessage(sb.toString())
                .setCancelable(false)
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setPositiveButton("Start Exam", (d, w) -> beginExamSession())
                .show();
    }

    private void beginExamSession() {
        if (examStarted || isFinishing() || isDestroyed()) return;
        if (currentExam != null && currentExam.isCameraMonitoringEnabled() && !cameraReady) {
            Toast.makeText(this, "Camera is not ready yet. Please wait.", Toast.LENGTH_SHORT).show();
            preExamDialogShown = false;
            showPreExamConfirmationDialog();
            return;
        }
        examStarted = true;
        setExamInteractionEnabled(true);
        startTimer(Math.max(1000L, pendingRemainingMs));
        updateLiveStatus("In Progress", false);
        updateProctoringStatus("🔒 Secure Mode  •  👁 AI Monitoring Active", false);
        Toast.makeText(this, "Exam started. Best of luck!", Toast.LENGTH_SHORT).show();
    }

    private void previousQuestion() {
        saveCurrentAnswer();
        if (currentQuestionIndex > 0) {
            currentQuestionIndex--;
            loadQuestion();
        }
    }

    private void nextQuestion() {
        saveCurrentAnswer();
        if (currentQuestionIndex < examQuestions.size() - 1) {
            currentQuestionIndex++;
            loadQuestion();
        } else {
            submitExam();
        }
    }

    private void saveCurrentAnswer() {
        if (examQuestions == null || examQuestions.isEmpty()) return;
        if (currentQuestionIndex < 0 || currentQuestionIndex >= examQuestions.size()) return;
        Question q = examQuestions.get(currentQuestionIndex);
        if (q.getType() == Question.QuestionType.MULTIPLE_CHOICE) {
            if (optionsRadioGroup == null || userAnswers == null) return;
            int selectedId = optionsRadioGroup.getCheckedRadioButtonId();
            if (selectedId == R.id.radio_opt_a) userAnswers[currentQuestionIndex] = "0";
            else if (selectedId == R.id.radio_opt_b) userAnswers[currentQuestionIndex] = "1";
            else if (selectedId == R.id.radio_opt_c) userAnswers[currentQuestionIndex] = "2";
            else if (selectedId == R.id.radio_opt_d) userAnswers[currentQuestionIndex] = "3";
        } else {
            if (userAnswers == null) return;
            if (textAnswerInput == null) return;
            userAnswers[currentQuestionIndex] = textAnswerInput.getText() != null ? textAnswerInput.getText().toString().trim() : "";
        }
    }

    private void submitExam() {
        submitExam(false);
    }

    private void submitExam(boolean suspiciousExit) {
        if (isExamLocked || isSubmitting) return;
        if (examQuestions == null || examQuestions.isEmpty() || userAnswers == null) return;
        isSubmitting = true;
        isExamLocked = true;
        saveCurrentAnswer(); // Final save
        updateLiveStatus(
                suspiciousExit ? "Suspicious - Auto Submitted" : "Submitted",
                suspiciousExit
        );

        int score = 0;
        for (int i = 0; i < examQuestions.size(); i++) {
            Question q = examQuestions.get(i);
            if (q.getType() == Question.QuestionType.MULTIPLE_CHOICE) {
                if (String.valueOf(q.getCorrectAnswerIndex()).equals(userAnswers[i])) {
                    score++;
                }
            } else {
                // Case-insensitive text comparison
                if (q.getCorrectAnswer() != null && q.getCorrectAnswer().equalsIgnoreCase(userAnswers[i])) {
                    score++;
                }
            }
        }
        updateCurrentStudentStatus(
                suspiciousExit ? "Suspicious - Auto Submitted" : "Submitted",
                suspiciousExit
        );
        final int finalScore = score;
        if (currentExam != null) {
            String safeStudentId = (studentId != null && !studentId.trim().isEmpty()) ? studentId : "unknown";
            String safeStudentName = (studentName != null && !studentName.trim().isEmpty()) ? studentName : "Unknown";
            StudentResult studentResult = new StudentResult(
                    safeStudentName,
                    safeStudentId,
                    finalScore,
                    examQuestions.size(),
                    System.currentTimeMillis(),
                    suspiciousExit,
                    studentEmail != null ? studentEmail : ""
            );
            ExamRepository.getInstance().saveStudentResult(currentExam.getId(), studentResult);
            updateLiveResult(finalScore, examQuestions.size(), System.currentTimeMillis(), suspiciousExit, () -> {
                navigateAwayFromResult(finalScore, examQuestions.size());
            });
        } else {
            navigateAwayFromResult(finalScore, examQuestions.size());
        }
    }

    private void navigateAwayFromResult(int score, int total) {
        if (!isFinishing() && !isDestroyed()) {
            clearExamDeadline();
            Intent intent;
            if (currentExam != null && currentExam.isStudentResultHidden()) {
                intent = new Intent(this, SubmitedActivity.class);
                intent.putExtra("EXAM_ID", currentExam.getId());
            } else {
                intent = new Intent(this, ResultActivity.class);
                intent.putExtra("SCORE", score);
                intent.putExtra("TOTAL", total);
                if (currentExam != null) {
                    intent.putExtra("EXAM_ID", currentExam.getId());
                }
            }
            startActivity(intent);
            finish();
        }
    }

    private void updateLiveResult(int score, int maxScore, long submitTime, boolean suspicious, Runnable onComplete) {
        if (examId != null && studentId != null) {
            try {
                com.google.firebase.database.DatabaseReference ref = 
                        com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("exam_sessions")
                        .child(examId)
                        .child("participants")
                        .child(studentId);
                        
                java.util.Map<String, Object> updates = new java.util.HashMap<>();
                updates.put("score", score);
                updates.put("totalQuestions", maxScore);
                updates.put("submittedAtMs", submitTime);
                updates.put("suspiciousExit", suspicious);
                
                ref.updateChildren(updates).addOnCompleteListener(task -> {
                    if (onComplete != null) onComplete.run();
                });
            } catch (Exception e) {
                Log.e(TAG, "Firebase sync failed for result", e);
                if (onComplete != null) onComplete.run();
            }
        } else {
            if (onComplete != null) onComplete.run();
        }
    }

    // --- SECURITY FEATURES ---

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (examStarted && !isExamLocked && !isFinishing() && !isDestroyed()) {
            handleExitAttempt("home");
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Intentionally no action here for exit counting.
        // onUserLeaveHint handles home/app-switch attempts once per action.
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save answer on pause in case of forced close
        if (examStarted && !isExamLocked && !isFinishing()) {
            saveCurrentAnswer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Hard safety: if app is closed/backgrounded during active exam, auto-submit.
        boolean autoSubmitOnClose = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_AUTO_SUBMIT_CLOSE, true);
        if (examStarted
                && !isExamLocked
                && !isSubmitting
                && !isFinishing()
                && !isChangingConfigurations()
                && autoSubmitOnClose
                && isAppInBackground()) {
            Toast.makeText(this, "App closed during exam. Auto-submitting...", Toast.LENGTH_LONG).show();
            submitExam(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentExam != null && currentExam.isDeveloperOptionsBlocked() && isDeveloperModeEnabled()) {
            Toast.makeText(this, "Developer Options must be disabled during this exam.", Toast.LENGTH_LONG).show();
            submitExam(true);
            return;
        }
        if (!isExamLocked && pendingHomeWarningDialog && !isFinishing() && !isDestroyed()) {
            pendingHomeWarningDialog = false;
            int remaining = Math.max(0, 5 - exitAttemptCount);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Exit Warning")
                    .setMessage("You attempted to leave exam screen.\nAttempts used: "
                            + exitAttemptCount + "/5\nRemaining: " + remaining
                            + "\nAt 5 attempts, exam will auto-submit.")
                    .setPositiveButton("Continue Exam", null)
                    .show();
        }
    }

    private void triggerSecurityViolation(String reason) {
        // Keep for compatibility if referenced in future; warning-only behavior.
        showExitWarning("SECURITY WARNING: " + reason);
    }

    private void showExitWarning(String message) {
        if (isExamLocked || isFinishing() || isDestroyed()) return;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void handleExitAttempt(String source) {
        boolean warningsEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_EXIT_WARNINGS, true);
        if (!warningsEnabled) return;
        if (!examStarted) return;
        if (isExamLocked || isSubmitting || isFinishing() || isDestroyed()) return;
        exitAttemptCount++;
        if (exitAttemptCount >= 5) {
            Toast.makeText(
                    this,
                    "Exit attempts reached limit (5). Auto-submitting exam.",
                    Toast.LENGTH_LONG
            ).show();
            submitExam(true);
            return;
        }
        int remaining = 5 - exitAttemptCount;
        if ("back".equals(source)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Exit Not Allowed")
                    .setMessage("Do not exit during exam.\nAttempts used: "
                            + exitAttemptCount + "/5\nRemaining: " + remaining
                            + "\nAt 5 attempts, exam will auto-submit.")
                    .setPositiveButton("Continue Exam", null)
                    .show();
        } else {
            pendingHomeWarningDialog = true;
            showExitWarning("Warning: Home/exit attempt " + exitAttemptCount + "/5. "
                    + remaining + " attempts left.");
        }
    }

    // Developer options check removed

    // --- AI PROCTORING ---

    private void startCamera() {
        if (cameraPreview == null) return;

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                
                FaceDetectorOptions options =
                        new FaceDetectorOptions.Builder()
                                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                                .build();
                faceDetector = FaceDetection.getClient(options);
                textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
                    processImageProxy(imageProxy);
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                cameraReady = true;
                updateProctoringStatus("🔒 Secure Mode  •  📷 Camera Ready", false);
                showPreExamConfirmationDialog();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to bind front camera preview", e);
                cameraReady = false;
                updateProctoringStatus("⚠️ Camera monitoring unavailable", true);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            long now = System.currentTimeMillis();
            boolean shouldCheckBook = examStarted && (now - lastBookCheckMs >= BOOK_CHECK_INTERVAL_MS);
            if (shouldCheckBook) {
                lastBookCheckMs = now;
            }
            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (shouldCheckBook && textRecognizer != null) {
                            textRecognizer.process(image)
                                    .addOnSuccessListener(recognizedText -> {
                                        boolean bookVisible = isBookLikelyVisible(recognizedText);
                                        if (bookVisible) {
                                            lastBookVisibleMs = System.currentTimeMillis();
                                        }
                                        boolean bookVisibleRecent =
                                                (System.currentTimeMillis() - lastBookVisibleMs) <= BOOK_VISIBLE_GRACE_MS;
                                        analyzeFaces(faces, bookVisibleRecent);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.w(TAG, "Book visibility text check failed", e);
                                        boolean bookVisibleRecent =
                                                (System.currentTimeMillis() - lastBookVisibleMs) <= BOOK_VISIBLE_GRACE_MS;
                                        analyzeFaces(faces, bookVisibleRecent);
                                    })
                                    .addOnCompleteListener(task -> imageProxy.close());
                        } else {
                            boolean bookVisibleRecent =
                                    (System.currentTimeMillis() - lastBookVisibleMs) <= BOOK_VISIBLE_GRACE_MS;
                            analyzeFaces(faces, bookVisibleRecent);
                            imageProxy.close();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Face detection failed", e);
                        imageProxy.close();
                    })
                    .addOnCompleteListener(task -> {
                        // Closed in success/failure path above.
                    });
        } else {
            imageProxy.close();
        }
    }

    private boolean isBookLikelyVisible(Text recognizedText) {
        if (recognizedText == null) return false;
        String full = recognizedText.getText();
        if (full == null || full.trim().isEmpty()) return false;
        int letterCount = 0;
        for (int i = 0; i < full.length(); i++) {
            if (Character.isLetterOrDigit(full.charAt(i))) {
                letterCount++;
            }
        }
        return recognizedText.getTextBlocks().size() >= 1 && letterCount >= 15;
    }

    private List<Question> buildRandomizedQuestionSet(List<Question> source) {
        List<Question> randomized = new ArrayList<>();
        if (source == null) return randomized;
        for (Question original : source) {
            if (original == null) continue;
            Question copy = new Question();
            copy.setQuestionText(original.getQuestionText());
            copy.setType(original.getType());
            copy.setCorrectAnswer(original.getCorrectAnswer());
            copy.setCorrectAnswerIndex(original.getCorrectAnswerIndex());
            List<String> opts = original.getOptions();
            if (opts != null) {
                copy.setOptions(new ArrayList<>(opts));
            } else {
                copy.setOptions(new ArrayList<>());
            }
            if (copy.getType() == Question.QuestionType.MULTIPLE_CHOICE && copy.getOptions() != null) {
                List<String> options = new ArrayList<>(copy.getOptions());
                int oldIndex = copy.getCorrectAnswerIndex();
                String correctValue =
                        (oldIndex >= 0 && oldIndex < options.size()) ? options.get(oldIndex) : null;
                Collections.shuffle(options);
                copy.setOptions(options);
                if (correctValue != null) {
                    int newIndex = options.indexOf(correctValue);
                    copy.setCorrectAnswerIndex(newIndex >= 0 ? newIndex : 0);
                } else {
                    copy.setCorrectAnswerIndex(0);
                }
            }
            randomized.add(copy);
        }
        Collections.shuffle(randomized);
        return randomized;
    }

    private void analyzeFaces(List<Face> faces, boolean bookVisibleRecent) {
        if (!examStarted || isExamLocked || isSubmitting || isFinishing()) return;
        
        long now = System.currentTimeMillis();
        // Avoid warning spam.
        if (System.currentTimeMillis() - lastWarningTime < 5000) return;

        if (faces.size() == 0) {
            // Open-book support: if book/text is visible in camera, do not warn for no-face.
            if (bookVisibleRecent) {
                noFaceSinceMs = 0L;
                multiFaceSinceMs = 0L;
                badPoseSinceMs = 0L;
                updateProctoringStatus("📘 Book detected  •  AI Monitoring Active", false);
                return;
            }
            if (noFaceSinceMs == 0L) noFaceSinceMs = now;
            multiFaceSinceMs = 0L;
            badPoseSinceMs = 0L;
            if (now - noFaceSinceMs >= NO_FACE_THRESHOLD_MS) {
                handleFaceViolation("No face/book detected continuously for 3 seconds.");
                noFaceSinceMs = now;
            }
        } else if (faces.size() > 1) {
            if (multiFaceSinceMs == 0L) multiFaceSinceMs = now;
            noFaceSinceMs = 0L;
            badPoseSinceMs = 0L;
            if (now - multiFaceSinceMs >= MULTI_FACE_THRESHOLD_MS) {
                handleFaceViolation("Multiple faces detected continuously for 3 seconds.");
                multiFaceSinceMs = now;
            }
        } else {
            noFaceSinceMs = 0L;
            multiFaceSinceMs = 0L;
            // One face detected, check head pose with grace period.
            Face face = faces.get(0);
            if (Math.abs(face.getHeadEulerAngleY()) > 35 || Math.abs(face.getHeadEulerAngleZ()) > 35) {
                if (badPoseSinceMs == 0L) badPoseSinceMs = now;
                if (now - badPoseSinceMs >= BAD_POSE_THRESHOLD_MS) {
                    handleFaceViolation("Suspicious head movement for 3 seconds. Look at the screen.");
                    badPoseSinceMs = now;
                }
            } else {
                badPoseSinceMs = 0L;
            }
        }
    }

    private void handleFaceViolation(String message) {
        faceWarningCount++;
        lastWarningTime = System.currentTimeMillis();
        updateProctoringStatus("⚠️ AI Alert: " + message, true);
        
        if (faceWarningCount >= MAX_FACE_WARNINGS) {
            Toast.makeText(this, "Maximum AI warnings reached. Auto-submitting exam.", Toast.LENGTH_LONG).show();
            submitExam(true);
        } else {
            int remaining = MAX_FACE_WARNINGS - faceWarningCount;
            if (remaining <= 1) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Critical AI Warning")
                        .setMessage(message + "\n\nWarning " + faceWarningCount + " of " + MAX_FACE_WARNINGS + ". Next warning will auto-submit the exam.")
                        .setPositiveButton("I Understand", null)
                        .show();
            } else {
                Toast.makeText(
                        this,
                        "AI warning " + faceWarningCount + "/" + MAX_FACE_WARNINGS + ": " + message,
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }


    private void updateProctoringStatus(String status, boolean isViolation) {
        runOnUiThread(() -> {
            TextView statusView = findViewById(R.id.monitoring_status);
            if (statusView != null) {
                statusView.setText(status);
                statusView.setBackgroundColor(isViolation ? 0xFFFF0000 : 0xFF4CAF50);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required for this monitored exam.",
                        Toast.LENGTH_LONG).show();
                if (cameraPreview != null) {
                    cameraPreview.setVisibility(android.view.View.GONE);
                }
                updateProctoringStatus("⚠️ Camera permission denied", true);
                if (currentExam != null && currentExam.isCameraMonitoringEnabled()) {
                    finish();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (examStatusRef != null && examStatusListener != null) {
            examStatusRef.removeEventListener(examStatusListener);
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (textRecognizer != null) {
            textRecognizer.close();
        }
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        // Never launch a new Activity from onDestroy; persist only.
        if (!isExamLocked && currentExam != null) {
            saveCurrentAnswer();
            ExamRepository.getInstance().saveJoinedExam(currentExam);
        }
    }

    private void updateCurrentStudentStatus(String statusText, boolean flagged) {
        if (currentExam == null || currentExam.getJoinedStudents() == null) {
            return;
        }
        com.example.nocheatzone.model.StudentStatus matched = null;
        for (com.example.nocheatzone.model.StudentStatus st : currentExam.getJoinedStudents()) {
            if (st == null) continue;
            if (studentId != null && studentId.equals(st.getStudentId())) {
                matched = st;
                break;
            }
        }
        if (matched == null && studentName != null) {
            for (com.example.nocheatzone.model.StudentStatus st : currentExam.getJoinedStudents()) {
                if (st == null) continue;
                if (studentName.equals(st.getName())) {
                    matched = st;
                    break;
                }
            }
        }
        if (matched != null) {
            matched.setStatus(statusText);
            matched.setFlagged(flagged);
            if (flagged) {
                matched.setFlagTimestamp(System.currentTimeMillis());
            }
        }
        ExamRepository.getInstance().saveExam(currentExam);
    }

    private long resolveOrCreateExamDeadline(int durationMinutes) {
        android.content.SharedPreferences prefs =
                getSharedPreferences(PREF_EXAM_SESSION, MODE_PRIVATE);
        String key = buildDeadlineKey();
        long storedDeadline = prefs.getLong(key, 0L);
        if (storedDeadline > 0L) {
            return storedDeadline;
        }
        long newDeadline = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);
        prefs.edit().putLong(key, newDeadline).apply();
        return newDeadline;
    }

    private void clearExamDeadline() {
        android.content.SharedPreferences prefs =
                getSharedPreferences(PREF_EXAM_SESSION, MODE_PRIVATE);
        prefs.edit().remove(buildDeadlineKey()).apply();
    }

    private String buildDeadlineKey() {
        String safeExamId = (examId != null && !examId.trim().isEmpty())
                ? examId
                : ("unknown_exam_" + hashSeed());
        String safeStudentId = (studentId != null && !studentId.trim().isEmpty())
                ? studentId
                : ("unknown_student_" + hashSeed());
        return KEY_DEADLINE_PREFIX + safeExamId + "_" + safeStudentId;
    }

    private String hashSeed() {
        String raw = String.valueOf(System.identityHashCode(this)) + "_" + System.currentTimeMillis();
        return Integer.toHexString(raw.hashCode());
    }

    private boolean isAppInBackground() {
        ActivityManager.RunningAppProcessInfo appProcessInfo =
                new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return appProcessInfo.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && appProcessInfo.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
    }

    private boolean isDeveloperModeEnabled() {
        try {
            return android.provider.Settings.Global.getInt(
                    getContentResolver(),
                    android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    0
            ) != 0;
        } catch (Exception e) {
            Log.w(TAG, "Unable to read developer options state", e);
            return false;
        }
    }
}
