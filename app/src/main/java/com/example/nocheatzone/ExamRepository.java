package com.example.nocheatzone;

import com.example.nocheatzone.model.Exam;
import com.example.nocheatzone.model.StudentResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton in-memory repository for Exam objects.
 * Acts as the single source of truth for all exam data within the app session.
 */
public class ExamRepository {

    private static ExamRepository instance;
    private final List<Exam> exams = new ArrayList<>();
    private final List<Exam> hostedExams = new ArrayList<>();
    private final List<Exam> joinedExams = new ArrayList<>();
    private final Map<String, List<StudentResult>> studentResultsByExamId = new LinkedHashMap<>();

    private static final String PREF_NAME = "NoCheatZoneExams";
    private static final String KEY_HOSTED = "hosted_exams";
    private static final String KEY_JOINED = "joined_exams";
    private static final String KEY_RESULTS = "student_results";

    private final android.content.Context context;
    private final com.google.gson.Gson gson = new com.google.gson.Gson();

    private ExamRepository(android.content.Context context) {
        this.context = context.getApplicationContext();
        loadFromPrefs();
        cleanupOldHistory();
    }

    public static synchronized ExamRepository getInstance(android.content.Context context) {
        if (instance == null) {
            instance = new ExamRepository(context);
        }
        return instance;
    }

    // Helper for easier access if already initialized
    public static synchronized ExamRepository getInstance() {
        if (instance == null) {
            throw new RuntimeException("ExamRepository must be initialized with context first!");
        }
        return instance;
    }

