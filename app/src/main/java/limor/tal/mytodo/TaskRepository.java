package limor.tal.mytodo;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;

import java.util.List;

public class TaskRepository {
    private TaskDao taskDao;
    private LiveData<List<Task>> allTasks;

    public TaskRepository(Application application) {
        try {
            if (TaskApplication.database == null) {
                Log.e("MyToDo", "TaskRepository: Database is null, initializing fallback");
                AppDatabase db = AppDatabase.getDatabase(application);
                taskDao = db.taskDao();
            } else {
                taskDao = TaskApplication.database.taskDao();
            }
            allTasks = taskDao.getAllTasks();
            Log.d("MyToDo", "TaskRepository: Initialized successfully");
        } catch (Exception e) {
            Log.e("MyToDo", "TaskRepository: Error initializing repository", e);
            // Fallback to getDatabase method
            try {
                AppDatabase db = AppDatabase.getDatabase(application);
                taskDao = db.taskDao();
                allTasks = taskDao.getAllTasks();
                Log.d("MyToDo", "TaskRepository: Fallback initialization successful");
            } catch (Exception fallbackError) {
                Log.e("MyToDo", "TaskRepository: Fallback initialization also failed", fallbackError);
            }
        }
    }

    public LiveData<List<Task>> getAllTasks() {
        return allTasks;
    }

    public List<Task> getAllTasksSync() {
        return taskDao.getAllTasksSync();
    }

    public void insert(Task task) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            taskDao.insert(task);
        });
    }
    
    // Synchronous insert that returns the task with ID set (for new tasks that need immediate ID)
    // NOTE: This must be called from a background thread (not the main thread)
    public Task insertSync(Task task) {
        try {
            Log.d("MyToDo", "TaskRepository: Starting synchronous insert - task: " + task.description + ", current id: " + task.id);
            long insertedId = taskDao.insertAndReturnId(task);
            task.id = (int) insertedId; // Set the ID explicitly from the return value
            Log.w("MyToDo", "TaskRepository: Synchronous insert completed - task: " + task.description + ", id: " + task.id + " (returned ID: " + insertedId + ")");
            
            // Verify the ID was set correctly
            if (task.id == 0) {
                Log.e("MyToDo", "TaskRepository: ERROR - Task ID is still 0 after insert! Returned ID was: " + insertedId);
            }
            
            return task;
        } catch (Exception e) {
            Log.e("MyToDo", "TaskRepository: Error in synchronous insert", e);
            return task;
        }
    }

    public void update(Task task) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            taskDao.update(task);
        });
    }

    public void updateTasks(List<Task> tasks) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Log.d("MyToDo", "Updating " + tasks.size() + " tasks");
            taskDao.updateTasks(tasks);
            for (Task task : tasks) {
                Log.d("MyToDo", "Task updated successfully: " + task.description + ", priority: " + task.priority);
            }
        });
    }

    public void delete(Task task) {
        delete(task, false); // Default to soft delete for backward compatibility
    }
    
    public void delete(Task task, boolean hardDelete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (hardDelete) {
                Log.d("MyToDo", "Hard deleting task: " + task.description);
                taskDao.delete(task);
                Log.d("MyToDo", "Task hard deleted successfully: " + task.description);
            } else {
                long deletionTime = System.currentTimeMillis();
                Log.w("MyToDo", "Soft deleting task: " + task.description + " (ID: " + task.id + 
                      ", FirestoreID: " + (task.firestoreDocumentId != null ? task.firestoreDocumentId : "NULL") + 
                      ", deletedAt: " + deletionTime + ")");
                // Perform soft delete by setting deletedAt timestamp
                task.deletedAt = deletionTime;
                task.updatedAt = deletionTime;
                taskDao.update(task);
                Log.w("MyToDo", "Task soft deleted successfully: " + task.description + " (deletedAt: " + task.deletedAt + ")");
            }
        });
    }

    public Task getTaskById(int taskId) {
        try {
            return taskDao.getTaskById(taskId);
        } catch (Exception e) {
            Log.e("MyToDo", "Error getting task by ID: " + taskId + ", error: " + e.getMessage(), e);
            return null;
        }
    }
}