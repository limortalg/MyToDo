package limor.tal.mytodo;

import android.app.Application;
import android.util.Log;

import androidx.room.Room;

public class TaskApplication extends Application {
    public static AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            database = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "task_database")
                    .fallbackToDestructiveMigration()
                    .build();
            Log.d("MyToDo", "TaskApplication: Database initialized successfully");
        } catch (Exception e) {
            Log.e("MyToDo", "TaskApplication: Error initializing database", e);
        }
    }
}
