package limor.tal.mytodo;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Task.class}, version = 6, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract TaskDao taskDao();

    private static volatile AppDatabase INSTANCE;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    // Migration from version 1 to 2: Add manualPosition column
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN manualPosition INTEGER");
        }
    };

    // Migration from version 2 to 3: Add reminderDays column
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN reminderDays TEXT");
        }
    };

    // Migration from version 3 to 4: Add firestoreDocumentId column
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN firestoreDocumentId TEXT");
        }
    };

    // Migration from version 4 to 5: Add createdAt and updatedAt columns
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN createdAt INTEGER");
            database.execSQL("ALTER TABLE tasks ADD COLUMN updatedAt INTEGER");
        }
    };

    // Migration from version 5 to 6: Add FamilySync integration fields
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN sourceApp TEXT");
            database.execSQL("ALTER TABLE tasks ADD COLUMN sourceTaskId TEXT");
            database.execSQL("ALTER TABLE tasks ADD COLUMN sourceGroupId TEXT");
            database.execSQL("ALTER TABLE tasks ADD COLUMN familySyncAssigneeId TEXT");
            database.execSQL("ALTER TABLE tasks ADD COLUMN familySyncCreatorId TEXT");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "task_database")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}