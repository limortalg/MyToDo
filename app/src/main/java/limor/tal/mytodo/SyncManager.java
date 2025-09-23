package limor.tal.mytodo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    
    // Test Firebase connection to diagnose API key issues
    public void testFirebaseConnection() {
        Log.d(TAG, "Testing Firebase connection...");
        if (!firestoreService.isUserAuthenticated()) {
            Log.e(TAG, "Firebase connection test failed: User not authenticated");
            return;
        }
        
        Log.d(TAG, "Firebase connection test: User is authenticated");
        Log.d(TAG, "Firebase connection test: Attempting to read from Firestore...");
        
        // Try a simple Firestore read to test the connection
        firestoreService.getAllTasksFromFirestore(new FirestoreService.FirestoreCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> tasks) {
                Log.d(TAG, "Firebase connection test: SUCCESS - Retrieved " + tasks.size() + " tasks from Firestore");
            }
            
            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Firebase connection test: FAILED - " + e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("API_KEY_SERVICE_BLOCKED")) {
                    Log.e(TAG, "Firebase connection test: API key is blocked - check Firebase console API restrictions");
                }
            }
        });
    }

    // Main sync method - handles bidirectional sync
    public void syncTasks(SyncCallback callback) {
        if (!firestoreService.isUserAuthenticated()) {
            callback.onSyncComplete(false, "User not authenticated");
            return;
        }
        
        // Test Firebase connection before proceeding with sync
        testFirebaseConnection();

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
            
            // Debug: Log all local task details
            for (limor.tal.mytodo.Task task : localTasks) {
                Log.d(TAG, "Local task - ID: " + task.id + ", Description: " + task.description + 
                      ", FirestoreID: " + (task.firestoreDocumentId != null ? task.firestoreDocumentId : "NULL"));
            }
            
            // Always download cloud tasks first, then merge
            firestoreService.loadUserTasks(new FirestoreService.TasksCallback() {
                @Override
                public void onTasksLoaded(List<limor.tal.mytodo.Task> cloudTasks) {
                    Log.d(TAG, "First sync: Downloaded " + cloudTasks.size() + " cloud tasks");
                    
                    // Debug: Log all cloud task details
                    for (limor.tal.mytodo.Task task : cloudTasks) {
                        Log.d(TAG, "Cloud task - ID: " + task.id + ", Description: " + task.description + 
                              ", FirestoreID: " + (task.firestoreDocumentId != null ? task.firestoreDocumentId : "NULL"));
                    }
                    
            // Upload local tasks that aren't in cloud
            final Set<Integer> uploadedTaskIds = new HashSet<>();
            for (limor.tal.mytodo.Task localTask : localTasks) {
                if (localTask.firestoreDocumentId == null) {
                    // Mark this task as being uploaded to prevent duplicate uploads
                    uploadedTaskIds.add(localTask.id);
                    
                    // This is a local-only task, upload it
                    firestoreService.saveTask(localTask, new FirestoreService.FirestoreCallback() {
                        @Override
                        public void onSuccess(Object result) {
                            Log.d(TAG, "Uploaded local task: " + localTask.description + " with ID: " + result);
                            // Update the local task with the firestoreDocumentId on background thread
                            localTask.firestoreDocumentId = (String) result;
                            executorService.execute(() -> {
                                taskDao.update(localTask);
                                Log.d(TAG, "Updated local task " + localTask.id + " with FirestoreID: " + localTask.firestoreDocumentId);
                            });
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Failed to upload local task: " + error);
                            // Remove from uploaded set on error so it can be retried
                            uploadedTaskIds.remove(localTask.id);
                        }
                    });
                }
            }
                    
                    // Merge cloud tasks with local tasks
                    mergeTasks(localTasks, cloudTasks, new SyncManager.SyncCallback() {
                        @Override
                        public void onSyncComplete(boolean success, String message) {
                            if (success) {
                                // Mark first sync as completed
                                prefs.edit()
                                        .putBoolean(PREF_FIRST_SYNC, true)
                                        .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                                        .apply();
                            }
                            callback.onSyncComplete(success, message);
                        }

                        @Override
                        public void onSyncProgress(String message) {
                            callback.onSyncProgress(message);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "First sync failed to load cloud tasks: " + error);
                    callback.onSyncComplete(false, "First sync failed: " + error);
                }
            });
            
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
            
            Log.d(TAG, "=== MERGE DEBUG START ===");
            Log.d(TAG, "Local changes: " + localChanges.size() + " tasks");
            Log.d(TAG, "Cloud tasks: " + cloudTasks.size() + " tasks");
            
            // Create maps for easier lookup using firestoreDocumentId
            Map<String, limor.tal.mytodo.Task> localMap = new HashMap<>();
            Map<String, limor.tal.mytodo.Task> cloudMap = new HashMap<>();

            for (limor.tal.mytodo.Task task : localChanges) {
                Log.d(TAG, "Local task for merge - ID: " + task.id + ", Description: " + task.description + 
                      ", FirestoreID: " + (task.firestoreDocumentId != null ? task.firestoreDocumentId : "NULL"));
                if (task.firestoreDocumentId != null) {
                    localMap.put(task.firestoreDocumentId, task);
                }
            }

            for (limor.tal.mytodo.Task task : cloudTasks) {
                Log.d(TAG, "Cloud task for merge - ID: " + task.id + ", Description: " + task.description + 
                      ", FirestoreID: " + (task.firestoreDocumentId != null ? task.firestoreDocumentId : "NULL"));
                if (task.firestoreDocumentId != null) {
                    cloudMap.put(task.firestoreDocumentId, task);
                }
            }
            
            Log.d(TAG, "Local map size: " + localMap.size());
            Log.d(TAG, "Cloud map size: " + cloudMap.size());
            
            List<limor.tal.mytodo.Task> tasksToUpdate = new ArrayList<>();
            List<limor.tal.mytodo.Task> tasksToInsert = new ArrayList<>();
            
            // Check for conflicts and new tasks
            for (limor.tal.mytodo.Task cloudTask : cloudTasks) {
                if (cloudTask.firestoreDocumentId != null && localMap.containsKey(cloudTask.firestoreDocumentId)) {
                    Log.d(TAG, "Found matching task: " + cloudTask.description + " (FirestoreID: " + cloudTask.firestoreDocumentId + ")");
                    limor.tal.mytodo.Task localTask = localMap.get(cloudTask.firestoreDocumentId);
                    
                    // Simple conflict resolution: use the more recently updated task
                    if (cloudTask.completionDate != null && localTask.completionDate != null) {
                        if (cloudTask.completionDate > localTask.completionDate) {
                            Log.d(TAG, "Using cloud version (newer completion): " + cloudTask.description);
                            tasksToUpdate.add(cloudTask);
                        } else {
                            Log.d(TAG, "Using local version (newer completion): " + localTask.description);
                            tasksToUpdate.add(localTask);
                        }
                    } else {
                        Log.d(TAG, "Using cloud version (default): " + cloudTask.description);
                        tasksToUpdate.add(cloudTask); // Prefer cloud version
                    }
                } else {
                    // New task from cloud
                    Log.d(TAG, "New cloud task to insert: " + cloudTask.description + " (FirestoreID: " + cloudTask.firestoreDocumentId + ")");
                    tasksToInsert.add(cloudTask);
                }
            }
            
            // Upload local changes that aren't in cloud
            final Set<Integer> uploadedTaskIds = new HashSet<>();
            for (limor.tal.mytodo.Task localTask : localChanges) {
                if (localTask.firestoreDocumentId == null || !cloudMap.containsKey(localTask.firestoreDocumentId)) {
                    // This is a new local task, upload it
                    Log.d(TAG, "Uploading local task: " + localTask.description + " (LocalID: " + localTask.id + 
                          ", FirestoreID: " + (localTask.firestoreDocumentId != null ? localTask.firestoreDocumentId : "NULL") + ")");
                    
                    // Mark this task as being uploaded to prevent duplicate uploads
                    uploadedTaskIds.add(localTask.id);
                    
                    firestoreService.saveTask(localTask, new FirestoreService.FirestoreCallback() {
                        @Override
                        public void onSuccess(Object result) {
                            Log.d(TAG, "Uploaded new local task: " + localTask.description + " with ID: " + result);
                            // Update the local task with the firestoreDocumentId on background thread
                            localTask.firestoreDocumentId = (String) result;
                            executorService.execute(() -> {
                                taskDao.update(localTask);
                                Log.d(TAG, "Updated local task " + localTask.id + " with FirestoreID: " + localTask.firestoreDocumentId);
                            });
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Failed to upload local task: " + error);
                            // Remove from uploaded set on error so it can be retried
                            uploadedTaskIds.remove(localTask.id);
                        }
                    });
                } else {
                    Log.d(TAG, "Skipping local task (already in cloud): " + localTask.description + " (FirestoreID: " + localTask.firestoreDocumentId + ")");
                }
            }
            
            // Update local database with cloud changes (run on background thread)
            executorService.execute(() -> updateLocalDatabase(tasksToUpdate, tasksToInsert, callback));
            
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
                // Run database operations on background thread
                executorService.execute(() -> {
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
                });
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
    
    // Reset first sync flag (useful for debugging)
    public void resetFirstSyncFlag() {
        prefs.edit().putBoolean(PREF_FIRST_SYNC, false).apply();
        Log.d(TAG, "First sync flag reset");
    }

    // Clear all local tasks and reset sync state (useful when starting fresh)
    public void clearLocalDataAndResetSync() {
        executorService.execute(() -> {
            try {
                // Clear all local tasks
                taskDao.deleteAllTasks();
                
                // Reset sync preferences
                prefs.edit()
                    .putBoolean(PREF_FIRST_SYNC, false)
                    .remove(PREF_LAST_SYNC)
                    .apply();
                
                Log.d(TAG, "Local data cleared and sync state reset");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing local data", e);
            }
        });
    }

    // Force download from cloud (ignore local data)
    public void forceDownloadFromCloud(SyncCallback callback) {
        if (!firestoreService.isUserAuthenticated()) {
            callback.onSyncComplete(false, "User not authenticated");
            return;
        }

        executorService.execute(() -> {
            try {
                callback.onSyncProgress("Downloading from cloud...");
                
                // Clear local data first
                taskDao.deleteAllTasks();
                
                // Download all tasks from cloud
                firestoreService.loadUserTasks(new FirestoreService.TasksCallback() {
                    @Override
                    public void onTasksLoaded(List<limor.tal.mytodo.Task> cloudTasks) {
                        executorService.execute(() -> {
                            try {
                                // Insert all cloud tasks into local database
                                for (limor.tal.mytodo.Task task : cloudTasks) {
                                    taskDao.insert(task);
                                }
                                
                                // Mark first sync as completed
                                prefs.edit()
                                        .putBoolean(PREF_FIRST_SYNC, true)
                                        .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                                        .apply();
                                
                                Log.d(TAG, "Downloaded " + cloudTasks.size() + " tasks from cloud");
                                callback.onSyncComplete(true, "Downloaded " + cloudTasks.size() + " tasks from cloud");
                                
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to save downloaded tasks", e);
                                callback.onSyncComplete(false, "Failed to save downloaded tasks: " + e.getMessage());
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Failed to download cloud tasks: " + error);
                        callback.onSyncComplete(false, "Failed to download cloud tasks: " + error);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Force download error", e);
                callback.onSyncComplete(false, "Force download error: " + e.getMessage());
            }
        });
    }

    // Cleanup
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
