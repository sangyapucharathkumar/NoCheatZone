package com.example.nocheatzone;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class Settings_Activity extends InternetCheckActivity {
    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PHONE = "user_phone";
    private static final String KEY_PROFILE_URI = "profile_image_uri";
    private static final String KEY_EXIT_WARNINGS = "exit_warnings_enabled";
    private static final String KEY_AUTO_SUBMIT_CLOSE = "auto_submit_on_close_enabled";
    private static final String KEY_PROCTOR_ALERTS = "proctor_alerts_enabled";
    private static final int REQUEST_CAMERA = 102;

    private ImageView profileImage;
    private TextView userNameView;
    private TextView userEmailView;
    private TextView userPhoneView;
    private TextView hostedCountView;
    private TextView joinedCountView;
    private SwitchMaterial switchDarkMode;
    private SwitchMaterial switchExitWarning;
    private SwitchMaterial switchAutoSubmitClose;
    private SwitchMaterial switchProctorAlerts;
    private boolean isApplyingDarkMode = false;
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
        }

        initViews();
        setupImagePicker();
        setupBottomNav();
        setupSettingsLogic();
        setupAccountActions();
        updatePermissionUI();
        updateExamStats();
        loadUserFromPrefs();
        fetchUserFromFirebase();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateExamStats();
        updatePermissionUI();
        loadUserFromPrefs();
        fetchUserFromFirebase();
    }

    private void initViews() {
        profileImage = findViewById(R.id.profile_image);
        userNameView = findViewById(R.id.user_name);
        userEmailView = findViewById(R.id.user_email);
        userPhoneView = findViewById(R.id.user_phone);
        hostedCountView = findViewById(R.id.text_hosted_count);
        joinedCountView = findViewById(R.id.text_joined_count);
        switchDarkMode = findViewById(R.id.switch_dark_mode);
        switchExitWarning = findViewById(R.id.switch_exit_warning);
        switchAutoSubmitClose = findViewById(R.id.switch_auto_submit_close);
        switchProctorAlerts = findViewById(R.id.switch_proctor_alerts);
    }

    private void setupImagePicker() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUri = prefs.getString(KEY_PROFILE_URI, null);
        if (savedUri != null) {
            try {
                profileImage.setImageURI(Uri.parse(savedUri));
            } catch (Exception ignored) {
                // Keep default avatar.
            }
        }

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    } catch (SecurityException ignored) {
                        // Some providers do not grant persistable permission.
                    }
                    profileImage.setImageURI(uri);
                    prefs.edit().putString(KEY_PROFILE_URI, uri.toString()).apply();
                });

        profileImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav == null) return;
        bottomNav.setSelectedItemId(R.id.nav_settings);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(getApplicationContext(), Main_Activity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_exams) {
                startActivity(new Intent(getApplicationContext(), Exams_Activity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return id == R.id.nav_settings;
        });
    }

    private void setupSettingsLogic() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isApplyingDarkMode = true;
        switchDarkMode.setChecked(prefs.getBoolean(KEY_DARK_MODE, false));
        isApplyingDarkMode = false;
        switchExitWarning.setChecked(prefs.getBoolean(KEY_EXIT_WARNINGS, true));
        switchAutoSubmitClose.setChecked(prefs.getBoolean(KEY_AUTO_SUBMIT_CLOSE, true));
        switchProctorAlerts.setChecked(prefs.getBoolean(KEY_PROCTOR_ALERTS, true));

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isApplyingDarkMode) return;
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
            showThemeSwitchAndRestart();
        });

        switchExitWarning.setOnCheckedChangeListener(
                (buttonView, isChecked) -> prefs.edit().putBoolean(KEY_EXIT_WARNINGS, isChecked).apply());
        switchAutoSubmitClose.setOnCheckedChangeListener(
                (buttonView, isChecked) -> prefs.edit().putBoolean(KEY_AUTO_SUBMIT_CLOSE, isChecked).apply());
        switchProctorAlerts.setOnCheckedChangeListener(
                (buttonView, isChecked) -> prefs.edit().putBoolean(KEY_PROCTOR_ALERTS, isChecked).apply());

        findViewById(R.id.btn_permission_camera).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission already granted", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            } else {
                Intent intent = new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)
                );
                startActivity(intent);
                Toast.makeText(this, "Enable camera permission in App Settings", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupAccountActions() {
        findViewById(R.id.btn_edit_name).setOnClickListener(v -> showEditNameDialog());
        findViewById(R.id.btn_change_password).setOnClickListener(v -> {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null || currentUser.getEmail() == null) {
                Toast.makeText(this, "No logged-in user email found.", Toast.LENGTH_SHORT).show();
                return;
            }
            FirebaseAuth.getInstance()
                    .sendPasswordResetEmail(currentUser.getEmail())
                    .addOnSuccessListener(unused -> Toast.makeText(
                            this,
                            "Password reset link sent to " + currentUser.getEmail(),
                            Toast.LENGTH_LONG
                    ).show())
                    .addOnFailureListener(e -> Toast.makeText(
                            this,
                            "Failed to send reset email: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show());
        });

        findViewById(R.id.btn_logout).setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    SessionManager sessionManager = new SessionManager(this);
                    sessionManager.setLogin(false);
                    Intent intent = new Intent(Settings_Activity.this, Login_Activity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show());
    }

    private void showEditNameDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(userNameView.getText());
        input.setSelection(input.getText().length());

        new android.app.AlertDialog.Builder(this)
                .setTitle("Edit Display Name")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText() != null ? input.getText().toString().trim() : "";
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit().putString(KEY_USER_NAME, newName).apply();
                    userNameView.setText(newName);
                    updateFirebaseUserName(newName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateFirebaseUserName(String newName) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users");
        ref.child(currentUser.getUid()).child("name").setValue(newName);
    }

    private void updatePermissionUI() {
        TextView statusView = findViewById(R.id.status_camera);
        TextView actionView = findViewById(R.id.action_camera);
        if (statusView == null || actionView == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            statusView.setText("Granted");
            statusView.setTextColor(ContextCompat.getColor(this, R.color.success));
            actionView.setVisibility(android.view.View.GONE);
        } else {
            statusView.setText("Not granted - tap to enable");
            statusView.setTextColor(ContextCompat.getColor(this, R.color.danger));
            actionView.setVisibility(android.view.View.VISIBLE);
            actionView.setText("GRANT");
        }
    }

    private void updateExamStats() {
        int hostedCount = ExamRepository.getInstance().getHostedExams().size();
        int joinedCount = ExamRepository.getInstance().getJoinedExams().size();
        if (hostedCountView != null) hostedCountView.setText("Hosted Exams: " + hostedCount);
        if (joinedCountView != null) joinedCountView.setText("Joined Exams: " + joinedCount);
    }

    private void loadUserFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String name = prefs.getString(KEY_USER_NAME, "NoCheat User");
        String email = prefs.getString(KEY_USER_EMAIL, "Email not available");
        String phone = prefs.getString(KEY_USER_PHONE, "Phone not added");
        if (userNameView != null) userNameView.setText(name);
        if (userEmailView != null) userEmailView.setText(email);
        if (userPhoneView != null) userPhoneView.setText(phone);
    }

    private void fetchUserFromFirebase() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (currentUser.getEmail() != null) {
            prefs.edit().putString(KEY_USER_EMAIL, currentUser.getEmail()).apply();
            if (userEmailView != null) userEmailView.setText(currentUser.getEmail());
        }

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users");
        ref.child(currentUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Keep cached values if profile node is missing.
                    loadUserFromPrefs();
                    return;
                }
                String name = snapshot.child("name").getValue(String.class);
                String email = snapshot.child("email").getValue(String.class);
                String phone = snapshot.child("phone").getValue(String.class);
                if (name != null && !name.trim().isEmpty()) {
                    prefs.edit().putString(KEY_USER_NAME, name).apply();
                    if (userNameView != null) userNameView.setText(name);
                }
                if (email != null && !email.trim().isEmpty()) {
                    prefs.edit().putString(KEY_USER_EMAIL, email).apply();
                    if (userEmailView != null) userEmailView.setText(email);
                }
                if (phone != null && !phone.trim().isEmpty()) {
                    prefs.edit().putString(KEY_USER_PHONE, phone).apply();
                    if (userPhoneView != null) userPhoneView.setText(phone);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Fallback to local cached data.
                loadUserFromPrefs();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            updatePermissionUI();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
    }

    @android.annotation.SuppressLint({"ScheduleExactAlarm", "MissingPermission"})
    private void showThemeSwitchAndRestart() {
        if (isFinishing() || isDestroyed()) return;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Dark Mode is Switching")
                .setMessage("The app will restart now to apply your theme update.")
                .setCancelable(false)
                .setPositiveButton("Continue", (dialog, which) -> {
                    Intent splashIntent = new Intent(Settings_Activity.this, SplashScreenActivity.class);
                    splashIntent.putExtra("THEME_SWITCH_RESTART", true);
                    splashIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    android.app.PendingIntent restartPendingIntent = android.app.PendingIntent.getActivity(
                            Settings_Activity.this,
                            2001,
                            splashIntent,
                            android.app.PendingIntent.FLAG_CANCEL_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
                    );
                    android.app.AlarmManager alarmManager =
                            (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
                    if (alarmManager != null) {
                        long triggerAt = android.os.SystemClock.elapsedRealtime() + 250;
                        alarmManager.setExact(android.app.AlarmManager.ELAPSED_REALTIME, triggerAt, restartPendingIntent);
                    }
                    finishAffinity();
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(0);
                })
                .show();
    }
}