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
            Log.d("MyToDo", "Inserting task: " + task.description + ", dueDate: " + task.dueDate + ", dayOfWeek: " + task.dayOfWeek);
            taskDao.insert(task);
            Log.d("MyToDo", "Task inserted successfully: " + task.description);
        });
    }

    public void update(Task task) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Log.d("MyToDo", "Updating task: " + task.description);
            taskDao.update(task);
            Log.d("MyToDo", "Task updated successfully: " + task.description);
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
        Log.d("MyToDo", "=== TASK REPOSITORY DELETE DEBUG START ===");
        Log.d("MyToDo", "Repository delete called for task - ID: " + task.id + ", Description: " + task.description);
        Log.d("MyToDo", "Task firestoreDocumentId: " + (task.firestoreDocumentId != null ? task.firestoreDocumentId : "NULL"));
        
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Log.d("MyToDo", "Repository: Executing database delete for task: " + task.description);
            try {
                taskDao.delete(task);
                Log.d("MyToDo", "Repository: Database delete successful for task: " + task.description);
                Log.d("MyToDo", "=== TASK REPOSITORY DELETE DEBUG END (SUCCESS) ===");
            } catch (Exception e) {
                Log.e("MyToDo", "Repository: Database delete failed for task: " + task.description + " - " + e.getMessage(), e);
                Log.d("MyToDo", "=== TASK REPOSITORY DELETE DEBUG END (ERROR) ===");
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