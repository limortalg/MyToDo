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
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;

public class FirestoreService {
    private static final String TAG = "FirestoreService";
    private static final String COLLECTION_TASKS = "mytodo_tasks";
    
    private FirebaseFirestore db;
    private FirebaseAuth auth;

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
    }

    // Save a single task to Firestore
    public void saveTask(limor.tal.mytodo.Task task, FirestoreCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("User not authenticated");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        FirestoreTask firestoreTask = FirestoreTask.fromTask(task, userId);
        firestoreTask.updateTimestamp();

        db.collection(COLLECTION_TASKS)
                .add(firestoreTask.toMap())
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "Task saved with ID: " + documentReference.getId());
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

    // Update an existing task in Firestore
    public void updateTask(String documentId, limor.tal.mytodo.Task task, FirestoreCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("User not authenticated");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        FirestoreTask firestoreTask = FirestoreTask.fromTask(task, userId);
        firestoreTask.documentId = documentId;
        firestoreTask.updateTimestamp();

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

    // Delete a task from Firestore
    public void deleteTask(String documentId, FirestoreCallback callback) {
        db.collection(COLLECTION_TASKS)
                .document(documentId)
                .delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Task deleted successfully");
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Error deleting task", e);
                        callback.onError("Failed to delete task: " + e.getMessage());
                    }
                });
    }

    // Load all tasks for the current user
    public void loadUserTasks(TasksCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("User not authenticated");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        
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
                                        limor.tal.mytodo.Task localTask = firestoreTask.toTask();
                                        tasks.add(localTask);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing task document", e);
                                }
                            }
                            Log.d(TAG, "Loaded " + tasks.size() + " tasks from Firestore");
                            callback.onTasksLoaded(tasks);
                        } else {
                            Log.e(TAG, "Error loading tasks", task.getException());
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
            firestoreTask.updateTimestamp();
            
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
}
