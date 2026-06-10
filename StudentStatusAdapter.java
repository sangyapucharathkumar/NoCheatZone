package com.example.nocheatzone;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nocheatzone.model.StudentStatus;

import java.util.List;

public class StudentStatusAdapter extends RecyclerView.Adapter<StudentStatusAdapter.ViewHolder> {

    public interface OnStudentRemoveListener {
        void onRemoveClicked(StudentStatus student);
    }

    private List<StudentStatus> studentList;
    private OnStudentRemoveListener removeListener;

    public StudentStatusAdapter(List<StudentStatus> studentList, OnStudentRemoveListener listener) {
        this.studentList = studentList != null ? studentList : new java.util.ArrayList<>();
        this.removeListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_status, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudentStatus student = studentList.get(position);
        holder.name.setText(student.getName());
        holder.status.setText(student.getStatus());

        if (student.isFlagged()) {
            holder.flagBadge.setVisibility(View.VISIBLE);
            holder.status.setTextColor(0xFFFF0000); // Red
        } else {
            holder.flagBadge.setVisibility(View.GONE);
            holder.status.setTextColor(0xFF4CAF50); // Green
        }

        String details = "";
        if (student.getEmail() != null && !student.getEmail().isEmpty()) details += student.getEmail();
        if (student.getPhone() != null && !student.getPhone().isEmpty()) {
            if (!details.isEmpty()) details += " • ";
            details += student.getPhone();
        }
        if (!details.isEmpty()) {
            holder.contact.setVisibility(View.VISIBLE);
            holder.contact.setText(details);
        } else {
            holder.contact.setVisibility(View.GONE);
        }

        if (holder.btnRemove != null) {
            holder.btnRemove.setOnClickListener(v -> {
                if (removeListener != null) {
                    removeListener.onRemoveClicked(student);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return studentList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView name, status, flagBadge, contact;
        public android.widget.ImageButton btnRemove;

        public ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.txt_student_name);
            status = itemView.findViewById(R.id.txt_student_status);
            flagBadge = itemView.findViewById(R.id.txt_flag_badge);
            contact = itemView.findViewById(R.id.txt_student_contact);
            btnRemove = itemView.findViewById(R.id.btn_remove_student);
        }
    }
}
