package com.example.nocheatzone;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

import com.example.nocheatzone.model.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class Register_Activity extends InternetCheckActivity {
    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PHONE = "user_phone";

    private TextInputEditText nameEditText;
    private TextInputEditText phoneEditText;
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private TextInputEditText confirmPasswordEditText;
    private MaterialButton registerButton;
    private TextView loginRedirectText;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mAuth = FirebaseAuth.getInstance();
        setContentView(R.layout.activity_register);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        // Initialize Views
        nameEditText = findViewById(R.id.register_name_edit_text);
        phoneEditText = findViewById(R.id.register_phone_edit_text);
        emailEditText = findViewById(R.id.register_email_edit_text);
        passwordEditText = findViewById(R.id.register_password_edit_text);
        confirmPasswordEditText = findViewById(R.id.register_confirm_password_edit_text);
        registerButton = findViewById(R.id.register_button);
        loginRedirectText = findViewById(R.id.login_redirect_text);
        TextView forgotPasswordText = findViewById(R.id.register_forgot_password);

        registerButton.setOnClickListener(v -> attemptRegister());

        loginRedirectText.setOnClickListener(v -> finish());

        if (forgotPasswordText != null) {
            forgotPasswordText.setOnClickListener(v -> handleForgotPassword());
        }
    }

    private void handleForgotPassword() {
        String email = emailEditText.getText() != null ? emailEditText.getText().toString().trim() : "";
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Enter your email address first");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email address");
            return;
        }

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Reset email sent! Check your inbox.", Toast.LENGTH_LONG).show();
                    } else {
                        Exception e = task.getException();
                        String error;
                        if (e instanceof FirebaseAuthInvalidUserException) {
                            error = "This email is not registered with us.";
                        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                            error = "Invalid email format.";
                        } else if (e instanceof FirebaseTooManyRequestsException) {
                            error = "Too many attempts. Please try again in a few minutes.";
                        } else {
                            error = e != null ? e.getMessage() : "Failed to send reset email.";
                        }
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void attemptRegister() {
        String name = nameEditText.getText() != null ? nameEditText.getText().toString().trim() : "";
        String phone = phoneEditText.getText() != null ? phoneEditText.getText().toString().trim() : "";
        String email = emailEditText.getText() != null ? emailEditText.getText().toString().trim() : "";
        String password = passwordEditText.getText() != null ? passwordEditText.getText().toString().trim() : "";
        String confirmPassword = confirmPasswordEditText.getText() != null ? confirmPasswordEditText.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            nameEditText.setError("Name is required");
            return;
        } 
        
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            return;
        } 
        
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email address");
            return;
        } 
        
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            return;
        } 
        
        if (password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters");
            return;
        } 
        
        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match");
            return;
        }

        // Firebase Registration
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser fbUser = mAuth.getCurrentUser();
                    if (fbUser != null) {
                        // Cache immediately so Settings can show details even if DB sync is delayed.
                        cacheUserProfile(name, email, phone);
                        String userId = fbUser.getUid();
                        User user = new User(userId, name, email, phone);
                        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users");
                        ref.child(userId).setValue(user).addOnCompleteListener(dbTask -> {
                            if (dbTask.isSuccessful()) {
                                // Send Verification Email
                                fbUser.sendEmailVerification().addOnCompleteListener(verifyTask -> {
                                    if (verifyTask.isSuccessful()) {
                                        Toast.makeText(Register_Activity.this, 
                                            "Verification email sent! Please verify to login.", 
                                            Toast.LENGTH_LONG).show();
                                    }
                                });
                                
                                finish();
                            } else {
                                mAuth.signOut();
                                String dbError = dbTask.getException() != null ? dbTask.getException().getMessage() : "Database Error";
                                Toast.makeText(Register_Activity.this,
                                        "Account created but profile could not be saved: " + dbError
                                                + "\nPlease try registering again.",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                } else {
                    String authError = task.getException() != null ? task.getException().getMessage() : "Registration failed";
                    Toast.makeText(Register_Activity.this, authError, Toast.LENGTH_LONG).show();
                }
            });
    }

    private void cacheUserProfile(String name, String email, String phone) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_USER_NAME, name)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_PHONE, phone)
                .apply();
    }
}
