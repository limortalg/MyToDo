package limor.tal.mytodo;

import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class FamilySyncService {
    private static final String TAG = "FamilySyncService";
    private static final String FAMILY_SYNC_TASKS_COLLECTION = "tasks";
    private static final String SYNC_LOGS_COLLECTION = "task_sync_logs";
    
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    public interface FamilySyncCallback {
        void onSuccess(Object result);
        void onError(String error);
    }

    public FamilySyncService() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Sync completion status from MyToDo to FamilySync
     */
    public void syncCompletionToFamilySync(String mytodoTaskId, boolean isCompleted, FamilySyncCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("User not authenticated");
            return;
        }

        // Get the MyToDo task to find the source FamilySync task ID
        db.collection("mytodo_tasks")
                .whereEqualTo("userId", auth.getCurrentUser().getUid())
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            FirestoreTask mytodoTask = null;
                            for (DocumentSnapshot document : task.getResult()) {
                                if (document.getId().equals(mytodoTaskId)) {
                                    mytodoTask = document.toObject(FirestoreTask.class);
                                    if (mytodoTask != null) {
                                        mytodoTask.documentId = document.getId();
                                    }
                                    break;
                                }
                            }
                            
                            if (mytodoTask == null || !mytodoTask.isExportedFromFamilySync()) {
                                Log.d(TAG, "No FamilySync source task found for MyToDo task: " + mytodoTaskId);
                                callback.onSuccess(null); // Not an exported task, no sync needed
                                return;
                            }

                            // Update the FamilySync task
                            updateFamilySyncTask(mytodoTask.sourceTaskId, isCompleted, callback);
                        } else {
                            Log.e(TAG, "Error finding MyToDo task", task.getException());
                            callback.onError("Failed to find task: " + task.getException().getMessage());
                        }
                    }
                });
    }

    /**
     * Update FamilySync task completion status
     */
    private void updateFamilySyncTask(String familySyncTaskId, boolean isCompleted, FamilySyncCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", isCompleted ? "DONE" : "PENDING");
        updates.put("completedAt", isCompleted ? System.currentTimeMillis() : null);
        updates.put("updatedAt", System.currentTimeMillis());

        db.collection(FAMILY_SYNC_TASKS_COLLECTION)
                .document(familySyncTaskId)
                .update(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Successfully synced completion status to FamilySync");
                        
                        // Log the sync action
                        logSyncAction("completion_sync_from_mytodo", familySyncTaskId, 
                                familySyncTaskId, isCompleted, callback);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Error syncing completion to FamilySync", e);
                        callback.onError("Failed to sync to FamilySync: " + e.getMessage());
                    }
                });
    }

    /**
     * Listen for FamilySync task completion changes and update MyToDo accordingly
     */
    public void subscribeToFamilySyncChanges(String mytodoTaskId, FamilySyncCallback callback) {
        // Get the MyToDo task to find the source FamilySync task ID
        db.collection("mytodo_tasks")
                .whereEqualTo("userId", auth.getCurrentUser().getUid())
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            FirestoreTask mytodoTask = null;
                            for (DocumentSnapshot document : task.getResult()) {
                                if (document.getId().equals(mytodoTaskId)) {
                                    mytodoTask = document.toObject(FirestoreTask.class);
                                    if (mytodoTask != null) {
                                        mytodoTask.documentId = document.getId();
                                    }
                                    break;
                                }
                            }
                            
                            final FirestoreTask finalMytoDoTask = mytodoTask;
                            if (finalMytoDoTask == null || !finalMytoDoTask.isExportedFromFamilySync()) {
                                Log.d(TAG, "No FamilySync source task found for MyToDo task: " + mytodoTaskId);
                                return;
                            }

                            // Listen to FamilySync task changes
                            db.collection(FAMILY_SYNC_TASKS_COLLECTION)
                                    .document(finalMytoDoTask.sourceTaskId)
                                    .addSnapshotListener((documentSnapshot, e) -> {
                                        if (e != null) {
                                            Log.e(TAG, "Error listening to FamilySync changes", e);
                                            return;
                                        }

                                        if (documentSnapshot != null && documentSnapshot.exists()) {
                                            Map<String, Object> data = documentSnapshot.getData();
                                            if (data != null) {
                                                String status = (String) data.get("status");
                                                final boolean isCompleted = "DONE".equals(status);
                                                
                                                // Update MyToDo task if status changed
                                                if (finalMytoDoTask.isCompleted != isCompleted) {
                                                    updateMyToDoTaskCompletion(mytodoTaskId, isCompleted);
                                                    
                                                    // Log the sync action
                                                    logSyncAction("completion_sync_from_familysync", 
                                                            finalMytoDoTask.sourceTaskId, mytodoTaskId, isCompleted, null);
                                                    
                                                    if (callback != null) {
                                                        callback.onSuccess(new HashMap<String, Object>() {{
                                                            put("type", "completion_changed");
                                                            put("isCompleted", isCompleted);
                                                            put("familySyncTask", data);
                                                        }});
                                                    }
                                                }
                                            }
                                        }
                                    });
                        } else {
                            Log.e(TAG, "Error finding MyToDo task for sync", task.getException());
                        }
                    }
                });
    }

    /**
     * Update MyToDo task completion status
     */
    private void updateMyToDoTaskCompletion(String mytodoTaskId, boolean isCompleted) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("isCompleted", isCompleted);
        updates.put("completionDate", isCompleted ? System.currentTimeMillis() : null);
        updates.put("updatedAt", System.currentTimeMillis());

        db.collection("mytodo_tasks")
                .document(mytodoTaskId)
                .update(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Successfully updated MyToDo task completion from FamilySync");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Error updating MyToDo task completion", e);
                    }
                });
    }

    /**
     * Enhanced task completion toggle that syncs with FamilySync
     */
    public void toggleTaskCompletionWithSync(String taskId, boolean isCompleted, FamilySyncCallback callback) {
        // First update MyToDo task
        Map<String, Object> updates = new HashMap<>();
        updates.put("isCompleted", isCompleted);
        updates.put("completionDate", isCompleted ? System.currentTimeMillis() : null);
        updates.put("updatedAt", System.currentTimeMillis());

        db.collection("mytodo_tasks")
                .document(taskId)
                .update(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "MyToDo task completion updated successfully");
                        
                        // Then sync with FamilySync if this is an exported task
                        syncCompletionToFamilySync(taskId, isCompleted, callback);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Error updating MyToDo task completion", e);
                        callback.onError("Failed to update task: " + e.getMessage());
                    }
                });
    }

    /**
     * Log sync actions for debugging and audit
     */
    private void logSyncAction(String action, String familySyncTaskId, String mytodoTaskId, 
                              boolean completionStatus, FamilySyncCallback callback) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("action", action);
        logData.put("familySyncTaskId", familySyncTaskId);
        logData.put("mytodoTaskId", mytodoTaskId);
        logData.put("userId", auth.getCurrentUser().getUid());
        logData.put("timestamp", System.currentTimeMillis());
        logData.put("completionStatus", completionStatus);

        db.collection(SYNC_LOGS_COLLECTION)
                .add(logData)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "Sync action logged successfully");
                        if (callback != null) {
                            callback.onSuccess(null);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Error logging sync action", e);
                        if (callback != null) {
                            callback.onError("Failed to log sync action: " + e.getMessage());
                        }
                    }
                });
    }

    /**
     * Check if a MyToDo task is exported from FamilySync
     */
    public boolean isExportedTask(FirestoreTask task) {
        return task.isExportedFromFamilySync();
    }

    /**
     * Get FamilySync task info for an exported MyToDo task
     */
    public void getFamilySyncTaskInfo(String sourceTaskId, FamilySyncCallback callback) {
        db.collection(FAMILY_SYNC_TASKS_COLLECTION)
                .document(sourceTaskId)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (documentSnapshot.exists()) {
                            callback.onSuccess(documentSnapshot.getData());
                        } else {
                            callback.onSuccess(null);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Error getting FamilySync task info", e);
                        callback.onError("Failed to get FamilySync task info: " + e.getMessage());
                    }
                });
    }
}
