package limor.tal.mytodo;

import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;

public class FirestoreService {
    private static final String TAG = "FirestoreService";
    private static final String COLLECTION_TASKS = "mytodo_tasks";
    
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FamilySyncService familySyncService;

    public interface FirestoreCallback {
        void onSuccess(Object result);
        void onError(String error);
    }

    public interface TasksCallback {
        void onTasksLoaded(List<limor.tal.mytodo.Task> tasks);
        void onError(String error);
    }

    public FirestoreService() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.familySyncService = new FamilySyncService();
        
        // Only set Firestore settings if they haven't been set yet
        try {
            // Disable offline persistence to ensure we always get fresh data from server
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(false)
                    .build();
            this.db.setFirestoreSettings(settings);
            Log.d(TAG, "Firestore offline persistence disabled - will always fetch fresh data from server");
        } catch (IllegalStateException e) {
            // Firestore settings already set, this is fine
            Log.d(TAG, "Firestore settings already configured, using existing settings");
        }
    }

    // Save a single task to Firestore
    public void saveTask(limor.tal.mytodo.Task task, FirestoreCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("User not authenticated");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        FirestoreTask firestoreTask = FirestoreTask.fromTask(task, userId);
        // Don't call updateTimestamp() here - preserve the task's original updatedAt

        if (task.firestoreDocumentId != null) {
            // Update existing task
            firestoreTask.documentId = task.firestoreDocumentId;
            db.collection(COLLECTION_TASKS).document(task.firestoreDocumentId)
                    .set(firestoreTask.toMap())
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d(TAG, "Task updated with ID: " + task.firestoreDocumentId);
                            callback.onSuccess(task.firestoreDocumentId);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            Log.e(TAG, "Error updating task", e);
                            callback.onError("Failed to update task: " + e.getMessage());
                        }
                    });
        } else {
            // Create new task
            db.collection(COLLECTION_TASKS)
                    .add(firestoreTask.toMap())
                    .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                        @Override
                        public void onSuccess(DocumentReference documentReference) {
                            Log.d(TAG, "Task saved with ID: " + documentReference.getId());
                            // Store the document ID back to the task for future syncs
                            task.firestoreDocumentId = documentReference.getId();
                            callback.onSuccess(documentReference.getId());
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            Log.e(TAG, "Error saving task", e);
                            callback.onError("Failed to save task: " + e.getMessage());
                        }
                    });
        }
    }

    // Update an existing task in Firestore
    public void updateTask(String documentId, limor.tal.mytodo.Task task, FirestoreCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("User not authenticated");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        FirestoreTask firestoreTask = FirestoreTask.fromTask(task, userId);
        firestoreTask.documentId = documentId;
        // Don't call updateTimestamp() here - preserve the task's original updatedAt

        db.collection(COLLECTION_TASKS)
                .document(documentId)
                .set(firestoreTask.toMap())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Task updated successfully");
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Error updating task", e);
                        callback.onError("Failed to update task: " + e.getMessage());
                    }
                });
    }

    // Soft delete a task from Firestore (set deletedAt timestamp)
    public void softDeleteTask(String documentId, FirestoreCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("User not authenticated");
            return;
        }

        long deletedAt = System.currentTimeMillis();
        db.collection(COLLECTION_TASKS)
                .document(documentId)
                .update("deletedAt", deletedAt, "updatedAt", deletedAt)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Task soft deleted successfully (deletedAt: " + deletedAt + ")");
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Error soft deleting task", e);
                        callback.onError("Failed to soft delete task: " + e.getMessage());
                    }
                });
    }

    // Hard delete a task from Firestore (completely remove - for cleanup purposes)
    public void deleteTask(String documentId, FirestoreCallback callback) {
        db.collection(COLLECTION_TASKS)
                .document(documentId)
                .delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Task hard deleted successfully");
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Error hard deleting task", e);
                        callback.onError("Failed to hard delete task: " + e.getMessage());
                    }
                });
    }

    // Load all tasks for the current user
    public void loadUserTasks(TasksCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("User not authenticated");
            return;
        }

        // Check if the user token is valid
        auth.getCurrentUser().getIdToken(false).addOnCompleteListener(new OnCompleteListener<GetTokenResult>() {
            @Override
            public void onComplete(Task<GetTokenResult> tokenTask) {
                if (tokenTask.isSuccessful()) {
                    String userId = auth.getCurrentUser().getUid();
                    Log.d(TAG, "Loading tasks from Firestore (offline persistence disabled - fresh data only) for user: " + userId);
                    loadTasksFromFirestore(userId, callback);
                } else {
                    Log.e(TAG, "User authentication token invalid", tokenTask.getException());
                    callback.onError("Authentication token invalid: " + tokenTask.getException().getMessage());
                }
            }
        });
    }
    
    private void loadTasksFromFirestore(String userId, TasksCallback callback) {
        
        db.collection(COLLECTION_TASKS)
                .whereEqualTo("userId", userId)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            List<limor.tal.mytodo.Task> tasks = new ArrayList<>();
                            for (DocumentSnapshot document : task.getResult()) {
                                try {
                                    FirestoreTask firestoreTask = document.toObject(FirestoreTask.class);
                                    if (firestoreTask != null) {
                                        firestoreTask.documentId = document.getId();
                                        
                                        // Skip soft-deleted tasks
                                        if (firestoreTask.isDeleted()) {
                                            Log.d(TAG, "Skipping soft-deleted task: " + firestoreTask.description + 
                                                  " (deletedAt: " + firestoreTask.deletedAt + ")");
                                            continue;
                                        }
                                        
                                        Log.d(TAG, "Loaded FirestoreTask: " + firestoreTask.description + 
                                              ", sourceApp: " + firestoreTask.sourceApp + 
                                              ", sourceTaskId: " + firestoreTask.sourceTaskId);
                                        limor.tal.mytodo.Task localTask = firestoreTask.toTask();
                                        Log.d(TAG, "Converted to local Task: " + localTask.description + 
                                              ", sourceApp: " + localTask.sourceApp + 
                                              ", sourceTaskId: " + localTask.sourceTaskId +
                                              ", isExportedFromFamilySync: " + localTask.isExportedFromFamilySync());
                                        tasks.add(localTask);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing task document", e);
                                }
                            }
                            Log.d(TAG, "Loaded " + tasks.size() + " tasks from Firestore (fresh data from server)");
                            callback.onTasksLoaded(tasks);
                        } else {
                            Log.e(TAG, "Error loading tasks from Firestore", task.getException());
                            callback.onError("Failed to load tasks: " + task.getException().getMessage());
                        }
                    }
                });
    }

    // Batch save multiple tasks
    public void batchSaveTasks(List<limor.tal.mytodo.Task> tasks, FirestoreCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("User not authenticated");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        WriteBatch batch = db.batch();

        for (limor.tal.mytodo.Task task : tasks) {
            FirestoreTask firestoreTask = FirestoreTask.fromTask(task, userId);
            // Don't call updateTimestamp() here - preserve the task's original updatedAt
            
            DocumentReference docRef = db.collection(COLLECTION_TASKS).document();
            batch.set(docRef, firestoreTask.toMap());
        }

        batch.commit()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Batch save successful");
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Batch save failed", e);
                        callback.onError("Failed to batch save tasks: " + e.getMessage());
                    }
                });
    }

    // Check if user is authenticated
    public boolean isUserAuthenticated() {
        return auth.getCurrentUser() != null;
    }

    // Get current user ID
    public String getCurrentUserId() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }
    
    // Update task completion with FamilySync sync
    public void updateTaskCompletionWithSync(String documentId, boolean isCompleted, FirestoreCallback callback) {
        familySyncService.toggleTaskCompletionWithSync(documentId, isCompleted, new FamilySyncService.FamilySyncCallback() {
            @Override
            public void onSuccess(Object result) {
                Log.d(TAG, "Task completion updated with FamilySync sync");
                callback.onSuccess(result);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error updating task completion with sync: " + error);
                callback.onError(error);
            }
        });
    }
    
    // Subscribe to FamilySync changes for a task
    public void subscribeToFamilySyncChanges(String taskId, FirestoreCallback callback) {
        familySyncService.subscribeToFamilySyncChanges(taskId, new FamilySyncService.FamilySyncCallback() {
            @Override
            public void onSuccess(Object result) {
                callback.onSuccess(result);
            }
            
            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
}
