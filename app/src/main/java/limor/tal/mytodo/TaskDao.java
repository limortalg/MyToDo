package limor.tal.mytodo;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskDao {
    @Insert
    void insert(Task task);

    @Update
    void update(Task task);

    @Update
    void updateTasks(List<Task> tasks);

    @Delete
    void delete(Task task);

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    LiveData<List<Task>> getAllTasks();

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    List<Task> getAllTasksSync();

    @Query("SELECT * FROM tasks")
    List<Task> getAllTasksIncludingDeletedSync();

    @Query("SELECT * FROM tasks WHERE id = :taskId AND deletedAt IS NULL")
    Task getTaskById(int taskId);

    @Query("SELECT * FROM tasks WHERE completionDate > :timestamp AND deletedAt IS NULL")
    List<Task> getTasksModifiedSince(long timestamp);

    @Query("DELETE FROM tasks")
    void deleteAllTasks();
}