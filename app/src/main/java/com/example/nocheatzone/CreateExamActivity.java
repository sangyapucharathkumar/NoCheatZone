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

import com.example.nocheatzone.model.Exam;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class CreateExamActivity extends InternetCheckActivity {

    private TextInputEditText titleInput;
    private TextInputEditText descInput;
    private SwitchMaterial switchAiProctoring;
    private SwitchMaterial switchCamera;
    private SwitchMaterial switchWindowLock;
    private SwitchMaterial switchDevOptions;
    private SwitchMaterial switchHideStudentResult;

    private TextInputEditText durationInput;
    private android.widget.TextView timeSummary;
    private long startTimestamp = 0;
    private long endTimestamp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_exam);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        // Setup Toolbar
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Initialize Views
        titleInput = findViewById(R.id.exam_title_input);
        descInput = findViewById(R.id.exam_desc_input);
        durationInput = findViewById(R.id.exam_duration_input);
        timeSummary = findViewById(R.id.text_time_summary);
        
        switchAiProctoring = findViewById(R.id.switch_ai_proctoring);
        switchCamera = findViewById(R.id.switch_camera);
        switchWindowLock = findViewById(R.id.switch_window_lock);
        switchDevOptions = findViewById(R.id.switch_dev_options);
        switchHideStudentResult = findViewById(R.id.switch_hide_student_result);

        findViewById(R.id.btn_start_time).setOnClickListener(v -> showTimePicker(true));
        findViewById(R.id.btn_end_time).setOnClickListener(v -> showTimePicker(false));

        MaterialButton btnNext = findViewById(R.id.btn_next_questions);
        btnNext.setOnClickListener(v -> createExamAndProceed());
    }

    private void showTimePicker(boolean isStart) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        new android.app.TimePickerDialog(this, (view, hourOfDay, minute) -> {
            java.util.Calendar picked = java.util.Calendar.getInstance();
            picked.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay);
            picked.set(java.util.Calendar.MINUTE, minute);
            picked.set(java.util.Calendar.SECOND, 0);
            
            if (isStart) startTimestamp = picked.getTimeInMillis();
            else endTimestamp = picked.getTimeInMillis();
            
            updateTimeSummary();
        }, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), false).show();
    }

    private void updateTimeSummary() {
        String startStr = startTimestamp == 0 ? "Not set" : new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(startTimestamp));
        String endStr = endTimestamp == 0 ? "Not set" : new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(endTimestamp));
        timeSummary.setText("Window: " + startStr + " to " + endStr);
    }

    private void createExamAndProceed() {
        String title = titleInput.getText() != null
                ? titleInput.getText().toString().trim()
                : "";
        String desc = descInput.getText() != null
                ? descInput.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(title)) {
            titleInput.setError("Exam Title is required");
            return;
        }
        
        Exam newExam = new Exam(title, desc);
        
        // Save Time Settings
        newExam.setStartTime(startTimestamp);
        newExam.setEndTime(endTimestamp);
        
        String durationStr = durationInput.getText() != null ? durationInput.getText().toString() : "60";
        try {
            newExam.setDurationMinutes(Integer.parseInt(durationStr));
        } catch (Exception e) {
            newExam.setDurationMinutes(60);
        }

        // Save Security Settings
        newExam.setAiProctoringEnabled(switchAiProctoring.isChecked());
        newExam.setCameraMonitoringEnabled(switchCamera.isChecked());
        newExam.setWindowSwitchingBlocked(switchWindowLock.isChecked());
        newExam.setDeveloperOptionsBlocked(switchDevOptions.isChecked());
        newExam.setStudentResultHidden(switchHideStudentResult != null && switchHideStudentResult.isChecked());

        ExamRepository.getInstance().saveDraftExamLocally(newExam);

        Toast.makeText(this, "Exam created! Now add questions.", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, AddQuestionsActivity.class);
        intent.putExtra("EXAM_ID", newExam.getId());
        startActivity(intent);
        finish();
    }
}
