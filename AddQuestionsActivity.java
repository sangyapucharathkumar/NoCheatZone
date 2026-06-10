package com.example.nocheatzone;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.nocheatzone.model.Exam;
import com.example.nocheatzone.model.Question;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AddQuestionsActivity extends AppCompatActivity {

    private Exam currentExam;
    private int questionCounter = 1;

    // Selected answer: 0=A, 1=B, 2=C, 3=D, -1=none
    private int selectedOptionIndex = -1;
    private int visibleOptions = 2;

    private TextInputEditText questionTextInput;
    private TextInputEditText optAInput, optBInput, optCInput, optDInput;

    private LinearLayout optionAContainer, optionBContainer, optionCContainer, optionDContainer;
    private RadioButton radioOptA, radioOptB, radioOptC, radioOptD;

    private com.google.android.material.chip.ChipGroup chipGroupType;
    private com.google.android.material.card.MaterialCardView cardMcq, cardText;
    private TextInputEditText correctAnswerInput;

    private final androidx.activity.result.ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    List<Question> scanned = readScannedQuestionsIntentExtra(result.getData());
                    if (scanned != null) {
                        for (Question q : scanned) {
                            currentExam.addQuestion(q);
                            questionCounter++;
                        }
                        ExamRepository.getInstance().saveDraftExamLocally(currentExam);
                        updateQuestionCounter();
                        Toast.makeText(this, scanned.size() + " Questions Scanned! 🤖", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<String> pdfLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    processPdfDocument(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_questions);
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Load Exam from Repository via ID
        String examId = getIntent().getStringExtra("EXAM_ID");
        if (examId != null) {
            currentExam = ExamRepository.getInstance().getExamById(examId);
        }

        // Fallback: support old EXAM_DATA extra for backward compatibility
        if (currentExam == null && getIntent().hasExtra("EXAM_DATA")) {
            currentExam = readExamFromIntentExtra(getIntent());
            if (currentExam != null) {
                ExamRepository.getInstance().saveDraftExamLocally(currentExam);
            }
        }

        if (currentExam == null) {
            Toast.makeText(this, "Error: No Exam Data Found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (currentExam.getQuestions() == null) {
            currentExam.setQuestions(new ArrayList<>());
        }

        // Setup Toolbar
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setTitle("Add Questions: " + currentExam.getTitle());
        toolbar.setSubtitle("Code: " + currentExam.getAccessCode());
        toolbar.setNavigationOnClickListener(v -> navigateToHostDashboard());

        // Initialize Views
        questionTextInput = findViewById(R.id.question_text_input);
        optAInput = findViewById(R.id.opt_a_input);
        optBInput = findViewById(R.id.opt_b_input);
        optCInput = findViewById(R.id.opt_c_input);
        optDInput = findViewById(R.id.opt_d_input);
        
        optionAContainer = findViewById(R.id.option_a_container);
        optionBContainer = findViewById(R.id.option_b_container);
        optionCContainer = findViewById(R.id.option_c_container);
        optionDContainer = findViewById(R.id.option_d_container);
        
        radioOptA = findViewById(R.id.radio_opt_a);
        radioOptB = findViewById(R.id.radio_opt_b);
        radioOptC = findViewById(R.id.radio_opt_c);
        radioOptD = findViewById(R.id.radio_opt_d);

        MaterialButton btnAddQuestion = findViewById(R.id.btn_add_another);
        MaterialButton btnReview = findViewById(R.id.btn_review_questions);
        MaterialButton btnFinish = findViewById(R.id.btn_finish_exam);
        MaterialButton btnScan = findViewById(R.id.btn_scan_camera);
        MaterialButton btnPdf = findViewById(R.id.btn_upload_pdf);

        cardMcq = findViewById(R.id.card_mcq_options);
        cardText = findViewById(R.id.card_text_answer);
        correctAnswerInput = findViewById(R.id.correct_answer_input);
        chipGroupType = findViewById(R.id.chip_group_type);

        chipGroupType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_mcq) {
                cardMcq.setVisibility(android.view.View.VISIBLE);
                cardText.setVisibility(android.view.View.GONE);
            } else {
                cardMcq.setVisibility(android.view.View.GONE);
                cardText.setVisibility(android.view.View.VISIBLE);
            }
        });

        // Set option container click listeners (tapping any part of the row selects it)
        if (optionAContainer != null) optionAContainer.setOnClickListener(v -> selectOption(0));
        if (optionBContainer != null) optionBContainer.setOnClickListener(v -> selectOption(1));
        if (optionCContainer != null) optionCContainer.setOnClickListener(v -> selectOption(2));
        if (optionDContainer != null) optionDContainer.setOnClickListener(v -> selectOption(3));
        
        MaterialButton btnAddOption = findViewById(R.id.btn_add_option);
        if (btnAddOption != null) {
            btnAddOption.setOnClickListener(v -> {
                if (visibleOptions == 2) {
                    if (optionCContainer != null) optionCContainer.setVisibility(android.view.View.VISIBLE);
                    visibleOptions = 3;
                } else if (visibleOptions == 3) {
                    if (optionDContainer != null) optionDContainer.setVisibility(android.view.View.VISIBLE);
                    visibleOptions = 4;
                    btnAddOption.setVisibility(android.view.View.GONE);
                }
            });
        }

        // Ensure only 2 options are visible initially (Yes/No style questions)
        visibleOptions = 2;
        if (optionCContainer != null) optionCContainer.setVisibility(android.view.View.GONE);
        if (optionDContainer != null) optionDContainer.setVisibility(android.view.View.GONE);
        if (btnAddOption != null) btnAddOption.setVisibility(android.view.View.VISIBLE);

        // Sync counter with existing questions
        questionCounter = currentExam.getQuestions().size() + 1;
        updateQuestionCounter();

        btnAddQuestion.setOnClickListener(v -> addQuestion());

        btnReview.setOnClickListener(v -> {
            Intent intent = new Intent(AddQuestionsActivity.this, ReviewQuestionsActivity.class);
            intent.putExtra("EXAM_ID", currentExam.getId());
            startActivity(intent);
        });

        btnFinish.setOnClickListener(v -> finishExam());
        
        btnScan.setOnClickListener(v -> {
            Intent intent = new Intent(AddQuestionsActivity.this, ScanQuestionActivity.class);
            scanLauncher.launch(intent);
        });

        btnPdf.setOnClickListener(v -> pdfLauncher.launch("application/pdf"));

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateToHostDashboard();
            }
        });
    }

    private void processPdfDocument(android.net.Uri uri) {
        Toast.makeText(this, "Processing PDF with AI... 🤖", Toast.LENGTH_SHORT).show();
        // Here we would use PdfRenderer + ML Kit
        // For now, redirect to ScanQuestionActivity with the URI
        Intent intent = new Intent(this, ScanQuestionActivity.class);
        intent.putExtra("DOCUMENT_URI", uri.toString());
        scanLauncher.launch(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recalculate counter in case questions were deleted in Review Activity
        if (currentExam != null) {
            if (currentExam.getQuestions() == null) {
                currentExam.setQuestions(new ArrayList<>());
            }
            questionCounter = currentExam.getQuestions().size() + 1;
            updateQuestionCounter();
        }
    }

    /**
     * Selects an option (0=A, 1=B, 2=C, 3=D) and updates the UI.
     * All other options are deselected.
     */
    private void selectOption(int index) {
        selectedOptionIndex = index;

        // Update all containers and radio buttons
        if (optionAContainer != null) updateOptionUI(optionAContainer, radioOptA, 0 == index);
        if (optionBContainer != null) updateOptionUI(optionBContainer, radioOptB, 1 == index);
        if (optionCContainer != null) updateOptionUI(optionCContainer, radioOptC, 2 == index);
        if (optionDContainer != null) updateOptionUI(optionDContainer, radioOptD, 3 == index);
    }

    /**
     * Updates a single option container's visual selected state.
     */
    private void updateOptionUI(LinearLayout container, RadioButton radio, boolean selected) {
        radio.setChecked(selected);
        if (selected) {
            container.setBackground(
                    ContextCompat.getDrawable(this, R.drawable.bg_option_selected));
        } else {
            container.setBackground(
                    ContextCompat.getDrawable(this, R.drawable.option_bg_selector));
        }
    }

    private void addQuestion() {
        String qText = questionTextInput.getText() != null ? questionTextInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(qText)) {
            questionTextInput.setError("Question text is required");
            return;
        }

        Question question = new Question();
        question.setQuestionText(qText);

        int checkedChipId = chipGroupType.getCheckedChipId();
        if (checkedChipId == R.id.chip_mcq) {
            String optA = (optAInput != null && optAInput.getText() != null) ? optAInput.getText().toString().trim() : "";
            String optB = (optBInput != null && optBInput.getText() != null) ? optBInput.getText().toString().trim() : "";
            String optC = (optCInput != null && optCInput.getText() != null) ? optCInput.getText().toString().trim() : "";
            String optD = (optDInput != null && optDInput.getText() != null) ? optDInput.getText().toString().trim() : "";

            if (TextUtils.isEmpty(optA) || TextUtils.isEmpty(optB)) {
                Toast.makeText(this, "Please fill at least 2 MCQ options", Toast.LENGTH_SHORT).show();
                return;
            }
            if (visibleOptions >= 3 && TextUtils.isEmpty(optC)) {
                Toast.makeText(this, "Please fill Option C", Toast.LENGTH_SHORT).show();
                return;
            }
            if (visibleOptions == 4 && TextUtils.isEmpty(optD)) {
                Toast.makeText(this, "Please fill Option D", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedOptionIndex == -1 || selectedOptionIndex >= visibleOptions) {
                Toast.makeText(this, "Please select the correct answer", Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> options = new ArrayList<>();
            options.add(optA);
            options.add(optB);
            if (visibleOptions >= 3) options.add(optC);
            if (visibleOptions == 4) options.add(optD);
            
            question.setOptions(options);
            question.setCorrectAnswerIndex(selectedOptionIndex);
            question.setType(Question.QuestionType.MULTIPLE_CHOICE);

        } else {
            String answer = correctAnswerInput.getText() != null ? correctAnswerInput.getText().toString().trim() : "";
            if (TextUtils.isEmpty(answer)) {
                correctAnswerInput.setError("Correct answer is required");
                return;
            }
            question.setCorrectAnswer(answer);
            question.setType(checkedChipId == R.id.chip_short_answer ? Question.QuestionType.SHORT_ANSWER : Question.QuestionType.FILL_IN_BLANKS);
        }

        currentExam.addQuestion(question);
        ExamRepository.getInstance().saveDraftExamLocally(currentExam);

        Toast.makeText(this, "Question " + questionCounter + " Added! ✅", Toast.LENGTH_SHORT).show();
        questionCounter++;
        clearFields();
    }

    private void clearFields() {
        if (questionTextInput != null) questionTextInput.setText("");
        if (optAInput != null) optAInput.setText("");
        if (optBInput != null) optBInput.setText("");
        if (optCInput != null) optCInput.setText("");
        if (optDInput != null) optDInput.setText("");
        if (correctAnswerInput != null) correctAnswerInput.setText("");
        selectedOptionIndex = -1;
        if (optionAContainer != null) updateOptionUI(optionAContainer, radioOptA, false);
        if (optionBContainer != null) updateOptionUI(optionBContainer, radioOptB, false);
        if (optionCContainer != null) updateOptionUI(optionCContainer, radioOptC, false);
        if (optionDContainer != null) updateOptionUI(optionDContainer, radioOptD, false);
        
        visibleOptions = 2;
        if (optionCContainer != null) optionCContainer.setVisibility(android.view.View.GONE);
        if (optionDContainer != null) optionDContainer.setVisibility(android.view.View.GONE);
        MaterialButton btnAddOption = findViewById(R.id.btn_add_option);
        if (btnAddOption != null) btnAddOption.setVisibility(android.view.View.VISIBLE);
        
        updateQuestionCounter();
    }

    private void updateQuestionCounter() {
        TextView counterText = findViewById(R.id.question_count_text);
        TextView badgeText = findViewById(R.id.question_number_badge);
        if (counterText != null) {
            counterText.setText("Question " + questionCounter);
        }
        if (badgeText != null) {
            badgeText.setText(String.valueOf(questionCounter));
        }
    }

    private void finishExam() {
        if (currentExam.getQuestions().isEmpty()) {
            Toast.makeText(this, "Please add at least one question", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save to hosted history
        ExamRepository.getInstance().saveHostedExam(currentExam);

        Toast.makeText(this,
                "Exam Created Successfully! 🚀",
                Toast.LENGTH_LONG).show();

        // Navigate to Monitoring Dashboard for the newly created exam
        Intent intent = new Intent(this, MonitoringActivity.class);
        intent.putExtra("EXAM_ID", currentExam.getId());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToHostDashboard() {
        Intent intent = new Intent(this, Host_Activity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Reads scanned questions defensively to avoid unsafe generic casts from Serializable extras.
     */
    @Nullable
    private static List<Question> readScannedQuestionsIntentExtra(@Nullable Intent data) {
        if (data == null) {
            return null;
        }
        final Serializable serializable;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            serializable = data.getSerializableExtra("SCANNED_QUESTIONS", Serializable.class);
        } else {
            serializable = data.getSerializableExtra("SCANNED_QUESTIONS");
        }
        if (!(serializable instanceof List<?>)) {
            return null;
        }
        List<?> rawList = (List<?>) serializable;
        List<Question> safeQuestions = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (!(item instanceof Question)) {
                return null;
            }
            safeQuestions.add((Question) item);
        }
        return safeQuestions;
    }

    @Nullable
    private static Exam readExamFromIntentExtra(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getSerializableExtra("EXAM_DATA", Exam.class);
        }
        Serializable extra = intent.getSerializableExtra("EXAM_DATA");
        return extra instanceof Exam ? (Exam) extra : null;
    }
}
