package com.example.nocheatzone;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nocheatzone.model.Exam;
import com.example.nocheatzone.model.Question;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

public class ReviewQuestionsActivity extends AppCompatActivity {

    private Exam currentExam;
    private RecyclerView recyclerView;
    private ReviewQuestionsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); 
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_review_questions);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        String examId = getIntent().getStringExtra("EXAM_ID");
        if (examId != null) {
            currentExam = ExamRepository.getInstance().getExamById(examId);
        }

        if (currentExam == null) {
            Toast.makeText(this, "Error: Exam not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setTitle("Review Questions");
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_review_questions);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            List<Question> questions = currentExam.getQuestions();
            if (questions == null) {
                questions = new java.util.ArrayList<>();
                currentExam.setQuestions(questions);
            }
            adapter = new ReviewQuestionsAdapter(questions, this::deleteQuestion, this::showEditDialog);
            recyclerView.setAdapter(adapter);
        }
    }

    private void showEditDialog(int position) {
        Question q = currentExam.getQuestions().get(position);
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Edit Question " + (position + 1));

        android.view.View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_edit_question, null);
        com.google.android.material.textfield.TextInputEditText qInput = dialogView.findViewById(R.id.edit_question_text);
        qInput.setText(q.getQuestionText());

        android.widget.LinearLayout optionsContainer = dialogView.findViewById(R.id.dialog_options_container);
        
        com.google.android.material.textfield.TextInputEditText optA = dialogView.findViewById(R.id.dialog_opt_a);
        com.google.android.material.textfield.TextInputEditText optB = dialogView.findViewById(R.id.dialog_opt_b);
        com.google.android.material.textfield.TextInputEditText optC = dialogView.findViewById(R.id.dialog_opt_c);
        com.google.android.material.textfield.TextInputEditText optD = dialogView.findViewById(R.id.dialog_opt_d);
        
        android.widget.RadioButton radioA = dialogView.findViewById(R.id.dialog_radio_a);
        android.widget.RadioButton radioB = dialogView.findViewById(R.id.dialog_radio_b);
        android.widget.RadioButton radioC = dialogView.findViewById(R.id.dialog_radio_c);
        android.widget.RadioButton radioD = dialogView.findViewById(R.id.dialog_radio_d);

        com.google.android.material.textfield.TextInputLayout ansContainer = dialogView.findViewById(R.id.dialog_text_answer_container);
        com.google.android.material.textfield.TextInputEditText ansInput = dialogView.findViewById(R.id.dialog_text_answer_input);

        if (q.getType() == Question.QuestionType.MULTIPLE_CHOICE) {
            optionsContainer.setVisibility(android.view.View.VISIBLE);
            ansContainer.setVisibility(android.view.View.GONE);
            
            List<String> options = q.getOptions();
            if (options != null) {
                if (options.size() > 0) optA.setText(options.get(0));
                if (options.size() > 1) optB.setText(options.get(1));
                if (options.size() > 2) optC.setText(options.get(2));
                if (options.size() > 3) optD.setText(options.get(3));
            }
            
            int ansIdx = q.getCorrectAnswerIndex();
            if (ansIdx == 0) radioA.setChecked(true);
            else if (ansIdx == 1) radioB.setChecked(true);
            else if (ansIdx == 2) radioC.setChecked(true);
            else if (ansIdx == 3) radioD.setChecked(true);
            
        } else {
            optionsContainer.setVisibility(android.view.View.GONE);
            ansContainer.setVisibility(android.view.View.VISIBLE);
            ansInput.setText(q.getCorrectAnswer());
        }

        builder.setView(dialogView);
        builder.setPositiveButton("Save", (dialog, which) -> {
            q.setQuestionText(qInput.getText().toString().trim());
            if (q.getType() == Question.QuestionType.MULTIPLE_CHOICE) {
                List<String> newOpts = java.util.Arrays.asList(
                    optA.getText().toString().trim(),
                    optB.getText().toString().trim(),
                    optC.getText().toString().trim(),
                    optD.getText().toString().trim()
                );
                q.setOptions(newOpts);
                
                int newAnsIdx = 0;
                if (radioB.isChecked()) newAnsIdx = 1;
                else if (radioC.isChecked()) newAnsIdx = 2;
                else if (radioD.isChecked()) newAnsIdx = 3;
                q.setCorrectAnswerIndex(newAnsIdx);
            } else {
                q.setCorrectAnswer(ansInput.getText().toString().trim());
            }
            ExamRepository.getInstance().saveDraftExamLocally(currentExam);
            adapter.notifyItemChanged(position);
            Toast.makeText(this, "Question Updated", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void deleteQuestion(int position) {
        List<Question> questions = currentExam.getQuestions();
        if (position >= 0 && position < questions.size()) {
            questions.remove(position);
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, questions.size());

            // Persist the deletion
            ExamRepository.getInstance().saveDraftExamLocally(currentExam);
            Toast.makeText(this, "Question Deleted", Toast.LENGTH_SHORT).show();

            if (questions.isEmpty()) {
                Toast.makeText(this, "No questions remaining", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
