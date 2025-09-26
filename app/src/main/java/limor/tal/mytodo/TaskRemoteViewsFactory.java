package limor.tal.mytodo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating remote views for the scrollable task list widget
 */
public class TaskRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private static final String TAG = "TaskRemoteViewsFactory";
    private Context context;
    private List<Task> tasks;
    private boolean showTodayOnly;
    private static final String PREF_SHOW_TODAY_ONLY = "widget_show_today_only";

    public TaskRemoteViewsFactory(Context context, Intent intent) {
        this.context = context;
        this.tasks = new ArrayList<>();
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate: Factory created");
    }

    @Override
    public void onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged: Refreshing task data");
        
        try {
            // Get the current show mode preference
            SharedPreferences prefs = context.getSharedPreferences("MyToDoPrefs", Context.MODE_PRIVATE);
            showTodayOnly = prefs.getBoolean(PREF_SHOW_TODAY_ONLY, false);
            Log.d(TAG, "onDataSetChanged: showTodayOnly = " + showTodayOnly);
            
            // Get all tasks from database
            Log.d(TAG, "onDataSetChanged: Creating TaskRepository");
            TaskRepository repository = new TaskRepository((android.app.Application) context.getApplicationContext());
            Log.d(TAG, "onDataSetChanged: Calling getAllTasksSync()");
            List<Task> allTasks = repository.getAllTasksSync();
            
            Log.d(TAG, "onDataSetChanged: getAllTasksSync() returned " + (allTasks != null ? allTasks.size() : "null") + " tasks");
            
            tasks.clear();
            
            if (allTasks != null) {
                Log.d(TAG, "onDataSetChanged: Processing " + allTasks.size() + " tasks");
                int completedCount = 0;
                int todayFilteredCount = 0;
                int addedCount = 0;
                
                for (Task task : allTasks) {
                    Log.d(TAG, "onDataSetChanged: Processing task: " + task.description + " (completed: " + task.isCompleted + ", dayOfWeek: " + task.dayOfWeek + ")");
                    
                    if (!task.isCompleted) {
                        // Filter by today if toggle is enabled
                        if (showTodayOnly && !isTaskForToday(context, task)) {
                            todayFilteredCount++;
                            Log.d(TAG, "onDataSetChanged: Skipping task (not for today): " + task.description);
                            continue;
                        }
                        tasks.add(task);
                        addedCount++;
                        Log.d(TAG, "onDataSetChanged: Added task: " + task.description);
                    } else {
                        completedCount++;
                        Log.d(TAG, "onDataSetChanged: Skipping completed task: " + task.description);
                    }
                }
                
                Log.d(TAG, "onDataSetChanged: Summary - Total: " + allTasks.size() + 
                      ", Completed: " + completedCount + 
                      ", Today filtered: " + todayFilteredCount + 
                      ", Added: " + addedCount);
            } else {
                Log.e(TAG, "onDataSetChanged: getAllTasksSync() returned null!");
            }
            
            Log.d(TAG, "onDataSetChanged: Final tasks list size: " + tasks.size() + " (showTodayOnly: " + showTodayOnly + ")");
            
        } catch (Exception e) {
            Log.e(TAG, "onDataSetChanged: Error loading tasks", e);
            tasks.clear();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: Factory destroyed");
        tasks.clear();
    }

    @Override
    public int getCount() {
        int count = tasks.size();
        Log.d(TAG, "getCount: Returning " + count + " tasks");
        return count;
    }

    @Override
    public RemoteViews getViewAt(int position) {
        Log.d(TAG, "getViewAt: position=" + position + ", tasks.size()=" + tasks.size());
        
        if (position >= tasks.size()) {
            Log.w(TAG, "getViewAt: Invalid position " + position + " (tasks.size()=" + tasks.size() + ")");
            return null;
        }
        
        Task task = tasks.get(position);
        Log.d(TAG, "getViewAt: Creating view for task: " + task.description + " (ID: " + task.id + ")");
        return createTaskView(context, task);
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private RemoteViews createTaskView(Context context, Task task) {
        Log.d(TAG, "createTaskView: Creating view for task: " + task.description);
        
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_task_item);
        
        // Set task description with priority indicator
        String prioritySymbol = "";
        switch (task.priority) {
            case 1: prioritySymbol = "🔴 "; break; // Red for High
            case 2: prioritySymbol = "🟡 "; break; // Yellow for Medium  
            case 3: prioritySymbol = "🟢 "; break; // Green for Low
            default: prioritySymbol = "⚪ "; break; // Gray for None
        }
        
        String displayText = prioritySymbol + task.description;
        views.setTextViewText(R.id.widget_task_item, displayText);
        Log.d(TAG, "createTaskView: Set task text: " + displayText);
        
        // Set click intent for task completion
        Intent completeIntent = new Intent();
        completeIntent.setAction(SimpleTaskWidgetProvider.ACTION_COMPLETE_TASK);
        completeIntent.putExtra("task_id", (int) task.id);
        views.setOnClickFillInIntent(R.id.widget_task_item, completeIntent);
        Log.d(TAG, "createTaskView: Set click intent for task ID: " + task.id);
        
        Log.d(TAG, "createTaskView: Successfully created simple view for task: " + task.description);
        return views;
    }

    private boolean isTaskForToday(Context context, Task task) {
        try {
            // Get current day name in app's language
            String[] daysOfWeek = context.getResources().getStringArray(R.array.days_of_week);
            java.util.Calendar today = java.util.Calendar.getInstance();
            int todayDayOfWeek = today.get(java.util.Calendar.DAY_OF_WEEK);
            
            // Map Calendar.DAY_OF_WEEK to days_of_week array indices
            int dayIndex;
            switch (todayDayOfWeek) {
                case java.util.Calendar.SUNDAY: dayIndex = 3; break;
                case java.util.Calendar.MONDAY: dayIndex = 4; break;
                case java.util.Calendar.TUESDAY: dayIndex = 5; break;
                case java.util.Calendar.WEDNESDAY: dayIndex = 6; break;
                case java.util.Calendar.THURSDAY: dayIndex = 7; break;
                case java.util.Calendar.FRIDAY: dayIndex = 8; break;
                case java.util.Calendar.SATURDAY: dayIndex = 9; break;
                default: dayIndex = 8; break;
            }
            
            String todayDayName = daysOfWeek[dayIndex];
            
            // Check if task is for today
            if (task.dayOfWeek != null && task.dayOfWeek.equals(todayDayName)) {
                return true;
            }
            
            // Check if task is immediate/overdue
            if (task.dayOfWeek != null && (task.dayOfWeek.equals("מיידי") || task.dayOfWeek.equals("Immediate"))) {
                return true;
            }
            
            // Check if task has due date for today
            if (task.dueDate != null) {
                java.util.Calendar taskDate = java.util.Calendar.getInstance();
                taskDate.setTimeInMillis(task.dueDate);
                
                java.util.Calendar todayCal = java.util.Calendar.getInstance();
                return taskDate.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
                       taskDate.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR);
            }
            
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "isTaskForToday: Error checking if task is for today", e);
            return false;
        }
    }
}
