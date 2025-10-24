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

    public void update(Task task) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Log.d("MyToDo", "REPOSITORY UPDATE DEBUG: Updating task: " + task.description + 
                  " (ID: " + task.id + 
                  ", FirestoreID: " + (task.firestoreDocumentId != null ? task.firestoreDocumentId : "NULL") + 
                  ", isRecurring: " + task.isRecurring + 
                  ", recurrenceType: " + task.recurrenceType + 
                  ", dueDate: " + task.dueDate + 
                  ", dayOfWeek: " + task.dayOfWeek + 
                  ", isCompleted: " + task.isCompleted + ")");
            taskDao.update(task);
            Log.d("MyToDo", "REPOSITORY UPDATE DEBUG: Task updated successfully: " + task.description + " (ID: " + task.id + ")");
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
                Log.d("MyToDo", "Soft deleting task: " + task.description);
                // Perform soft delete by setting deletedAt timestamp
                task.deletedAt = System.currentTimeMillis();
                task.updatedAt = System.currentTimeMillis();
                taskDao.update(task);
                Log.d("MyToDo", "Task soft deleted successfully: " + task.description + " (deletedAt: " + task.deletedAt + ")");
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