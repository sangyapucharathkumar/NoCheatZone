package com.example.nocheatzone;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nocheatzone.model.Question;

import java.util.List;

public class ReviewQuestionsAdapter extends RecyclerView.Adapter<ReviewQuestionsAdapter.ReviewViewHolder> {

    private final List<Question> questionList;
    private final OnQuestionDeleteListener deleteListener;
    private final OnEditListener editListener;

    public interface OnQuestionDeleteListener {
        void onDeleteClick(int position);
    }

    public interface OnEditListener {
        void onEditClick(int position);
    }

    public ReviewQuestionsAdapter(List<Question> questionList, 
                                  OnQuestionDeleteListener deleteListener,
                                  OnEditListener editListener) {
        this.questionList = questionList;
        this.deleteListener = deleteListener;
        this.editListener = editListener;
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_review_question, parent, false);
        return new ReviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        Question q = questionList.get(position);
        holder.questionNumber.setText("Q" + (position + 1));
        holder.questionText.setText(q.getQuestionText());

        if (q.getType() == Question.QuestionType.MULTIPLE_CHOICE) {
            List<String> opts = q.getOptions();
            String answerLetter = "A";
            String answerText = "N/A";
            int idx = q.getCorrectAnswerIndex();
            if (idx >= 0 && idx < (opts != null ? opts.size() : 0)) {
                answerLetter = String.valueOf((char) ('A' + idx));
                answerText = opts.get(idx);
            }
            holder.correctAnswer.setText("Answer: " + answerLetter + ") " + answerText);
        } else {
            holder.correctAnswer.setText("Correct Answer: " + q.getCorrectAnswer());
        }

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDeleteClick(position);
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (editListener != null) editListener.onEditClick(position);
        });
    }

    @Override
    public int getItemCount() {
        return questionList.size();
    }

    static class ReviewViewHolder extends RecyclerView.ViewHolder {
        TextView questionNumber, questionText, correctAnswer;
        ImageButton btnDelete, btnEdit;

        public ReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            questionNumber = itemView.findViewById(R.id.text_question_number);
            questionText = itemView.findViewById(R.id.text_question_desc);
            correctAnswer = itemView.findViewById(R.id.text_correct_answer);
            btnDelete = itemView.findViewById(R.id.btn_delete_question);
            btnEdit = itemView.findViewById(R.id.btn_edit_question);
        }
    }
}
