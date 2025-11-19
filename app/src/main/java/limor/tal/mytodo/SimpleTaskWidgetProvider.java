package limor.tal.mytodo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.Calendar;

/**
 * Widget provider for scrollable task list
 */
public class SimpleTaskWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "SimpleTaskWidget";
    
    // Action constants
    public static final String ACTION_REFRESH = "limor.tal.mytodo.REFRESH_WIDGET";
    public static final String ACTION_OPEN_APP = "limor.tal.mytodo.OPEN_APP";
    public static final String ACTION_COMPLETE_TASK = "limor.tal.mytodo.COMPLETE_TASK";
    public static final String ACTION_TOGGLE_MODE = "limor.tal.mytodo.TOGGLE_MODE";
    public static final String ACTION_ADD_TASK = "limor.tal.mytodo.ADD_TASK";
    
    // SharedPreferences key
    private static final String PREF_SHOW_TODAY_ONLY = "widget_show_today_only";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate called for " + appWidgetIds.length + " widgets");
        
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        
        String action = intent.getAction();
        Log.d(TAG, "onReceive: " + action);
        Log.d(TAG, "onReceive: Intent extras: " + intent.getExtras());
        Log.d(TAG, "onReceive: Intent component: " + intent.getComponent());
        Log.d(TAG, "onReceive: Intent data: " + intent.getData());
        Log.d(TAG, "onReceive: Intent flags: " + intent.getFlags());
        Log.d(TAG, "onReceive: Intent package: " + intent.getPackage());
        Log.d(TAG, "onReceive: Intent scheme: " + intent.getScheme());
        
        // Add specific debugging for task completion
        if (action != null && action.contains("COMPLETE")) {
            Log.d(TAG, "onReceive: COMPLETE action detected - action: " + action);
            Log.d(TAG, "onReceive: COMPLETE intent extras: " + intent.getExtras());
            Log.d(TAG, "onReceive: COMPLETE intent component: " + intent.getComponent());
        }
        
        // Add debugging for ANY intent received
        Log.d(TAG, "onReceive: ANY intent received - action: " + action);
        if (intent.getExtras() != null) {
            Log.d(TAG, "onReceive: ANY intent extras: " + intent.getExtras());
        }
        
        // Add debugging to detect if ANY PendingIntent is being triggered
        if (action != null && !action.startsWith("android.appwidget.action")) {
            Log.d(TAG, "onReceive: NON-WIDGET intent received! This means PendingIntents are working!");
            Log.d(TAG, "onReceive: Non-widget action: " + action);
            Log.d(TAG, "onReceive: Non-widget extras: " + intent.getExtras());
        }
        
        // Add specific debugging for task completion intents
        if (intent.getExtras() != null && intent.getExtras().containsKey("task_id")) {
            Log.d(TAG, "onReceive: Intent contains task_id - this might be a task completion!");
            Log.d(TAG, "onReceive: task_id value: " + intent.getIntExtra("task_id", -1));
            Log.d(TAG, "onReceive: debug_source: " + intent.getStringExtra("debug_source"));
        }
        
        if (ACTION_REFRESH.equals(action)) {
            Log.d(TAG, "Refresh action received");
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName componentName = new ComponentName(context, SimpleTaskWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
            
            for (int appWidgetId : appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId);
            }
        } else if (ACTION_TOGGLE_MODE.equals(action)) {
            Log.d(TAG, "Toggle mode action received");
            String mode = intent.getStringExtra("mode");
            if ("all".equals(mode)) {
                setShowMode(context, false);
            } else if ("today".equals(mode)) {
                setShowMode(context, true);
            } else {
                toggleShowMode(context); // Fallback to old behavior
            }
        } else if (ACTION_COMPLETE_TASK.equals(action)) {
            Log.d(TAG, "Complete task action received");
            int taskId = intent.getIntExtra("task_id", -1);
            Log.d(TAG, "Task ID from intent: " + taskId);
            Log.d(TAG, "Intent extras: " + intent.getExtras());
            if (taskId != -1) {
                completeTask(context, taskId);
            } else {
                Log.e(TAG, "No valid task ID found in intent");
            }
        } else if (ACTION_ADD_TASK.equals(action)) {
            Log.d(TAG, "Add task action received");
            openAppWithAddTask(context);
        }
    }
    
    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Log.d(TAG, "updateWidget called for ID: " + appWidgetId);
        
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            
            // Set up toggle buttons
            Intent toggleAllIntent = new Intent(context, SimpleTaskWidgetProvider.class);
            toggleAllIntent.setAction(ACTION_TOGGLE_MODE);
            toggleAllIntent.putExtra("mode", "all");
            PendingIntent toggleAllPendingIntent = PendingIntent.getBroadcast(context, 0, toggleAllIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_toggle_all, toggleAllPendingIntent);
            
            Intent toggleTodayIntent = new Intent(context, SimpleTaskWidgetProvider.class);
            toggleTodayIntent.setAction(ACTION_TOGGLE_MODE);
            toggleTodayIntent.putExtra("mode", "today");
            PendingIntent toggleTodayPendingIntent = PendingIntent.getBroadcast(context, 1, toggleTodayIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_toggle_today, toggleTodayPendingIntent);
            
            // Set up add task button
            Intent addTaskIntent = new Intent(context, SimpleTaskWidgetProvider.class);
            addTaskIntent.setAction(ACTION_ADD_TASK);
            PendingIntent addTaskPendingIntent = PendingIntent.getBroadcast(context, 4, addTaskIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_add_task, addTaskPendingIntent);
            Log.d(TAG, "Set add task button click");
            
            // Set up refresh button
            Intent refreshIntent = new Intent(context, SimpleTaskWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH);
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 2, refreshIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent);
            
            // Set up title click to open app
            Intent openAppIntent = new Intent(context, MainActivity.class);
            openAppIntent.putExtra("refresh_from_widget", true);
            PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 3, openAppIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent);
            Log.d(TAG, "Set title click to open app");
            
            // Update toggle button to show split state
            boolean showTodayOnly = isShowTodayOnly(context);
            
            // Use app language for consistency (same as "my tasks" headline)
            String allText = context.getString(limor.tal.mytodo.R.string.all_tasks);
            String todayText = context.getString(limor.tal.mytodo.R.string.today_tasks);
            
            // Set button texts
            views.setTextViewText(R.id.widget_toggle_all, allText);
            views.setTextViewText(R.id.widget_toggle_today, todayText);
            
            // Set button backgrounds based on active state
            if (showTodayOnly) {
                // Today is active - green background for today, gray for all
                views.setInt(R.id.widget_toggle_today, "setBackgroundResource", R.drawable.toggle_button_active);
                views.setInt(R.id.widget_toggle_all, "setBackgroundResource", R.drawable.toggle_button_inactive);
            } else {
                // All is active - green background for all, gray for today
                views.setInt(R.id.widget_toggle_all, "setBackgroundResource", R.drawable.toggle_button_active);
                views.setInt(R.id.widget_toggle_today, "setBackgroundResource", R.drawable.toggle_button_inactive);
            }
            
            // Update widget title
            String widgetTitle = context.getString(limor.tal.mytodo.R.string.my_tasks);
            views.setTextViewText(R.id.widget_title, widgetTitle);
            
            // Set up the ListView with RemoteViewsService for scrolling
            Log.d(TAG, "Setting up RemoteViewsService for widget ID: " + appWidgetId);
            
            Intent serviceIntent = new Intent(context, TaskWidgetService.class);
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            Log.d(TAG, "Created service intent with appWidgetId: " + appWidgetId);
            
            try {
                views.setRemoteAdapter(R.id.widget_tasks_container, serviceIntent);
                Log.d(TAG, "Successfully set remote adapter on widget_tasks_container");
                
                // Set up template intent for task completion
                // Individual RemoteViews items in ListView require template intent approach
                Intent templateIntent = new Intent(context, SimpleTaskWidgetProvider.class);
                templateIntent.setAction(ACTION_COMPLETE_TASK);
                // Don't put extras in template intent - they should come from fill-in intent
                Log.d(TAG, "Set pending intent template for task completion with action: " + ACTION_COMPLETE_TASK);
                Log.d(TAG, "Template intent details - action: " + templateIntent.getAction() + ", extras: " + templateIntent.getExtras() + ", component: " + templateIntent.getComponent());
                
                PendingIntent templatePendingIntent = PendingIntent.getBroadcast(context, 100, templateIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                views.setPendingIntentTemplate(R.id.widget_tasks_container, templatePendingIntent);
                Log.d(TAG, "Successfully set pending intent template for ListView");
                Log.d(TAG, "Template PendingIntent created with request code: 100");
                Log.d(TAG, "Template PendingIntent target package: " + templatePendingIntent.getTargetPackage());
                
                // Note: Cannot use both setPendingIntentTemplate and setOnClickPendingIntent on same view
                
                // Set empty view
                views.setEmptyView(R.id.widget_tasks_container, R.id.widget_empty_view);
                Log.d(TAG, "Set empty view for ListView");
            } catch (Exception e) {
                Log.e(TAG, "Error setting remote adapter", e);
                // Fallback to simple text
                RemoteViews errorView = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
                errorView.setTextViewText(android.R.id.text1, "Error setting adapter: " + e.getMessage());
                errorView.setTextColor(android.R.id.text1, 0xFFFFFFFF);
                views.addView(R.id.widget_tasks_container, errorView);
            }
            
            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
            Log.d(TAG, "Widget updated with RemoteViewsService setup");
            
            // Notify the RemoteViewsService that data has changed
            try {
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_tasks_container);
                Log.d(TAG, "Notified RemoteViewsService of data change for widget ID: " + appWidgetId);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying RemoteViewsService", e);
            }
            
            Log.d(TAG, "Widget updated successfully with scrollable list");
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget", e);
        }
    }
    
    private void setShowMode(Context context, boolean showTodayOnly) {
        SharedPreferences prefs = context.getSharedPreferences("MyToDoPrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_SHOW_TODAY_ONLY, showTodayOnly).apply();
        
        // Refresh all widgets
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, SimpleTaskWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
        
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }
    
    private void toggleShowMode(Context context) {
        boolean currentMode = isShowTodayOnly(context);
        setShowMode(context, !currentMode);
    }
    
    private boolean isShowTodayOnly(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("MyToDoPrefs", Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_SHOW_TODAY_ONLY, false);
    }
    
    private void completeTask(Context context, int taskId) {
        Log.d(TAG, "completeTask: Starting completion for task ID: " + taskId);
        
        // Run database operations on background thread
        new Thread(() -> {
            try {
                // Use direct database access
                AppDatabase database = AppDatabase.getDatabase(context);
                TaskDao taskDao = database.taskDao();
                Task task = taskDao.getTaskById(taskId);
                
                if (task != null) {
                    Log.d(TAG, "completeTask: Found task: " + task.description + ", current completed status: " + task.isCompleted + ", recurrenceType: " + task.recurrenceType);
                    
                    // Handle recurring task completion
                    if (task.isRecurring && TaskConstants.RECURRENCE_MONTHLY.equals(task.recurrenceType)) {
                        // For monthly tasks, update due date to next month and reset to waiting
                        Log.d(TAG, "completeTask: Handling monthly recurring task: " + task.description);
                        Calendar calendar = Calendar.getInstance();
                        if (task.dueDate != null) {
                            calendar.setTimeInMillis(task.dueDate);
                            calendar.add(Calendar.MONTH, 1);
                            task.dueDate = calendar.getTimeInMillis();
                        } else {
                            calendar.add(Calendar.MONTH, 1);
                            task.dueDate = calendar.getTimeInMillis();
                        }
                        task.dayOfWeek = TaskConstants.DAY_NONE; // Reset to waiting
                        task.isCompleted = false;
                        task.completionDate = null;
                        Log.d(TAG, "completeTask: Monthly task completed and moved to next month: " + task.description + ", new due date: " + task.dueDate);
                    } else if (task.isRecurring && TaskConstants.RECURRENCE_WEEKLY.equals(task.recurrenceType)) {
                        // For weekly tasks, update due date to next week and reset to waiting
                        Log.d(TAG, "completeTask: Handling weekly recurring task: " + task.description);
                        Calendar calendar = Calendar.getInstance();
                        if (task.dueDate != null) {
                            calendar.setTimeInMillis(task.dueDate);
                            calendar.add(Calendar.WEEK_OF_YEAR, 1);
                            task.dueDate = calendar.getTimeInMillis();
                        } else {
                            calendar.add(Calendar.WEEK_OF_YEAR, 1);
                            task.dueDate = calendar.getTimeInMillis();
                        }
                        task.dayOfWeek = TaskConstants.DAY_NONE; // Reset to waiting
                        task.isCompleted = false;
                        task.completionDate = null;
                        Log.d(TAG, "completeTask: Weekly task completed and moved to next week: " + task.description + ", new due date: " + task.dueDate);
                    } else if (task.isRecurring && TaskConstants.RECURRENCE_BIWEEKLY.equals(task.recurrenceType)) {
                        // For bi-weekly tasks, update due date to 2 weeks later and reset to waiting
                        Log.d(TAG, "completeTask: Handling bi-weekly recurring task: " + task.description);
                        Calendar calendar = Calendar.getInstance();
                        if (task.dueDate != null) {
                            calendar.setTimeInMillis(task.dueDate);
                            calendar.add(Calendar.WEEK_OF_YEAR, 2);
                            task.dueDate = calendar.getTimeInMillis();
                        } else {
                            calendar.add(Calendar.WEEK_OF_YEAR, 2);
                            task.dueDate = calendar.getTimeInMillis();
                        }
                        task.dayOfWeek = TaskConstants.DAY_NONE; // Reset to waiting
                        task.isCompleted = false;
                        task.completionDate = null;
                        Log.d(TAG, "completeTask: Bi-weekly task completed and moved to 2 weeks later: " + task.description + ", new due date: " + task.dueDate);
                    } else if (task.isRecurring && TaskConstants.RECURRENCE_YEARLY.equals(task.recurrenceType)) {
                        // For yearly tasks, update due date to next year and reset to waiting
                        Log.d(TAG, "completeTask: Handling yearly recurring task: " + task.description);
                        Calendar calendar = Calendar.getInstance();
                        if (task.dueDate != null) {
                            calendar.setTimeInMillis(task.dueDate);
                            calendar.add(Calendar.YEAR, 1);
                            task.dueDate = calendar.getTimeInMillis();
                        } else {
                            calendar.add(Calendar.YEAR, 1);
                            task.dueDate = calendar.getTimeInMillis();
                        }
                        task.dayOfWeek = TaskConstants.DAY_NONE; // Reset to waiting
                        task.isCompleted = false;
                        task.completionDate = null;
                        Log.d(TAG, "completeTask: Yearly task completed and moved to next year: " + task.description + ", new due date: " + task.dueDate);
                    } else {
                        // For non-recurring tasks, mark as completed
                        Log.d(TAG, "completeTask: Handling non-recurring task: " + task.description);
                        task.isCompleted = true;
                        task.completionDate = System.currentTimeMillis();
                    }
                    
                    taskDao.update(task);
                    Log.d(TAG, "completeTask: Task completed successfully: " + task.description);
                    
                    // If task has Firestore document ID, sync with FamilySync
                    if (task.firestoreDocumentId != null) {
                        Log.d(TAG, "completeTask: Task has Firestore ID, syncing with FamilySync: " + task.firestoreDocumentId);
                        try {
                            FirestoreService firestoreService = new FirestoreService();
                            firestoreService.updateTaskCompletionWithSync(task.firestoreDocumentId, true, new FirestoreService.FirestoreCallback() {
                                @Override
                                public void onSuccess(Object result) {
                                    Log.d(TAG, "completeTask: FamilySync sync successful for task: " + task.description);
                                }
                                
                                @Override
                                public void onError(String error) {
                                    Log.e(TAG, "completeTask: FamilySync sync failed for task: " + task.description + ", error: " + error);
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "completeTask: Error syncing with FamilySync: " + e.getMessage(), e);
                        }
                    } else {
                        Log.d(TAG, "completeTask: Task has no Firestore ID, skipping FamilySync sync: " + task.description);
                    }
                    
                    // Refresh all widgets on main thread
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                        ComponentName componentName = new ComponentName(context, SimpleTaskWidgetProvider.class);
                        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
                        
                        for (int appWidgetId : appWidgetIds) {
                            updateWidget(context, appWidgetManager, appWidgetId);
                        }
                        
        // Also notify the app to refresh its data
        Intent refreshIntent = new Intent("limor.tal.mytodo.REFRESH_TASKS");
        context.sendBroadcast(refreshIntent);
        Log.d(TAG, "completeTask: Sent refresh broadcast to app");
        
        // Force complete widget refresh to clear any cached checkbox states
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "completeTask: Forcing complete widget refresh to clear any cached states");
            AppWidgetManager delayedAppWidgetManager = AppWidgetManager.getInstance(context);
            int[] delayedAppWidgetIds = delayedAppWidgetManager.getAppWidgetIds(new ComponentName(context, SimpleTaskWidgetProvider.class));
            
            // Force complete widget refresh by calling updateWidget for each widget
            for (int appWidgetId : delayedAppWidgetIds) {
                Log.d(TAG, "completeTask: Forcing complete refresh for widget ID: " + appWidgetId);
                updateWidget(context, delayedAppWidgetManager, appWidgetId);
            }
        }, 300); // 300ms delay to ensure database update is complete
                    });
                } else {
                    Log.e(TAG, "completeTask: Task not found with ID: " + taskId);
                }
            } catch (Exception e) {
                Log.e(TAG, "completeTask: Error completing task: " + e.getMessage(), e);
            }
        }).start();
    }
    
    private void openAppWithAddTask(Context context) {
        Log.d(TAG, "openAppWithAddTask: Opening app with add task modal for current day");
        
        try {
            // Get current day in English
            java.util.Calendar today = java.util.Calendar.getInstance();
            String todayEnglishDay = TaskConstants.getEnglishDayName(today.get(java.util.Calendar.DAY_OF_WEEK));
            
            Log.d(TAG, "openAppWithAddTask: Current day: " + todayEnglishDay);
            
            // Create intent to open MainActivity with add task parameters
            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra("open_add_task", true);
            intent.putExtra("preset_day", todayEnglishDay);
            intent.putExtra("from_widget", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            context.startActivity(intent);
            Log.d(TAG, "openAppWithAddTask: Started MainActivity with add task intent");
            
        } catch (Exception e) {
            Log.e(TAG, "openAppWithAddTask: Error opening app with add task", e);
        }
    }
    
}