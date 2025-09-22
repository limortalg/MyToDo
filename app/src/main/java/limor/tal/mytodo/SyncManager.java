package limor.tal.mytodo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SyncManager {
    private static final String TAG = "SyncManager";
    private static final String PREFS_NAME = "MyToDoPrefs";
    private static final String PREF_LAST_SYNC = "last_sync_timestamp";
    private static final String PREF_FIRST_SYNC = "first_sync_completed";
    
    private Context context;
    private FirestoreService firestoreService;
    private TaskDao taskDao;
    private ExecutorService executorService;
    private SharedPreferences prefs;

    public interface SyncCallback {
        void onSyncComplete(boolean success, String message);
        void onSyncProgress(String message);
    }

    public SyncManager(Context context) {
        this.context = context;
        this.firestoreService = new FirestoreService();
        this.taskDao = AppDatabase.getDatabase(context).taskDao();
        this.executorService = Executors.newSingleThreadExecutor();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Main sync method - handles bidirectional sync
    public void syncTasks(SyncCallback callback) {
        if (!firestoreService.isUserAuthenticated()) {
            callback.onSyncComplete(false, "User not authenticated");
            return;
        }

        executorService.execute(() -> {
            try {
                callback.onSyncProgress("Starting sync...");
                
                // Check if this is the first sync
                boolean isFirstSync = !prefs.getBoolean(PREF_FIRST_SYNC, false);
                
                if (isFirstSync) {
                    callback.onSyncProgress("First sync - uploading local tasks...");
                    performFirstSync(callback);
                } else {
                    callback.onSyncProgress("Syncing changes...");
                    performIncrementalSync(callback);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Sync failed", e);
                callback.onSyncComplete(false, "Sync failed: " + e.getMessage());
            }
        });
    }

    // First sync - upload all local tasks to cloud
    private void performFirstSync(SyncCallback callback) {
        try {
            // Get all local tasks
            List<limor.tal.mytodo.Task> localTasks = taskDao.getAllTasksSync();
            Log.d(TAG, "First sync: Found " + localTasks.size() + " local tasks");
            
            if (localTasks.isEmpty()) {
                // No local tasks, just download from cloud
                downloadCloudTasks(callback);
            } else {
                // Upload local tasks to cloud
                firestoreService.batchSaveTasks(localTasks, new FirestoreService.FirestoreCallback() {
                    @Override
                    public void onSuccess(Object result) {
                        Log.d(TAG, "First sync: Local tasks uploaded successfully");
                        // Mark first sync as completed
                        prefs.edit()
                                .putBoolean(PREF_FIRST_SYNC, true)
                                .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                                .apply();
                        
                        callback.onSyncComplete(true, "First sync completed - " + localTasks.size() + " tasks uploaded");
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "First sync failed: " + error);
                        callback.onSyncComplete(false, "First sync failed: " + error);
                    }
                });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "First sync error", e);
            callback.onSyncComplete(false, "First sync error: " + e.getMessage());
        }
    }

    // Incremental sync - sync changes since last sync
    private void performIncrementalSync(SyncCallback callback) {
        try {
            long lastSyncTime = prefs.getLong(PREF_LAST_SYNC, 0);
            
            // For now, get all local tasks (we can optimize this later)
            List<limor.tal.mytodo.Task> localChanges = taskDao.getAllTasksSync();
            Log.d(TAG, "Incremental sync: Found " + localChanges.size() + " local tasks");
            
            // Download cloud tasks
            firestoreService.loadUserTasks(new FirestoreService.TasksCallback() {
                @Override
                public void onTasksLoaded(List<limor.tal.mytodo.Task> cloudTasks) {
                    Log.d(TAG, "Incremental sync: Downloaded " + cloudTasks.size() + " cloud tasks");
                    
                    // Merge local and cloud changes
                    mergeTasks(localChanges, cloudTasks, callback);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Failed to load cloud tasks: " + error);
                    callback.onSyncComplete(false, "Failed to load cloud tasks: " + error);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Incremental sync error", e);
            callback.onSyncComplete(false, "Incremental sync error: " + e.getMessage());
        }
    }

    // Merge local and cloud tasks
    private void mergeTasks(List<limor.tal.mytodo.Task> localChanges, List<limor.tal.mytodo.Task> cloudTasks, SyncCallback callback) {
        try {
            callback.onSyncProgress("Merging changes...");
            
            // Create maps for easier lookup using firestoreDocumentId
            Map<String, limor.tal.mytodo.Task> localMap = new HashMap<>();
            Map<String, limor.tal.mytodo.Task> cloudMap = new HashMap<>();

            for (limor.tal.mytodo.Task task : localChanges) {
                if (task.firestoreDocumentId != null) {
                    localMap.put(task.firestoreDocumentId, task);
                }
            }

            for (limor.tal.mytodo.Task task : cloudTasks) {
                if (task.firestoreDocumentId != null) {
                    cloudMap.put(task.firestoreDocumentId, task);
                }
            }
            
            List<limor.tal.mytodo.Task> tasksToUpdate = new ArrayList<>();
            List<limor.tal.mytodo.Task> tasksToInsert = new ArrayList<>();
            
            // Check for conflicts and new tasks
            for (limor.tal.mytodo.Task cloudTask : cloudTasks) {
                if (cloudTask.firestoreDocumentId != null && localMap.containsKey(cloudTask.firestoreDocumentId)) {
                    limor.tal.mytodo.Task localTask = localMap.get(cloudTask.firestoreDocumentId);
                    
                    // Simple conflict resolution: use the more recently updated task
                    if (cloudTask.completionDate != null && localTask.completionDate != null) {
                        if (cloudTask.completionDate > localTask.completionDate) {
                            tasksToUpdate.add(cloudTask);
                        } else {
                            tasksToUpdate.add(localTask);
                        }
                    } else {
                        tasksToUpdate.add(cloudTask); // Prefer cloud version
                    }
                } else {
                    // New task from cloud
                    tasksToInsert.add(cloudTask);
                }
            }
            
            // Upload local changes that aren't in cloud
            for (limor.tal.mytodo.Task localTask : localChanges) {
                if (localTask.firestoreDocumentId == null || !cloudMap.containsKey(localTask.firestoreDocumentId)) {
                    // This is a new local task, upload it
                    firestoreService.saveTask(localTask, new FirestoreService.FirestoreCallback() {
                        @Override
                        public void onSuccess(Object result) {
                            Log.d(TAG, "Uploaded new local task: " + localTask.description);
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Failed to upload local task: " + error);
                        }
                    });
                }
            }
            
            // Update local database with cloud changes
            updateLocalDatabase(tasksToUpdate, tasksToInsert, callback);
            
        } catch (Exception e) {
            Log.e(TAG, "Merge error", e);
            callback.onSyncComplete(false, "Merge error: " + e.getMessage());
        }
    }

    // Update local database with merged tasks
    private void updateLocalDatabase(List<limor.tal.mytodo.Task> tasksToUpdate, List<limor.tal.mytodo.Task> tasksToInsert, SyncCallback callback) {
        try {
            // Update existing tasks
            for (limor.tal.mytodo.Task task : tasksToUpdate) {
                taskDao.update(task);
            }
            
            // Insert new tasks
            for (limor.tal.mytodo.Task task : tasksToInsert) {
                taskDao.insert(task);
            }
            
            // Update last sync timestamp
            prefs.edit()
                    .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                    .apply();
            
            Log.d(TAG, "Local database updated: " + tasksToUpdate.size() + " updated, " + tasksToInsert.size() + " inserted");
            callback.onSyncComplete(true, "Sync completed - " + (tasksToUpdate.size() + tasksToInsert.size()) + " tasks synchronized");
            
        } catch (Exception e) {
            Log.e(TAG, "Database update error", e);
            callback.onSyncComplete(false, "Database update error: " + e.getMessage());
        }
    }

    // Download tasks from cloud (for first sync when no local tasks)
    private void downloadCloudTasks(SyncCallback callback) {
        firestoreService.loadUserTasks(new FirestoreService.TasksCallback() {
            @Override
            public void onTasksLoaded(List<limor.tal.mytodo.Task> tasks) {
                try {
                    // Insert all cloud tasks into local database
                    for (limor.tal.mytodo.Task task : tasks) {
                        taskDao.insert(task);
                    }
                    
                    // Mark first sync as completed
                    prefs.edit()
                            .putBoolean(PREF_FIRST_SYNC, true)
                            .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                            .apply();
                    
                    Log.d(TAG, "Downloaded " + tasks.size() + " tasks from cloud");
                    callback.onSyncComplete(true, "First sync completed - " + tasks.size() + " tasks downloaded");
                    
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save downloaded tasks", e);
                    callback.onSyncComplete(false, "Failed to save downloaded tasks: " + e.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to download cloud tasks: " + error);
                callback.onSyncComplete(false, "Failed to download cloud tasks: " + error);
            }
        });
    }

    // Check if sync is needed
    public boolean needsSync() {
        if (!firestoreService.isUserAuthenticated()) {
            return false;
        }
        
        long lastSync = prefs.getLong(PREF_LAST_SYNC, 0);
        long timeSinceLastSync = System.currentTimeMillis() - lastSync;
        
        // Sync if it's been more than 1 minute since last sync
        return timeSinceLastSync > 1 * 60 * 1000;
    }

    // Force sync (ignore time check)
    public void forceSync(SyncCallback callback) {
        syncTasks(callback);
    }

    // Cleanup
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
