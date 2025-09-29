package limor.tal.mytodo;

import android.app.Application;
import android.util.Log;

import androidx.room.Room;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

public class TaskApplication extends Application {
    public static AppDatabase database;
    private static SyncManager syncManager;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this);
            Log.d("MyToDo", "TaskApplication: Firebase initialized successfully");
        } catch (Exception e) {
            Log.e("MyToDo", "TaskApplication: Error initializing Firebase", e);
        }
        
        // Initialize Room database
        try {
            database = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "task_database")
                    .build();
            Log.d("MyToDo", "TaskApplication: Database initialized successfully");
        } catch (Exception e) {
            Log.e("MyToDo", "TaskApplication: Error initializing database", e);
        }
        
        // Initialize SyncManager as singleton
        try {
            syncManager = new SyncManager(this);
            Log.d("MyToDo", "TaskApplication: SyncManager initialized successfully");
        } catch (Exception e) {
            Log.e("MyToDo", "TaskApplication: Error initializing SyncManager", e);
        }
    }
    
    public static SyncManager getSyncManager() {
        return syncManager;
    }
}
