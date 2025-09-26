package limor.tal.mytodo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.List;

/**
 * Simplified widget provider for displaying tasks on the home screen
 */
public class SimpleTaskWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "SimpleTaskWidget";
    public static final String ACTION_REFRESH = "limor.tal.mytodo.REFRESH_WIDGET";
    private static final String ACTION_OPEN_APP = "limor.tal.mytodo.OPEN_APP";
    private static final String ACTION_COMPLETE_TASK = "limor.tal.mytodo.COMPLETE_TASK";
    private static final String ACTION_TOGGLE_MODE = "limor.tal.mytodo.TOGGLE_MODE";
    
    // Widget preference keys
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
        
        if (ACTION_REFRESH.equals(action)) {
            Log.d(TAG, "Refresh action received");
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName componentName = new ComponentName(context, SimpleTaskWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
            onUpdate(context, appWidgetManager, appWidgetIds);
        } else if (ACTION_OPEN_APP.equals(action)) {
            Log.d(TAG, "Open app action received");
            Intent appIntent = new Intent(context, MainActivity.class);
            appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appIntent.putExtra("refresh_from_widget", true);
            context.startActivity(appIntent);
        } else if (ACTION_COMPLETE_TASK.equals(action)) {
            Log.d(TAG, "Complete Task action received");
            int taskId = intent.getIntExtra("task_id", -1);
            if (taskId != -1) {
                completeTask(context, taskId);
            }
        } else if (ACTION_TOGGLE_MODE.equals(action)) {
            Log.d(TAG, "Toggle mode action received");
            String mode = intent.getStringExtra("mode");
            if ("all".equals(mode)) {
                setShowMode(context, false); // Set to show all tasks
            } else if ("today".equals(mode)) {
                setShowMode(context, true); // Set to show today's tasks
            } else {
                toggleShowMode(context); // Fallback to old behavior
            }
        }
    }
    
    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Log.d(TAG, "updateWidget called for ID: " + appWidgetId);
        
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            
        // Set up toggle button with visual state
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
        
        // Note: RemoteViews doesn't support dynamic background changes
        // The toggle functionality works through click handling and text changes
        
        // Set up refresh button
        Intent refreshIntent = new Intent(context, SimpleTaskWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 0, refreshIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent);
            
        // Set up title click to open app
        Intent openAppIntent = new Intent(context, SimpleTaskWidgetProvider.class);
        openAppIntent.setAction(ACTION_OPEN_APP);
        PendingIntent openAppPendingIntent = PendingIntent.getBroadcast(context, 1, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent);
            
            // Clear existing content
            views.removeAllViews(R.id.widget_tasks_container);
            
            // Show loading message first
            RemoteViews loadingView = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
            loadingView.setTextViewText(android.R.id.text1, "Loading tasks...");
            loadingView.setTextColor(android.R.id.text1, 0xFFFFFFFF);
            views.addView(R.id.widget_tasks_container, loadingView);
            
            // Update widget with loading message
            appWidgetManager.updateAppWidget(appWidgetId, views);
            
            // Get tasks in background thread
            new Thread(() -> {
                try {
                    Log.d(TAG, "Attempting database access in background thread");
                    
                    // Use direct database access instead of repository
                    AppDatabase database = AppDatabase.getDatabase(context);
                    TaskDao taskDao = database.taskDao();
                    List<Task> allTasks = taskDao.getAllTasksSync();
                    
                    Log.d(TAG, "Found " + (allTasks != null ? allTasks.size() : 0) + " total tasks");
                    
                    if (allTasks == null) {
                        Log.e(TAG, "Database returned null tasks list");
                        return;
                    }
                    
                    if (allTasks.isEmpty()) {
                        Log.w(TAG, "Database returned empty tasks list - this might be why no tasks are shown");
                    }
                    
                    // Create new RemoteViews for the updated content
                    RemoteViews updatedViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
                    
                    // Set up toggle buttons again
                    Intent toggleAllIntent2 = new Intent(context, SimpleTaskWidgetProvider.class);
                    toggleAllIntent2.setAction(ACTION_TOGGLE_MODE);
                    toggleAllIntent2.putExtra("mode", "all");
                    PendingIntent toggleAllPendingIntent2 = PendingIntent.getBroadcast(context, 2, toggleAllIntent2, 
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    updatedViews.setOnClickPendingIntent(R.id.widget_toggle_all, toggleAllPendingIntent2);
                    
                    Intent toggleTodayIntent2 = new Intent(context, SimpleTaskWidgetProvider.class);
                    toggleTodayIntent2.setAction(ACTION_TOGGLE_MODE);
                    toggleTodayIntent2.putExtra("mode", "today");
                    PendingIntent toggleTodayPendingIntent2 = PendingIntent.getBroadcast(context, 3, toggleTodayIntent2, 
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    updatedViews.setOnClickPendingIntent(R.id.widget_toggle_today, toggleTodayPendingIntent2);
                    
                    // Update toggle button to show split state
                    boolean showTodayOnly2 = isShowTodayOnly(context);
                    
                    // Use app language for consistency
                    String allText2 = context.getString(limor.tal.mytodo.R.string.all_tasks);
                    String todayText2 = context.getString(limor.tal.mytodo.R.string.today_tasks);
                    
                    // Set button texts
                    updatedViews.setTextViewText(R.id.widget_toggle_all, allText2);
                    updatedViews.setTextViewText(R.id.widget_toggle_today, todayText2);
                    
                    // Set button backgrounds based on active state
                    if (showTodayOnly2) {
                        // Today is active - green background for today, gray for all
                        updatedViews.setInt(R.id.widget_toggle_today, "setBackgroundResource", R.drawable.toggle_button_active);
                        updatedViews.setInt(R.id.widget_toggle_all, "setBackgroundResource", R.drawable.toggle_button_inactive);
                    } else {
                        // All is active - green background for all, gray for today
                        updatedViews.setInt(R.id.widget_toggle_all, "setBackgroundResource", R.drawable.toggle_button_active);
                        updatedViews.setInt(R.id.widget_toggle_today, "setBackgroundResource", R.drawable.toggle_button_inactive);
                    }
                    
                    // Update widget title
                    String widgetTitle2 = context.getString(limor.tal.mytodo.R.string.my_tasks);
                    updatedViews.setTextViewText(R.id.widget_title, widgetTitle2);
                    
                    // Note: RemoteViews doesn't support dynamic background changes
                    // The toggle functionality works through click handling and text changes
                    
                    Intent refreshIntent2 = new Intent(context, SimpleTaskWidgetProvider.class);
                    refreshIntent2.setAction(ACTION_REFRESH);
                    PendingIntent refreshPendingIntent2 = PendingIntent.getBroadcast(context, 0, refreshIntent2, 
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    updatedViews.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent2);
                    
                    Intent openAppIntent2 = new Intent(context, SimpleTaskWidgetProvider.class);
                    openAppIntent2.setAction(ACTION_OPEN_APP);
                    PendingIntent openAppPendingIntent2 = PendingIntent.getBroadcast(context, 1, openAppIntent2, 
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    updatedViews.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent2);
                    
                    // Clear existing content
                    updatedViews.removeAllViews(R.id.widget_tasks_container);
                    
                    if (allTasks != null && !allTasks.isEmpty()) {
                        boolean showTodayOnlyBg = isShowTodayOnly(context);
                        int incompleteCount = 0;
                        
                        for (Task task : allTasks) {
                            Log.d(TAG, "Task: " + task.description + ", completed: " + task.isCompleted);
                            if (!task.isCompleted) {
                                // Filter by today if toggle is enabled
                                if (showTodayOnlyBg && !isTaskForToday(context, task)) {
                                    Log.d(TAG, "Skipping task (not for today): " + task.description);
                                    continue;
                                }
                                
                                incompleteCount++;
                                if (incompleteCount <= 5) { // Show max 5 tasks
                                    Log.d(TAG, "Creating view for incomplete task: " + task.description);
                                    RemoteViews taskView = createSimpleTaskView(context, task);
                                    updatedViews.addView(R.id.widget_tasks_container, taskView);
                                    Log.d(TAG, "Added task view to widget");
                                }
                            }
                        }
                        
                        Log.d(TAG, "Added " + incompleteCount + " incomplete tasks to widget");
                        
                        if (incompleteCount == 0) {
                            RemoteViews emptyView = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
                            
                            // Use app language for empty messages
                            String emptyMessage = showTodayOnlyBg ? 
                                context.getString(limor.tal.mytodo.R.string.no_tasks_today) : 
                                context.getString(limor.tal.mytodo.R.string.no_tasks);
                            emptyView.setTextViewText(android.R.id.text1, emptyMessage);
                            emptyView.setTextColor(android.R.id.text1, 0xFFFFFFFF);
                            updatedViews.addView(R.id.widget_tasks_container, emptyView);
                        }
                    } else {
                        Log.d(TAG, "No tasks found in database");
                        RemoteViews emptyView = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
                        
                        // Use app language for empty message
                        String emptyMessage = context.getString(limor.tal.mytodo.R.string.no_tasks);
                        emptyView.setTextViewText(android.R.id.text1, emptyMessage);
                        emptyView.setTextColor(android.R.id.text1, 0xFFFFFFFF);
                        updatedViews.addView(R.id.widget_tasks_container, emptyView);
                    }
                    
                    // Update the widget from background thread
                    appWidgetManager.updateAppWidget(appWidgetId, updatedViews);
                    Log.d(TAG, "Widget updated successfully from background thread");
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error getting tasks in background thread", e);
                    
                    // Update widget with error message
                    RemoteViews errorViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
                    
                    // Set up toggle buttons
                    Intent toggleAllIntent3 = new Intent(context, SimpleTaskWidgetProvider.class);
                    toggleAllIntent3.setAction(ACTION_TOGGLE_MODE);
                    toggleAllIntent3.putExtra("mode", "all");
                    PendingIntent toggleAllPendingIntent3 = PendingIntent.getBroadcast(context, 4, toggleAllIntent3, 
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    errorViews.setOnClickPendingIntent(R.id.widget_toggle_all, toggleAllPendingIntent3);
                    
                    Intent toggleTodayIntent3 = new Intent(context, SimpleTaskWidgetProvider.class);
                    toggleTodayIntent3.setAction(ACTION_TOGGLE_MODE);
                    toggleTodayIntent3.putExtra("mode", "today");
                    PendingIntent toggleTodayPendingIntent3 = PendingIntent.getBroadcast(context, 5, toggleTodayIntent3, 
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    errorViews.setOnClickPendingIntent(R.id.widget_toggle_today, toggleTodayPendingIntent3);
                    
                    // Update toggle button to show split state
                    boolean showTodayOnly3 = isShowTodayOnly(context);
                    
                    // Use app language for consistency
                    String allText3 = context.getString(limor.tal.mytodo.R.string.all_tasks);
                    String todayText3 = context.getString(limor.tal.mytodo.R.string.today_tasks);
                    
                    // Set button texts
                    errorViews.setTextViewText(R.id.widget_toggle_all, allText3);
                    errorViews.setTextViewText(R.id.widget_toggle_today, todayText3);
                    
                    // Set button backgrounds based on active state
                    if (showTodayOnly3) {
                        // Today is active - green background for today, gray for all
                        errorViews.setInt(R.id.widget_toggle_today, "setBackgroundResource", R.drawable.toggle_button_active);
                        errorViews.setInt(R.id.widget_toggle_all, "setBackgroundResource", R.drawable.toggle_button_inactive);
                    } else {
                        // All is active - green background for all, gray for today
                        errorViews.setInt(R.id.widget_toggle_all, "setBackgroundResource", R.drawable.toggle_button_active);
                        errorViews.setInt(R.id.widget_toggle_today, "setBackgroundResource", R.drawable.toggle_button_inactive);
                    }
                    
                    // Update widget title
                    String widgetTitle3 = context.getString(limor.tal.mytodo.R.string.my_tasks);
                    errorViews.setTextViewText(R.id.widget_title, widgetTitle3);
                    
                    // Note: RemoteViews doesn't support dynamic background changes
                    // The toggle functionality works through click handling and text changes
                    
                    Intent refreshIntent3 = new Intent(context, SimpleTaskWidgetProvider.class);
                    refreshIntent3.setAction(ACTION_REFRESH);
                    PendingIntent refreshPendingIntent3 = PendingIntent.getBroadcast(context, 0, refreshIntent3, 
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    errorViews.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent3);
                    
                    Intent openAppIntent3 = new Intent(context, SimpleTaskWidgetProvider.class);
                    openAppIntent3.setAction(ACTION_OPEN_APP);
                    PendingIntent openAppPendingIntent3 = PendingIntent.getBroadcast(context, 1, openAppIntent3, 
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    errorViews.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent3);
                    
                    // Show error message
                    RemoteViews errorView = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
                    String errorMsg = "Error: " + e.getMessage();
                    if (errorMsg.length() > 50) {
                        errorMsg = errorMsg.substring(0, 50) + "...";
                    }
                    errorView.setTextViewText(android.R.id.text1, errorMsg);
                    errorView.setTextColor(android.R.id.text1, 0xFFFFFFFF);
                    errorViews.addView(R.id.widget_tasks_container, errorView);
                    
                    appWidgetManager.updateAppWidget(appWidgetId, errorViews);
                }
            }).start();
            
            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
            Log.d(TAG, "Widget updated successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget", e);
        }
    }
    
    private RemoteViews createSimpleTaskView(Context context, Task task) {
        RemoteViews views = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
        
        String taskText = task.description != null ? task.description : "No description";
        views.setTextViewText(android.R.id.text1, "☐ " + taskText);
        views.setTextColor(android.R.id.text1, 0xFFFFFFFF);
        
        // Make the task clickable to complete it
        try {
            Intent completeIntent = new Intent(context, SimpleTaskWidgetProvider.class);
            completeIntent.setAction(ACTION_COMPLETE_TASK);
            completeIntent.putExtra("task_id", (int) task.id);
            // Use a unique request code based on task ID to avoid conflicts
            int requestCode = (int) (task.id + 1000);
            PendingIntent completePendingIntent = PendingIntent.getBroadcast(context, requestCode, completeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(android.R.id.text1, completePendingIntent);
            Log.d(TAG, "Set click listener for task: " + task.description + " (ID: " + task.id + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error setting click listener for task: " + task.description, e);
        }
        
        return views;
    }
    
    private void completeTask(Context context, int taskId) {
        Log.d(TAG, "Completing task with ID: " + taskId);
        
        // Run in background thread to avoid blocking
        new Thread(() -> {
            try {
                AppDatabase database = AppDatabase.getDatabase(context);
                TaskDao taskDao = database.taskDao();
                Task task = taskDao.getTaskById(taskId);
                
                if (task != null) {
                    task.isCompleted = true;
                    taskDao.update(task);
                    Log.d(TAG, "Task completed successfully: " + task.description);
                    
                    // Refresh all widgets after completing the task
                    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                    ComponentName componentName = new ComponentName(context, SimpleTaskWidgetProvider.class);
                    int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
                    for (int appWidgetId : appWidgetIds) {
                        updateWidget(context, appWidgetManager, appWidgetId);
                    }
                } else {
                    Log.e(TAG, "Task not found with ID: " + taskId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error completing task", e);
            }
        }).start();
    }
    
    private void setShowMode(Context context, boolean showTodayOnly) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_SHOW_TODAY_ONLY, showTodayOnly).apply();
        
        Log.d(TAG, "Show mode set to: " + (showTodayOnly ? "Today only" : "All tasks"));
        
        // Refresh all widgets
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, SimpleTaskWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }
    
    private void toggleShowMode(Context context) {
        Log.d(TAG, "Toggling show mode");
        
        // Get current preference
        android.content.SharedPreferences prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
        boolean showTodayOnly = prefs.getBoolean(PREF_SHOW_TODAY_ONLY, false);
        
        // Toggle the preference
        boolean newMode = !showTodayOnly;
        setShowMode(context, newMode);
    }
    
    private boolean isShowTodayOnly(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_SHOW_TODAY_ONLY, false); // Default to showing all tasks
    }
    
    private boolean isTaskForToday(Context context, Task task) {
        // Get current date and time
        java.util.Calendar today = java.util.Calendar.getInstance();
        long todayMillis = today.getTimeInMillis();
        
        // Get today's day of week (1=Sunday, ..., 7=Saturday)
        int todayDayOfWeek = today.get(java.util.Calendar.DAY_OF_WEEK);
        
        // Map Calendar.DAY_OF_WEEK to days_of_week array indices (same as TaskViewModel)
        // Calendar.DAY_OF_WEEK: 1=Sunday, 2=Monday, 3=Tuesday, 4=Wednesday, 5=Thursday, 6=Friday, 7=Saturday
        int todayIndex;
        switch (todayDayOfWeek) {
            case java.util.Calendar.SUNDAY: todayIndex = 3; break;    // Sunday -> index 3
            case java.util.Calendar.MONDAY: todayIndex = 4; break;    // Monday -> index 4
            case java.util.Calendar.TUESDAY: todayIndex = 5; break;   // Tuesday -> index 5
            case java.util.Calendar.WEDNESDAY: todayIndex = 6; break; // Wednesday -> index 6
            case java.util.Calendar.THURSDAY: todayIndex = 7; break;  // Thursday -> index 7
            case java.util.Calendar.FRIDAY: todayIndex = 8; break;    // Friday -> index 8
            case java.util.Calendar.SATURDAY: todayIndex = 9; break;  // Saturday -> index 9
            default: todayIndex = 8; break; // Default to Friday
        }
        
        // Get day names from the app's resources (same as TaskViewModel uses)
        String[] daysOfWeek = context.getResources().getStringArray(limor.tal.mytodo.R.array.days_of_week);
        String todayDayName = daysOfWeek[todayIndex];
        
        Log.d(TAG, "isTaskForToday: Today day name: " + todayDayName + " (index " + todayIndex + ")");
        
        Log.d(TAG, "isTaskForToday: Today is " + todayDayName + " (index " + todayIndex + ", dayOfWeek " + todayDayOfWeek + ")");
        Log.d(TAG, "isTaskForToday: Current time: " + today.getTime());
        Log.d(TAG, "isTaskForToday: Task " + task.description + " has dayOfWeek: " + task.dayOfWeek + ", dueDate: " + task.dueDate);
        
        // Check if task is categorized as "today" using the same logic as TaskViewModel
        
        // 1. Check if task has dayOfWeek set to today
        if (task.dayOfWeek != null) {
            // Map the stored dayOfWeek to current language for comparison
            String mappedDayOfWeek = mapDayOfWeekToCurrentLanguage(task.dayOfWeek, daysOfWeek);
            Log.d(TAG, "isTaskForToday: Task dayOfWeek: " + task.dayOfWeek + " -> mapped: " + mappedDayOfWeek + " vs today: " + todayDayName);
            
            if (mappedDayOfWeek.equals(todayDayName)) {
                Log.d(TAG, "isTaskForToday: Task matches today's day - returning true");
                return true;
            }
            
            // Check if task is set to "Immediate" (overdue)
            if (mappedDayOfWeek.equals("מיידי") || mappedDayOfWeek.equals("Immediate")) {
                Log.d(TAG, "isTaskForToday: Task is immediate - returning true");
                return true;
            }
        }
        
        // 2. Check if task has dueDate set to today
        if (task.dueDate != null) {
            java.util.Calendar dueDateCal = java.util.Calendar.getInstance();
            dueDateCal.setTimeInMillis(task.dueDate);
            int dueDayOfWeek = dueDateCal.get(java.util.Calendar.DAY_OF_WEEK);
            
            // Map due date day to array index
            int dueDayIndex;
            switch (dueDayOfWeek) {
                case java.util.Calendar.SUNDAY: dueDayIndex = 3; break;
                case java.util.Calendar.MONDAY: dueDayIndex = 4; break;
                case java.util.Calendar.TUESDAY: dueDayIndex = 5; break;
                case java.util.Calendar.WEDNESDAY: dueDayIndex = 6; break;
                case java.util.Calendar.THURSDAY: dueDayIndex = 7; break;
                case java.util.Calendar.FRIDAY: dueDayIndex = 8; break;
                case java.util.Calendar.SATURDAY: dueDayIndex = 9; break;
                default: dueDayIndex = 8; break;
            }
            
            Log.d(TAG, "isTaskForToday: Due date day index: " + dueDayIndex + " vs today index: " + todayIndex);
            
            if (dueDayIndex == todayIndex) {
                Log.d(TAG, "isTaskForToday: Task due date matches today - returning true");
                return true;
            }
            
            // Check if task is overdue (past due date)
            if (task.dueDate < todayMillis) {
                Log.d(TAG, "isTaskForToday: Task is overdue - returning true");
                return true; // Overdue tasks are considered "today"
            }
        }
        
        Log.d(TAG, "isTaskForToday: Task does not match today - returning false");
        return false;
    }
    
    private String mapDayOfWeekToCurrentLanguage(String storedDayOfWeek, String[] currentDaysOfWeek) {
        try {
            if (storedDayOfWeek == null) return null;
            if (currentDaysOfWeek == null || currentDaysOfWeek.length < 10) {
                Log.e(TAG, "mapDayOfWeekToCurrentLanguage: currentDaysOfWeek array is invalid");
                return storedDayOfWeek;
            }
            
            // Handle special categories
            if ("None".equals(storedDayOfWeek) || "ללא".equals(storedDayOfWeek)) {
                return currentDaysOfWeek[0];
            }
            if ("Immediate".equals(storedDayOfWeek) || "מיידי".equals(storedDayOfWeek)) {
                return currentDaysOfWeek[1];
            }
            if ("Soon".equals(storedDayOfWeek) || "בקרוב".equals(storedDayOfWeek)) {
                return currentDaysOfWeek[2];
            }
            
            // Handle Hebrew day names - map to current language
            String[] hebrewDays = {"ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"};
            int[] hebrewIndices = {3, 4, 5, 6, 7, 8, 9}; // Corresponding indices in days_of_week array
            
            for (int i = 0; i < hebrewDays.length; i++) {
                if (hebrewDays[i].equals(storedDayOfWeek)) {
                    return currentDaysOfWeek[hebrewIndices[i]];
                }
            }
            
            // Handle English day names - map to current language
            String[] englishDays = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            int[] englishIndices = {3, 4, 5, 6, 7, 8, 9}; // Corresponding indices in days_of_week array
            
            for (int i = 0; i < englishDays.length; i++) {
                if (englishDays[i].equals(storedDayOfWeek)) {
                    return currentDaysOfWeek[englishIndices[i]];
                }
            }
            
            return storedDayOfWeek;
        } catch (Exception e) {
            Log.e(TAG, "mapDayOfWeekToCurrentLanguage: Error mapping day of week", e);
            return storedDayOfWeek;
        }
    }
    
    
    @Override
    public void onEnabled(Context context) {
        Log.d(TAG, "Widget enabled");
    }
    
    @Override
    public void onDisabled(Context context) {
        Log.d(TAG, "Widget disabled");
    }
}
