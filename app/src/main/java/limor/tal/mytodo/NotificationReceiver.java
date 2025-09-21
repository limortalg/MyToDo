package limor.tal.mytodo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Application;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class NotificationReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "task_reminder_channel";
    private static final String ACTION_SNOOZE = "limor.tal.mytodo.SNOOZE";
    private static final String ACTION_COMPLETE = "limor.tal.mytodo.COMPLETE";
    private static final String ACTION_DELETE = "limor.tal.mytodo.DELETE";
    private static final String ACTION_EDIT = "limor.tal.mytodo.EDIT";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String taskDescription = intent.getStringExtra("task_description");
            int taskId = intent.getIntExtra("task_id", 0);

            if (taskDescription == null || taskId == 0) {
                Log.e("MyToDo", "NotificationReceiver: Invalid intent data - description: " + taskDescription + ", id: " + taskId);
                return;
            }

            // Handle snooze action
            if (ACTION_SNOOZE.equals(intent.getAction())) {
                handleSnoozeAction(context, taskDescription, taskId);
                return;
            }

            // Handle complete action
            if (ACTION_COMPLETE.equals(intent.getAction())) {
                handleCompleteAction(context, taskDescription, taskId);
                return;
            }

            // Handle delete action
            if (ACTION_DELETE.equals(intent.getAction())) {
                handleDeleteAction(context, taskDescription, taskId);
                return;
            }

            // Handle edit action
            if (ACTION_EDIT.equals(intent.getAction())) {
                handleEditAction(context, taskDescription, taskId);
                return;
            }

            // Start the ReminderService for persistent ringing notification
            Intent serviceIntent = new Intent(context, ReminderService.class);
            serviceIntent.putExtra("task_id", taskId);
            serviceIntent.putExtra("task_description", taskDescription);
            serviceIntent.putExtra("task_day", intent.getStringExtra("task_day"));
            
            try {
                context.startForegroundService(serviceIntent);
                Log.d("MyToDo", "Started ReminderService from NotificationReceiver for task: " + taskDescription);
            } catch (Exception e) {
                Log.e("MyToDo", "Failed to start ReminderService, falling back to basic notification", e);
                // Fallback to basic notification if service fails
                createMainNotification(context, taskDescription, taskId);
            }
            
        } catch (Exception e) {
            Log.e("MyToDo", "NotificationReceiver: Error in onReceive", e);
        }
    }

    private void handleSnoozeAction(Context context, String taskDescription, int taskId) {
        try {
            // Cancel the original notification
            NotificationManagerCompat.from(context).cancel(taskId);
            Log.d("MyToDo", "Snoozed notification for task: " + taskDescription + ", ID: " + taskId);

            // Schedule a new reminder 15 minutes later
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                Log.e("MyToDo", "handleSnoozeAction: AlarmManager is null");
                return;
            }
            
            Intent snoozeIntent = new Intent(context, NotificationReceiver.class);
            snoozeIntent.putExtra("task_description", taskDescription);
            snoozeIntent.putExtra("task_id", taskId);
            
            int flags = PendingIntent.FLAG_IMMUTABLE;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_UPDATE_CURRENT;
            }
            
            PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                    context,
                    taskId,
                    snoozeIntent,
                    flags);

            Calendar snoozeTime = Calendar.getInstance();
            snoozeTime.add(Calendar.MINUTE, 5); // Changed to 5 minutes to match ReminderManager
            
            // Use the most reliable alarm scheduling method
            if (android.os.Build.VERSION.SDK_INT >= 31 && alarmManager.canScheduleExactAlarms()) {
                AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(snoozeTime.getTimeInMillis(), snoozePendingIntent);
                alarmManager.setAlarmClock(alarmClockInfo, snoozePendingIntent);
                Log.d("MyToDo", "Scheduled ALARM CLOCK snooze for task: " + taskDescription + " at " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(snoozeTime.getTime()));
            } else if (android.os.Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime.getTimeInMillis(), snoozePendingIntent);
                Log.d("MyToDo", "Scheduled exact snooze for task: " + taskDescription + " at " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(snoozeTime.getTime()));
            } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, snoozeTime.getTimeInMillis(), snoozePendingIntent);
                Log.d("MyToDo", "Scheduled snooze (fallback) for task: " + taskDescription + " at " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(snoozeTime.getTime()));
            }
        } catch (Exception e) {
            Log.e("MyToDo", "handleSnoozeAction: Error handling snooze", e);
        }
    }

    private void handleCompleteAction(Context context, String taskDescription, int taskId) {
        // Cancel the notification
        NotificationManagerCompat.from(context).cancel(taskId);
        Log.d("MyToDo", "Completing task from notification: " + taskDescription + ", ID: " + taskId);

        // Mark task as completed in database
        new Thread(() -> {
            try {
                Log.d("MyToDo", "NotificationReceiver: Starting task completion for ID: " + taskId);
                TaskRepository repository = new TaskRepository((Application) context.getApplicationContext());
                Task task = repository.getTaskById(taskId);
                Log.d("MyToDo", "NotificationReceiver: Retrieved task: " + (task != null ? task.description : "null") + ", isCompleted: " + (task != null ? task.isCompleted : "N/A"));
                
                if (task != null) {
                    Log.d("MyToDo", "NotificationReceiver: Before update - isCompleted: " + task.isCompleted + ", completionDate: " + task.completionDate);
                    
                    // Mark as completed and save completion time
                    task.isCompleted = true;
                    task.completionDate = System.currentTimeMillis();
                    
                    // Only clear fields for NON-recurring tasks
                    if (!task.isRecurring) {
                        task.dueDate = null;
                        task.dueTime = null;
                        task.dayOfWeek = null;
                        task.isRecurring = false;
                        task.recurrenceType = null;
                        task.reminderOffset = null;
                        task.priority = 0;
                        Log.d("MyToDo", "NotificationReceiver: Cleared fields for non-recurring task: " + task.description);
                    } else {
                        Log.d("MyToDo", "NotificationReceiver: Preserved fields for recurring task: " + task.description + ", recurrenceType: " + task.recurrenceType);
                    }
                    
                    Log.d("MyToDo", "NotificationReceiver: After update - isCompleted: " + task.isCompleted + ", completionDate: " + task.completionDate);
                    
                    repository.update(task);
                    Log.d("MyToDo", "NotificationReceiver: Task marked as completed: " + taskDescription + ", ID: " + taskId);
                    
                    // Verify the update worked by retrieving the task again
                    Task updatedTask = repository.getTaskById(taskId);
                    Log.d("MyToDo", "NotificationReceiver: Verification - retrieved task isCompleted: " + (updatedTask != null ? updatedTask.isCompleted : "null"));
                    
                    // Show toast message on main thread
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        Toast.makeText(context, context.getString(R.string.task_completed_notification, taskDescription), Toast.LENGTH_SHORT).show();
                    });
                } else {
                    Log.w("MyToDo", "NotificationReceiver: Task not found for completion: " + taskId);
                }
            } catch (Exception e) {
                Log.e("MyToDo", "NotificationReceiver: Error completing task: " + e.getMessage(), e);
            }
        }).start();
    }

    private void handleDeleteAction(Context context, String taskDescription, int taskId) {
        // Cancel the notification
        NotificationManagerCompat.from(context).cancel(taskId);
        Log.d("MyToDo", "Deleting task from notification: " + taskDescription + ", ID: " + taskId);

        // Delete task from database
        new Thread(() -> {
            try {
                Log.d("MyToDo", "NotificationReceiver: Starting task deletion for ID: " + taskId);
                TaskRepository repository = new TaskRepository((Application) context.getApplicationContext());
                Task task = repository.getTaskById(taskId);
                Log.d("MyToDo", "NotificationReceiver: Retrieved task for deletion: " + (task != null ? task.description : "null"));
                
                if (task != null) {
                    repository.delete(task);
                    Log.d("MyToDo", "NotificationReceiver: Task deleted: " + taskDescription + ", ID: " + taskId);
                    
                    // Show toast message on main thread
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        Toast.makeText(context, context.getString(R.string.task_deleted_notification, taskDescription), Toast.LENGTH_SHORT).show();
                    });
                } else {
                    Log.w("MyToDo", "NotificationReceiver: Task not found for deletion: " + taskId);
                }
            } catch (Exception e) {
                Log.e("MyToDo", "NotificationReceiver: Error deleting task: " + e.getMessage(), e);
            }
        }).start();
    }

    private void handleEditAction(Context context, String taskDescription, int taskId) {
        try {
            // Cancel the notification
            NotificationManagerCompat.from(context).cancel(taskId);
            Log.d("MyToDo", "Opening edit dialog for task from notification: " + taskDescription + ", ID: " + taskId);

            // Open MainActivity with edit intent
            Intent editIntent = new Intent(context, MainActivity.class);
            editIntent.setAction("EDIT_TASK");
            editIntent.putExtra("task_id", taskId);
            editIntent.putExtra("task_description", taskDescription);
            editIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(editIntent);
            
            Log.d("MyToDo", "NotificationReceiver: Started MainActivity with edit intent for task ID: " + taskId);
        } catch (Exception e) {
            Log.e("MyToDo", "handleEditAction: Error handling edit action", e);
        }
    }

    private void createMainNotification(Context context, String taskDescription, int taskId) {
        try {
            // Create NotificationChannel for API 26+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.task_reminder),
                        NotificationManager.IMPORTANCE_DEFAULT);
                NotificationManagerCompat.from(context).createNotificationChannel(channel);
            }

            // Use safer PendingIntent flags
            int flags = PendingIntent.FLAG_IMMUTABLE;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_UPDATE_CURRENT;
            }

            // Intent for opening MainActivity when notification is tapped
            Intent mainIntent = new Intent(context, MainActivity.class);
            PendingIntent mainPendingIntent = PendingIntent.getActivity(
                    context,
                    taskId,
                    mainIntent,
                    flags);

            // Intent for snooze action
            Intent snoozeIntent = new Intent(context, NotificationReceiver.class);
            snoozeIntent.setAction(ACTION_SNOOZE);
            snoozeIntent.putExtra("task_description", taskDescription);
            snoozeIntent.putExtra("task_id", taskId);
            PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                    context,
                    taskId + 1000, // Unique request code for snooze
                    snoozeIntent,
                    flags);

            // Intent for complete action
            Intent completeIntent = new Intent(context, NotificationReceiver.class);
            completeIntent.setAction(ACTION_COMPLETE);
            completeIntent.putExtra("task_description", taskDescription);
            completeIntent.putExtra("task_id", taskId);
            PendingIntent completePendingIntent = PendingIntent.getBroadcast(
                    context,
                    taskId + 2000, // Unique request code for complete
                    completeIntent,
                    flags);

            // Intent for delete action
            Intent deleteIntent = new Intent(context, NotificationReceiver.class);
            deleteIntent.setAction(ACTION_DELETE);
            deleteIntent.putExtra("task_description", taskDescription);
            deleteIntent.putExtra("task_id", taskId);
            PendingIntent deletePendingIntent = PendingIntent.getBroadcast(
                    context,
                    taskId + 3000, // Unique request code for delete
                    deleteIntent,
                    flags);

            // Intent for edit action
            Intent editIntent = new Intent(context, NotificationReceiver.class);
            editIntent.setAction(ACTION_EDIT);
            editIntent.putExtra("task_description", taskDescription);
            editIntent.putExtra("task_id", taskId);
            PendingIntent editPendingIntent = PendingIntent.getBroadcast(
                    context,
                    taskId + 4000, // Unique request code for edit
                    editIntent,
                    flags);

            String reminderTitle = context.getString(R.string.task_reminder);
            if (reminderTitle == null) {
                reminderTitle = "Task Reminder"; // Fallback
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(reminderTitle)
                    .setContentText(taskDescription)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(mainPendingIntent)
                    .setAutoCancel(true)
                    .addAction(0, context.getString(R.string.snooze), snoozePendingIntent)
                    .addAction(0, context.getString(R.string.complete), completePendingIntent)
                    .addAction(0, context.getString(R.string.delete), deletePendingIntent)
                    .addAction(0, context.getString(R.string.edit), editPendingIntent);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.notify(taskId, builder.build());
            Log.d("MyToDo", "Notification posted for task: " + taskDescription + ", ID: " + taskId);
            
        } catch (SecurityException e) {
            Log.e("MyToDo", "Notification permission not granted", e);
        } catch (Exception e) {
            Log.e("MyToDo", "createMainNotification: Error creating notification", e);
        }
    }
}
