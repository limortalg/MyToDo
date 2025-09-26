package limor.tal.mytodo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import limor.tal.mytodo.AppDatabase;
import limor.tal.mytodo.TaskDao;
import limor.tal.mytodo.Task;

public class ReminderService extends Service {
    private static final String TAG = "ReminderService";
    private static final String CHANNEL_ID = "reminder_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private boolean isPlaying = false;
    private Task currentTask;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ReminderService created");
        
        // Initialize vibrator
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        // Create notification channel for Android 8.0+
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ReminderService started");
        
        if (intent != null && intent.getAction() != null) {
            // Handle action button clicks
            String action = intent.getAction();
            int taskId = intent.getIntExtra("task_id", -1);
            
            Log.d(TAG, "Received action: " + action + " for task ID: " + taskId);
            switch (action) {
                case "SNOOZE":
                    Log.d(TAG, "Handling snooze action");
                    handleSnooze(taskId);
                    break;
                case "DELETE":
                    Log.d(TAG, "Handling delete action");
                    handleDelete(taskId);
                    break;
                case "STOP_REMINDER":
                    Log.d(TAG, "Handling stop reminder action");
                    handleStopReminder(taskId);
                    break;
                default:
                    Log.w(TAG, "Unknown action: " + action);
                    break;
            }
        } else if (intent != null && intent.hasExtra("task_id")) {
            // Handle normal service startup
            int taskId = intent.getIntExtra("task_id", -1);
            String taskDescription = intent.getStringExtra("task_description");
            String taskDay = intent.getStringExtra("task_day");
            
            if (taskId != -1) {
                // Create a dummy task for display purposes
                currentTask = new Task(taskDescription, null, taskDay, false, null, false, 0);
                currentTask.id = taskId;
                
                // Start the persistent reminder
                startPersistentReminder();
                
                // Try to start foreground service, but handle failure gracefully
                try {
                    startForeground(NOTIFICATION_ID, createNotification());
                    Log.d(TAG, "Successfully started foreground service");
                } catch (Exception e) {
                    Log.w(TAG, "Could not start foreground service: " + e.getMessage());
                    // Fallback: Show a high-priority notification instead
                    try {
                        NotificationManager notificationManager = getSystemService(NotificationManager.class);
                        if (notificationManager != null) {
                            notificationManager.notify(NOTIFICATION_ID, createNotification());
                            Log.d(TAG, "Started reminder as high-priority notification instead of foreground service");
                        }
                    } catch (Exception fallbackException) {
                        Log.e(TAG, "Failed to show notification as fallback: " + fallbackException.getMessage());
                    }
                }
            }
        }
        
