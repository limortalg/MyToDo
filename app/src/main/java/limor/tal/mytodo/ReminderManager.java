package limor.tal.mytodo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.util.Calendar;

public class ReminderManager {
    private static final String TAG = "ReminderManager";
    private Context context;
    private AlarmManager alarmManager;
    
    public ReminderManager(Context context) {
        this.context = context;
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }
    
    /**
     * Schedule a reminder for a task
     * @param task The task to schedule a reminder for
     * @param reminderTimeMillis When the reminder should trigger (in milliseconds)
     */
    public void scheduleReminder(Task task, long reminderTimeMillis) {
        Log.d(TAG, "scheduleReminder called for task: " + task.description + ", id: " + task.id + ", reminderOffset: " + task.reminderOffset + ", reminderTimeMillis: " + reminderTimeMillis);
        
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null, cannot schedule reminder");
            return;
        }
        
        if (task.reminderOffset == null || task.reminderOffset < 0) {
            Log.d(TAG, "Task " + task.description + " has no reminder offset, skipping");
            return;
        }
        
        // Calculate when the reminder should trigger
        long triggerTime;
        if (task.reminderOffset == 0) {
            // Reminder at the exact time of the task
            triggerTime = reminderTimeMillis;
            Log.d(TAG, "Task " + task.description + " has reminder AT the time (offset: 0)");
        } else {
            // reminderTimeMillis is already calculated as (dueTime - reminderOffset) in MainActivity
            // So we just use it directly
            triggerTime = reminderTimeMillis;
            Log.d(TAG, "Task " + task.description + " has reminder " + task.reminderOffset + " minutes before (already calculated in MainActivity)");
        }
        
        // Don't schedule if the reminder time has already passed
        if (triggerTime <= System.currentTimeMillis()) {
            Log.d(TAG, "Reminder time has already passed for task: " + task.description);
            return;
        }
        
        // Create intent for the ReminderService
        Intent reminderIntent = new Intent(context, ReminderService.class);
        reminderIntent.putExtra("task_id", task.id);
        reminderIntent.putExtra("task_description", task.description);
        reminderIntent.putExtra("task_day", task.dayOfWeek);
        
        // Create unique request code for each task
        int requestCode = task.id;
        
        // Create PendingIntent
        PendingIntent pendingIntent = PendingIntent.getService(context, requestCode, reminderIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        try {
            // Schedule the alarm
            if (android.os.Build.VERSION.SDK_INT >= 31 && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                Log.d(TAG, "Scheduled exact reminder for task: " + task.description + " at " + triggerTime);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                Log.d(TAG, "Scheduled reminder for task: " + task.description + " at " + triggerTime);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception scheduling reminder for task: " + task.description, e);
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling reminder for task: " + task.description, e);
        }
    }
    
    /**
     * Cancel a reminder for a task
     * @param task The task to cancel the reminder for
     */
    public void cancelReminder(Task task) {
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null, cannot cancel reminder");
            return;
        }
        
        // Create the same intent that was used to schedule
        Intent reminderIntent = new Intent(context, ReminderService.class);
        reminderIntent.putExtra("task_id", task.id);
        reminderIntent.putExtra("task_description", task.description);
        reminderIntent.putExtra("task_day", task.dayOfWeek);
        
        // Create unique request code for each task
        int requestCode = task.id;
        
        // Create PendingIntent with the same flags
        PendingIntent pendingIntent = PendingIntent.getService(context, requestCode, reminderIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        try {
            alarmManager.cancel(pendingIntent);
            Log.d(TAG, "Cancelled reminder for task: " + task.description);
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling reminder for task: " + task.description, e);
        }
    }
    
    /**
     * Schedule a snooze reminder (5 minutes from now)
     * @param task The task to snooze
     */
    public void scheduleSnooze(Task task) {
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null, cannot schedule snooze");
            return;
        }
        
        // Schedule for 5 minutes from now
        long snoozeTime = System.currentTimeMillis() + (5 * 60 * 1000); // 5 minutes
        
        Log.d(TAG, "scheduleSnooze: Current time: " + System.currentTimeMillis());
        Log.d(TAG, "scheduleSnooze: Snooze time: " + snoozeTime);
        Log.d(TAG, "scheduleSnooze: Snooze in: " + (snoozeTime - System.currentTimeMillis()) + " ms");
        
        // Use NotificationReceiver instead of ReminderService for better reliability
        Intent reminderIntent = new Intent(context, NotificationReceiver.class);
        reminderIntent.putExtra("task_id", task.id);
        reminderIntent.putExtra("task_description", task.description);
        reminderIntent.putExtra("task_day", task.dayOfWeek);
        
        // Use a different request code for snooze to avoid conflicts
        int requestCode = task.id + 10000; // Add 10000 to make it unique
        
        // Create PendingIntent for BroadcastReceiver (more reliable than Service for background)
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, reminderIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        try {
            // Use setAlarmClock for highest priority and immunity to doze mode
            // This is the most reliable method for critical alarms
            if (android.os.Build.VERSION.SDK_INT >= 31 && alarmManager.canScheduleExactAlarms()) {
                // Create AlarmClockInfo for maximum reliability
                AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(snoozeTime, pendingIntent);
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
                Log.d(TAG, "Scheduled ALARM CLOCK snooze reminder for task: " + task.description + " at " + snoozeTime);
            } else if (android.os.Build.VERSION.SDK_INT >= 23) {
                // Use setExactAndAllowWhileIdle for API 23+
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent);
                Log.d(TAG, "Scheduled exact snooze reminder for task: " + task.description + " at " + snoozeTime);
            } else {
                // Fallback for older Android versions
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent);
                Log.d(TAG, "Scheduled snooze reminder (fallback) for task: " + task.description + " at " + snoozeTime);
            }
            
            // Log the actual trigger time for debugging
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            Log.d(TAG, "scheduleSnooze: Alarm will trigger at: " + sdf.format(new java.util.Date(snoozeTime)));
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception scheduling snooze for task: " + task.description, e);
            // Try fallback method
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent);
                Log.d(TAG, "Used fallback method for snooze reminder: " + task.description);
            } catch (Exception fallbackE) {
                Log.e(TAG, "Fallback also failed for snooze: " + task.description, fallbackE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling snooze for task: " + task.description, e);
        }
    }
    
    /**
     * Calculate the next reminder time for a recurring task
     * @param task The recurring task
     * @return The next reminder time in milliseconds, or -1 if no reminder should be scheduled
     */
    public long calculateNextReminderTime(Task task) {
        if (task.reminderOffset == null || task.reminderOffset <= 0) {
            return -1; // No reminder
        }
        
        // For now, just return the current time + reminder offset
        // In a full implementation, this would calculate based on the task's schedule
        return System.currentTimeMillis() + (task.reminderOffset * 60 * 1000);
    }
}
