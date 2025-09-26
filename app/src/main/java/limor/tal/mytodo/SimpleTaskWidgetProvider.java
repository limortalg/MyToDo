package limor.tal.mytodo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.RemoteViews;

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
            if (taskId != -1) {
                completeTask(context, taskId);
            }
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
        Log.d(TAG, "Completing task: " + taskId);
        
        try {
            // Use direct database access
            AppDatabase database = AppDatabase.getDatabase(context);
            TaskDao taskDao = database.taskDao();
            Task task = taskDao.getTaskById(taskId);
            
            if (task != null) {
                task.isCompleted = true;
                taskDao.update(task);
                Log.d(TAG, "Task completed successfully: " + task.description);
                
                // Refresh all widgets
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                ComponentName componentName = new ComponentName(context, SimpleTaskWidgetProvider.class);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
                
                for (int appWidgetId : appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId);
                }
            } else {
                Log.e(TAG, "Task not found: " + taskId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error completing task", e);
        }
    }
}