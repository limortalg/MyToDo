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
        if (isSyncing) {
            callback.onSyncComplete(false, "Sync already in progress");
            return;
        }
        
        if (!firestoreService.isUserAuthenticated()) {
            Log.d(TAG, "syncTasks: User not authenticated, aborting sync");
            callback.onSyncComplete(false, "User not authenticated");
            return;
        }
        
        isSyncing = true;
        
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
            }
        });
    }

    // First sync - download cloud tasks and merge with local tasks
    private void performFirstSync(SyncCallback callback) {
        try {
            // Get all local tasks
            List<limor.tal.mytodo.Task> localTasks = taskDao.getAllTasksIncludingDeletedSync();
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
            List<limor.tal.mytodo.Task> localChanges = taskDao.getAllTasksIncludingDeletedSync();
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
                // CRITICAL FIX: Don't skip deleted cloud tasks - we need to process them to apply deletions locally
                boolean cloudIsDeleted = cloudTask.deletedAt != null && cloudTask.deletedAt > 0;
                
                if (cloudTask.firestoreDocumentId != null && localMap.containsKey(cloudTask.firestoreDocumentId)) {
                    limor.tal.mytodo.Task localTask = localMap.get(cloudTask.firestoreDocumentId);
                    
                    // If cloud task is deleted, we need to apply the deletion locally
                    if (cloudIsDeleted) {
                        boolean localIsDeleted = localTask.deletedAt != null && localTask.deletedAt > 0;
                        if (!localIsDeleted) {
                            // DEBUG: Uncomment for debugging
                            // Log.w(TAG, "SYNC DELETION: Cloud task is deleted, applying deletion locally - " + cloudTask.description + 
                            //       " (Cloud deletedAt: " + cloudTask.deletedAt + 
                            //       ", Local deletedAt: " + (localTask.deletedAt != null ? localTask.deletedAt : "null") + 
                            //       ", FirestoreID: " + cloudTask.firestoreDocumentId + ")");
                            // Add to tasksToUpdate so the deletion can be applied locally
                            tasksToUpdate.add(cloudTask);
                        } else {
                            // Both are deleted, nothing to do
                            // DEBUG: Log.d(TAG, "SYNC DEBUG: Both cloud and local tasks are deleted - " + cloudTask.description);
                        }
                        continue;
                    }
                    
                    // If local task is soft-deleted, still process it in tasksToUpdate
                    // The updateLocalDatabase logic will properly handle deletion conflicts
                    if (localTask.deletedAt != null && localTask.deletedAt > 0) {
                        Log.d(TAG, "SYNC DEBUG: Local task is soft-deleted, but cloud task exists - " + localTask.description + 
                              " (Local deletedAt: " + localTask.deletedAt + 
                              ", Cloud deletedAt: " + (cloudTask.deletedAt != null ? cloudTask.deletedAt : "null") + 
                              ", Cloud updatedAt: " + cloudTask.updatedAt + 
                              ") - adding to tasksToUpdate for conflict resolution");
                        // Still add to tasksToUpdate so updateLocalDatabase can handle the conflict properly
                        tasksToUpdate.add(cloudTask);
                        continue;
                    }
                    
                    Log.d(TAG, "SYNC DEBUG: Found matching task - " + cloudTask.description + 
                          " (Cloud ID: " + cloudTask.firestoreDocumentId + 
                          ", Local ID: " + localTask.id + 
                          ", Cloud updatedAt: " + cloudTask.updatedAt + 
                          ", Local updatedAt: " + localTask.updatedAt + 
                          ", Cloud isCompleted: " + cloudTask.isCompleted + 
                          ", Local isCompleted: " + localTask.isCompleted + 
                          ", Cloud dueDate: " + cloudTask.dueDate + 
                          ", Local dueDate: " + localTask.dueDate + ")");
                    
                    // Improved conflict resolution: use the more recently updated task based on updatedAt
                    long localUpdatedAt = localTask.updatedAt != null ? localTask.updatedAt : 0;
                    long cloudUpdatedAt = cloudTask.updatedAt != null ? cloudTask.updatedAt : 0;
                    
                    if (localUpdatedAt > cloudUpdatedAt) {
                        Log.d(TAG, "SYNC DEBUG: Using local version (newer updatedAt) for " + localTask.description + 
                              " (Local updatedAt: " + localUpdatedAt + ", Cloud updatedAt: " + cloudUpdatedAt + ")");
                        tasksToUpdate.add(localTask);
                    } else if (cloudUpdatedAt > localUpdatedAt) {
                        Log.d(TAG, "SYNC DEBUG: Using cloud version (newer updatedAt) for " + cloudTask.description + 
                              " (Local updatedAt: " + localUpdatedAt + ", Cloud updatedAt: " + cloudUpdatedAt + ")");
                        tasksToUpdate.add(cloudTask);
                    } else {
                        // Same timestamp, prefer local version to avoid overwriting recent changes
                        Log.d(TAG, "SYNC DEBUG: Using local version (same updatedAt, prefer local) for " + localTask.description);
                        tasksToUpdate.add(localTask);
                    }
                } else {
                    // New task from cloud
                    // Skip deleted tasks that don't exist locally - nothing to delete
                    if (cloudIsDeleted) {
                        Log.d(TAG, "SYNC DEBUG: Cloud task is deleted but doesn't exist locally - skipping - " + cloudTask.description + 
                              " (FirestoreID: " + cloudTask.firestoreDocumentId + ")");
                        continue;
                    }
                    Log.d(TAG, "SYNC DEBUG: New task from cloud - " + cloudTask.description + 
                          " (Cloud ID: " + cloudTask.firestoreDocumentId + ")");
                    tasksToInsert.add(cloudTask);
                }
            }
            
            // Upload local changes that aren't in cloud
            final Set<Integer> uploadedTaskIds = new HashSet<>();
            for (limor.tal.mytodo.Task localTask : localChanges) {
                // Skip ALL soft-deleted tasks (don't upload them to cloud)
                if (localTask.deletedAt != null && localTask.deletedAt > 0) {
                    continue;
                }
                
                // Check if already being uploaded to prevent duplicates
                if (uploadedTaskIds.contains(localTask.id)) {
                    continue;
                }
                
                // Check if this task is already in cloud
                boolean foundInCloud = false;
                if (localTask.firestoreDocumentId != null && cloudMap.containsKey(localTask.firestoreDocumentId)) {
                    foundInCloud = true;
                }
                
                if (!foundInCloud) {
                    // This is a new local task, upload it
                    Log.d(TAG, "UPLOAD DEBUG: Uploading new task - " + localTask.description + 
                          " (ID: " + localTask.id + ", FirestoreID: " + (localTask.firestoreDocumentId != null ? localTask.firestoreDocumentId : "NULL") + ")");
                    
                    // Mark this task as being uploaded to prevent duplicate uploads
                    uploadedTaskIds.add(localTask.id);
                    
                    firestoreService.saveTask(localTask, new FirestoreService.FirestoreCallback() {
                        @Override
                        public void onSuccess(Object result) {
                            Log.d(TAG, "UPLOAD DEBUG: Success - " + localTask.description + 
                                  " (Local ID: " + localTask.id + ", Firestore ID: " + result + ")");
                            // Update the local task with the firestoreDocumentId
                            localTask.firestoreDocumentId = (String) result;
                            
                            // Update database on background thread
                            executorService.execute(() -> {
                                try {
                                    taskDao.update(localTask);
                                    Log.d(TAG, "UPLOAD DEBUG: Database updated - " + localTask.description + " (Firestore ID: " + localTask.firestoreDocumentId + ")");
                                } catch (Exception e) {
                                    Log.e(TAG, "UPLOAD DEBUG: Database update failed - " + localTask.description, e);
                                }
                            });
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Failed to upload local task: " + localTask.description + " - " + error);
                            // Remove from uploaded set on error so it can be retried
                            uploadedTaskIds.remove(localTask.id);
                        }
                    });
                }
            }
            
            // Upload local changes for existing tasks that have been modified
            for (limor.tal.mytodo.Task localTask : localChanges) {
                if (localTask.firestoreDocumentId != null && cloudMap.containsKey(localTask.firestoreDocumentId)) {
                    // Handle soft-deleted tasks
                    if (localTask.deletedAt != null && localTask.deletedAt > 0) {
                        // Check if cloud task is not yet soft-deleted
                        limor.tal.mytodo.Task cloudTask = cloudMap.get(localTask.firestoreDocumentId);
                        long cloudDeletedAt = cloudTask.deletedAt != null ? cloudTask.deletedAt : 0;
                        
                        if (cloudDeletedAt == 0) {
                            // Cloud task is not soft-deleted yet, sync the deletion
                            // DEBUG: Uncomment for debugging
                            // Log.d(TAG, "SOFT DELETE DEBUG: Syncing deletion - " + localTask.description + 
                            //       " (Firestore ID: " + localTask.firestoreDocumentId + ")");
                            
                            firestoreService.softDeleteTask(localTask.firestoreDocumentId, new FirestoreService.FirestoreCallback() {
                                @Override
                                public void onSuccess(Object result) {
                                    // DEBUG: Uncomment for debugging
                                    // Log.d(TAG, "SOFT DELETE DEBUG: Success - " + localTask.description);
                                }

                                @Override
                                public void onError(String error) {
                                    Log.e(TAG, "SOFT DELETE DEBUG: Failed - " + localTask.description + " - " + error);
                                }
                            });
                        }
                        continue;
                    }
                    
                    // This is an existing task, check if local version is newer
                    limor.tal.mytodo.Task cloudTask = cloudMap.get(localTask.firestoreDocumentId);
                    long localUpdatedAt = localTask.updatedAt != null ? localTask.updatedAt : 0;
                    long cloudUpdatedAt = cloudTask.updatedAt != null ? cloudTask.updatedAt : 0;
                    
                    if (localUpdatedAt > cloudUpdatedAt) {
                        Log.d(TAG, "SYNC DEBUG: Uploading updated existing task - " + localTask.description + 
                              " (Local ID: " + localTask.id + 
                              ", Firestore ID: " + localTask.firestoreDocumentId + 
                              ", Local updatedAt: " + localUpdatedAt + 
                              ", Cloud updatedAt: " + cloudUpdatedAt + 
                              ", Local isCompleted: " + localTask.isCompleted + 
                              ", Cloud isCompleted: " + cloudTask.isCompleted + 
                              ", Local dueDate: " + localTask.dueDate + 
                              ", Cloud dueDate: " + cloudTask.dueDate + ")");
                        
                        firestoreService.saveTask(localTask, new FirestoreService.FirestoreCallback() {
                            @Override
                            public void onSuccess(Object result) {
                                Log.d(TAG, "SYNC DEBUG: Successfully updated existing task in cloud - " + localTask.description + 
                                      " (Local ID: " + localTask.id + 
                                      ", Firestore ID: " + result + ")");
                            }

                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Failed to upload modified local task: " + error);
                            }
                        });
                    } else {
                        Log.d(TAG, "SYNC DEBUG: Skipping upload of existing task (cloud is newer) - " + localTask.description + 
                              " (Local ID: " + localTask.id + 
                              ", Local updatedAt: " + localUpdatedAt + 
                              ", Cloud updatedAt: " + cloudUpdatedAt + ")");
                    }
                }
            }
            
            // Find tasks to delete (local tasks that are no longer in cloud)
            List<limor.tal.mytodo.Task> tasksToDelete = new ArrayList<>();
            for (limor.tal.mytodo.Task localTask : localChanges) {
                if (localTask.firestoreDocumentId != null && !cloudMap.containsKey(localTask.firestoreDocumentId)) {
                    tasksToDelete.add(localTask);
                }
            }
            
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
            List<limor.tal.mytodo.Task> allLocalTasks = taskDao.getAllTasksIncludingDeletedSync();
            
            // Update existing tasks - find local task by firestoreDocumentId and update it
            for (limor.tal.mytodo.Task cloudTask : tasksToUpdate) {
                // Find the local task with this firestoreDocumentId
                limor.tal.mytodo.Task localTaskToUpdate = null;
                
                for (limor.tal.mytodo.Task localTask : allLocalTasks) {
                    if (cloudTask.firestoreDocumentId != null && cloudTask.firestoreDocumentId.equals(localTask.firestoreDocumentId)) {
                        localTaskToUpdate = localTask;
                        break;
                    }
                }
                
                if (localTaskToUpdate != null) {
                    // Check if local task is soft-deleted
                    boolean localIsDeleted = localTaskToUpdate.deletedAt != null && localTaskToUpdate.deletedAt > 0;
                    boolean cloudIsDeleted = cloudTask.deletedAt != null && cloudTask.deletedAt > 0;
                    
                    // If local task is soft-deleted and cloud task is not, preserve the local deletion
                    // Don't overwrite a deleted task with non-deleted cloud data
                    if (localIsDeleted && !cloudIsDeleted) {
                        final Task finalLocalTaskToUpdate = localTaskToUpdate; // Make final for inner class
                        final String taskDescription = finalLocalTaskToUpdate.description; // Make final for inner class
                        Log.w(TAG, "DELETION SYNC: Preserving local soft-deletion - " + taskDescription + 
                              " (Local deletedAt: " + finalLocalTaskToUpdate.deletedAt + ", Cloud deletedAt: null, FirestoreID: " + 
                              (finalLocalTaskToUpdate.firestoreDocumentId != null ? finalLocalTaskToUpdate.firestoreDocumentId : "NULL") + ")");
                        // Keep the local task as deleted, but update firestoreDocumentId if needed
                        if (finalLocalTaskToUpdate.firestoreDocumentId == null) {
                            finalLocalTaskToUpdate.firestoreDocumentId = cloudTask.firestoreDocumentId;
                            taskDao.update(finalLocalTaskToUpdate);
                            Log.w(TAG, "DELETION SYNC: Updated firestoreDocumentId for deleted task: " + taskDescription);
                        }
                        // Try to sync the deletion to cloud
                        if (finalLocalTaskToUpdate.firestoreDocumentId != null) {
                            final String firestoreId = finalLocalTaskToUpdate.firestoreDocumentId; // Make final for inner class
                            firestoreService.softDeleteTask(firestoreId, new FirestoreService.FirestoreCallback() {
                                @Override
                                public void onSuccess(Object result) {
                                    Log.w(TAG, "DELETION SYNC: Successfully synced deletion to cloud - " + taskDescription);
                                }
                                @Override
                                public void onError(String error) {
                                    Log.e(TAG, "DELETION SYNC: Failed to sync deletion to cloud - " + taskDescription + " - " + error);
                                }
                            });
                        }
                        continue;
                    }
                    
                    // Smart merge: use the newer version for each field based on updatedAt timestamp
                    long localUpdatedAt = localTaskToUpdate.updatedAt != null ? localTaskToUpdate.updatedAt : 0;
                    long cloudUpdatedAt = cloudTask.updatedAt != null ? cloudTask.updatedAt : 0;
                    
                    // Use cloud data if it's newer, otherwise keep local data
                    if (cloudUpdatedAt > localUpdatedAt) {
                        // CRITICAL: Preserve local deletion status BEFORE merging any fields
                        // This must be captured BEFORE any modifications to localTaskToUpdate
                        Long preservedDeletedAt = localTaskToUpdate.deletedAt != null && localTaskToUpdate.deletedAt > 0 
                                ? localTaskToUpdate.deletedAt : null;
                        
                        // Enhanced logging before merge
                        Log.d(TAG, "SYNC MERGE: Merging cloud data into local task - " + localTaskToUpdate.description + 
                              " (Local deletedAt: " + preservedDeletedAt + ", Cloud deletedAt: " + 
                              (cloudTask.deletedAt != null ? cloudTask.deletedAt : "null") + 
                              ", Local updatedAt: " + localUpdatedAt + ", Cloud updatedAt: " + cloudUpdatedAt + ")");
                        
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
                        
                    // Handle deletedAt: if cloud is deleted, use cloud's deletedAt
                    // Otherwise, preserve local deletedAt if it exists - NEVER clear a local deletion
                    if (cloudIsDeleted) {
                        localTaskToUpdate.deletedAt = cloudTask.deletedAt;
                        Log.d(TAG, "SYNC DEBUG: Cloud task is deleted, applying deletion - " + cloudTask.description + 
                              " (Cloud deletedAt: " + cloudTask.deletedAt + ")");
                    } else if (preservedDeletedAt != null && preservedDeletedAt > 0) {
                        // CRITICAL: Always preserve local deletion - cloud version doesn't know about it
                        localTaskToUpdate.deletedAt = preservedDeletedAt;
                        Log.w(TAG, "SYNC DEBUG: Preserving local deletion during cloud merge - " + localTaskToUpdate.description + 
                              " (Local deletedAt: " + preservedDeletedAt + ", Cloud deletedAt: null - task was deleted locally but not on cloud)");
                    } else {
                        // Neither is deleted, keep deletedAt as null
                        // BUT: Triple-check that we're not accidentally clearing a deletion
                        if (localTaskToUpdate.deletedAt != null && localTaskToUpdate.deletedAt > 0) {
                            Log.e(TAG, "DELETION BUG DETECTED: About to clear deletedAt for task that was deleted! " + 
                                  localTaskToUpdate.description + " (deletedAt: " + localTaskToUpdate.deletedAt + 
                                  ", cloudDeletedAt: " + cloudTask.deletedAt + ", preservedDeletedAt: " + preservedDeletedAt + ")");
                            // Don't clear it - preserve the deletion
                            localTaskToUpdate.deletedAt = preservedDeletedAt != null ? preservedDeletedAt : localTaskToUpdate.deletedAt;
                        } else {
                            localTaskToUpdate.deletedAt = null;
                        }
                    }
                    } else {
                        // Keep all local data, but update the firestoreDocumentId if it was missing
                        if (localTaskToUpdate.firestoreDocumentId == null) {
                            localTaskToUpdate.firestoreDocumentId = cloudTask.firestoreDocumentId;
                        }
                        // If cloud is deleted but local is not, and local is newer, keep local (not deleted)
                        // But if cloud is deleted and local is also deleted, prefer the newer deletion
                        if (cloudIsDeleted && !localIsDeleted) {
                            // Local is newer and not deleted, keep it
                            Log.d(TAG, "SYNC DEBUG: Local task is newer and not deleted, keeping local version - " + localTaskToUpdate.description);
                        } else if (cloudIsDeleted && localIsDeleted) {
                            // Both deleted, use the newer deletedAt
                            if (cloudTask.deletedAt > localTaskToUpdate.deletedAt) {
                                localTaskToUpdate.deletedAt = cloudTask.deletedAt;
                                Log.d(TAG, "SYNC DEBUG: Both deleted, using newer deletion timestamp - " + localTaskToUpdate.description);
                            }
                        }
                    }
                    
                    // Log before updating to track deletedAt changes
                    Long oldDeletedAt = localTaskToUpdate.deletedAt;
                    Log.w(TAG, "SYNC UPDATE: Updating task - " + localTaskToUpdate.description + 
                          " (ID: " + localTaskToUpdate.id + ", FirestoreID: " + 
                          (localTaskToUpdate.firestoreDocumentId != null ? localTaskToUpdate.firestoreDocumentId : "NULL") + 
                          ", oldDeletedAt: " + oldDeletedAt + ", newDeletedAt: " + localTaskToUpdate.deletedAt + 
                          ", cloudDeletedAt: " + cloudTask.deletedAt + ")");
                    
                    taskDao.update(localTaskToUpdate);
                    
                    // Verify the update didn't accidentally restore a deleted task
                    if (oldDeletedAt != null && oldDeletedAt > 0 && localTaskToUpdate.deletedAt == null) {
                        Log.e(TAG, "DELETION BUG DETECTED: Task that was deleted now has deletedAt=null! " + 
                              localTaskToUpdate.description + " (was deletedAt: " + oldDeletedAt + ")");
                    }
                } else {
                    // This is a cloud task that should be updated but no local match found
                    // This can happen in first sync when there are no local tasks yet
                    // Move it to insert list instead of inserting it here
                    tasksToInsert.add(cloudTask);
                }
            }
            
            // Insert new tasks - but first check if they already exist
            for (limor.tal.mytodo.Task task : tasksToInsert) {
                // Check if a task with this firestoreDocumentId already exists
                boolean alreadyExists = false;
                boolean existingTaskIsDeleted = false;
                if (task.firestoreDocumentId != null) {
                    for (limor.tal.mytodo.Task existingTask : allLocalTasks) {
                        if (task.firestoreDocumentId.equals(existingTask.firestoreDocumentId)) {
                            alreadyExists = true;
                            // Check if the existing task is soft-deleted
                            existingTaskIsDeleted = existingTask.deletedAt != null && existingTask.deletedAt > 0;
                            break;
                        }
                    }
                }
                
                // Don't insert if task already exists (even if soft-deleted)
                // Soft-deleted tasks should stay deleted and will be synced to cloud separately
                if (!alreadyExists) {
                    // Also check if the cloud task itself is deleted - don't insert deleted tasks
                    boolean cloudIsDeleted = task.deletedAt != null && task.deletedAt > 0;
                    if (!cloudIsDeleted) {
                        Log.w(TAG, "SYNC INSERT: Inserting new task from cloud - " + task.description + 
                              " (FirestoreID: " + (task.firestoreDocumentId != null ? task.firestoreDocumentId : "NULL") + 
                              ", deletedAt: " + task.deletedAt + ")");
                        taskDao.insert(task);
                        Log.w(TAG, "SYNC INSERT: Task inserted successfully - " + task.description);
                    } else {
                        Log.w(TAG, "SYNC INSERT: Skipping insertion of deleted cloud task - " + task.description + 
                              " (deletedAt: " + task.deletedAt + ")");
                    }
                } else if (existingTaskIsDeleted) {
                    // This shouldn't happen if mergeTasks is working correctly, but handle it just in case
                    // If cloud task is NOT deleted but local is deleted, preserve the local deletion
                    boolean cloudIsDeleted = task.deletedAt != null && task.deletedAt > 0;
                    if (!cloudIsDeleted) {
                        // Cloud task is not deleted, but local is - PRESERVE THE LOCAL DELETION
                        // This is a critical bug fix: never restore a task that was deleted locally
                        Log.w(TAG, "SYNC INSERT BUG FIX: Cloud task exists and is NOT deleted, but local is soft-deleted - " + task.description + 
                              " (Local deletedAt: " + (task.deletedAt != null ? task.deletedAt : "null") + 
                              ", Cloud deletedAt: null, Cloud updatedAt: " + task.updatedAt + 
                              ") - PRESERVING LOCAL DELETION (this should have been handled in tasksToUpdate)");
                        // Find the existing local task and ensure it stays deleted, sync deletion to cloud
                        for (limor.tal.mytodo.Task existingTask : allLocalTasks) {
                            if (task.firestoreDocumentId != null && task.firestoreDocumentId.equals(existingTask.firestoreDocumentId)) {
                                // CRITICAL: NEVER restore a deleted task - preserve the deletion
                                long localDeletedAt = existingTask.deletedAt != null ? existingTask.deletedAt : 0;
                                
                                // Ensure firestoreDocumentId is set if missing
                                if (existingTask.firestoreDocumentId == null) {
                                    existingTask.firestoreDocumentId = task.firestoreDocumentId;
                                    taskDao.update(existingTask);
                                    Log.w(TAG, "SYNC INSERT: Updated firestoreDocumentId for deleted task - " + task.description);
                                }
                                
                                // Sync the deletion to cloud
                                final String firestoreId = existingTask.firestoreDocumentId;
                                firestoreService.softDeleteTask(firestoreId, new FirestoreService.FirestoreCallback() {
                                    @Override
                                    public void onSuccess(Object result) {
                                        Log.w(TAG, "SYNC INSERT: Successfully synced deletion to cloud - " + task.description);
                                    }
                                    @Override
                                    public void onError(String error) {
                                        Log.e(TAG, "SYNC INSERT: Failed to sync deletion to cloud - " + task.description + " - " + error);
                                    }
                                });
                                
                                Log.w(TAG, "SYNC INSERT: Preserved local deletion (deletedAt: " + localDeletedAt + ") - " + task.description);
                                break;
                            }
                        }
                    } else {
                        Log.w(TAG, "SYNC INSERT: Skipping insertion - task exists locally and is soft-deleted, cloud is also deleted - " + task.description);
                    }
                } else {
                    Log.w(TAG, "SYNC INSERT: Skipping insertion - task already exists locally (not deleted) - " + task.description);
                }
            }
            
            // Delete tasks that are no longer in cloud
            for (limor.tal.mytodo.Task task : tasksToDelete) {
                taskDao.delete(task);
            }
            
            // Update last sync timestamp
            prefs.edit()
                    .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                    .apply();
            
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


    // Directly sync a deletion to Firestore - called immediately when a task is deleted
    public void syncDeletionToFirestore(String firestoreDocumentId, String taskDescription) {
        if (firestoreDocumentId == null || firestoreDocumentId.isEmpty()) {
            Log.w(TAG, "syncDeletionToFirestore: Cannot sync deletion - firestoreDocumentId is null or empty for task: " + taskDescription);
            return;
        }
        
        if (!firestoreService.isUserAuthenticated()) {
            Log.w(TAG, "syncDeletionToFirestore: Cannot sync deletion - user not authenticated for task: " + taskDescription);
            return;
        }
        
        Log.w(TAG, "syncDeletionToFirestore: Immediately syncing deletion to Firestore - " + taskDescription + 
              " (FirestoreID: " + firestoreDocumentId + ")");
        
        firestoreService.softDeleteTask(firestoreDocumentId, new FirestoreService.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                Log.w(TAG, "syncDeletionToFirestore: Successfully synced deletion to Firestore - " + taskDescription + 
                      " (FirestoreID: " + firestoreDocumentId + ")");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "syncDeletionToFirestore: Failed to sync deletion to Firestore - " + taskDescription + 
                      " (FirestoreID: " + firestoreDocumentId + ") - Error: " + error);
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
