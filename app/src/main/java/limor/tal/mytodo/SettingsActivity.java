package limor.tal.mytodo;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity implements FirebaseAuthService.AuthCallback {
    private static final String TAG = "Settings";
    private static final int REQUEST_CODE_ALARM_SOUND = 1001;
    private static final String PREFS_NAME = "MyToDoPrefs";
    
    private TextView currentSoundTextView;
    private Button selectSoundButton;
    private Button testSoundButton;
    private Uri selectedSoundUri;
    
    // Account settings
    private TextView accountStatusTextView;
    private Button signInOutButton;
    private Button resetLoginButton;
    private Button manualSyncButton;
    private FirebaseAuthService authService;
    private SyncManager syncManager;
    private SharedPreferences prefs;
    
    private final ActivityResultLauncher<Intent> soundPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                        if (uri != null) {
                            selectedSoundUri = uri;
                            updateSoundDisplay();
                            Log.d(TAG, "Sound selected: " + uri.toString());
                        }
                    }
                }
            }
    );
    
    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(TAG, "Sign-in result: " + result.getResultCode());
                Intent data = result.getData();
                if (data != null && authService != null) {
                    authService.handleSignInResult(data, this);
                } else {
                    Log.w(TAG, "Sign-in failed - no data or auth service");
                    updateAccountStatus(); // Reset button state
                    Toast.makeText(this, getString(R.string.sign_in_error, "Unknown error"), Toast.LENGTH_LONG).show();
                }
            }
    );
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set language BEFORE calling super.onCreate (same as MainActivity)
        SharedPreferences prefs = getSharedPreferences("MyToDoPrefs", MODE_PRIVATE);
        String deviceLang = Locale.getDefault().getLanguage();
        String defaultLang = (deviceLang.startsWith("he") || deviceLang.startsWith("iw")) ? "he" : "en";
        String language = prefs.getString("language", defaultLang);
        
        Log.d(TAG, "SettingsActivity: Device language: " + deviceLang);
        Log.d(TAG, "SettingsActivity: Default language: " + defaultLang);
        Log.d(TAG, "SettingsActivity: Selected language: " + language);
        
        // Set locale BEFORE super.onCreate so it applies to layout inflation
        setLocale(language);
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // Initialize Firebase Auth and Sync
        authService = new FirebaseAuthService(this);
        syncManager = new SyncManager(this);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Initialize views
        currentSoundTextView = findViewById(R.id.currentSoundTextView);
        selectSoundButton = findViewById(R.id.selectSoundButton);
        
        // Initialize account views
        accountStatusTextView = findViewById(R.id.accountStatusTextView);
        signInOutButton = findViewById(R.id.signInOutButton);
        resetLoginButton = findViewById(R.id.resetLoginButton);
        manualSyncButton = findViewById(R.id.manualSyncButton);
        testSoundButton = findViewById(R.id.testSoundButton);
        
        // Debug: Check what strings are being loaded
        Log.d(TAG, "Account settings string: " + getString(R.string.account_settings));
        Log.d(TAG, "Sign in string: " + getString(R.string.sign_in_with_google));
        Log.d(TAG, "Not signed in string: " + getString(R.string.not_signed_in));
        
        // Setup account settings after locale is set
        setupAccountSettings();
        
        // Set default sound
        selectedSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (selectedSoundUri == null) {
            selectedSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        updateSoundDisplay();
        
        // Set up button listeners
        selectSoundButton.setOnClickListener(v -> openSoundPicker());
        testSoundButton.setOnClickListener(v -> testSound());
        
        // Back button
        Button backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            Log.d(TAG, "Back button found and set up");
            backButton.setOnClickListener(v -> finish());
        } else {
            Log.e(TAG, "Back button not found!");
        }
    }
    
    private void openSoundPicker() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, selectedSoundUri);
        
        soundPickerLauncher.launch(intent);
    }
    
    private void testSound() {
        if (selectedSoundUri != null) {
            try {
                // Create a temporary MediaPlayer to test the sound
                android.media.MediaPlayer mediaPlayer = new android.media.MediaPlayer();
                mediaPlayer.setDataSource(this, selectedSoundUri);
                mediaPlayer.prepare();
                mediaPlayer.start();
                
                // Stop after 3 seconds
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                });
                
                Toast.makeText(this, getString(R.string.playing_test_sound), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error testing sound", e);
                Toast.makeText(this, getString(R.string.error_playing_sound), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void updateSoundDisplay() {
        if (selectedSoundUri != null) {
            String soundName = getSoundName(selectedSoundUri);
            currentSoundTextView.setText(getString(R.string.current_sound) + ": " + soundName);
        } else {
            currentSoundTextView.setText(getString(R.string.current_sound) + ": Default");
        }
    }
    
    private String getSoundName(Uri uri) {
        try {
            if (RingtoneManager.isDefault(uri)) {
                return getString(R.string.reminder_sound) + " (Default)";
            }
            
            android.media.Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null) {
                return ringtone.getTitle(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting sound name", e);
        }
        return "Unknown Sound";
    }
    
    public Uri getSelectedSoundUri() {
        return selectedSoundUri;
    }
    
    private void setupAccountSettings() {
        updateAccountStatus();
        
        signInOutButton.setOnClickListener(v -> {
            if (authService.isUserSignedIn()) {
                // Sign out
                authService.signOut();
                updateAccountStatus();
                Toast.makeText(this, getString(R.string.sign_out_success), Toast.LENGTH_SHORT).show();
            } else {
                // Sign in - show loading state
                signInOutButton.setEnabled(false);
                signInOutButton.setText(getString(R.string.signing_in));
                
                try {
                    Intent signInIntent = authService.getSignInIntent();
                    signInLauncher.launch(signInIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Error launching sign-in", e);
                    Toast.makeText(this, getString(R.string.sign_in_start_error), Toast.LENGTH_LONG).show();
                    updateAccountStatus(); // Reset button state
                }
            }
        });
        
        resetLoginButton.setOnClickListener(v -> {
            // Clear the skipped login preference
            prefs.edit().remove("skipped_login").apply();
            Toast.makeText(this, getString(R.string.reset_login_message), Toast.LENGTH_SHORT).show();
        });

        manualSyncButton.setOnClickListener(v -> {
            if (authService.isUserSignedIn()) {
                manualSyncButton.setEnabled(false);
                manualSyncButton.setText("מסנכרן...");
                
                // Reset first sync flag to ensure we download cloud tasks
                syncManager.resetFirstSyncFlag();
                
                syncManager.forceSync(new SyncManager.SyncCallback() {
                    @Override
                    public void onSyncComplete(boolean success, String message) {
                        runOnUiThread(() -> {
                            manualSyncButton.setEnabled(true);
                            manualSyncButton.setText("סנכרן עכשיו");
                            Toast.makeText(SettingsActivity.this, 
                                success ? "סנכרון הושלם בהצלחה" : "סנכרון נכשל: " + message, 
                                Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onSyncProgress(String message) {
                        Log.d(TAG, "Manual sync progress: " + message);
                    }
                });
            } else {
                Toast.makeText(this, "יש להתחבר תחילה", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown sync manager to prevent memory leaks
        if (syncManager != null) {
            syncManager.shutdown();
            Log.d(TAG, "onDestroy: SyncManager shutdown");
        }
    }
    
    private void updateAccountStatus() {
        if (authService.isUserSignedIn()) {
            // User is signed in
            String displayName = authService.getCurrentUser().getDisplayName();
            if (displayName != null) {
                accountStatusTextView.setText(getString(R.string.signed_in_as, displayName));
            } else {
                accountStatusTextView.setText(getString(R.string.signed_in_as, "User"));
            }
            signInOutButton.setText(getString(R.string.sign_out));
        } else {
            // User is not signed in
            accountStatusTextView.setText(getString(R.string.not_signed_in));
            signInOutButton.setText(getString(R.string.sign_in_with_google));
        }
    }
    
    @Override
    public void onAuthSuccess(User user) {
        Log.d(TAG, "Authentication successful for user: " + user.getDisplayName());
        updateAccountStatus();
        Toast.makeText(this, getString(R.string.welcome_user, user.getDisplayName()), Toast.LENGTH_SHORT).show();
        
        // Clear the skipped login flag since user is now signed in
        prefs.edit().remove("skipped_login").apply();
    }
    
    @Override
    public void onAuthError(String error) {
        Log.e(TAG, "Authentication failed: " + error);
        Toast.makeText(this, getString(R.string.sign_in_error, error), Toast.LENGTH_LONG).show();
        updateAccountStatus(); // Reset button state
    }
    
    private void setLocale(String language) {
        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(
                (language != null && language.startsWith("he")) ? "he-IL" : "en-US");
        AppCompatDelegate.setApplicationLocales(appLocale);
        Log.d(TAG, "setLocale: Applied app locale: " + (language != null ? language : "en"));
    }
}
