package com.example.nocheatzone;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

@SuppressLint("CustomSplashScreen")
public class SplashScreenActivity extends AppCompatActivity {

    private boolean navigationTriggered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Install system splash
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        // Animate splash icon
        splashScreen.setOnExitAnimationListener(splashScreenViewProvider -> {
            View iconView = splashScreenViewProvider.getIconView();
            iconView.animate()
                    .rotation(1080f)
                    .setDuration(700)
                    .withEndAction(() -> {
                        iconView.animate().scaleX(10f)
                                .scaleY(10f)
                                .alpha(0f)
                                .withEndAction(this::navigateNext)
                                .start();
                    })
                    .start();
        });
    }

    // Replace with your real login check
    private boolean isUserLoggedIn() {
        SessionManager sessionManager = new SessionManager(this);
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        return sessionManager.isLoggedIn() && user != null && user.isEmailVerified();
    }

    private void navigateNext() {
        if (navigationTriggered || isFinishing() || isDestroyed()) return;
        navigationTriggered = true;
        Class<?> destination = isUserLoggedIn() ? Main_Activity.class : Login_Activity.class;
        Intent intent = new Intent(SplashScreenActivity.this, destination);
        startActivity(intent);
        finish();
    }
}
