package com.example.nocheatzone;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.nocheatzone.model.Question;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScanQuestionActivity extends InternetCheckActivity {

    private static final String TAG = "ScanQuestionActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private PreviewView viewFinder;
    private ImageCapture imageCapture;
    private ProgressBar progressBar;
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scan_question);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));

        viewFinder = findViewById(R.id.viewFinder);
        progressBar = findViewById(R.id.progress_loading);
        View btnCapture = findViewById(R.id.btn_capture);

        String docUriString = getIntent().getStringExtra("DOCUMENT_URI");
        if (docUriString != null) {
            // PDF mode
            btnCapture.setVisibility(View.GONE);
            viewFinder.setVisibility(View.GONE);
            processPdfFromUri(android.net.Uri.parse(docUriString));
        } else {
            // Camera mode
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            }
        }

        btnCapture.setOnClickListener(v -> takePhoto());
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void processPdfFromUri(android.net.Uri uri) {
        progressBar.setVisibility(View.VISIBLE);
        try {
            android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
            if (pfd != null) {
                android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(pfd);
                if (renderer.getPageCount() > 0) {
                    android.graphics.pdf.PdfRenderer.Page page = renderer.openPage(0);
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(page.getWidth(), page.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                    
                    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                    canvas.drawColor(android.graphics.Color.WHITE);
                    
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    
                    InputImage image = InputImage.fromBitmap(bitmap, 0);
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                            .addOnSuccessListener(visionText -> {
                                parseQuestions(visionText);
                                page.close();
                                renderer.close();
                            });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "PDF Error", e);
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load PDF", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        progressBar.setVisibility(View.VISIBLE);

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                processImage(image);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ScanQuestionActivity.this, "Capture Failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void processImage(ImageProxy imageProxy) {
        @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        parseQuestions(visionText);
                        imageProxy.close();
                    })
                    .addOnFailureListener(e -> {
                        progressBar.setVisibility(View.GONE);
                        imageProxy.close();
                        Toast.makeText(this, "Text parsing failed", Toast.LENGTH_SHORT).show();
                    });
        } else {
            imageProxy.close();
            progressBar.setVisibility(View.GONE);
        }
    }

    private void parseQuestions(Text visionText) {
        String fullText = visionText.getText();
        String[] lines = fullText.split("\n");

        ArrayList<Question> questions = new ArrayList<>();
        
        Pattern questionPattern = Pattern.compile("(?i)^[\\(\\[]?\\d+[.)\\]]?\\s*(.*)");
        Pattern optionPattern = Pattern.compile("(?i)^[\\(\\-\\s]*([A-D])[.)\\]]?\\s+(.*)");
        Pattern answerPattern = Pattern.compile("(?i)^(?:ans(?:wer)?|correct(?:\\s+answer)?)[\\s:]*([A-D]|.+)");
        Pattern inlineOptionPattern = Pattern.compile("(?i)\\(([a-d])\\)\\s*([^\\(]+)");

        Question currentQ = null;
        List<String> currentOpts = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            Matcher aMatcher = answerPattern.matcher(trimmed);
            if (aMatcher.find()) {
                if (currentQ != null) {
                    String ans = aMatcher.group(1).trim();
                    if (ans.length() == 1 && Character.isLetter(ans.charAt(0))) {
                        currentQ.setCorrectAnswerIndex(ans.toUpperCase(java.util.Locale.ROOT).charAt(0) - 'A');
                        currentQ.setType(Question.QuestionType.MULTIPLE_CHOICE);
                    } else {
                        currentQ.setCorrectAnswer(ans);
                        if (currentOpts.isEmpty()) {
                            currentQ.setType(Question.QuestionType.SHORT_ANSWER);
                        }
                    }
                }
                continue;
            }

            Matcher qMatcher = questionPattern.matcher(trimmed);
            if (qMatcher.find()) {
                if (currentQ != null) {
                    completeCurrentQuestion(questions, currentQ, currentOpts);
                }
                currentQ = new Question();
                String qText = qMatcher.group(1).trim();
                currentOpts = new ArrayList<>();
                
                Matcher inlineOptMatcher = inlineOptionPattern.matcher(qText);
                int firstInlineIdx = -1;
                while (inlineOptMatcher.find()) {
                    if (firstInlineIdx == -1) firstInlineIdx = inlineOptMatcher.start();
                    currentOpts.add(inlineOptMatcher.group(2).trim());
                }
                
                if (firstInlineIdx != -1) {
                    currentQ.setQuestionText(qText.substring(0, firstInlineIdx).trim());
                    currentQ.setType(Question.QuestionType.MULTIPLE_CHOICE);
                } else {
                    currentQ.setQuestionText(qText);
                    if (qText.contains("___")) {
                        currentQ.setType(Question.QuestionType.FILL_IN_BLANKS);
                    } else {
                        currentQ.setType(Question.QuestionType.SHORT_ANSWER);
                    }
                }
                continue;
            }

            Matcher oMatcher = optionPattern.matcher(trimmed);
            if (oMatcher.find() && currentQ != null) {
                Matcher inlineOptMatcher = inlineOptionPattern.matcher(trimmed);
                boolean foundInline = false;
                while (inlineOptMatcher.find()) {
                    foundInline = true;
                    currentOpts.add(inlineOptMatcher.group(2).trim());
                }
                if (!foundInline) {
                    currentOpts.add(oMatcher.group(2).trim());
                }
                currentQ.setType(Question.QuestionType.MULTIPLE_CHOICE);
                continue;
            }
            
            if (currentQ != null && currentOpts.isEmpty()) {
                currentQ.setQuestionText(currentQ.getQuestionText() + " " + trimmed);
            }
        }

        if (currentQ != null) {
            completeCurrentQuestion(questions, currentQ, currentOpts);
        }

        progressBar.setVisibility(View.GONE);

        if (questions.isEmpty()) {
            Toast.makeText(this, "No questions found in document", Toast.LENGTH_LONG).show();
            return;
        }

        Intent resultIntent = new Intent();
        resultIntent.putExtra("SCANNED_QUESTIONS", questions);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void completeCurrentQuestion(List<Question> list, Question q, List<String> opts) {
        if (q.getType() == Question.QuestionType.MULTIPLE_CHOICE || !opts.isEmpty()) {
            q.setType(Question.QuestionType.MULTIPLE_CHOICE);
            while (opts.size() < 2) opts.add("Option " + (opts.size() + 1));
            if (opts.size() > 4) opts = opts.subList(0, 4);
            q.setOptions(opts);
            if (q.getCorrectAnswerIndex() < 0 || q.getCorrectAnswerIndex() >= opts.size()) {
                q.setCorrectAnswerIndex(0);
            }
        }
        list.add(q);
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
