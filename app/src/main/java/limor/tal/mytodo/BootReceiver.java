package limor.tal.mytodo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.util.List;

/**
 * BootReceiver handles rescheduling reminders after device restart.
 * This is crucial because alarms are cleared when the device reboots.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "BootReceiver received: " + intent.getAction());
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())) {
            
            Log.d(TAG, "Device boot completed or app updated, rescheduling reminders");
            rescheduleAllReminders(context);
        }
    }
    
    private void rescheduleAllReminders(Context context) {
        try {
            // Run in background thread to avoid blocking
            new Thread(() -> {
                try {
                    Log.d(TAG, "Starting to reschedule all reminders");
                    
                    // Get all tasks from database using the repository
                    TaskRepository repository = new TaskRepository((android.app.Application) context.getApplicationContext());
                    List<Task> allTasks = repository.getAllTasksSync();
                    
                    Log.d(TAG, "Found " + allTasks.size() + " tasks to check for reminders");
                    
                    ReminderManager reminderManager = new ReminderManager(context);
                    int rescheduledCount = 0;
                    
                    for (Task task : allTasks) {
                        // Only reschedule reminders for incomplete tasks that have reminder settings
                        if (!task.isCompleted && 
                            task.reminderOffset != null && 
                            task.reminderOffset >= 0 && 
                            task.dueTime != null) {
                            
                            try {
                                // Calculate the next reminder time
                                java.util.Calendar reminderTime = java.util.Calendar.getInstance();
                                
                                // Convert milliseconds since midnight to hours and minutes
                                long millisSinceMidnight = task.dueTime;
                                int hours = (int) (millisSinceMidnight / (60 * 60 * 1000));
                                int minutes = (int) ((millisSinceMidnight % (60 * 60 * 1000)) / (60 * 1000));
                                
                                reminderTime.set(java.util.Calendar.HOUR_OF_DAY, hours);
                                reminderTime.set(java.util.Calendar.MINUTE, minutes);
                                reminderTime.set(java.util.Calendar.SECOND, 0);
                                reminderTime.set(java.util.Calendar.MILLISECOND, 0);
                                reminderTime.add(java.util.Calendar.MINUTE, -task.reminderOffset);
                                
                                // Handle specific date tasks
                                if (task.dueDate != null) {
                                    reminderTime.setTimeInMillis(task.dueDate);
                                    reminderTime.set(java.util.Calendar.HOUR_OF_DAY, hours);
                                    reminderTime.set(java.util.Calendar.MINUTE, minutes);
                                    reminderTime.set(java.util.Calendar.SECOND, 0);
                                    reminderTime.set(java.util.Calendar.MILLISECOND, 0);
                                    reminderTime.add(java.util.Calendar.MINUTE, -task.reminderOffset);
                                } else if (task.dayOfWeek != null) {
                                    // Handle day-of-week tasks
                                    String[] daysOfWeek = context.getResources().getStringArray(R.array.days_of_week);
                                    if (daysOfWeek != null && daysOfWeek.length > 2) {
                                        int targetDay = -1;
                                        for (int i = 3; i < daysOfWeek.length; i++) {
                                            if (daysOfWeek[i].equals(task.dayOfWeek)) {
                                                targetDay = i - 2; // Map to Calendar.DAY_OF_WEEK
                                                break;
                                            }
                                        }
                                        if (targetDay != -1) {
                                            java.util.Calendar today = java.util.Calendar.getInstance();
                                            int currentDay = today.get(java.util.Calendar.DAY_OF_WEEK);
                                            int daysToAdd = (targetDay - currentDay + 7) % 7;
                                            if (daysToAdd == 0 && today.getTimeInMillis() > reminderTime.getTimeInMillis()) {
                                                daysToAdd = 7; // Schedule for next week if time has passed today
                                            }
                                            reminderTime.add(java.util.Calendar.DAY_OF_MONTH, daysToAdd);
                                        }
                                    }
                                } else {
                                    // Handle time-only tasks
                                    java.util.Calendar now = java.util.Calendar.getInstance();
                                    if (reminderTime.getTimeInMillis() <= now.getTimeInMillis()) {
                                        reminderTime.add(java.util.Calendar.DAY_OF_MONTH, 1); // Schedule for next day
                                    }
                                }
                                
                                // Ensure reminder time is in the future
                                if (reminderTime.getTimeInMillis() <= System.currentTimeMillis()) {
                                    reminderTime.add(java.util.Calendar.DAY_OF_MONTH, 1);
                                }
                                
                                // Schedule the reminder
                                reminderManager.scheduleReminder(task, reminderTime.getTimeInMillis());
                                rescheduledCount++;
                                
                                Log.d(TAG, "Rescheduled reminder for task: " + task.description + 
                                      " at " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                      .format(reminderTime.getTime()));
                                
                            } catch (Exception e) {
                                Log.e(TAG, "Error rescheduling reminder for task: " + task.description, e);
                            }
                        }
                    }
                    
                    Log.d(TAG, "Rescheduling complete. Rescheduled " + rescheduledCount + " reminders out of " + allTasks.size() + " tasks");
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error during reminder rescheduling", e);
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting reminder rescheduling thread", e);
        }
    }
}
