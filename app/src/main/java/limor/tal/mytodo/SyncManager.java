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
    private volatile boolean isSyncing = false;

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
        if (!firestoreService.isUserAuthenticated()) {
            Log.e(TAG, "Firebase connection test failed: User not authenticated");
            return;
        }
        
        // Try a simple Firestore read to test the connection
        firestoreService.loadUserTasks(new FirestoreService.TasksCallback() {
            @Override
            public void onTasksLoaded(List<Task> tasks) {
                Log.d(TAG, "Firebase connection test: SUCCESS - Retrieved " + tasks.size() + " tasks from Firestore");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Firebase connection test: FAILED - " + error);
                if (error != null && error.contains("API_KEY_SERVICE_BLOCKED")) {
                    Log.e(TAG, "Firebase connection test: API key is blocked - check Firebase console API restrictions");
                }
            }
        });
    }

    // Main sync method - handles bidirectional sync
    public void syncTasks(SyncCallback callback) {
        Log.w(TAG, "SYNC DEBUG: syncTasks called - isSyncing: " + isSyncing);
        
        if (isSyncing) {
            Log.w(TAG, "SYNC DEBUG: Sync already in progress, ignoring request");
            callback.onSyncComplete(false, "Sync already in progress");
            return;
        }
        
        if (!firestoreService.isUserAuthenticated()) {
            Log.d(TAG, "syncTasks: User not authenticated, aborting sync");
            callback.onSyncComplete(false, "User not authenticated");
            return;
        }
        
        isSyncing = true;
        Log.w(TAG, "SYNC DEBUG: Starting sync - timestamp: " + System.currentTimeMillis());
        
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
            } finally {
                isSyncing = false;
                Log.w(TAG, "SYNC DEBUG: Sync completed, isSyncing set to false");
            }
        });
    }

    // First sync - download cloud tasks and merge with local tasks
    private void performFirstSync(SyncCallback callback) {
        try {
            // Get all local tasks
            List<limor.tal.mytodo.Task> localTasks = taskDao.getAllTasksSync();
            Log.d(TAG, "First sync: Found " + localTasks.size() + " local tasks");
            
            // Download cloud tasks and merge with local tasks
            firestoreService.loadUserTasks(new FirestoreService.TasksCallback() {
                @Override
                public void onTasksLoaded(List<limor.tal.mytodo.Task> cloudTasks) {
                    Log.d(TAG, "First sync: Downloaded " + cloudTasks.size() + " cloud tasks");
                    
                    // Merge cloud tasks with local tasks
                    mergeTasks(localTasks, cloudTasks, new SyncManager.SyncCallback() {
                        @Override
                        public void onSyncComplete(boolean success, String message) {
                            Log.w(TAG, "SYNC DEBUG: First sync callback completed - success: " + success + ", message: " + message);
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
            // Log.d(TAG, "Incremental sync: Found " + localChanges.size() + " local tasks");
            
            
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

            Log.w(TAG, "DUPLICATION DEBUG: Building local map from " + localChanges.size() + " local tasks");
            for (limor.tal.mytodo.Task task : localChanges) {
                if (task.firestoreDocumentId != null) {
                    localMap.put(task.firestoreDocumentId, task);
                    Log.w(TAG, "DUPLICATION DEBUG: Added to local map: " + task.description + 
                          " (LocalID: " + task.id + ", FirestoreID: " + task.firestoreDocumentId + ")");
                } else {
                    Log.w(TAG, "DUPLICATION DEBUG: Local task has no FirestoreID: " + task.description + 
                          " (LocalID: " + task.id + ")");
                }
            }

            Log.w(TAG, "DUPLICATION DEBUG: Building cloud map from " + cloudTasks.size() + " cloud tasks");
            for (limor.tal.mytodo.Task task : cloudTasks) {
                if (task.firestoreDocumentId != null) {
                    cloudMap.put(task.firestoreDocumentId, task);
                    Log.w(TAG, "DUPLICATION DEBUG: Added to cloud map: " + task.description + 
                          " (CloudID: " + task.id + ", FirestoreID: " + task.firestoreDocumentId + ")");
                } else {
                    Log.w(TAG, "DUPLICATION DEBUG: Cloud task has no FirestoreID: " + task.description + 
                          " (CloudID: " + task.id + ")");
                }
            }
            
            List<limor.tal.mytodo.Task> tasksToUpdate = new ArrayList<>();
            List<limor.tal.mytodo.Task> tasksToInsert = new ArrayList<>();
            
            // Check for conflicts and new tasks
            for (limor.tal.mytodo.Task cloudTask : cloudTasks) {
                Log.w(TAG, "SYNC DEBUG: Processing cloud task: " + cloudTask.description + 
                      " (FirestoreID: " + cloudTask.firestoreDocumentId + 
                      ", deletedAt: " + cloudTask.deletedAt + 
                      ", isDeleted: " + (cloudTask.deletedAt != null && cloudTask.deletedAt > 0) + ")");
                
                // Skip soft-deleted cloud tasks
                if (cloudTask.deletedAt != null && cloudTask.deletedAt > 0) {
                    Log.w(TAG, "SYNC DEBUG: Skipping soft-deleted cloud task: " + cloudTask.description + 
                          " (FirestoreID: " + cloudTask.firestoreDocumentId + ", deletedAt: " + cloudTask.deletedAt + ")");
                    continue;
                }
                
                if (cloudTask.firestoreDocumentId != null && localMap.containsKey(cloudTask.firestoreDocumentId)) {
                    Log.d(TAG, "SYNC DEBUG: Found matching local task: " + cloudTask.description + " (FirestoreID: " + cloudTask.firestoreDocumentId + ")");
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
                        Log.w(TAG, "SYNC DEBUG: Using cloud version (default): " + cloudTask.description + 
                              " (FirestoreID: " + cloudTask.firestoreDocumentId + ")");
                        tasksToUpdate.add(cloudTask); // Prefer cloud version
                    }
                } else {
                    // New task from cloud - check if this is first sync (no local tasks)
                    if (localChanges.isEmpty()) {
                        Log.w(TAG, "SYNC DEBUG: First sync - inserting cloud task: " + cloudTask.description + 
                              " (FirestoreID: " + cloudTask.firestoreDocumentId + ")");
                        tasksToInsert.add(cloudTask);
                    } else {
                        Log.w(TAG, "SYNC DEBUG: New cloud task to insert: " + cloudTask.description + 
                              " (FirestoreID: " + cloudTask.firestoreDocumentId + 
                              ", deletedAt: " + cloudTask.deletedAt + ")");
                        tasksToInsert.add(cloudTask);
                    }
                }
            }
            
            // Upload local changes that aren't in cloud
            final Set<Integer> uploadedTaskIds = new HashSet<>();
            for (limor.tal.mytodo.Task localTask : localChanges) {
                Log.d(TAG, "UPLOAD DEBUG: Checking local task: " + localTask.description + 
                      " (LocalID: " + localTask.id + 
                      ", FirestoreID: " + (localTask.firestoreDocumentId != null ? localTask.firestoreDocumentId : "NULL") + 
                      ", updatedAt: " + localTask.updatedAt + 
                      ", deletedAt: " + localTask.deletedAt + 
                      ", isDeleted: " + (localTask.deletedAt != null && localTask.deletedAt > 0) + ")");
                
                // Skip soft-deleted tasks
                if (localTask.deletedAt != null && localTask.deletedAt > 0) {
                    Log.w(TAG, "UPLOAD DEBUG: Skipping soft-deleted local task: " + localTask.description + 
                          " (LocalID: " + localTask.id + ", deletedAt: " + localTask.deletedAt + ")");
                    continue;
                }
                
                // Check if already being uploaded to prevent duplicates
                if (uploadedTaskIds.contains(localTask.id)) {
                    Log.w(TAG, "UPLOAD DEBUG: Task already being uploaded, skipping: " + localTask.description + " (LocalID: " + localTask.id + ")");
                    continue;
                }
                
                // Check if this task is already in cloud
                boolean foundInCloud = false;
                if (localTask.firestoreDocumentId != null && cloudMap.containsKey(localTask.firestoreDocumentId)) {
                    foundInCloud = true;
                }
                
                if (!foundInCloud) {
                    // This is a new local task, upload it
                    Log.w(TAG, "UPLOAD DEBUG: Uploading local task: " + localTask.description + 
                          " (LocalID: " + localTask.id + 
                          ", FirestoreID: " + (localTask.firestoreDocumentId != null ? localTask.firestoreDocumentId : "NULL") + 
                          ", Reason: Not found in cloud" + 
                          ", updatedAt: " + localTask.updatedAt + ")");
                    
                    // Mark this task as being uploaded to prevent duplicate uploads
                    uploadedTaskIds.add(localTask.id);
                    
                    firestoreService.saveTask(localTask, new FirestoreService.FirestoreCallback() {
                        @Override
                        public void onSuccess(Object result) {
                            Log.d(TAG, "UPLOAD DEBUG: Successfully uploaded task: " + localTask.description + 
                                  " (LocalID: " + localTask.id + 
                                  ", New FirestoreID: " + result + ")");
                            // Update the local task with the firestoreDocumentId on background thread
                            localTask.firestoreDocumentId = (String) result;
                            executorService.execute(() -> {
                                taskDao.update(localTask);
                                Log.d(TAG, "UPLOAD DEBUG: Updated local task " + localTask.id + " with FirestoreID: " + localTask.firestoreDocumentId);
                            });
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "UPLOAD DEBUG: Failed to upload local task: " + localTask.description + " (LocalID: " + localTask.id + ") - " + error);
                            // Remove from uploaded set on error so it can be retried
                            uploadedTaskIds.remove(localTask.id);
                        }
                    });
                } else {
                    Log.d(TAG, "UPLOAD DEBUG: Skipping local task (already in cloud): " + localTask.description + 
                          " (LocalID: " + localTask.id + 
                          ", FirestoreID: " + localTask.firestoreDocumentId + ")");
                }
            }
            
            // Upload local changes for existing tasks that have been modified
            for (limor.tal.mytodo.Task localTask : localChanges) {
                if (localTask.firestoreDocumentId != null && cloudMap.containsKey(localTask.firestoreDocumentId)) {
                    // Skip soft-deleted tasks
                    if (localTask.deletedAt != null && localTask.deletedAt > 0) {
                        Log.w(TAG, "UPLOAD DEBUG: Skipping soft-deleted local task (modified): " + localTask.description + 
                              " (LocalID: " + localTask.id + ", deletedAt: " + localTask.deletedAt + ")");
                        continue;
                    }
                    
                    // This is an existing task, check if local version is newer
                    limor.tal.mytodo.Task cloudTask = cloudMap.get(localTask.firestoreDocumentId);
                    long localUpdatedAt = localTask.updatedAt != null ? localTask.updatedAt : 0;
                    long cloudUpdatedAt = cloudTask.updatedAt != null ? cloudTask.updatedAt : 0;
                    
                    if (localUpdatedAt > cloudUpdatedAt) {
                        Log.w(TAG, "UPLOAD DEBUG: Uploading modified local task: " + localTask.description + 
                              " (LocalID: " + localTask.id + 
                              ", FirestoreID: " + localTask.firestoreDocumentId + 
                              ", local updatedAt: " + localUpdatedAt + ", cloud updatedAt: " + cloudUpdatedAt + ")");
                        
                        firestoreService.saveTask(localTask, new FirestoreService.FirestoreCallback() {
                            @Override
                            public void onSuccess(Object result) {
                                Log.d(TAG, "Uploaded modified local task: " + localTask.description);
                            }

                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Failed to upload modified local task: " + error);
                            }
                        });
                    }
                }
            }
            
            // Find tasks to delete (local tasks that are no longer in cloud)
            List<limor.tal.mytodo.Task> tasksToDelete = new ArrayList<>();
            for (limor.tal.mytodo.Task localTask : localChanges) {
                if (localTask.firestoreDocumentId != null && !cloudMap.containsKey(localTask.firestoreDocumentId)) {
                    Log.w(TAG, "DELETION DEBUG: Task to delete (no longer in cloud): " + localTask.description + 
                          " (LocalID: " + localTask.id + ", FirestoreID: " + localTask.firestoreDocumentId + ")");
                    tasksToDelete.add(localTask);
                }
            }
            
            Log.w(TAG, "DELETION DEBUG: Found " + tasksToDelete.size() + " tasks to delete from local database");
            // Log.d(TAG, "Found " + tasksToDelete.size() + " tasks to delete - timestamp: " + System.currentTimeMillis());
            
            // Update local database with cloud changes (run on background thread)
            executorService.execute(() -> updateLocalDatabase(tasksToUpdate, tasksToInsert, tasksToDelete, callback));
            
        } catch (Exception e) {
            Log.e(TAG, "Merge error", e);
            callback.onSyncComplete(false, "Merge error: " + e.getMessage());
        }
    }

    // Update local database with merged tasks
    private void updateLocalDatabase(List<limor.tal.mytodo.Task> tasksToUpdate, List<limor.tal.mytodo.Task> tasksToInsert, List<limor.tal.mytodo.Task> tasksToDelete, SyncCallback callback) {
        try {
            // Get all local tasks once at the beginning
            List<limor.tal.mytodo.Task> allLocalTasks = taskDao.getAllTasksSync();
            Log.w(TAG, "DUPLICATION DEBUG: Starting updateLocalDatabase with " + allLocalTasks.size() + " existing local tasks");
            
            // Update existing tasks - find local task by firestoreDocumentId and update it
            for (limor.tal.mytodo.Task cloudTask : tasksToUpdate) {
                // Find the local task with this firestoreDocumentId
                limor.tal.mytodo.Task localTaskToUpdate = null;
                
                Log.d(TAG, "DUPLICATION DEBUG: Looking for local task with firestoreDocumentId: " + cloudTask.firestoreDocumentId + " for cloud task: " + cloudTask.description);
                
                for (limor.tal.mytodo.Task localTask : allLocalTasks) {
                    if (cloudTask.firestoreDocumentId != null && cloudTask.firestoreDocumentId.equals(localTask.firestoreDocumentId)) {
                        localTaskToUpdate = localTask;
                        Log.d(TAG, "DUPLICATION DEBUG: Found matching local task: " + localTask.description + " (ID: " + localTask.id + ")");
                        break;
                    }
                }
                
                if (localTaskToUpdate != null) {
                    // Smart merge: use the newer version for each field based on updatedAt timestamp
                    long localUpdatedAt = localTaskToUpdate.updatedAt != null ? localTaskToUpdate.updatedAt : 0;
                    long cloudUpdatedAt = cloudTask.updatedAt != null ? cloudTask.updatedAt : 0;
                    
                    // Use cloud data if it's newer, otherwise keep local data
                    if (cloudUpdatedAt > localUpdatedAt) {
                        Log.d(TAG, "DUPLICATION DEBUG: Using cloud data (newer) for task: " + localTaskToUpdate.description + " (reminderOffset: " + cloudTask.reminderOffset + ")");
                        localTaskToUpdate.description = cloudTask.description;
                        localTaskToUpdate.dueDate = cloudTask.dueDate;
                        localTaskToUpdate.dueTime = cloudTask.dueTime;
                        localTaskToUpdate.dayOfWeek = cloudTask.dayOfWeek;
                        localTaskToUpdate.isRecurring = cloudTask.isRecurring;
                        localTaskToUpdate.recurrenceType = cloudTask.recurrenceType;
                        localTaskToUpdate.isCompleted = cloudTask.isCompleted;
                        localTaskToUpdate.priority = cloudTask.priority;
                        localTaskToUpdate.completionDate = cloudTask.completionDate;
                        localTaskToUpdate.reminderOffset = cloudTask.reminderOffset;
                        localTaskToUpdate.reminderDays = cloudTask.reminderDays;
                        localTaskToUpdate.manualPosition = cloudTask.manualPosition;
                        localTaskToUpdate.updatedAt = cloudTask.updatedAt;
                    } else {
                        Log.d(TAG, "DUPLICATION DEBUG: Keeping local data (newer or same) for task: " + localTaskToUpdate.description + " (reminderOffset: " + localTaskToUpdate.reminderOffset + ")");
                        // Keep all local data, but update the firestoreDocumentId if it was missing
                        if (localTaskToUpdate.firestoreDocumentId == null) {
                            localTaskToUpdate.firestoreDocumentId = cloudTask.firestoreDocumentId;
                        }
                    }
                    
                    taskDao.update(localTaskToUpdate);
                } else {
                    // This is a cloud task that should be updated but no local match found
                    // This can happen in first sync when there are no local tasks yet
                    // Move it to insert list instead of inserting it here
                    Log.w(TAG, "DUPLICATION DEBUG: Cloud task in update list but no local match found, moving to insert: " + cloudTask.description + " (FirestoreID: " + cloudTask.firestoreDocumentId + ")");
                    tasksToInsert.add(cloudTask);
                }
            }
            
            // Insert new tasks - but first check if they already exist
            for (limor.tal.mytodo.Task task : tasksToInsert) {
                // Check if a task with this firestoreDocumentId already exists
                boolean alreadyExists = false;
                if (task.firestoreDocumentId != null) {
                    for (limor.tal.mytodo.Task existingTask : allLocalTasks) {
                        if (task.firestoreDocumentId.equals(existingTask.firestoreDocumentId)) {
                            alreadyExists = true;
                            Log.w(TAG, "DUPLICATION DEBUG: Task already exists, skipping insert: " + task.description + 
                                  " (FirestoreID: " + task.firestoreDocumentId + 
                                  ", Existing LocalID: " + existingTask.id + ")");
                            break;
                        }
                    }
                }
                
                if (!alreadyExists) {
                    Log.w(TAG, "DUPLICATION DEBUG: Inserting task from cloud: " + task.description + 
                          " (CloudID: " + task.id + 
                          ", FirestoreID: " + (task.firestoreDocumentId != null ? task.firestoreDocumentId : "NULL") + 
                          ", updatedAt: " + task.updatedAt + 
                          ", deletedAt: " + task.deletedAt + ")");
                    taskDao.insert(task);
                }
            }
            
            // Delete tasks that are no longer in cloud
            for (limor.tal.mytodo.Task task : tasksToDelete) {
                Log.w(TAG, "DELETION DEBUG: Deleting task from local DB: " + task.description + 
                      " (LocalID: " + task.id + 
                      ", FirestoreID: " + (task.firestoreDocumentId != null ? task.firestoreDocumentId : "NULL") + ")");
                taskDao.delete(task);
            }
            
            // Update last sync timestamp
            prefs.edit()
                    .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                    .apply();
            
            Log.w(TAG, "DUPLICATION DEBUG: Final sync results - " + 
                  "Tasks to update: " + tasksToUpdate.size() + 
                  ", Tasks to insert: " + tasksToInsert.size() + 
                  ", Tasks to delete: " + tasksToDelete.size());
            
            // Debug: Log all tasks that will be inserted
            for (limor.tal.mytodo.Task task : tasksToInsert) {
                Log.w(TAG, "DUPLICATION DEBUG: Will insert task: " + task.description + 
                      " (CloudID: " + task.id + 
                      ", FirestoreID: " + (task.firestoreDocumentId != null ? task.firestoreDocumentId : "NULL") + ")");
            }
            
            Log.d(TAG, "Sync completed: " + (tasksToUpdate.size() + tasksToInsert.size()) + " tasks synchronized, " + tasksToDelete.size() + " deleted");
            callback.onSyncComplete(true, "Sync completed - " + (tasksToUpdate.size() + tasksToInsert.size()) + " tasks synchronized, " + tasksToDelete.size() + " deleted");
            
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
        // Log.d(TAG, "forceSync: Starting forced sync - timestamp: " + System.currentTimeMillis());
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