    private void loadFromPrefs() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);

        exams.clear();
        hostedExams.clear();
        joinedExams.clear();
        studentResultsByExamId.clear();

        String hostedJson = prefs.getString(KEY_HOSTED, null);
        if (hostedJson != null) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<Exam>>(){}.getType();
            List<Exam> loaded = gson.fromJson(hostedJson, type);
            if (loaded != null) {
                for (Exam exam : loaded) {
                    if (exam != null) {
                        upsertById(hostedExams, exam);
                        upsertById(exams, exam);
                    }
                }
            }
        }

        String joinedJson = prefs.getString(KEY_JOINED, null);
        if (joinedJson != null) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<Exam>>(){}.getType();
            List<Exam> loaded = gson.fromJson(joinedJson, type);
            if (loaded != null) {
                for (Exam exam : loaded) {
                    if (exam != null) {
                        upsertById(joinedExams, exam);
                        upsertById(exams, exam);
                    }
                }
            }
        }

        String resultsJson = prefs.getString(KEY_RESULTS, null);
        if (resultsJson != null) {
            java.lang.reflect.Type type =
                    new com.google.gson.reflect.TypeToken<Map<String, List<StudentResult>>>() {
                    }.getType();
            Map<String, List<StudentResult>> loaded = gson.fromJson(resultsJson, type);
            if (loaded != null) {
                studentResultsByExamId.putAll(loaded);
            }
        }
    }

    private final java.util.concurrent.ExecutorService diskIoExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    private void saveToPrefs() {
        // We capture lightweight list copies on the calling thread to prevent
        // ConcurrentModificationException while serializing.
        final List<Exam> hostedSnap;
        final List<Exam> joinedSnap;
        final Map<String, List<StudentResult>> resultsSnap;
        
        synchronized (this) {
            hostedSnap = new ArrayList<>(hostedExams);
            joinedSnap = new ArrayList<>(joinedExams);
            resultsSnap = new LinkedHashMap<>(studentResultsByExamId);
        }

        diskIoExecutor.execute(() -> {
            String hostedJson = gson.toJson(hostedSnap);
            String joinedJson = gson.toJson(joinedSnap);
            String resultsJson = gson.toJson(resultsSnap);

            android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            
            editor.putString(KEY_HOSTED, hostedJson);
            editor.putString(KEY_JOINED, joinedJson);
            editor.putString(KEY_RESULTS, resultsJson);
            editor.apply();
        });
    }

    /**
     * Save an exam you hosted.
     */
    public synchronized void saveHostedExam(Exam exam) {
        if (exam == null) return;
        upsertById(hostedExams, exam);
        saveExam(exam); // Sync with other lists + prefs
    }

    /**
     * Save an exam you joined.
     */
    public synchronized void saveJoinedExam(Exam exam) {
        if (exam == null) return;
        upsertById(joinedExams, exam);
        saveExam(exam); // Sync with other lists + prefs
    }

    /**
     * Safely store a draft exam in memory so fragments/activities can access it via ID.
     * Does NOT sync to Firebase or Local Prefs yet.
     */
    public synchronized void saveDraftExamLocally(Exam exam) {
        if (exam == null) return;
        ensureExamIdentifiers(exam);
        upsertById(exams, exam);
    }

    public List<Exam> getHostedExams() {
        dedupeInPlace(hostedExams);
        return new ArrayList<>(hostedExams);
    }

    public List<Exam> getJoinedExams() {
        dedupeInPlace(joinedExams);
        return new ArrayList<>(joinedExams); 
    }

    public void cleanupOldHistory() {
        // PERMANENT HISTORY: User requested that history remains until app is uninstalled.
        // No automatic cleanup will occur.
    }

    /**
     * Save or update an exam. If an exam with the same ID exists, it is replaced.
     */
    public synchronized void saveExam(Exam exam) {
        if (exam == null)
            return;
        ensureExamIdentifiers(exam);
        
        try {
            com.google.firebase.database.DatabaseReference ref =
                    com.google.firebase.database.FirebaseDatabase.getInstance().getReference();
            ref.child("exams").child(exam.getId()).setValue(exam);
            // Explicit leaf so StudentExamActivity and other clients can listen at exams/{id}/ended
            // regardless of how the full POJO maps field names.
            ref.child("exams").child(exam.getId()).child("ended").setValue(exam.isEnded());
        } catch (Exception e) {
            // Ignored, Firebase may not be initialized yet
        }

        upsertById(exams, exam);
        replaceByIdIfPresent(hostedExams, exam);
        replaceByIdIfPresent(joinedExams, exam);

        dedupeInPlace(exams);
        dedupeInPlace(hostedExams);
        dedupeInPlace(joinedExams);

        saveToPrefs();
    }

    /** Returns a copy of the exam list to prevent external mutation. */
    public List<Exam> getAllExams() {
        return new ArrayList<>(exams);
    }

    /** Returns the exam with the given ID, or null if not found. */
    public Exam getExamById(String id) {
        if (id == null) return null;
        for (Exam exam : exams) {
            if (exam != null && id.equals(exam.getId())) {
                return exam;
            }
        }
        return null;
    }

    /** Returns the exam with the given 6-digit access code, or null if not found. */
    public Exam getExamByCode(String code) {
        if (code == null) return null;
        for (Exam exam : exams) {
            if (exam.getAccessCode() != null && exam.getAccessCode().equalsIgnoreCase(code.trim())) {
                return exam;
            }
        }
        return null;
    }

    /** Delete an exam by ID. */
    public void deleteExam(String id) {
        if (id == null) return;
        exams.removeIf(exam -> exam != null && id.equals(exam.getId()));
        hostedExams.removeIf(exam -> exam != null && id.equals(exam.getId()));
        joinedExams.removeIf(exam -> exam != null && id.equals(exam.getId()));
        saveToPrefs();
    }

    /** Returns the number of stored exams. */
    public int getExamCount() {
        return exams.size();
    }

    /** Clears all locally cached exam data (used on account switch). */
    public void clearAllData() {
        exams.clear();
        hostedExams.clear();
        joinedExams.clear();
        studentResultsByExamId.clear();
        saveToPrefs();
    }

    public synchronized void saveStudentResult(String examId, StudentResult result) {
        if (examId == null || examId.trim().isEmpty() || result == null) return;
        List<StudentResult> existing = studentResultsByExamId.get(examId);
        if (existing == null) {
            existing = new ArrayList<>();
            studentResultsByExamId.put(examId, existing);
        }

        String incomingStudentId = result.getStudentId();
        if (incomingStudentId != null && !incomingStudentId.trim().isEmpty()) {
            for (int i = 0; i < existing.size(); i++) {
                StudentResult current = existing.get(i);
                if (current != null && incomingStudentId.equals(current.getStudentId())) {
                    existing.set(i, result);
                    saveToPrefs();
                    return;
                }
            }
        }
        existing.add(result);
        saveToPrefs();
    }

    public synchronized List<StudentResult> getStudentResults(String examId) {
        if (examId == null || examId.trim().isEmpty()) return new ArrayList<>();
        List<StudentResult> existing = studentResultsByExamId.get(examId);
        if (existing == null) return new ArrayList<>();
        return new ArrayList<>(existing);
    }

    private void ensureExamIdentifiers(Exam exam) {
        if (exam.getId() == null || exam.getId().trim().isEmpty()) {
            exam.setId(String.valueOf(System.currentTimeMillis()));
        }
        String code = exam.getAccessCode();
        if (code == null || !code.matches("\\d{6}") || isAccessCodeUsedByOtherExam(exam.getId(), code)) {
            exam.setAccessCode(generateUniqueAccessCode(exam.getId()));
        }
    }

    private boolean isAccessCodeUsedByOtherExam(String examId, String accessCode) {
        if (accessCode == null) return false;
        for (Exam current : exams) {
            if (current == null) continue;
            if (examId != null && examId.equals(current.getId())) continue;
            if (accessCode.equals(current.getAccessCode())) return true;
        }
        return false;
    }

    private String generateUniqueAccessCode(String examId) {
        for (int attempt = 0; attempt < 200; attempt++) {
            int raw = (int) (Math.random() * 900000) + 100000;
            String candidate = String.valueOf(raw);
            if (!isAccessCodeUsedByOtherExam(examId, candidate)) {
                return candidate;
            }
        }
        return String.valueOf((int) (Math.random() * 900000) + 100000);
    }

    private void upsertById(List<Exam> list, Exam exam) {
        if (exam == null) return;
        String id = exam.getId();
        if (id == null || id.trim().isEmpty()) {
            if (!list.contains(exam)) {
                list.add(exam);
            }
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Exam current = list.get(i);
            if (current != null && id.equals(current.getId())) {
                list.set(i, exam);
                return;
            }
        }
        list.add(exam);
    }

    private void dedupeInPlace(List<Exam> list) {
        Map<String, Exam> byId = new LinkedHashMap<>();
        List<Exam> noId = new ArrayList<>();
        for (Exam exam : list) {
            if (exam == null) continue;
            String id = exam.getId();
            if (id == null || id.trim().isEmpty()) {
                noId.add(exam);
            } else {
                byId.put(id, exam);
            }
        }
        list.clear();
        list.addAll(byId.values());
        list.addAll(noId);
    }

    private void replaceByIdIfPresent(List<Exam> list, Exam exam) {
        if (exam == null) return;
        String id = exam.getId();
        if (id == null || id.trim().isEmpty()) return;
        for (int i = 0; i < list.size(); i++) {
            Exam current = list.get(i);
            if (current != null && id.equals(current.getId())) {
                list.set(i, exam);
                return;
            }
        }
    }
}