        // Return START_STICKY to restart service if killed
        return START_STICKY;
    }
    
    private void startPersistentReminder() {
        if (isPlaying) {
            return; // Already playing
        }
        
        Log.d(TAG, "Starting persistent reminder for task: " + currentTask.description);
        
        // Start sound
        startSound();
        
        // Start vibration
        startVibration();
        
        isPlaying = true;
    }
    
    private void startSound() {
        try {
            // Get default alarm sound
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmSound == null) {
                // Fallback to notification sound if no alarm sound
                alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            
            mediaPlayer.setDataSource(this, alarmSound);
            mediaPlayer.setLooping(true); // Loop continuously
            mediaPlayer.prepare();
            mediaPlayer.start();
            
            Log.d(TAG, "Sound started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting sound", e);
        }
    }
    
    private void startVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            // Create a vibration pattern: wait 0ms, vibrate 1000ms, wait 500ms, repeat
            long[] pattern = {0, 1000, 500};
            int[] amplitudes = {0, 255, 0};
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, amplitudes, 0);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(pattern, 0); // 0 means repeat indefinitely
            }
            
            Log.d(TAG, "Vibration started");
        }
    }
    
    private void stopReminder() {
        Log.d(TAG, "Stopping reminder");
        
        // Stop sound
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping media player", e);
            }
        }
        
        // Stop vibration
        if (vibrator != null) {
            vibrator.cancel();
        }
        
        // Cancel the notification
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
            Log.d(TAG, "Cancelled notification in stopReminder");
        }
        
        isPlaying = false;
    }
    
    private Notification createNotification() {
        // Create intent for snooze action
        Intent snoozeIntent = new Intent(this, ReminderService.class);
        snoozeIntent.setAction("SNOOZE");
        snoozeIntent.putExtra("task_id", currentTask.id);
        PendingIntent snoozePendingIntent = PendingIntent.getService(this, 0, snoozeIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Create intent for complete action - DIRECT to MainActivity to avoid trampoline
        Intent completeIntent = new Intent(this, MainActivity.class);
        completeIntent.setAction("COMPLETE_TASK_FROM_REMINDER");
        completeIntent.putExtra("task_id", currentTask.id);
        completeIntent.putExtra("stop_reminder", true); // Signal to stop the reminder
        completeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent completePendingIntent = PendingIntent.getActivity(this, 1, completeIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Create intent for edit action - DIRECT to MainActivity to avoid trampoline
        Intent editIntent = new Intent(this, MainActivity.class);
        editIntent.setAction("EDIT_TASK_FROM_REMINDER");
        editIntent.putExtra("task_id", currentTask.id);
        editIntent.putExtra("stop_reminder", true); // Signal to stop the reminder
        editIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent editPendingIntent = PendingIntent.getActivity(this, 2, editIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Create intent for delete action
        Intent deleteIntent = new Intent(this, ReminderService.class);
        deleteIntent.setAction("DELETE");
        deleteIntent.putExtra("task_id", currentTask.id);
        PendingIntent deletePendingIntent = PendingIntent.getService(this, 3, deleteIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        Log.d(TAG, "createNotification: Building notification with 3 action buttons (reduced for compatibility)");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.task_reminder))
                .setContentText(currentTask.description)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true) // Can't be dismissed by swiping
                .setAutoCancel(false)
                .setOnlyAlertOnce(false) // Alert every time
                .setDefaults(NotificationCompat.DEFAULT_ALL) // Sound, vibration, lights
                .setVibrate(new long[]{0, 1000, 500, 1000}) // Custom vibration pattern
                .setLights(0xFF0000FF, 3000, 3000) // Blue light, 3 seconds on, 3 seconds off
                .addAction(R.drawable.ic_launcher_foreground, getString(R.string.snooze_5min), snoozePendingIntent)
                .addAction(R.drawable.ic_launcher_foreground, getString(R.string.complete), completePendingIntent)
                .addAction(R.drawable.ic_launcher_foreground, getString(R.string.edit), editPendingIntent);
        
        Log.d(TAG, "createNotification: Notification built with 3 action buttons (Snooze, Complete, Edit)");
        Log.d(TAG, "createNotification: Note: Delete action removed to ensure all buttons are visible");
        Log.d(TAG, "createNotification: Users can delete tasks by editing them and using the delete button in the app");
        
        return builder.build();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("High priority reminders that can't be ignored");
            channel.setSound(null, null); // We'll handle sound manually
            channel.enableVibration(false); // We'll handle vibration manually
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableLights(true);
            channel.setLightColor(0xFF0000FF); // Blue light
            channel.setShowBadge(true);
            channel.setImportance(NotificationManager.IMPORTANCE_HIGH);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "ReminderService destroyed");
        stopReminder();
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void handleSnooze(int taskId) {
        Log.d(TAG, "Snoozing task: " + taskId);
        
        // Stop current reminder
        stopReminder();
        
        // Schedule reminder for 5 minutes later using ReminderManager
        if (currentTask != null) {
            ReminderManager reminderManager = new ReminderManager(this);
            reminderManager.scheduleSnooze(currentTask);
            Log.d(TAG, "Snooze scheduled for task: " + currentTask.description);
        }
        
        // Stop the service
        stopSelf();
    }
    

    

    
    private void handleDelete(int taskId) {
        Log.d(TAG, "Deleting task: " + taskId);
        
        // Stop current reminder
        stopReminder();
        
        // Delete task from database
        try {
            // Create a new thread to access the database
            new Thread(() -> {
                try {
                    AppDatabase database = AppDatabase.getDatabase(this);
                    TaskDao taskDao = database.taskDao();
                    
                    // Get the task first, then delete it
                    Task task = taskDao.getTaskById(taskId);
                    if (task != null) {
                        taskDao.delete(task);
                        Log.d(TAG, "Task deleted from database: " + task.description);
                    } else {
                        Log.w(TAG, "Task not found in database: " + taskId);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error deleting task from database: " + taskId, e);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up task deletion: " + taskId, e);
        }
        
        // Stop the service
        stopSelf();
    }
    
    private void handleStopReminder(int taskId) {
        Log.d(TAG, "Stopping reminder for task: " + taskId);
        
        // Stop current reminder
        stopReminder();
        
        // Cancel the notification
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
            Log.d(TAG, "Cancelled notification for task: " + taskId);
        }
        
        // Stop the service
        stopSelf();
    }
}
