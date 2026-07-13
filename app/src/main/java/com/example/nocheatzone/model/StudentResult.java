package com.example.nocheatzone.model;

public class StudentResult {
    private String name;
    private String studentId;
    private int score;
    private int totalQuestions;
    private long submittedAtMs;
    private boolean suspiciousSubmission;
    private String email;

    public StudentResult() {
        // Required empty public constructor for Firebase
    }

    public StudentResult(String name, String studentId, int score, int totalQuestions) {
        this(name, studentId, score, totalQuestions, System.currentTimeMillis(), false);
    }

    public StudentResult(
            String name,
            String studentId,
            int score,
            int totalQuestions,
            long submittedAtMs,
            boolean suspiciousSubmission
    ) {
        this(name, studentId, score, totalQuestions, submittedAtMs, suspiciousSubmission, "");
    }
    
    public StudentResult(
            String name,
            String studentId,
            int score,
            int totalQuestions,
            long submittedAtMs,
            boolean suspiciousSubmission,
            String email
    ) {
        this.name = name;
        this.studentId = studentId;
        this.score = score;
        this.totalQuestions = totalQuestions;
        this.submittedAtMs = submittedAtMs;
        this.suspiciousSubmission = suspiciousSubmission;
        this.email = email;
    }


    // Keep backward-compatible constructor
    public StudentResult(String name, String studentId, int score) {
        this(name, studentId, score, 100);
    }

    public String getName() {
        return name;
    }

    public String getStudentId() {
        return studentId;
    }

    public int getScore() {
        return score;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public int getPercentage() {
        if (totalQuestions == 0)
            return 0;
        return score * 100 / totalQuestions;
    }

    public long getSubmittedAtMs() {
        return submittedAtMs;
    }

    public boolean isSuspiciousSubmission() {
        return suspiciousSubmission;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
