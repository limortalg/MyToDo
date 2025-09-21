package limor.tal.mytodo;

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

    @Query("SELECT * FROM tasks")
    LiveData<List<Task>> getAllTasks();

    @Query("SELECT * FROM tasks")
    List<Task> getAllTasksSync();

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    Task getTaskById(int taskId);
}