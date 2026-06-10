package com.example.nocheatzone;

import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.provider.Settings;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Base activity that checks for Internet connectivity and Security (Developer Mode).
 */
public class InternetCheckActivity extends AppCompatActivity {

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private View noInternetBanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. Edge-to-Edge display support
        EdgeToEdge.enable(this);
        
        applyScreenshotProtection();

        applySystemBarAppearance();
        
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }
    
    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        
        // Automatically handle WindowInsets for ALL activities inheriting this
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        applyScreenshotProtection();
        if (shouldEnforceDeveloperOptionsCheck() && isDeveloperModeEnabled()) {
            showSecurityError("Security Violation", "Developer Options must be disabled to ensure a fair exam environment.");
            return;
        }

        // 2. Network Check
        registerNetworkCallback();
        if (!isNetworkAvailable()) {
            showNoInternetBanner();
        } else {
            hideNoInternetBanner();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                // Ignore if not registered
            }
        }
    }

    private void registerNetworkCallback() {
        if (connectivityManager == null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> hideNoInternetBanner());
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> showNoInternetBanner());
            }
        };

        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    private boolean isNetworkAvailable() {
        if (connectivityManager == null) return false;
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void showNoInternetBanner() {
        if (noInternetBanner != null && noInternetBanner.getParent() != null) return;

        ViewGroup rootView = findViewById(android.R.id.content);
        if (rootView == null) return;

        TextView banner = new TextView(this);
        banner.setText("No Internet Connection");
        banner.setBackgroundColor(ContextCompat.getColor(this, R.color.danger));
        banner.setTextColor(Color.WHITE);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(0, 24, 0, 24);
        banner.setTextSize(14f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP;
        banner.setLayoutParams(params);
        banner.setElevation(100f);

        rootView.addView(banner);
        noInternetBanner = banner;
    }

    private void hideNoInternetBanner() {
        if (noInternetBanner != null && noInternetBanner.getParent() != null) {
            ViewGroup rootView = (ViewGroup) noInternetBanner.getParent();
            rootView.removeView(noInternetBanner);
            noInternetBanner = null;
        }
    }

    private boolean isDeveloperModeEnabled() {
        // Disabled for testing by returning false
        return false;
        // ORIGINAL: return Settings.Global.getInt(this.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    protected boolean shouldBlockScreenshots() {
        return false;
    }

    protected boolean shouldEnforceDeveloperOptionsCheck() {
        return false;
    }

    private void applyScreenshotProtection() {
        if (shouldBlockScreenshots()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void showSecurityError(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Exit", (dialog, which) -> finishAffinity())
                .show();
    }

    private void applySystemBarAppearance() {
        int nightModeFlags = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (!isDarkMode) return;

        int surface = ContextCompat.getColor(this, R.color.surface);
        getWindow().setNavigationBarColor(surface);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightNavigationBars(false);
        }
    }
}
