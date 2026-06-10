package com.example.nocheatzone;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nocheatzone.model.Exam;

import java.util.List;

/**
 * Adapter for displaying a list of Exams in a RecyclerView.
 * Extracted from Exams_Activity to fix the non-static inner class memory leak.
 */
public class ExamHistoryAdapter extends RecyclerView.Adapter<ExamHistoryAdapter.ExamViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Exam exam);
    }

    private List<Exam> exams;
    private OnItemClickListener listener;
    private boolean isJoinedView = false;

    public ExamHistoryAdapter(List<Exam> exams) {
        this.exams = exams;
    }

    public ExamHistoryAdapter(List<Exam> exams, boolean isJoinedView) {
        this.exams = exams;
        this.isJoinedView = isJoinedView;
    }

    public ExamHistoryAdapter(List<Exam> exams, OnItemClickListener listener) {
        this.exams = exams;
        this.listener = listener;
    }

    /** Update the adapter's dataset and notify the change. */
    public void updateData(List<Exam> newExams) {
        this.exams = newExams;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ExamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_exam_history, parent, false);
        return new ExamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExamViewHolder holder, int position) {
        Exam exam = exams.get(position);
        holder.title.setText(exam.getTitle());
        holder.date.setText("Access Code: " + exam.getAccessCode()); 
        holder.description.setText(exam.getDescription());

        if (isJoinedView) {
            List<com.example.nocheatzone.model.StudentResult> results = ExamRepository.getInstance().getStudentResults(exam.getId());
            if (results != null && !results.isEmpty()) {
                com.example.nocheatzone.model.StudentResult latestResult = results.get(results.size() - 1);
                
                int correct = latestResult.getScore();
                int total = latestResult.getTotalQuestions();
                int pct = latestResult.getPercentage();
                
                holder.score.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
                holder.score.setGravity(android.view.Gravity.CENTER);
                holder.score.setText(correct + "/" + total + "\n" + pct + "%");
                
                // Set green if passed, red if failed
                if (pct >= 50) {
                    holder.score.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.success));
                } else {
                    holder.score.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.danger));
                }
            } else {
                holder.score.setText("N/A");
                holder.score.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.white));
            }
        } else {
            int count = exam.getQuestions() != null ? exam.getQuestions().size() : 0;
            holder.score.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            holder.score.setText(count + "Q");
            // Badge uses @drawable/bg_initials_circle (same indigo as primary) — use white for contrast.
            holder.score.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.white));
        }

        if (listener != null) {
            holder.itemView.setOnClickListener(v -> listener.onItemClick(exam));
        }
    }

    @Override
    public int getItemCount() {
        return exams.size();
    }

    static class ExamViewHolder extends RecyclerView.ViewHolder {
        TextView title, date, score, description;

        public ExamViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.exam_title);
            date = itemView.findViewById(R.id.exam_date);
            description = itemView.findViewById(R.id.exam_description);
            score = itemView.findViewById(R.id.exam_score);
            // exam_score_bg no longer exists — the badge is now a LinearLayout
            // wrapping the exam_score TextView; color is set in the layout drawable.
        }
    }
}
