package limor.tal.mytodo;

import android.app.Activity;
import android.content.Intent;
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

public class ReminderSettingsActivity extends AppCompatActivity {
    private static final String TAG = "ReminderSettings";
    private static final int REQUEST_CODE_ALARM_SOUND = 1001;
    
    private TextView currentSoundTextView;
    private Button selectSoundButton;
    private Button testSoundButton;
    private Uri selectedSoundUri;
    
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
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder_settings);
        
        // Initialize views
        currentSoundTextView = findViewById(R.id.currentSoundTextView);
        selectSoundButton = findViewById(R.id.selectSoundButton);
        testSoundButton = findViewById(R.id.testSoundButton);
        
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
            backButton.setOnClickListener(v -> finish());
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
}
