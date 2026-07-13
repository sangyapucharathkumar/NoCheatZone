package com.example.nocheatzone.model;

import java.io.Serializable;
import java.util.List;

public class Question implements Serializable {
    public enum QuestionType { MULTIPLE_CHOICE, FILL_IN_BLANKS, SHORT_ANSWER }

    private String questionText;
    private List<String> options; // Used for MULTIPLE_CHOICE
    private int correctAnswerIndex; // Used for MULTIPLE_CHOICE
    private String correctAnswer; // Used for FILL_IN_BLANKS and SHORT_ANSWER
    private QuestionType type = QuestionType.MULTIPLE_CHOICE; // Default type

    public Question() {
    }

    public Question(String questionText, List<String> options, int correctAnswerIndex) {
        this.questionText = questionText;
        this.options = options;
        this.correctAnswerIndex = correctAnswerIndex;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public int getCorrectAnswerIndex() {
        return correctAnswerIndex;
    }

    // Renamed to be consistent with getter name
    public void setCorrectAnswerIndex(int correctAnswerIndex) {
        this.correctAnswerIndex = correctAnswerIndex;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public QuestionType getType() {
        return type;
    }

    public void setType(QuestionType type) {
        this.type = type;
    }
}
