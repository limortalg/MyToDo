package limor.tal.mytodo;

import android.content.Context;
import android.util.Log;
import java.util.List;

/**
 * Utility class for migrating existing tasks from Hebrew to English database values.
 * This should be run once when the app is updated to the new English-only system.
 */
public class TaskMigrationUtils {
    private static final String TAG = "TaskMigrationUtils";
    private static final String MIGRATION_KEY = "task_migration_to_english_completed";
    
    /**
     * Check if migration has already been completed
     */
    public static boolean isMigrationCompleted(Context context) {
        return context.getSharedPreferences("MyToDoPrefs", Context.MODE_PRIVATE)
                .getBoolean(MIGRATION_KEY, false);
    }
    
    /**
     * Mark migration as completed
     */
    public static void markMigrationCompleted(Context context) {
        context.getSharedPreferences("MyToDoPrefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(MIGRATION_KEY, true)
                .apply();
        Log.d(TAG, "Migration marked as completed");
    }
    
    /**
     * Migrate all tasks from Hebrew to English values
     */
    public static void migrateTasksToEnglish(Context context) {
        if (isMigrationCompleted(context)) {
            Log.d(TAG, "Migration already completed, skipping");
            return;
        }
        
        Log.d(TAG, "Starting migration of tasks to English values");
        
        try {
            TaskRepository repository = new TaskRepository((android.app.Application) context.getApplicationContext());
            List<Task> allTasks = repository.getAllTasksSync();
            
            if (allTasks == null || allTasks.isEmpty()) {
                Log.d(TAG, "No tasks to migrate");
                markMigrationCompleted(context);
                return;
            }
            
            int migratedCount = 0;
            boolean hasChanges = false;
            
            for (Task task : allTasks) {
                boolean taskChanged = false;
                
                // Migrate dayOfWeek
                if (task.dayOfWeek != null) {
                    String englishDayName = TaskTranslationUtils.convertHebrewToEnglishDayName(task.dayOfWeek);
                    if (!englishDayName.equals(task.dayOfWeek)) {
                        Log.d(TAG, "Migrating task " + task.id + " dayOfWeek: " + task.dayOfWeek + " -> " + englishDayName);
                        task.dayOfWeek = englishDayName;
                        taskChanged = true;
                    }
                }
                
                // Migrate recurrenceType
                if (task.recurrenceType != null) {
                    String englishRecurrenceType = TaskTranslationUtils.convertHebrewToEnglishRecurrenceType(task.recurrenceType);
                    if (!englishRecurrenceType.equals(task.recurrenceType)) {
                        Log.d(TAG, "Migrating task " + task.id + " recurrenceType: " + task.recurrenceType + " -> " + englishRecurrenceType);
                        task.recurrenceType = englishRecurrenceType;
                        taskChanged = true;
                    }
                }
                
                if (taskChanged) {
                    repository.update(task);
                    migratedCount++;
                    hasChanges = true;
                }
            }
            
            if (hasChanges) {
                Log.d(TAG, "Migration completed: " + migratedCount + " tasks migrated to English values");
            } else {
                Log.d(TAG, "Migration completed: No tasks needed migration");
            }
            
            markMigrationCompleted(context);
            
        } catch (Exception e) {
            Log.e(TAG, "Error during migration", e);
            // Don't mark as completed if there was an error
        }
    }
    
    /**
     * Run migration in background thread
     */
    public static void migrateTasksToEnglishAsync(Context context) {
        Log.d(TAG, "Starting async migration to English values");
        new Thread(() -> {
            migrateTasksToEnglish(context);
        }).start();
    }
}
