package com.example.nocheatzone.model;

public class StudentStatus {
    private String name;
    private String studentId;
    private String status; // "Active", "Submitted", "Cheating Flagged"
    private boolean isFlagged;
    private long flagTimestamp; // Unix Timestamp in MS of when the security violation triggered
    private String email;
    private String phone;

    public StudentStatus() {
        // Required empty public constructor for Firebase
    }

    public StudentStatus(String name, String studentId, String status, boolean isFlagged, long flagTimestamp) {
        this.name = name;
        this.studentId = studentId;
        this.status = status;
        this.isFlagged = isFlagged;
        this.flagTimestamp = flagTimestamp;
    }

    // Keep backward-compatible constructor for general updates
    public StudentStatus(String name, String studentId, String status, boolean isFlagged) {
        this(name, studentId, status, isFlagged, 0); // 0 means no flag triggered yet
    }

    // Original legacy constructor
    public StudentStatus(String name, String status, boolean isFlagged) {
        this(name, "N/A", status, isFlagged, 0);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isFlagged() {
        return isFlagged;
    }

    public void setFlagged(boolean flagged) {
        isFlagged = flagged;
    }

    public long getFlagTimestamp() {
        return flagTimestamp;
    }

    public void setFlagTimestamp(long flagTimestamp) {
        this.flagTimestamp = flagTimestamp;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
