package limor.tal.mytodo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.SignInButton;

public class LoginActivity extends AppCompatActivity implements FirebaseAuthService.AuthCallback {
    private static final String TAG = "LoginActivity";
    private static final String PREFS_NAME = "MyToDoPrefs";
    private static final String PREF_SKIPPED_LOGIN = "skipped_login";
    
    private FirebaseAuthService authService;
    private ProgressBar progressBar;
    private SignInButton signInButton;
    private Button skipSignInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authService = new FirebaseAuthService(this);
        progressBar = findViewById(R.id.progressBar);
        signInButton = findViewById(R.id.signInButton);
        skipSignInButton = findViewById(R.id.skipSignInButton);

        // Check if user is already signed in
        if (authService.isUserSignedIn()) {
            Log.d(TAG, "User already signed in, going to MainActivity");
            goToMainActivity();
            return;
        }

        // Check if user previously skipped login
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean skippedLogin = prefs.getBoolean(PREF_SKIPPED_LOGIN, false);
        if (skippedLogin) {
            Log.d(TAG, "User previously skipped login, going to MainActivity");
            goToMainActivity();
            return;
        }

        // Show login screen for new users
        Log.d(TAG, "Showing login screen for new user");

        setupSignInButton();
        setupSkipButton();
    }

    private void setupSignInButton() {
        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signInWithGoogle();
            }
        });
    }

    private void setupSkipButton() {
        skipSignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                skipSignIn();
            }
        });
    }

    private void signInWithGoogle() {
        Log.d(TAG, "Starting Google Sign-In");
        showProgress(true);
        
        Intent signInIntent = authService.getSignInIntent();
        startActivityForResult(signInIntent, FirebaseAuthService.RC_SIGN_IN);
    }

    private void skipSignIn() {
        Log.d(TAG, "User skipped sign-in");
        
        // Save that user skipped login
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_SKIPPED_LOGIN, true).apply();
        
        goToMainActivity();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FirebaseAuthService.RC_SIGN_IN) {
            showProgress(false);
            authService.handleSignInResult(data, this);
        }
    }

    @Override
    public void onAuthSuccess(User user) {
        Log.d(TAG, "Authentication successful for user: " + user.getDisplayName());
        
        // Clear the skipped login flag since user is now signed in
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().remove(PREF_SKIPPED_LOGIN).apply();
        
        Toast.makeText(this, "Welcome, " + user.getDisplayName() + "!", Toast.LENGTH_SHORT).show();
        goToMainActivity();
    }

    @Override
    public void onAuthError(String error) {
        Log.e(TAG, "Authentication failed: " + error);
        Toast.makeText(this, "Sign-in failed: " + error, Toast.LENGTH_LONG).show();
    }

    private void goToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        signInButton.setEnabled(!show);
        skipSignInButton.setEnabled(!show);
    }
}
