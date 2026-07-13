package com.example.nocheatzone.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Exam implements Serializable {
    private String id;
    private String title;
    private String description;
    private String hostId;
    private String accessCode; 
    private List<StudentStatus> joinedStudents = new ArrayList<>(); 
    
    // History & State
    private long creationTimestamp;
    private boolean isEnded;

    public long getCreationTimestamp() { return creationTimestamp; }
    public void setCreationTimestamp(long creationTimestamp) { this.creationTimestamp = creationTimestamp; }

    public boolean isEnded() { return isEnded; }
    public void setEnded(boolean ended) { isEnded = ended; }
    
    // Time Configuration
    private long startTime;      
    private long endTime;        
    private int durationMinutes; 

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    // Security Settings
    private boolean isAiProctoringEnabled;
    private boolean isCameraMonitoringEnabled;
    private boolean isWindowSwitchingBlocked;
    private boolean isDeveloperOptionsBlocked;
    private boolean isStudentResultHidden;

    // Questions
    private List<Question> questions = new ArrayList<>();

    public Exam() {
        // Required empty public constructor for Firebase
        this.questions = new ArrayList<>();
    }

    public Exam(String title, String description) {
        this.title = title;
        this.description = description;
        this.id = String.valueOf(System.currentTimeMillis()); 
        this.creationTimestamp = System.currentTimeMillis();
        this.isEnded = false;
        this.accessCode = generateAccessCode();
        this.questions = new ArrayList<>();
    }

    private String generateAccessCode() {
        // Simple random 6-digit generation
        int code = (int) (Math.random() * 900000) + 100000;
        return String.valueOf(code);
    }

    public String getAccessCode() {
        return accessCode;
    }

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    public List<StudentStatus> getJoinedStudents() {
        return joinedStudents;
    }

    public void setJoinedStudents(List<StudentStatus> joinedStudents) {
        this.joinedStudents = joinedStudents;
    }

    public void addStudent(StudentStatus student) {
        if (joinedStudents == null) joinedStudents = new ArrayList<>();
        joinedStudents.add(student);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<Question> getQuestions() {
        if (questions == null) {
            questions = new ArrayList<>();
        }
        return questions;
    }

    public void setQuestions(List<Question> questions) {
        this.questions = (questions != null) ? questions : new ArrayList<>();
    }

    public void addQuestion(Question question) {
        if (question == null) return;
        getQuestions().add(question);
    }

    public boolean isAiProctoringEnabled() {
        return isAiProctoringEnabled;
    }

    public void setAiProctoringEnabled(boolean aiProctoringEnabled) {
        isAiProctoringEnabled = aiProctoringEnabled;
    }

    public boolean isCameraMonitoringEnabled() {
        return isCameraMonitoringEnabled;
    }

    public void setCameraMonitoringEnabled(boolean cameraMonitoringEnabled) {
        isCameraMonitoringEnabled = cameraMonitoringEnabled;
    }

    public boolean isWindowSwitchingBlocked() {
        return isWindowSwitchingBlocked;
    }

    public void setWindowSwitchingBlocked(boolean windowSwitchingBlocked) {
        isWindowSwitchingBlocked = windowSwitchingBlocked;
    }

    public boolean isDeveloperOptionsBlocked() {
        return isDeveloperOptionsBlocked;
    }

    public void setDeveloperOptionsBlocked(boolean developerOptionsBlocked) {
        isDeveloperOptionsBlocked = developerOptionsBlocked;
    }

    public boolean isStudentResultHidden() {
        return isStudentResultHidden;
    }

    public void setStudentResultHidden(boolean studentResultHidden) {
        isStudentResultHidden = studentResultHidden;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }
}
