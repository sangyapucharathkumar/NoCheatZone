package com.example.nocheatzone;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class Login_Activity extends InternetCheckActivity {
    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PHONE = "user_phone";
    private static final String KEY_LAST_SIGNED_IN_EMAIL = "last_signed_in_email";

    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private MaterialButton loginButton;
    private TextView registerRedirectText;
    private TextView statusText;
    private FirebaseAuth mAuth;
    private boolean isSigningIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        SessionManager sessionManager = new SessionManager(this);
        
        // Redirect if already logged in
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, Main_Activity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        // Initialize UI Elements
        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        loginButton = findViewById(R.id.login_button);
        registerRedirectText = findViewById(R.id.register_redirect_text);
        statusText = findViewById(R.id.status_text);
        TextView forgotPasswordText = findViewById(R.id.forgot_password_text);

        loginButton.setOnClickListener(v -> attemptLogin());

        registerRedirectText.setOnClickListener(v -> {
            Intent intent = new Intent(Login_Activity.this, Register_Activity.class);
            startActivity(intent);
        });

        if (forgotPasswordText != null) {
            forgotPasswordText.setOnClickListener(v -> handleForgotPassword());
        }
    }

    private void handleForgotPassword() {
        String email = emailEditText.getText() != null ? emailEditText.getText().toString().trim() : "";
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Enter your email to reset password");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email address");
            return;
        }

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Success! A reset link has been sent to " + email + ". Check your spam folder if you don't see it.", Toast.LENGTH_LONG).show();
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

    private void attemptLogin() {
        if (isSigningIn) return;
        String email = emailEditText.getText() != null ? emailEditText.getText().toString().trim() : "";
        String password = passwordEditText.getText() != null ? passwordEditText.getText().toString().trim() : "";

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

        setSigningInState(true, "Please wait, checking your details...");

        // Firebase Login
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        com.google.firebase.auth.FirebaseUser fbUser = mAuth.getCurrentUser();
                        if (fbUser != null && !fbUser.isEmailVerified()) {
                            // BLOCK LOGIN if NOT VERIFIED
                            mAuth.signOut(); // Logout the unverified session
                            Toast.makeText(this, "Please verify your email address first. Check your inbox.", Toast.LENGTH_LONG).show();
                            setSigningInState(false, null);
                            return;
                        }

                        SessionManager sessionManager = new SessionManager(this);
                        sessionManager.setLogin(true);
                        handleAccountSwitchAndResetData(email);
                        String uid = fbUser != null ? fbUser.getUid() : null;
                        cacheProfileAndOpenMain(uid, email);
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Auth failed.";
                        Toast.makeText(Login_Activity.this, error, Toast.LENGTH_LONG).show();
                        
                        // Show the "Register first" prompt if they fail to login
                        if (statusText != null) {
                            statusText.setVisibility(View.VISIBLE);
                            statusText.setText("Login failed. Please check your credentials.");
                            statusText.setTextColor(ContextCompat.getColor(this, R.color.danger));
                        }
                        setSigningInState(false, null);
                    }
                });
    }

    private void cacheProfileAndOpenMain(String uid, String fallbackEmail) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String existingName = prefs.getString(KEY_USER_NAME, "NoCheat User");
        String existingPhone = prefs.getString(KEY_USER_PHONE, "Phone not added");
        saveProfileToPrefs(existingName, fallbackEmail, existingPhone);

        if (uid == null) {
            openMain();
            return;
        }
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users").child(uid);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@androidx.annotation.NonNull DataSnapshot snapshot) {
                String name = existingName;
                String email = fallbackEmail;
                String phone = existingPhone;
                if (snapshot.exists()) {
                    String dbName = snapshot.child("name").getValue(String.class);
                    String dbEmail = snapshot.child("email").getValue(String.class);
                    String dbPhone = snapshot.child("phone").getValue(String.class);
                    if (dbName != null && !dbName.trim().isEmpty()) name = dbName;
                    if (dbEmail != null && !dbEmail.trim().isEmpty()) email = dbEmail;
                    if (dbPhone != null && !dbPhone.trim().isEmpty()) phone = dbPhone;
                }
                saveProfileToPrefs(name, email, phone);
                openMain();
            }

            @Override
            public void onCancelled(@androidx.annotation.NonNull DatabaseError error) {
                openMain();
            }
        });
    }

    private void saveProfileToPrefs(String name, String email, String phone) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_USER_NAME, name)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_PHONE, phone)
                .apply();
    }

    private void openMain() {
        Intent intent = new Intent(Login_Activity.this, Main_Activity.class);
        startActivity(intent);
        finish();
    }

    private void setSigningInState(boolean signingIn, String message) {
        isSigningIn = signingIn;
        if (loginButton != null) {
            loginButton.setEnabled(!signingIn);
            loginButton.setText(signingIn ? "SIGNING IN..." : "SIGN IN");
        }
        if (emailEditText != null) emailEditText.setEnabled(!signingIn);
        if (passwordEditText != null) passwordEditText.setEnabled(!signingIn);
        if (registerRedirectText != null) registerRedirectText.setEnabled(!signingIn);
        if (statusText != null) {
            if (signingIn) {
                statusText.setVisibility(View.VISIBLE);
                statusText.setText(message != null ? message : "Please wait...");
                statusText.setTextColor(ContextCompat.getColor(this, R.color.primary));
            } else if ("Please wait, checking your details...".contentEquals(statusText.getText())) {
                statusText.setVisibility(View.GONE);
            }
        }
    }

    private void handleAccountSwitchAndResetData(String currentEmail) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String previousEmail = prefs.getString(KEY_LAST_SIGNED_IN_EMAIL, null);
        String normalizedCurrent = currentEmail != null ? currentEmail.trim().toLowerCase(java.util.Locale.ROOT) : "";
        String normalizedPrevious = previousEmail != null ? previousEmail.trim().toLowerCase(java.util.Locale.ROOT) : "";

        if (!normalizedCurrent.isEmpty()
                && !normalizedPrevious.isEmpty()
                && !normalizedCurrent.equals(normalizedPrevious)) {
            ExamRepository.getInstance().clearAllData();
        }

        if (!normalizedCurrent.isEmpty()) {
            prefs.edit().putString(KEY_LAST_SIGNED_IN_EMAIL, normalizedCurrent).apply();
        }
    }
}
