package limor.tal.mytodo;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import limor.tal.mytodo.AppDatabase;
import limor.tal.mytodo.TaskDao;
import limor.tal.mytodo.SyncManager;
import limor.tal.mytodo.FirebaseAuthService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;

public class MainActivity extends AppCompatActivity {
    private TaskViewModel viewModel;
    private String lastDraggedCategory; // Track category of last dragged item
    private Task draggedTask = null; // Track which specific task was dragged
    private boolean actualDragOccurred = false; // Track if actual position changes happened
    private TaskAdapter adapter;
    private EditText searchEditText;
    private CheckBox includeCompletedCheckBox;
    private TextView emptyStateTextView;
    private RecyclerView recyclerView;
    private Button editButton;
    private Button moveButton;
    private Button completeButton;
    private Button deleteButton;
    private Long selectedDueDate;
    private Long selectedDueTime;
    private Integer selectedReminderOffset;
    private String selectedDayOfWeek;
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "MyToDoPrefs";
    private static final String PREF_LANGUAGE = "language";
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 100;
    private int selectedContextMenuPosition = -1; // Store position of context menu
    private Task selectedTask = null;
    private static boolean isProcessingEditFromReminder = false;
    private static long lastEditFromReminderTime = 0;
    private static final long MIN_EDIT_INTERVAL = 2000; // 2 seconds minimum between edits
    
    // Sync-related fields
    private SyncManager syncManager;
    private FirebaseAuthService authService;
    


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String deviceLang = Locale.getDefault().getLanguage();
        String defaultLang = (deviceLang.startsWith("he") || deviceLang.startsWith("iw")) ? "he" : "en";
        String language = prefs.getString(PREF_LANGUAGE, defaultLang);
        setLocale(language);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize sync services
        syncManager = new SyncManager(this);
        authService = new FirebaseAuthService(this);



        // Check launcher icon resources
        checkLauncherIconResources();
        
        // Debug: Check which layout is being used
        Log.d("MyToDo", "onCreate: Layout debug - Checking which layout file is being used");
        try {
            View appNameTablet = findViewById(R.id.appNameTablet);
            if (appNameTablet != null) {
                Log.d("MyToDo", "onCreate: TABLET LAYOUT detected - appNameTablet found");
            } else {
                Log.d("MyToDo", "onCreate: PHONE LAYOUT detected - appNameTablet not found");
            }
            
            // Check screen dimensions
            android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int screenWidthDp = (int) (displayMetrics.widthPixels / displayMetrics.density);
            int screenHeightDp = (int) (displayMetrics.heightPixels / displayMetrics.density);
            int smallestWidthDp = Math.min(screenWidthDp, screenHeightDp);
            
            Log.d("MyToDo", "onCreate: Screen dimensions - Width: " + screenWidthDp + "dp, Height: " + screenHeightDp + "dp, Smallest: " + smallestWidthDp + "dp");
            Log.d("MyToDo", "onCreate: Layout qualifier needed - sw" + smallestWidthDp + "dp");
            
        } catch (Exception e) {
            Log.e("MyToDo", "onCreate: Error checking layout type", e);
        }
        
        // Check exact alarm permission for Android 12+
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                    Log.w("MyToDo", "onCreate: Exact alarms not allowed, user should enable in settings");
                    // Show permission dialog after a short delay to let the UI initialize
                    new android.os.Handler().postDelayed(() -> requestExactAlarmPermission(), 1000);
                }
            } catch (Exception e) {
                Log.w("MyToDo", "onCreate: Could not check exact alarm permission", e);
            }
        }
        
        recyclerView = findViewById(R.id.recyclerView);
        viewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        emptyStateTextView = findViewById(R.id.emptyStateTextView);

                // Handle intent actions from ReminderService (after viewModel is initialized)
        Intent intent = getIntent();
        // Log.d("MyToDo", "onCreate: Stack trace for onCreate call:");
        // Thread.dumpStack();
        
        if (intent != null && "EDIT_TASK_FROM_REMINDER".equals(intent.getAction())) {
            if (isProcessingEditFromReminder) {
                Log.d("MyToDo", "onCreate: Skipping EDIT_TASK_FROM_REMINDER intent - already processing, flag is: " + isProcessingEditFromReminder);
                // Clear the intent to prevent future processing
                setIntent(new Intent());
                Log.d("MyToDo", "onCreate: Cleared intent after skipping");
                return;
            }
            
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastEditFromReminderTime < MIN_EDIT_INTERVAL) {
                Log.d("MyToDo", "onCreate: Skipping EDIT_TASK_FROM_REMINDER - too soon since last edit (" + (currentTime - lastEditFromReminderTime) + "ms < " + MIN_EDIT_INTERVAL + "ms)");
                // Clear the intent
                setIntent(new Intent());
                Log.d("MyToDo", "onCreate: Cleared intent after skipping");
                return;
            }
            
            int taskId = intent.getIntExtra("task_id", -1);
            if (taskId != -1) {
                Log.d("MyToDo", "onCreate: Received EDIT_TASK_FROM_REMINDER for taskId: " + taskId);
                Log.d("MyToDo", "onCreate: About to set isProcessingEditFromReminder flag from: " + isProcessingEditFromReminder + " to TRUE");
                isProcessingEditFromReminder = true; // Set flag to prevent reprocessing
                lastEditFromReminderTime = currentTime; // Update timestamp
                Log.d("MyToDo", "onCreate: Set isProcessingEditFromReminder flag to TRUE, timestamp: " + lastEditFromReminderTime);
                
                // Clear the intent immediately to prevent dialog from reopening
                Log.d("MyToDo", "onCreate: About to clear intent from: " + (getIntent() != null ? getIntent().getAction() : "null"));
                setIntent(new Intent());
                Log.d("MyToDo", "onCreate: Cleared intent to prevent dialog from reopening. New intent action: " + (getIntent() != null ? getIntent().getAction() : "null"));
                
                // Find and select the task, then show edit dialog
                Log.d("MyToDo", "onCreate: Setting up ONE-TIME getAllTasks observer for reminder edit");
                Observer<List<Task>> reminderObserver = new Observer<List<Task>>() {
                    @Override
                    public void onChanged(List<Task> tasks) {
                        if (tasks != null) {
                            Log.d("MyToDo", "onCreate: Reminder observer triggered with " + tasks.size() + " tasks, looking for taskId: " + taskId);
                            for (Task task : tasks) {
                                if (task.id == taskId) {
                                    Log.d("MyToDo", "onCreate: Found task: " + task.description + ", id: " + task.id);
                                    Log.d("MyToDo", "onCreate: Task details - description: " + task.description + ", dayOfWeek: " + task.dayOfWeek + ", dueTime: " + task.dueTime + ", isRecurring: " + task.isRecurring + ", recurrenceType: " + task.recurrenceType + ", reminderOffset: " + task.reminderOffset);
                                    
                                    selectedTask = task;
                                    updateButtonStates();
                                    Log.d("MyToDo", "onCreate: About to show edit dialog for task: " + task.description);
                                    Log.d("MyToDo", "onCreate: Calling showTaskDialog with task: " + task.description + ", id: " + task.id);
                                    showTaskDialog(task);
                                    Log.d("MyToDo", "onCreate: Edit dialog shown for task: " + task.description);
                                    Log.d("MyToDo", "onCreate: Returned from showTaskDialog call");
                                    Log.d("MyToDo", "onCreate: Reminder edit processing complete - removing observer to prevent re-triggering");
                                    
                                    // CRITICAL FIX: Remove this observer after processing to prevent re-triggering
                                    viewModel.getAllTasks().removeObserver(this);
                                    Log.d("MyToDo", "onCreate: Removed reminder observer to prevent dialog reopening");
                                    break;
                                }
                            }
                        } else {
                            Log.w("MyToDo", "onCreate: Reminder observer triggered with null tasks list");
                        }
                    }
                };
                viewModel.getAllTasks().observe(this, reminderObserver);
            }
        }
        
        // Handle COMPLETE_TASK_FROM_REMINDER intent
        if (intent != null && "COMPLETE_TASK_FROM_REMINDER".equals(intent.getAction())) {
            int taskId = intent.getIntExtra("task_id", -1);
            if (taskId != -1) {
                Log.d("MyToDo", "onCreate: Received COMPLETE_TASK_FROM_REMINDER for taskId: " + taskId);
                Observer<List<Task>> completeObserver = new Observer<List<Task>>() {
                    @Override
                    public void onChanged(List<Task> tasks) {
                        if (tasks != null) {
                            Log.d("MyToDo", "onCreate: Found " + tasks.size() + " tasks, looking for taskId: " + taskId);
                            for (Task task : tasks) {
                                if (task.id == taskId) {
                                    Log.d("MyToDo", "onCreate: Found completed task: " + task.description + ", id: " + task.id);
                                    // Force refresh the UI to show the completed task
                                    viewModel.updateTasksByCategory(tasks);
                                    Log.d("MyToDo", "onCreate: Forced UI refresh for completed task: " + task.description);
                                    
                                    // Clear the intent to prevent reprocessing
                                    setIntent(new Intent());
                                    Log.d("MyToDo", "onCreate: Cleared intent for completed task");
                                    
                                    // Remove this observer to prevent re-triggering
                                    viewModel.getAllTasks().removeObserver(this);
                                    Log.d("MyToDo", "onCreate: Removed complete observer to prevent re-triggering");
                                    break;
                                }
                            }
                        } else {
                            Log.w("MyToDo", "onCreate: Tasks list is null");
                        }
                    }
                };
                viewModel.getAllTasks().observe(this, completeObserver);
            }
        }

        // App icon ImageView for display
        ImageView appIconImageView = findViewById(R.id.appIconImageView);
        if (appIconImageView != null) {
            Log.d("MyToDo", "onCreate: App icon ImageView found successfully");
        } else {
            Log.e("MyToDo", "onCreate: App icon ImageView not found!");
        }

        
        // Settings button
        ImageButton settingsButton = findViewById(R.id.settingsButton);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
            });
        }

        // Sync button
        ImageButton syncButton = findViewById(R.id.syncButton);
        if (syncButton != null) {
            syncButton.setOnClickListener(v -> {
                if (authService.isUserSignedIn()) {
                    syncManager.syncTasks(new SyncManager.SyncCallback() {
                        @Override
                        public void onSyncComplete(boolean success, String message) {
                            runOnUiThread(() -> {
                                if (success) {
                                    Toast.makeText(MainActivity.this, "Sync completed successfully", Toast.LENGTH_SHORT).show();
                                    viewModel.forceRefreshTasks(); // Refresh the task list
                                } else {
                                    Toast.makeText(MainActivity.this, "Sync failed: " + message, Toast.LENGTH_LONG).show();
                                }
                            });
                        }

                        @Override
                        public void onSyncProgress(String message) {
                            runOnUiThread(() -> {
                                // Could show progress in a snackbar or status bar
                                Log.d("MyToDo", "Sync progress: " + message);
                            });
                        }
                    });
                } else {
                    Toast.makeText(this, "Please sign in to sync tasks", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Action buttons
        ImageButton addButton = findViewById(R.id.addButton);
        editButton = findViewById(R.id.editButton);
        moveButton = findViewById(R.id.moveButton);
        completeButton = findViewById(R.id.completeButton);
        deleteButton = findViewById(R.id.deleteButton);

        // Add button click listener
        if (addButton != null) {
            addButton.setOnClickListener(v -> {
                showTaskDialog(null);
            });
        }

        // Edit button click listener
        if (editButton != null) {
            editButton.setOnClickListener(v -> {
                if (selectedTask != null) {
                    showTaskDialog(selectedTask);
                } else {
                    Toast.makeText(this, getString(R.string.select_task_to_edit), Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Move button click listener
        if (moveButton != null) {
            moveButton.setOnClickListener(v -> {
                if (selectedTask != null) {
                    showMoveTaskDialog(selectedTask);
                } else {
                    Toast.makeText(this, getString(R.string.select_task_to_move), Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Complete button click listener
        if (completeButton != null) {
            completeButton.setOnClickListener(v -> {
                if (selectedTask != null) {
                                            // Check if the button should be enabled (action is allowed)
                        if (canCompleteTask(selectedTask)) {
                            // Action is allowed, proceed with completion
                            selectedTask.isCompleted = !selectedTask.isCompleted;
                            
                            // Set completion date when marking as completed
                            if (selectedTask.isCompleted) {
                                selectedTask.completionDate = System.currentTimeMillis();
                                Log.d("MyToDo", "Complete button: Task marked as completed with completion date: " + selectedTask.completionDate);
                                
                                // Cancel any pending reminder when completing the task
                                if (selectedTask.reminderOffset != null) {
                                    cancelReminder(selectedTask.id);
                                    Log.d("MyToDo", "Complete button: Cancelled reminder for completed task: " + selectedTask.description);
                                }
                            } else {
                                // Clear completion date when uncompleting
                                selectedTask.completionDate = null;
                                Log.d("MyToDo", "Complete button: Task uncompleted, cleared completion date");
                                
                                // Reschedule reminder if task was uncompleted and has a reminder
                                if (selectedTask.reminderOffset != null && selectedTask.reminderOffset >= 0) {
                                    try {
                                        scheduleReminder(selectedTask);
                                        Log.d("MyToDo", "Complete button: Rescheduled reminder for uncompleted task: " + selectedTask.description);
                                    } catch (Exception e) {
                                        Log.e("MyToDo", "Complete button: Error rescheduling reminder for uncompleted task", e);
                                    }
                                }
                            }
                            
                            viewModel.update(selectedTask);
                        selectedTask = null;
                        updateButtonStates();
                    } else {
                        // Action not allowed, show explanation
                        Toast.makeText(this, getString(R.string.daily_task_wrong_day), Toast.LENGTH_LONG).show();
                        Log.d("MyToDo", "Complete button: Showed explanation for disabled button - daily task wrong day");
                    }
                } else {
                    Toast.makeText(this, getString(R.string.select_task_to_complete), Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Delete button click listener
        if (deleteButton != null) {
            deleteButton.setOnClickListener(v -> {
                if (selectedTask != null) {
                    // Show confirmation dialog before deleting
                    showDeleteConfirmationDialog(selectedTask);
                } else {
                    Toast.makeText(this, getString(R.string.select_task_to_delete), Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Search controls layout (initially hidden)
        LinearLayout searchControlsLayout = findViewById(R.id.searchControlsLayout);
        searchEditText = findViewById(R.id.searchEditText);
        includeCompletedCheckBox = findViewById(R.id.includeCompletedCheckBox);
        
        // Search icon button - toggle search controls visibility
        ImageButton searchIconButton = findViewById(R.id.searchIconButton);
        if (searchIconButton != null) {
            searchIconButton.setOnClickListener(v -> {
                if (searchControlsLayout.getVisibility() == View.VISIBLE) {
                    // Hide search controls
                    searchControlsLayout.setVisibility(View.GONE);
                    searchEditText.setText(""); // Clear search
                    // Update search query to empty to show all tasks
                    viewModel.setSearchQuery("");
                } else {
                    // Show search controls
                    searchControlsLayout.setVisibility(View.VISIBLE);
                    searchEditText.requestFocus(); // Focus on search input
                }
            });
        }

        // Search text change listener
        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    viewModel.setSearchQuery(s.toString());
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        // Include completed checkbox listener
        if (includeCompletedCheckBox != null) {
            includeCompletedCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                viewModel.setIncludeCompleted(isChecked);
                Log.d("MyToDo", "includeCompletedCheckBox: Include completed changed: " + isChecked);
            });
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskAdapter(new ArrayList<>(), viewModel, this, task -> {
            selectedTask = task;
            updateButtonStates();
        });
        recyclerView.setAdapter(adapter);
        

        // Request notification permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_POST_NOTIFICATIONS);
            }
        }

        // Enhanced drag and drop with multi-position support and visual feedback
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                try {
                    int fromPosition = viewHolder.getAdapterPosition();
                    int toPosition = target.getAdapterPosition();

                    Log.d("MyToDo", "Enhanced onMove: Attempting to move from position " + fromPosition + " to " + toPosition);
                    
                    // Get item descriptions for better logging
                    Object fromItem = adapter.getItem(fromPosition);
                    Object toItem = adapter.getItem(toPosition);
                    String fromDesc = (fromItem instanceof Task) ? ((Task) fromItem).description : "header:" + fromItem;
                    String toDesc = (toItem instanceof Task) ? ((Task) toItem).description : "header:" + toItem;
                    Log.d("MyToDo", "Enhanced onMove: Moving '" + fromDesc + "' to position of '" + toDesc + "'");

                    // Validate positions
                    if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                        Log.d("MyToDo", "Enhanced onMove: Invalid positions");
                        return false;
                    }

                    // Only allow moving tasks (not headers)
                    if (adapter.getItemViewType(fromPosition) != TaskAdapter.TYPE_TASK ||
                            adapter.getItemViewType(toPosition) != TaskAdapter.TYPE_TASK) {
                        Log.d("MyToDo", "Enhanced onMove: Cannot move non-task items");
                        return false;
                    }
                    
                    // Check if both items are in the same category
                    String fromCategory = adapter.getCategoryForPosition(fromPosition);
                    String toCategory = adapter.getCategoryForPosition(toPosition);
                    
                    if (fromCategory == null || toCategory == null || !fromCategory.equals(toCategory)) {
                        Log.d("MyToDo", "Enhanced onMove: Cannot move between different categories");
                        return false;
                    }

                    // Track the category and specific task being dragged for later database update
                    lastDraggedCategory = fromCategory;
                    actualDragOccurred = true; // Mark that an actual drag occurred
                    
                    // Track which specific task was dragged (reuse fromItem from above)
                    if (fromItem instanceof Task) {
                        draggedTask = (Task) fromItem;
                        Log.d("MyToDo", "Enhanced onMove: Tracking dragged task: " + draggedTask.description);
                    }

                    // Allow multi-position moves - this is the key enhancement!
                    adapter.moveItem(fromPosition, toPosition);
                    Log.d("MyToDo", "Enhanced onMove: Successfully moved item from " + fromPosition + " to " + toPosition);
                    return true;
                    
                } catch (Exception e) {
                    Log.e("MyToDo", "Enhanced onMove: Error during move operation", e);
                    return false;
                }
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                // No swiping for now
            }
            
            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    // Add visual feedback when dragging starts
                    if (viewHolder != null) {
                        viewHolder.itemView.setAlpha(0.8f);
                        viewHolder.itemView.setElevation(8f);
                        viewHolder.itemView.setScaleX(1.05f);
                        viewHolder.itemView.setScaleY(1.05f);
                        Log.d("MyToDo", "Enhanced drag: Drag started with visual feedback");
                    }
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    // Restore visual state when dragging ends
                    if (viewHolder != null) {
                        viewHolder.itemView.setAlpha(1.0f);
                        viewHolder.itemView.setElevation(0f);
                        viewHolder.itemView.setScaleX(1.0f);
                        viewHolder.itemView.setScaleY(1.0f);
                        Log.d("MyToDo", "Enhanced drag: Drag ended, restored visual state");
                    }
                }
            }
            
            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // Ensure visual state is restored
                viewHolder.itemView.setAlpha(1.0f);
                viewHolder.itemView.setElevation(0f);
                viewHolder.itemView.setScaleX(1.0f);
                viewHolder.itemView.setScaleY(1.0f);
                
                // Now that the drag is complete, persist the order to database
                if (lastDraggedCategory != null && actualDragOccurred) {
                    Log.d("MyToDo", "Drag completed - persisting order for category: " + lastDraggedCategory);
                    adapter.persistCategoryOrderToDatabase(lastDraggedCategory, viewModel, draggedTask);
                    lastDraggedCategory = null; // Reset for next drag operation
                    draggedTask = null; // Reset the dragged task
                    actualDragOccurred = false; // Reset for next drag operation
                } else if (lastDraggedCategory != null) {
                    Log.d("MyToDo", "Drag ended but no actual moves occurred - skipping database update");
                    lastDraggedCategory = null; // Reset for next drag operation
                    draggedTask = null; // Reset the dragged task
                    actualDragOccurred = false; // Reset for next drag operation
                }
            }
            
            @Override
            public boolean isLongPressDragEnabled() {
                // Enable long press drag on the drag handle
                return true;
            }
        });
        
        itemTouchHelper.attachToRecyclerView(recyclerView);
        
        Log.d("MyToDo", "onCreate: Enhanced drag and drop implemented with visual feedback");

        viewModel.getTasksByCategory().observe(this, items -> {
            Log.d("MyToDo", "onCreate: Tasks observed, count: " + items.size());
            for (Object item : items) {
                Log.d("MyToDo", "onCreate: Item: " + (item instanceof String ? item : ((Task) item).description + ", id: " + ((Task) item).id));
            }
            adapter.setItems(items);
            boolean isEmpty = items.isEmpty();
            emptyStateTextView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            Log.d("MyToDo", "onCreate: Empty state visibility: " + (isEmpty ? "VISIBLE" : "GONE"));
            Log.d("MyToDo", "onCreate: Empty state text: " + emptyStateTextView.getText().toString());
        });
        viewModel.getSearchQuery().observe(this, query -> {
            Log.d("MyToDo", "onCreate: Search query observed: " + query);
        });
        viewModel.getIncludeCompleted().observe(this, include -> {
            Log.d("MyToDo", "onCreate: Include completed observed: " + include);
            includeCompletedCheckBox.setChecked(include);
        });
        viewModel.getAllTasks().observe(this, tasks -> {
            Log.d("MyToDo", "onCreate: Main getAllTasks observer triggered with " + (tasks != null ? tasks.size() : "null") + " tasks");
            if (tasks != null) {
                Log.d("MyToDo", "onCreate: Main getAllTasks observer calling updateTasksByCategory() with " + tasks.size() + " tasks");
                viewModel.updateTasksByCategory(tasks);
                Log.d("MyToDo", "onCreate: Main getAllTasks observer finished updateTasksByCategory()");
            } else {
                Log.w("MyToDo", "onCreate: Main getAllTasks observer received null tasks");
            }
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setSearchQuery(s.toString());
                Log.d("MyToDo", "searchEditText: Search query changed: " + s.toString());
            }
        });

        includeCompletedCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setIncludeCompleted(isChecked);
            Log.d("MyToDo", "includeCompletedCheckBox: Include completed changed: " + isChecked);
        });

        // Note: Button click listeners are set up earlier in the onCreate method with proper flag management

    }



    private void setLocale(String language) {
        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(
                (language != null && language.startsWith("he")) ? "he-IL" : "en-US");
        AppCompatDelegate.setApplicationLocales(appLocale);
        Log.d("MyToDo", "setLocale: Applied app locale: " + (language != null ? language : "en"));
    }
    
    private void handleEditTaskFromReminder(Intent intent) {
        Log.d("MyToDo", "handleEditTaskFromReminder: Processing edit intent from reminder");
        int taskId = intent.getIntExtra("task_id", -1);
        if (taskId == -1) {
            Log.e("MyToDo", "handleEditTaskFromReminder: No task_id in intent");
            return;
        }
        
        // Stop the reminder service if requested
        boolean stopReminder = intent.getBooleanExtra("stop_reminder", false);
        if (stopReminder) {
            Log.d("MyToDo", "handleEditTaskFromReminder: Stopping reminder service for task: " + taskId);
            Intent stopServiceIntent = new Intent(this, ReminderService.class);
            stopServiceIntent.setAction("STOP_REMINDER");
            stopServiceIntent.putExtra("task_id", taskId);
            startService(stopServiceIntent);
        }
        
        Log.d("MyToDo", "handleEditTaskFromReminder: Looking for task with id: " + taskId);
        
        // Find and select the task, then show edit dialog
        Log.d("MyToDo", "handleEditTaskFromReminder: Setting up ONE-TIME getAllTasks observer for reminder edit");
        Observer<List<Task>> reminderObserver = new Observer<List<Task>>() {
            @Override
            public void onChanged(List<Task> tasks) {
                if (tasks != null) {
                    Log.d("MyToDo", "handleEditTaskFromReminder: Reminder observer triggered with " + tasks.size() + " tasks, looking for taskId: " + taskId);
                    for (Task task : tasks) {
                        if (task.id == taskId) {
                            Log.d("MyToDo", "handleEditTaskFromReminder: Found task: " + task.description + ", id: " + task.id);
                            
                            selectedTask = task;
                            updateButtonStates();
                            Log.d("MyToDo", "handleEditTaskFromReminder: About to show edit dialog for task: " + task.description);
                            showTaskDialog(task);
                            Log.d("MyToDo", "handleEditTaskFromReminder: Edit dialog shown for task: " + task.description);
                            
                            // CRITICAL FIX: Remove this observer after processing to prevent re-triggering
                            viewModel.getAllTasks().removeObserver(this);
                            Log.d("MyToDo", "handleEditTaskFromReminder: Removed reminder observer to prevent dialog reopening");
                            break;
                        }
                    }
                } else {
                    Log.w("MyToDo", "handleEditTaskFromReminder: Reminder observer triggered with null tasks list");
                }
            }
        };
        viewModel.getAllTasks().observe(this, reminderObserver);
    }
    
    private void handleCompleteTaskFromReminder(Intent intent) {
        Log.d("MyToDo", "handleCompleteTaskFromReminder: Processing complete intent from reminder");
        Log.d("MyToDo", "handleCompleteTaskFromReminder: Intent action: " + intent.getAction());
        Log.d("MyToDo", "handleCompleteTaskFromReminder: Intent extras: " + intent.getExtras());
        
        int taskId = intent.getIntExtra("task_id", -1);
        if (taskId == -1) {
            Log.e("MyToDo", "handleCompleteTaskFromReminder: No task_id in intent");
            return;
        }
        
        // Stop the reminder service if requested
        boolean stopReminder = intent.getBooleanExtra("stop_reminder", false);
        if (stopReminder) {
            Log.d("MyToDo", "handleCompleteTaskFromReminder: Stopping reminder service for task: " + taskId);
            Intent stopServiceIntent = new Intent(this, ReminderService.class);
            stopServiceIntent.setAction("STOP_REMINDER");
            stopServiceIntent.putExtra("task_id", taskId);
            startService(stopServiceIntent);
        }
        
        Log.d("MyToDo", "handleCompleteTaskFromReminder: Marking task as completed, id: " + taskId);
        
        // Mark the task as completed in the database
        executorService.execute(() -> {
            try {
                AppDatabase database = AppDatabase.getDatabase(this);
                TaskDao taskDao = database.taskDao();
                
                Task task = taskDao.getTaskById(taskId);
                if (task != null) {
                    Log.d("MyToDo", "handleCompleteTaskFromReminder: Found task: " + task.description + ", marking as completed");
                    Log.d("MyToDo", "handleCompleteTaskFromReminder: Task before update - isCompleted: " + task.isCompleted);
                    
                    task.isCompleted = true;
                    // Set completion date so the task moves to completed category
                    task.completionDate = System.currentTimeMillis();
                    taskDao.update(task);
                    Log.d("MyToDo", "handleCompleteTaskFromReminder: Task marked as completed: " + task.description);
                    Log.d("MyToDo", "handleCompleteTaskFromReminder: Task completion date set to: " + task.completionDate);
                    
                    // Verify the update was successful by reading the task again
                    Task updatedTask = taskDao.getTaskById(taskId);
                    if (updatedTask != null) {
                        Log.d("MyToDo", "handleCompleteTaskFromReminder: Verification - task after update - isCompleted: " + updatedTask.isCompleted + ", completionDate: " + updatedTask.completionDate);
                    }
                    
                    // Refresh the UI on the main thread
                    runOnUiThread(() -> {
                        Log.d("MyToDo", "handleCompleteTaskFromReminder: Refreshing UI after task completion");
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                        // Add a small delay to allow database transaction to complete
                        // then force refresh the ViewModel
                        new android.os.Handler().postDelayed(() -> {
                            viewModel.forceRefreshTasks();
                        }, 100);
                    });
                } else {
                    Log.w("MyToDo", "handleCompleteTaskFromReminder: Task not found: " + taskId);
                }
            } catch (Exception e) {
                Log.e("MyToDo", "handleCompleteTaskFromReminder: Error completing task", e);
            }
        });
    }

    private void showTaskDialog(Task task) {
        Log.d("MyToDo", "showTaskDialog: Current intent action: " + (getIntent() != null ? getIntent().getAction() : "null"));
        Log.d("MyToDo", "showTaskDialog: isProcessingEditFromReminder flag: " + isProcessingEditFromReminder);
        // Log.d("MyToDo", "showTaskDialog: Stack trace for dialog opening:");
        // Thread.dumpStack();
        
        // Clear any pending EDIT_TASK_FROM_REMINDER intent to prevent dialog from reopening
        if (getIntent() != null && "EDIT_TASK_FROM_REMINDER".equals(getIntent().getAction())) {
            Log.d("MyToDo", "showTaskDialog: Clearing EDIT_TASK_FROM_REMINDER intent before showing dialog");
            setIntent(new Intent());
        }
        
        Log.d("MyToDo", "showTaskDialog: Opening task dialog for task: " + (task != null ? task.description + ", id: " + task.id : "new task"));
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_task, null);
        builder.setView(dialogView);

        EditText descriptionEditText = dialogView.findViewById(R.id.descriptionEditText);
        ImageButton dueDateButton = dialogView.findViewById(R.id.dueDateButton);
        TextView dueDateTextView = dialogView.findViewById(R.id.dueDateTextView);
        ImageButton clearDateButton = dialogView.findViewById(R.id.clearDateButton);
        ImageButton dueTimeButton = dialogView.findViewById(R.id.dueTimeButton);
        TextView dueTimeTextView = dialogView.findViewById(R.id.dueTimeTextView);
        ImageButton clearTimeButton = dialogView.findViewById(R.id.clearTimeButton);
        Spinner dayOfWeekSpinner = dialogView.findViewById(R.id.dayOfWeekSpinner);
        CheckBox recurringCheckBox = dialogView.findViewById(R.id.recurringCheckBox);
        Spinner recurrenceTypeSpinner = dialogView.findViewById(R.id.recurrenceTypeSpinner);
        CheckBox reminderCheckBox = dialogView.findViewById(R.id.reminderCheckBox);
        Spinner reminderSpinner = dialogView.findViewById(R.id.reminderSpinner);
        
        // Reminder days controls
        LinearLayout reminderDaysLayout = dialogView.findViewById(R.id.reminderDaysLayout);
        CheckBox[] reminderDayCheckBoxes = new CheckBox[7];
        reminderDayCheckBoxes[0] = dialogView.findViewById(R.id.reminderSunday);
        reminderDayCheckBoxes[1] = dialogView.findViewById(R.id.reminderMonday);
        reminderDayCheckBoxes[2] = dialogView.findViewById(R.id.reminderTuesday);
        reminderDayCheckBoxes[3] = dialogView.findViewById(R.id.reminderWednesday);
        reminderDayCheckBoxes[4] = dialogView.findViewById(R.id.reminderThursday);
        reminderDayCheckBoxes[5] = dialogView.findViewById(R.id.reminderFriday);
        reminderDayCheckBoxes[6] = dialogView.findViewById(R.id.reminderSaturday);
        Button selectWeekdaysButton = dialogView.findViewById(R.id.selectWeekdaysButton);
        Button selectWeekendsButton = dialogView.findViewById(R.id.selectWeekendsButton);
        Button selectAllDaysButton = dialogView.findViewById(R.id.selectAllDaysButton);
        
        Button saveButton = dialogView.findViewById(R.id.saveButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);

        // Initialize from task or defaults
        if (task != null) {
            Log.d("MyToDo", "showTaskDialog: Populating form with task data - description: " + task.description + ", dayOfWeek: " + task.dayOfWeek + ", dueTime: " + task.dueTime + ", isRecurring: " + task.isRecurring + ", recurrenceType: " + task.recurrenceType + ", reminderOffset: " + task.reminderOffset);
            descriptionEditText.setText(task.description);
            selectedDueDate = task.dueDate;
            selectedDueTime = task.dueTime;
            selectedDayOfWeek = task.dayOfWeek;
            selectedReminderOffset = task.reminderOffset; // null means no reminder
            recurringCheckBox.setChecked(task.isRecurring);
            Log.d("MyToDo", "showTaskDialog: Form populated with - selectedDayOfWeek: " + selectedDayOfWeek + ", selectedReminderOffset: " + selectedReminderOffset + ", recurringCheckBox checked: " + recurringCheckBox.isChecked());
        } else {
            selectedDueDate = null;
            selectedDueTime = null;
            // Default day of week to the currently expanded category for new tasks
            String[] days = getResources().getStringArray(R.array.days_of_week);
            String currentExpandedCategory = null;
            
            // Get the currently expanded category from the adapter
            if (adapter != null) {
                // Find which category is currently expanded
                for (int i = 0; i < days.length; i++) {
                    if (adapter.isCategoryExpanded(days[i])) {
                        currentExpandedCategory = days[i];
                        break;
                    }
                }
            }
            
            // If no category is expanded, fall back to today's day
            if (currentExpandedCategory == null) {
                Calendar todayCal = Calendar.getInstance();
                int today = todayCal.get(Calendar.DAY_OF_WEEK);
                int idx;
                switch (today) {
                    case Calendar.SUNDAY: idx = 3; break;
                    case Calendar.MONDAY: idx = 4; break;
                    case Calendar.TUESDAY: idx = 5; break;
                    case Calendar.WEDNESDAY: idx = 6; break;
                    case Calendar.THURSDAY: idx = 7; break;
                    case Calendar.FRIDAY: idx = 8; break;
                    case Calendar.SATURDAY: idx = 9; break;
                    default: idx = 3; break;
                }
                selectedDayOfWeek = days[idx];
            } else {
                selectedDayOfWeek = currentExpandedCategory;
            }
            
            selectedReminderOffset = null;
            recurringCheckBox.setChecked(false);
        }

        // Day of week spinner
        String[] daysOfWeek = getResources().getStringArray(R.array.days_of_week);
        if (daysOfWeek == null || daysOfWeek.length == 0) {
            Log.e("MyToDo", "showTaskDialog: daysOfWeek array is null or empty, using fallback");
            daysOfWeek = getResources().getStringArray(R.array.days_of_week);
        }
        final String[] finalDaysOfWeek = daysOfWeek;
        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, daysOfWeek);
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dayOfWeekSpinner.setAdapter(dayAdapter);
        if (selectedDayOfWeek != null) {
            try {
                String mapped = mapDayOfWeekToCurrentLanguage(selectedDayOfWeek, daysOfWeek);
                int idx = findIndex(daysOfWeek, mapped);
                if (idx >= 0 && idx < daysOfWeek.length) {
                    dayOfWeekSpinner.setSelection(idx);
                } else {
                    dayOfWeekSpinner.setSelection(0);
                }
            } catch (Exception e) {
                Log.e("MyToDo", "showTaskDialog: Error setting day of week spinner selection", e);
                dayOfWeekSpinner.setSelection(0);
            }
        }
        dayOfWeekSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < finalDaysOfWeek.length) {
                    selectedDayOfWeek = finalDaysOfWeek[position];
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Recurrence spinner
        String[] recurrenceTypes = getResources().getStringArray(R.array.recurrence_types);
        if (recurrenceTypes == null || recurrenceTypes.length == 0) {
            Log.e("MyToDo", "showTaskDialog: recurrenceTypes array is null or empty, using fallback");
            recurrenceTypes = getResources().getStringArray(R.array.recurrence_types);
        }
        ArrayAdapter<String> recurrenceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, recurrenceTypes);
        recurrenceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recurrenceTypeSpinner.setAdapter(recurrenceAdapter);
        recurrenceTypeSpinner.setVisibility(recurringCheckBox.isChecked() ? View.VISIBLE : View.GONE);
        if (task != null && task.recurrenceType != null) {
            try {
                int rIdx = findIndex(recurrenceTypes, task.recurrenceType);
                if (rIdx >= 0 && rIdx < recurrenceTypes.length) {
                    recurrenceTypeSpinner.setSelection(rIdx);
                } else {
                    recurrenceTypeSpinner.setSelection(0);
                }
            } catch (Exception e) {
                Log.e("MyToDo", "showTaskDialog: Error setting recurrence spinner selection", e);
                recurrenceTypeSpinner.setSelection(0);
            }
        }
        // Recurrence type spinner listener - update reminder days visibility when selection changes
        recurrenceTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Update reminder days visibility when recurrence type changes
                updateReminderDaysVisibility(reminderDaysLayout, reminderCheckBox.isChecked(), recurringCheckBox.isChecked(), recurrenceTypeSpinner);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        recurringCheckBox.setOnCheckedChangeListener((b, checked) -> {
            recurrenceTypeSpinner.setVisibility(checked ? View.VISIBLE : View.GONE);
            // Update reminder days visibility when recurring checkbox changes
            updateReminderDaysVisibility(reminderDaysLayout, reminderCheckBox.isChecked(), checked, recurrenceTypeSpinner);
        });

        // Reminder spinner
        String[] reminderOptions = getResources().getStringArray(R.array.reminder_options);
        if (reminderOptions == null || reminderOptions.length == 0) {
            Log.e("MyToDo", "showTaskDialog: reminderOptions array is null or empty, using fallback");
            reminderOptions = getResources().getStringArray(R.array.reminder_options);
        }
        ArrayAdapter<String> reminderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, reminderOptions);
        reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reminderSpinner.setAdapter(reminderAdapter);
        // Only allow reminder when a time exists; null offset = no reminder
        reminderCheckBox.setEnabled(selectedDueTime != null);
        reminderCheckBox.setChecked(selectedDueTime != null && selectedReminderOffset != null);
        reminderSpinner.setVisibility(reminderCheckBox.isChecked() ? View.VISIBLE : View.GONE);
        
        // Safe reminder spinner selection
        try {
            int reminderPosition = getReminderOffsetPosition(selectedReminderOffset != null ? selectedReminderOffset : 0);
            if (reminderPosition >= 0 && reminderPosition < reminderOptions.length) {
                reminderSpinner.setSelection(reminderPosition);
            } else {
                reminderSpinner.setSelection(0);
            }
        } catch (Exception e) {
            Log.e("MyToDo", "showTaskDialog: Error setting reminder spinner selection", e);
            reminderSpinner.setSelection(0);
        }
        
        reminderCheckBox.setOnCheckedChangeListener((b, checked) -> {
            reminderSpinner.setVisibility(checked ? View.VISIBLE : View.GONE);
            updateReminderDaysVisibility(reminderDaysLayout, checked, recurringCheckBox.isChecked(), recurrenceTypeSpinner);
        });
        
        // Initialize reminder days from task data
        initializeReminderDays(task, reminderDayCheckBoxes);
        
        // Update reminder days visibility based on current state
        updateReminderDaysVisibility(reminderDaysLayout, reminderCheckBox.isChecked(), recurringCheckBox.isChecked(), recurrenceTypeSpinner);
        
        // Recurring checkbox listener - show/hide reminder days
        recurringCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            recurrenceTypeSpinner.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            updateReminderDaysVisibility(reminderDaysLayout, reminderCheckBox.isChecked(), isChecked, recurrenceTypeSpinner);
        });
        
        // Reminder days quick selection buttons
        selectWeekdaysButton.setOnClickListener(v -> {
            // Get current language to determine weekdays - check both stored preference and current locale
            String storedLang = prefs.getString(PREF_LANGUAGE, "en");
            String currentLocale = Locale.getDefault().getLanguage();
            
            // Use Hebrew weekdays if either stored preference is Hebrew OR current system locale is Hebrew
            boolean isHebrew = storedLang.startsWith("he") || currentLocale.startsWith("he") || currentLocale.startsWith("iw");
            
            boolean[] weekdays;
            if (isHebrew) {
                // Hebrew locale: weekdays are Sunday-Thursday (index 0-4)
                weekdays = new boolean[]{true, true, true, true, true, false, false};
            } else {
                // English/other locales: weekdays are Monday-Friday (index 1-5)
                weekdays = new boolean[]{false, true, true, true, true, true, false};
            }
            setReminderDays(reminderDayCheckBoxes, weekdays);
        });
        selectWeekendsButton.setOnClickListener(v -> {
            // Get current language to determine weekend days - check both stored preference and current locale
            String storedLang = prefs.getString(PREF_LANGUAGE, "en");
            String currentLocale = Locale.getDefault().getLanguage();
            
            // Use Hebrew weekend if either stored preference is Hebrew OR current system locale is Hebrew
            boolean isHebrew = storedLang.startsWith("he") || currentLocale.startsWith("he") || currentLocale.startsWith("iw");
            
            boolean[] weekendDays;
            if (isHebrew) {
                // Hebrew locale: weekend is Friday-Saturday (index 5-6)
                weekendDays = new boolean[]{false, false, false, false, false, true, true};
            } else {
                // English/other locales: weekend is Sunday-Saturday (index 0-6, but Sunday=0, Saturday=6)
                weekendDays = new boolean[]{true, false, false, false, false, false, true};
            }
            setReminderDays(reminderDayCheckBoxes, weekendDays);
        });
        selectAllDaysButton.setOnClickListener(v -> setReminderDays(reminderDayCheckBoxes, new boolean[]{true, true, true, true, true, true, true}));

        // Date/time labels
        updateDateLabel(dueDateTextView, selectedDueDate);
        updateTimeLabel(dueTimeTextView, selectedDueTime);
        clearDateButton.setVisibility(selectedDueDate != null ? View.VISIBLE : View.GONE);
        clearTimeButton.setVisibility(selectedDueTime != null ? View.VISIBLE : View.GONE);

        // Date picker
        dueDateButton.setOnClickListener(v -> {
            try {
                final Calendar cal = Calendar.getInstance();
                if (selectedDueDate != null) cal.setTimeInMillis(selectedDueDate);
                DatePickerDialog dlg = new DatePickerDialog(this, (view, y, m, d) -> {
                    try {
                        Calendar c = Calendar.getInstance();
                        c.set(Calendar.YEAR, y);
                        c.set(Calendar.MONTH, m);
                        c.set(Calendar.DAY_OF_MONTH, d);
                        c.set(Calendar.HOUR_OF_DAY, 0);
                        c.set(Calendar.MINUTE, 0);
                        c.set(Calendar.SECOND, 0);
                        c.set(Calendar.MILLISECOND, 0);
                        selectedDueDate = c.getTimeInMillis();
                        updateDateLabel(dueDateTextView, selectedDueDate);
                        clearDateButton.setVisibility(View.VISIBLE);
                    } catch (Exception e) {
                        Log.e("MyToDo", "showTaskDialog: Error in date picker callback", e);
                    }
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
                dlg.show();
            } catch (Exception e) {
                Log.e("MyToDo", "showTaskDialog: Error showing date picker", e);
            }
        });
        clearDateButton.setOnClickListener(v -> {
            selectedDueDate = null;
            updateDateLabel(dueDateTextView, null);
            clearDateButton.setVisibility(View.GONE);
        });

        // Time picker
        dueTimeButton.setOnClickListener(v -> {
            try {
                final Calendar cal = Calendar.getInstance();
                if (selectedDueTime != null) {
                    // Convert milliseconds since midnight to hour/minute for TimePickerDialog
                    int hours = (int) (selectedDueTime / (60 * 60 * 1000));
                    int minutes = (int) ((selectedDueTime % (60 * 60 * 1000)) / (60 * 1000));
                    cal.set(Calendar.HOUR_OF_DAY, hours);
                    cal.set(Calendar.MINUTE, minutes);
                }
                TimePickerDialog dlg = new TimePickerDialog(this, (view, h, m) -> {
                    try {
                        // Store time as milliseconds since midnight for proper sorting
                        selectedDueTime = (long) h * 60 * 60 * 1000 + (long) m * 60 * 1000;
                        updateTimeLabel(dueTimeTextView, selectedDueTime);
                        reminderCheckBox.setEnabled(true);
                        clearTimeButton.setVisibility(View.VISIBLE);
                    } catch (Exception e) {
                        Log.e("MyToDo", "showTaskDialog: Error in time picker callback", e);
                    }
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true);
                dlg.show();
            } catch (Exception e) {
                Log.e("MyToDo", "showTaskDialog: Error showing time picker", e);
            }
        });
        clearTimeButton.setOnClickListener(v -> {
            selectedDueTime = null;
            updateTimeLabel(dueTimeTextView, null);
            clearTimeButton.setVisibility(View.GONE);
            // Remove reminder when time removed
            selectedReminderOffset = null;
            reminderCheckBox.setChecked(false);
            reminderCheckBox.setEnabled(false);
            reminderSpinner.setVisibility(View.GONE);
        });

        final AlertDialog dialog = builder.create();

        saveButton.setOnClickListener(v -> {
            Log.d("MyToDo", "saveButton: Current intent action: " + (getIntent() != null ? getIntent().getAction() : "null"));
            Log.d("MyToDo", "saveButton: isProcessingEditFromReminder flag: " + isProcessingEditFromReminder);
            // Log.d("MyToDo", "saveButton: Stack trace for save button click:");
            // Thread.dumpStack();
            
            try {
                String description = descriptionEditText.getText().toString();
                boolean isReminderEnabled = reminderCheckBox.isChecked() && selectedDueTime != null;
                Integer reminderOffset = null;
                
                if (isReminderEnabled) {
                    try {
                        reminderOffset = getSelectedReminderOffset(reminderSpinner);
                        Log.d("MyToDo", "showTaskDialog: Reminder enabled, selected offset: " + reminderOffset);
                    } catch (Exception e) {
                        Log.e("MyToDo", "showTaskDialog: Error getting reminder offset", e);
                        reminderOffset = 0; // Default to "at time"
                    }
                } else {
                    Log.d("MyToDo", "showTaskDialog: Reminder disabled, setting offset to null");
                }

                if (description.isEmpty()) {
                    Toast.makeText(this, getString(R.string.description_required), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (task == null) {
                    // Calculate priority based on whether task has time or not
                    int defaultPriority = calculateNewTaskPriority(selectedDueTime, selectedDayOfWeek);
                    
                    Task newTask = new Task(description, selectedDueDate, selectedDayOfWeek, recurringCheckBox.isChecked(),
                            recurringCheckBox.isChecked() ? getSelectedRecurrenceType(recurrenceTypeSpinner) : null,
                            false, defaultPriority);
                    newTask.dueTime = selectedDueTime;
                    newTask.reminderOffset = reminderOffset;
                    newTask.reminderDays = getReminderDaysString(reminderDayCheckBoxes, recurringCheckBox.isChecked());
                    Log.d("MyToDo", "showTaskDialog: New task created - description=" + newTask.description + 
                          ", dueTime=" + newTask.dueTime + ", priority=" + newTask.priority + 
                          ", manualPosition=" + newTask.manualPosition + ", reminderOffset=" + reminderOffset);
                    if (reminderOffset != null && reminderOffset >= 0) {
                        try {
                            if (reminderOffset == 0) {
                                Log.d("MyToDo", "showTaskDialog: Scheduling reminder for new task AT the time (offset: 0)");
                            } else {
                                Log.d("MyToDo", "showTaskDialog: Scheduling reminder for new task " + reminderOffset + " minutes before");
                            }
                            scheduleReminder(newTask);
                        } catch (Exception e) {
                            Log.e("MyToDo", "showTaskDialog: Error scheduling reminder for new task", e);
                            // Continue with task creation even if reminder fails
                        }
                    } else {
                        Log.d("MyToDo", "showTaskDialog: No reminder scheduled for new task (offset: " + reminderOffset + ")");
                    }
                    Log.d("MyToDo", "showTaskDialog: About to call viewModel.insert for new task: " + newTask.description);
                    viewModel.insert(newTask);
                    Log.d("MyToDo", "showTaskDialog: viewModel.insert called for new task: " + newTask.description + ", id: " + newTask.id);
                    
                    // Sync new task to cloud if user is authenticated
                    if (authService.isUserSignedIn()) {
                        syncManager.forceSync(new SyncManager.SyncCallback() {
                            @Override
                            public void onSyncComplete(boolean success, String message) {
                                Log.d("MyToDo", "New task sync: " + (success ? "Success" : "Failed") + " - " + message);
                            }

                            @Override
                            public void onSyncProgress(String message) {
                                Log.d("MyToDo", "New task sync progress: " + message);
                            }
                        });
                    }
                } else {
                    // Cancel existing reminder before updating
                    if (task.reminderOffset != null && task.reminderOffset > 0) {
                        cancelReminder(task.id);
                        Log.d("MyToDo", "showTaskDialog: Cancelled existing reminder for task: " + task.description + ", id: " + task.id);
                    }
                    
                    task.description = description;
                    task.dueDate = selectedDueDate;
                    task.dueTime = selectedDueTime;
                    task.dayOfWeek = selectedDayOfWeek;
                    task.isRecurring = recurringCheckBox.isChecked();
                    task.recurrenceType = task.isRecurring ? getSelectedRecurrenceType(recurrenceTypeSpinner) : null;
                    task.reminderOffset = reminderOffset;
                    task.reminderDays = getReminderDaysString(reminderDayCheckBoxes, recurringCheckBox.isChecked());
                    
                    // Recalculate priority if time was added/changed
                    task.priority = calculateNewTaskPriority(selectedDueTime, selectedDayOfWeek);
                    Log.d("MyToDo", "showTaskDialog: Updated priority for existing task: " + task.description + " -> priority: " + task.priority);
                    Log.d("MyToDo", "showTaskDialog: Set reminder offset to: " + reminderOffset + " for task: " + task.description);
                    if (reminderOffset != null && reminderOffset >= 0) {
                        try {
                            if (reminderOffset == 0) {
                                Log.d("MyToDo", "showTaskDialog: Scheduling reminder for existing task AT the time (offset: 0)");
                            } else {
                                Log.d("MyToDo", "showTaskDialog: Scheduling reminder for existing task " + reminderOffset + " minutes before");
                            }
                            scheduleReminder(task);
                        } catch (Exception e) {
                            Log.e("MyToDo", "showTaskDialog: Error scheduling reminder for existing task", e);
                            // Continue with task update even if reminder fails
                        }
                    } else {
                        Log.d("MyToDo", "showTaskDialog: No reminder scheduled for existing task (offset: " + reminderOffset + ")");
                    }
                    Log.d("MyToDo", "showTaskDialog: About to call viewModel.update for existing task: " + task.description + ", id: " + task.id);
                    viewModel.update(task);
                    Log.d("MyToDo", "showTaskDialog: viewModel.update called for existing task: " + task.description + ", id: " + task.id);
                    
                    // Sync updated task to cloud if user is authenticated
                    if (authService.isUserSignedIn()) {
                        syncManager.forceSync(new SyncManager.SyncCallback() {
                            @Override
                            public void onSyncComplete(boolean success, String message) {
                                Log.d("MyToDo", "Task update sync: " + (success ? "Success" : "Failed") + " - " + message);
                            }

                            @Override
                            public void onSyncProgress(String message) {
                                Log.d("MyToDo", "Task update sync progress: " + message);
                            }
                        });
                    }
                }
                Log.d("MyToDo", "showTaskDialog: Dismissing dialog");
                // Log.d("MyToDo", "saveButton: About to call dialog.dismiss()");
                Log.d("MyToDo", "showTaskDialog: About to dismiss dialog - this should trigger ViewModel observers");
                dialog.dismiss();
                // Log.d("MyToDo", "saveButton: After dialog.dismiss() call");
                Log.d("MyToDo", "showTaskDialog: Dialog dismissed successfully");
                Log.d("MyToDo", "showTaskDialog: After dialog dismissal - ViewModel observers may be triggered");
                
                // Clear the processing flag to prevent dialog from reopening
                Log.d("MyToDo", "saveButton: About to clear processing flag from: " + isProcessingEditFromReminder);
                isProcessingEditFromReminder = false;
                Log.d("MyToDo", "saveButton: Cleared processing flag to: " + isProcessingEditFromReminder);
            } catch (Exception e) {
                Log.e("MyToDo", "showTaskDialog: Error in save button click listener", e);
                Toast.makeText(this, getString(R.string.error_saving_task), Toast.LENGTH_SHORT).show();
            }
        });

        cancelButton.setOnClickListener(v -> {
            Log.d("MyToDo", "cancelButton: Current intent action: " + (getIntent() != null ? getIntent().getAction() : "null"));
            Log.d("MyToDo", "cancelButton: isProcessingEditFromReminder flag: " + isProcessingEditFromReminder);
            // Log.d("MyToDo", "cancelButton: Stack trace for cancel button click:");
            // Thread.dumpStack();
            
            // Log.d("MyToDo", "cancelButton: About to call dialog.dismiss()");
            dialog.dismiss();
            // Log.d("MyToDo", "cancelButton: After dialog.dismiss() call");
            // Clear the processing flag to prevent dialog from reopening
            Log.d("MyToDo", "cancelButton: About to clear processing flag from: " + isProcessingEditFromReminder);
            isProcessingEditFromReminder = false;
            Log.d("MyToDo", "cancelButton: Cleared processing flag to: " + isProcessingEditFromReminder);
            Log.d("MyToDo", "showTaskDialog: Cancel button clicked, cleared processing flag");
        });

        // Log.d("MyToDo", "showTaskDialog: About to show dialog");
        dialog.show();
        // Log.d("MyToDo", "showTaskDialog: Dialog.show() called successfully");
    }

    private void showDeleteConfirmationDialog(Task task) {
        Log.d("MyToDo", "showDeleteConfirmationDialog: Showing confirmation for task: " + task.description + ", id: " + task.id);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.delete_task))
               .setMessage(getString(R.string.delete_task_confirmation, task.description))
               .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                   Log.d("MyToDo", "showDeleteConfirmationDialog: User confirmed deletion of task: " + task.description + ", id: " + task.id);
                   
                   // Cancel any pending reminders for this task
                   if (task.reminderOffset != null) {
                       cancelReminder(task.id);
                       Log.d("MyToDo", "showDeleteConfirmationDialog: Cancelled reminder for task: " + task.description);
                   }
                   
                   // Delete the task
                   viewModel.delete(task);
                   selectedTask = null;
                   updateButtonStates();
                   
                   // Sync task deletion to cloud if user is authenticated
                   if (authService.isUserSignedIn()) {
                       syncManager.forceSync(new SyncManager.SyncCallback() {
                           @Override
                           public void onSyncComplete(boolean success, String message) {
                               Log.d("MyToDo", "Task deletion sync: " + (success ? "Success" : "Failed") + " - " + message);
                           }

                           @Override
                           public void onSyncProgress(String message) {
                               Log.d("MyToDo", "Task deletion sync progress: " + message);
                           }
                       });
                   }
                   
                   Toast.makeText(this, getString(R.string.task_deleted), Toast.LENGTH_SHORT).show();
                   Log.d("MyToDo", "showDeleteConfirmationDialog: Task deleted successfully: " + task.description);
               })
               .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                   Log.d("MyToDo", "showDeleteConfirmationDialog: User cancelled deletion of task: " + task.description);
                   dialog.dismiss();
               })
               .setCancelable(true);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        Log.d("MyToDo", "showDeleteConfirmationDialog: Confirmation dialog shown for task: " + task.description);
    }

    private int findIndex(String[] array, String value) {
        try {
            if (value == null || array == null) return -1;
            for (int i = 0; i < array.length; i++) {
                if (value.equals(array[i])) return i;
            }
            return -1;
        } catch (Exception e) {
            Log.e("MyToDo", "findIndex: Error finding index in array", e);
            return -1;
        }
    }

    private void updateDateLabel(TextView textView, Long millis) {
        try {
            if (textView == null) return;
            if (millis == null) {
                textView.setText("");
            } else {
                textView.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(millis)));
            }
        } catch (Exception e) {
            Log.e("MyToDo", "updateDateLabel: Error updating date label", e);
            if (textView != null) {
                textView.setText("");
            }
        }
    }

    private void updateTimeLabel(TextView textView, Long millisSinceMidnight) {
        try {
            if (textView == null) return;
            if (millisSinceMidnight == null) {
                textView.setText("");
            } else {
                // Convert milliseconds since midnight to hour:minute format
                int hours = (int) (millisSinceMidnight / (60 * 60 * 1000));
                int minutes = (int) ((millisSinceMidnight % (60 * 60 * 1000)) / (60 * 1000));
                textView.setText(String.format(Locale.getDefault(), "%02d:%02d", hours, minutes));
            }
        } catch (Exception e) {
            Log.e("MyToDo", "updateTimeLabel: Error updating time label", e);
            if (textView != null) {
                textView.setText("");
            }
        }
    }

    private String mapDayOfWeekToCurrentLanguage(String storedDayOfWeek, String[] currentDaysOfWeek) {
        try {
            if (storedDayOfWeek == null) return null;
            if (currentDaysOfWeek == null || currentDaysOfWeek.length < 3) {
                Log.e("MyToDo", "mapDayOfWeekToCurrentLanguage: currentDaysOfWeek array is invalid");
                return storedDayOfWeek;
            }
            
            if ("None".equals(storedDayOfWeek) || "ללא".equals(storedDayOfWeek)) {
                return currentDaysOfWeek[0];
            }
            if ("Immediate".equals(storedDayOfWeek) || "מיידי".equals(storedDayOfWeek)) {
                return currentDaysOfWeek[1];
            }
            if ("Soon".equals(storedDayOfWeek) || "בקרוב".equals(storedDayOfWeek)) {
                return currentDaysOfWeek[2];
            }
            
            // Handle Hebrew day names
            String[] hebrewDays = {"ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"};
            int hebrewIndex = -1;
            for (int i = 0; i < hebrewDays.length; i++) {
                if (hebrewDays[i].equals(storedDayOfWeek)) {
                    hebrewIndex = i;
                    break;
                }
            }
            if (hebrewIndex >= 0 && hebrewIndex + 3 < currentDaysOfWeek.length) {
                return currentDaysOfWeek[hebrewIndex + 3];
            }
            return storedDayOfWeek;
        } catch (Exception e) {
            Log.e("MyToDo", "mapDayOfWeekToCurrentLanguage: Error mapping day of week", e);
            return storedDayOfWeek;
        }
    }

    private void showMoveTaskDialog(Task task) {
        Log.d("MyToDo", "showMoveTaskDialog: Opening move dialog for task: " + task.description + ", id: " + task.id);
        String[] daysOfWeek = getResources().getStringArray(R.array.days_of_week);
        
        // Create a custom layout for better organization
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_move_task, null);
        
        // Set up the first row (Waiting and Soon)
        Button noneButton = dialogView.findViewById(R.id.noneButton);
        Button soonButton = dialogView.findViewById(R.id.soonButton);
        
        // Set up the weekday buttons (7 buttons total: 3+4 layout)
        Button weekday1Button = dialogView.findViewById(R.id.weekday1Button);
        Button weekday2Button = dialogView.findViewById(R.id.weekday2Button);
        Button weekday3Button = dialogView.findViewById(R.id.weekday3Button);
        Button weekday4Button = dialogView.findViewById(R.id.weekday4Button);
        Button weekday5Button = dialogView.findViewById(R.id.weekday5Button);
        Button weekday6Button = dialogView.findViewById(R.id.weekday6Button);
        Button weekday7Button = dialogView.findViewById(R.id.weekday7Button);
        
        // Set up the fourth row (Immediate)
        Button immediateButton = dialogView.findViewById(R.id.immediateButton);
        
        // Set button texts for first and last rows
        if (daysOfWeek.length >= 10) {
            noneButton.setText(daysOfWeek[0]);      // Waiting
            soonButton.setText(daysOfWeek[2]);      // Soon
            immediateButton.setText(daysOfWeek[1]); // Immediate
        }
        
        // Calculate which day to start the weekdays from
        Calendar today = Calendar.getInstance();
        int todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK);
        int startDayIndex;
        
        // Convert Calendar day to our array index
        switch (todayDayOfWeek) {
            case Calendar.SUNDAY: startDayIndex = 3; break;
            case Calendar.MONDAY: startDayIndex = 4; break;
            case Calendar.TUESDAY: startDayIndex = 5; break;
            case Calendar.WEDNESDAY: startDayIndex = 6; break;
            case Calendar.THURSDAY: startDayIndex = 7; break;
            case Calendar.FRIDAY: startDayIndex = 8; break;
            case Calendar.SATURDAY: startDayIndex = 9; break;
            default: startDayIndex = 4; break;
        }
        
        Log.d("MyToDo", "showMoveTaskDialog: Today is " + daysOfWeek[startDayIndex] + " (index: " + startDayIndex + ")");
        
        // If task is already set to today, start from tomorrow
        String taskCurrentDay = task.dayOfWeek;
        if (taskCurrentDay != null && taskCurrentDay.equals(daysOfWeek[startDayIndex])) {
            // Move to next day, wrapping around from Saturday (9) to Sunday (3)
            if (startDayIndex == 9) {
                startDayIndex = 3; // Saturday -> Sunday
            } else {
                startDayIndex++; // Any other day -> next day
            }
            Log.d("MyToDo", "showMoveTaskDialog: Task is on today, starting from tomorrow: " + daysOfWeek[startDayIndex] + " (index: " + startDayIndex + ")");
        } else {
            Log.d("MyToDo", "showMoveTaskDialog: Task is NOT on today, starting from today: " + daysOfWeek[startDayIndex] + " (index: " + startDayIndex + ")");
        }
        
        // Populate all 7 weekday buttons starting from startDayIndex
        Button[] weekdayButtons = {weekday1Button, weekday2Button, weekday3Button, 
                                   weekday4Button, weekday5Button, weekday6Button, weekday7Button};
        
        Log.d("MyToDo", "showMoveTaskDialog: Populating all 7 weekday buttons starting from index " + startDayIndex);
        
        for (int i = 0; i < 7; i++) {
            // Use modulo 7 to wrap around, then add 3 to get back to our array indices (3-9)
            int dayIndex = ((startDayIndex - 3 + i) % 7) + 3;
            
            if (dayIndex >= 3 && dayIndex <= 9) {
                weekdayButtons[i].setText(daysOfWeek[dayIndex]);
                Log.d("MyToDo", "showMoveTaskDialog: Button " + i + " shows: " + daysOfWeek[dayIndex] + " (index: " + dayIndex + ")");
            }
        }
        
        // Now disable (gray out) the current category instead of hiding it
        if (taskCurrentDay != null) {
            if (taskCurrentDay.equals(daysOfWeek[0])) {
                // Task is in "Waiting" category - disable the waiting button
                noneButton.setEnabled(false);
                noneButton.setBackground(getResources().getDrawable(R.drawable.rounded_button_disabled));
                Log.d("MyToDo", "showMoveTaskDialog: Disabled waiting button - task is already in waiting category");
            } else if (taskCurrentDay.equals(daysOfWeek[1])) {
                // Task is in "Immediate" category - disable the immediate button
                immediateButton.setEnabled(false);
                immediateButton.setBackground(getResources().getDrawable(R.drawable.rounded_button_disabled));
                Log.d("MyToDo", "showMoveTaskDialog: Disabled immediate button - task is already in immediate category");
            } else if (taskCurrentDay.equals(daysOfWeek[2])) {
                // Task is in "Soon" category - disable the soon button
                soonButton.setEnabled(false);
                soonButton.setBackground(getResources().getDrawable(R.drawable.rounded_button_disabled));
                Log.d("MyToDo", "showMoveTaskDialog: Disabled soon button - task is already in soon category");
            } else {
                // Task is on a specific weekday - find and disable that button
                for (int i = 0; i < 7; i++) {
                    if (taskCurrentDay.equals(weekdayButtons[i].getText().toString())) {
                        weekdayButtons[i].setEnabled(false);
                        weekdayButtons[i].setBackground(getResources().getDrawable(R.drawable.rounded_button_disabled));
                        Log.d("MyToDo", "showMoveTaskDialog: Disabled weekday button " + i + " - task is already on " + taskCurrentDay);
                        break;
                    }
                }
            }
        }
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.move)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .create();
        
        // Set click listeners with dialog dismissal
        View.OnClickListener dayClickListener = v -> {
            String newDay = null;
            if (v == noneButton && noneButton.isEnabled()) newDay = daysOfWeek[0];
            else if (v == soonButton && soonButton.isEnabled()) newDay = daysOfWeek[2];
            else if (v == immediateButton && immediateButton.isEnabled()) newDay = daysOfWeek[1];
            else if (v == weekday1Button && weekday1Button.isEnabled()) newDay = weekday1Button.getText().toString();
            else if (v == weekday2Button && weekday2Button.isEnabled()) newDay = weekday2Button.getText().toString();
            else if (v == weekday3Button && weekday3Button.isEnabled()) newDay = weekday3Button.getText().toString();
            else if (v == weekday4Button && weekday4Button.isEnabled()) newDay = weekday4Button.getText().toString();
            else if (v == weekday5Button && weekday5Button.isEnabled()) newDay = weekday5Button.getText().toString();
            else if (v == weekday6Button && weekday6Button.isEnabled()) newDay = weekday6Button.getText().toString();
            else if (v == weekday7Button && weekday7Button.isEnabled()) newDay = weekday7Button.getText().toString();
            
            if (newDay != null) {
                task.dayOfWeek = newDay;
                viewModel.update(task);
                Log.d("MyToDo", "showMoveTaskDialog: Task moved to day: " + newDay + ", id: " + task.id);
                
                // Sync task move to cloud if user is authenticated
                if (authService.isUserSignedIn()) {
                    syncManager.forceSync(new SyncManager.SyncCallback() {
                        @Override
                        public void onSyncComplete(boolean success, String message) {
                            Log.d("MyToDo", "Task move sync: " + (success ? "Success" : "Failed") + " - " + message);
                        }

                        @Override
                        public void onSyncProgress(String message) {
                            Log.d("MyToDo", "Task move sync progress: " + message);
                        }
                    });
                }
                
                // Close the dialog after moving the task
                dialog.dismiss();
            }
        };
        
        noneButton.setOnClickListener(dayClickListener);
        soonButton.setOnClickListener(dayClickListener);
        immediateButton.setOnClickListener(dayClickListener);
        weekday1Button.setOnClickListener(dayClickListener);
        weekday2Button.setOnClickListener(dayClickListener);
        weekday3Button.setOnClickListener(dayClickListener);
        weekday4Button.setOnClickListener(dayClickListener);
        weekday5Button.setOnClickListener(dayClickListener);
        weekday6Button.setOnClickListener(dayClickListener);
        weekday7Button.setOnClickListener(dayClickListener);
        
        dialog.show();
    }

    private void dismissDialog() {
        if (getFragmentManager().findFragmentByTag("task_dialog") != null) {
            getFragmentManager().beginTransaction().remove(getFragmentManager().findFragmentByTag("task_dialog")).commit();
        }
        if (getFragmentManager().findFragmentByTag("move_task_dialog") != null) {
            getFragmentManager().beginTransaction().remove(getFragmentManager().findFragmentByTag("move_task_dialog")).commit();
        }
    }

    private void updateButtonStates() {
        boolean hasSelectedTask = selectedTask != null;
        editButton.setEnabled(hasSelectedTask);
        moveButton.setEnabled(hasSelectedTask);
        deleteButton.setEnabled(hasSelectedTask);
        
        // Handle complete button with special logic for daily tasks
        boolean canComplete = hasSelectedTask && canCompleteTask(selectedTask);
        String completionTooltip = null;
        
        if (hasSelectedTask) {
            // Update complete button text based on task completion status
            completeButton.setText(selectedTask.isCompleted ? getString(R.string.uncomplete) : getString(R.string.complete));
            
            // If can't complete, set tooltip
            if (!canCompleteTask(selectedTask)) {
                completionTooltip = getString(R.string.daily_task_wrong_day);
                Log.d("MyToDo", "updateButtonStates: Disabling complete button for daily task " + 
                      selectedTask.description + " - can only be completed on current day");
            }
        }
        
        completeButton.setEnabled(canComplete);
        
        // Set content description for accessibility
        if (completionTooltip != null) {
            completeButton.setContentDescription(completionTooltip);
        } else {
            completeButton.setContentDescription(null);
        }
    }

    public void openEditDialog(Task task) {
        Log.d("MyToDo", "openEditDialog: Opening edit dialog for task: " + task.description + ", id: " + task.id);
        showTaskDialog(task);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, retrieve and schedule the pending task
                SharedPreferences tempPrefs = getSharedPreferences("TempTask", MODE_PRIVATE);
                String description = tempPrefs.getString("task_to_schedule", null);
                if (description != null) {
                    int taskId = tempPrefs.getInt("task_id", 0);
                    long dueDate = tempPrefs.getLong("due_date", 0);
                    long dueTime = tempPrefs.getLong("due_time", 0);
                    int reminderOffset = tempPrefs.getInt("reminder_offset", 0);
                    String dayOfWeek = tempPrefs.getString("day_of_week", null);
                    boolean reminderEnabled = tempPrefs.getBoolean("reminder_enabled", false);
                    Task task = new Task(description, dueDate != 0 ? dueDate : null, dayOfWeek, false, null, false, 0);
                    task.id = taskId;
                    task.dueTime = dueTime != 0 ? dueTime : null;
                    task.reminderOffset = reminderEnabled ? reminderOffset : 0;
                    if (reminderEnabled) {
                        scheduleReminder(task);
                    }
                    // Clear temporary storage
                    tempPrefs.edit().clear().apply();
                    Log.d("MyToDo", "onRequestPermissionsResult: Scheduled pending reminder for task: " + description + " after permission granted, id: " + taskId);
                }
            } else {
                Log.w("MyToDo", "onRequestPermissionsResult: POST_NOTIFICATIONS permission denied, reminders will not work");
            }
        }
    }

    private void requestExactAlarmPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.exact_alarms_required))
                            .setMessage(getString(R.string.exact_alarms_message))
                            .setPositiveButton(getString(R.string.open_settings), (dialog, which) -> {
                                try {
                                    Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                                    startActivity(intent);
                                } catch (Exception e) {
                                    Log.e("MyToDo", "Error opening exact alarm settings", e);
                                    // Fallback to general settings
                                    try {
                                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                        intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
                                        startActivity(intent);
                                    } catch (Exception fallbackException) {
                                        Log.e("MyToDo", "Error opening app settings", fallbackException);
                                    }
                                }
                            })
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show();
                }
            } catch (Exception e) {
                Log.e("MyToDo", "Error checking exact alarm permission", e);
            }
        }
    }

    private void scheduleReminder(Task task) {
        try {
            if (task == null) {
                Log.e("MyToDo", "scheduleReminder: Task is null");
                return;
            }
            
            if (task.dueTime == null || task.reminderOffset == null || task.reminderOffset < 0) {
                Log.w("MyToDo", "scheduleReminder: Cannot schedule reminder for task: " + task.description + ", dueTime: " + task.dueTime + ", reminderOffset: " + task.reminderOffset + ", id: " + task.id);
                return;
            }
            
            // Check if this is a daily recurring task with specific reminder days
            if (task.isRecurring && task.reminderDays != null && !task.reminderDays.isEmpty()) {
                String[] recurrenceTypes = getResources().getStringArray(R.array.recurrence_types);
                String dailyRecurrenceType = recurrenceTypes[0]; // "Daily" or "יומי"
                boolean isDailyRecurring = dailyRecurrenceType.equals(task.recurrenceType) || 
                                         "Daily".equals(task.recurrenceType) || 
                                         "יומי".equals(task.recurrenceType);
                
                if (isDailyRecurring) {
                    // Parse reminder days and check if today is included
                    Calendar today = Calendar.getInstance();
                    int todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK) - 1; // Convert to 0=Sunday, 1=Monday, etc.
                    
                    boolean shouldRemindToday = false;
                    String[] dayIndices = task.reminderDays.split(",");
                    for (String dayIndexStr : dayIndices) {
                        try {
                            int dayIndex = Integer.parseInt(dayIndexStr.trim());
                            if (dayIndex == todayDayOfWeek) {
                                shouldRemindToday = true;
                                break;
                            }
                        } catch (NumberFormatException e) {
                            Log.e("MyToDo", "scheduleReminder: Invalid day index in reminderDays: " + dayIndexStr, e);
                        }
                    }
                    
                    if (!shouldRemindToday) {
                        Log.d("MyToDo", "scheduleReminder: Daily recurring task '" + task.description + 
                              "' does not have reminder enabled for today (day " + todayDayOfWeek + "), skipping");
                        return;
                    } else {
                        Log.d("MyToDo", "scheduleReminder: Daily recurring task '" + task.description + 
                              "' has reminder enabled for today (day " + todayDayOfWeek + "), proceeding");
                    }
                }
            }
            
            // Check notification permission for Android 13+
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w("MyToDo", "scheduleReminder: POST_NOTIFICATIONS not granted; requesting permission first");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
                // Store pending task data to schedule after permission (best-effort; id may be 0 for new tasks)
                SharedPreferences tempPrefs = getSharedPreferences("TempTask", MODE_PRIVATE);
                tempPrefs.edit()
                        .putString("task_to_schedule", task.description)
                        .putInt("task_id", task.id)
                        .putLong("due_date", task.dueDate != null ? task.dueDate : 0)
                        .putLong("due_time", task.dueTime != null ? task.dueTime : 0)
                        .putInt("reminder_offset", task.reminderOffset)
                        .putString("day_of_week", task.dayOfWeek)
                        .putBoolean("reminder_enabled", true)
                        .apply();
                return;
            }
            
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                Log.e("MyToDo", "scheduleReminder: AlarmManager is null");
                return;
            }
            
            // Check if exact alarms are allowed (Android 12+)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    if (!alarmManager.canScheduleExactAlarms()) {
                        Log.w("MyToDo", "scheduleReminder: Exact alarms not allowed, requesting permission");
                        requestExactAlarmPermission();
                        return;
                    }
                } catch (Exception e) {
                    Log.w("MyToDo", "scheduleReminder: Could not check exact alarm permission", e);
                }
            }
            
            // Calculate the reminder time
            // Convert milliseconds since midnight to hours and minutes
            long millisSinceMidnight = task.dueTime;
            int hours = (int) (millisSinceMidnight / (60 * 60 * 1000));
            int minutes = (int) ((millisSinceMidnight % (60 * 60 * 1000)) / (60 * 1000));
            
            Calendar reminderTime = Calendar.getInstance();
            reminderTime.set(Calendar.HOUR_OF_DAY, hours);
            reminderTime.set(Calendar.MINUTE, minutes);
            reminderTime.set(Calendar.SECOND, 0);
            reminderTime.set(Calendar.MILLISECOND, 0);
            reminderTime.add(Calendar.MINUTE, -task.reminderOffset);

            String[] daysOfWeek = getResources().getStringArray(R.array.days_of_week);
            if (daysOfWeek == null || daysOfWeek.length < 3) {
                Log.e("MyToDo", "scheduleReminder: daysOfWeek array is invalid");
                return;
            }
            
            if (task.dueDate != null) {
                // Task has both date and time - set the specific date, then apply the time
                reminderTime.setTimeInMillis(task.dueDate);
                reminderTime.set(Calendar.HOUR_OF_DAY, hours);
                reminderTime.set(Calendar.MINUTE, minutes);
                reminderTime.set(Calendar.SECOND, 0);
                reminderTime.set(Calendar.MILLISECOND, 0);
                reminderTime.add(Calendar.MINUTE, -task.reminderOffset);
            } else if (task.dayOfWeek != null && daysOfWeek.length > 2 && 
                    !task.dayOfWeek.equals(daysOfWeek[0]) &&
                    !task.dayOfWeek.equals(daysOfWeek[1]) &&
                    !task.dayOfWeek.equals(daysOfWeek[2])) {
                // Calculate next occurrence of dayOfWeek
                int targetDay = -1;
                for (int i = 3; i < daysOfWeek.length; i++) {
                    if (daysOfWeek[i].equals(task.dayOfWeek)) {
                        targetDay = i - 2; // Map to Calendar.DAY_OF_WEEK (1=Sunday, ..., 7=Saturday)
                        break;
                    }
                }
                if (targetDay != -1) {
                    Calendar today = Calendar.getInstance();
                    int currentDay = today.get(Calendar.DAY_OF_WEEK);
                    int daysToAdd = (targetDay - currentDay + 7) % 7;
                    if (daysToAdd == 0 && today.getTimeInMillis() > reminderTime.getTimeInMillis()) {
                        daysToAdd = 7; // Schedule for next week if time has passed today
                    }
                    reminderTime.add(Calendar.DAY_OF_MONTH, daysToAdd);
                }
            } else {
                // Handle time-only tasks (Immediate/None/Soon or no dayOfWeek)
                Calendar now = Calendar.getInstance();
                Calendar candidate = Calendar.getInstance();
                candidate.set(Calendar.HOUR_OF_DAY, hours);
                candidate.set(Calendar.MINUTE, minutes);
                candidate.set(Calendar.SECOND, 0);
                candidate.set(Calendar.MILLISECOND, 0);
                if (candidate.getTimeInMillis() <= now.getTimeInMillis()) {
                    candidate.add(Calendar.DAY_OF_MONTH, 1); // schedule for next day
                }
                reminderTime.setTimeInMillis(candidate.getTimeInMillis());
                reminderTime.add(Calendar.MINUTE, -task.reminderOffset);
            }

            // Ensure reminder time is in the future
            if (reminderTime.getTimeInMillis() <= System.currentTimeMillis()) {
                Log.w("MyToDo", "scheduleReminder: Reminder time is in the past, scheduling for tomorrow");
                reminderTime.add(Calendar.DAY_OF_MONTH, 1);
            }

            // Additional safety check - ensure time is still in the future after adjustments
            if (reminderTime.getTimeInMillis() <= System.currentTimeMillis()) {
                Log.w("MyToDo", "scheduleReminder: Reminder time still in the past after adjustment, adding another day");
                reminderTime.add(Calendar.DAY_OF_MONTH, 1);
            }

            // Final safety check - if time is still invalid, schedule for 1 hour from now
            if (reminderTime.getTimeInMillis() <= System.currentTimeMillis()) {
                Log.w("MyToDo", "scheduleReminder: Using fallback time - 1 hour from now");
                reminderTime = Calendar.getInstance();
                reminderTime.add(Calendar.HOUR_OF_DAY, 1);
            }

            // Use ReminderManager instead of direct service scheduling for better reliability
            ReminderManager reminderManager = new ReminderManager(this);
            long reminderTimeMillis = reminderTime.getTimeInMillis();
            
            // Create a copy of the task with the current reminder offset for ReminderManager
            Task taskForReminder = new Task(task.description, task.dueDate, task.dayOfWeek, 
                    task.isRecurring, task.recurrenceType, task.isCompleted, task.priority);
            taskForReminder.id = task.id;
            taskForReminder.dueTime = task.dueTime;
            taskForReminder.reminderOffset = task.reminderOffset;
            
            Log.d("MyToDo", "scheduleReminder: Task reminder offset: " + task.reminderOffset + ", taskForReminder offset: " + taskForReminder.reminderOffset);
            
            reminderManager.scheduleReminder(taskForReminder, reminderTimeMillis);
            Log.d("MyToDo", "scheduleReminder: Scheduled reminder using ReminderManager for task: " + task.description + " at " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(reminderTime.getTime()) + ", id: " + task.id);
            
        } catch (Exception e) {
            Log.e("MyToDo", "scheduleReminder: Error scheduling reminder for task: " + (task != null ? task.description : "null") + ", id: " + (task != null ? task.id : "null"), e);
        }
    }

    private void cancelReminder(int taskId) {
        try {
            if (taskId <= 0) {
                Log.w("MyToDo", "cancelReminder: Invalid taskId: " + taskId);
                return;
            }
            
            // Use ReminderManager to cancel the main reminder
            ReminderManager reminderManager = new ReminderManager(this);
            // We need to create a dummy task to cancel the reminder
            Task dummyTask = new Task("", null, null, false, null, false, 0);
            dummyTask.id = taskId;
            reminderManager.cancelReminder(dummyTask);
            Log.d("MyToDo", "cancelReminder: Canceled main reminder for taskId: " + taskId);
            
            // Also cancel any pending snooze alarms for this task
            cancelSnoozeReminder(taskId);
            Log.d("MyToDo", "cancelReminder: Canceled snooze reminder for taskId: " + taskId);
            
        } catch (Exception e) {
            Log.e("MyToDo", "cancelReminder: Error canceling reminder for taskId: " + taskId, e);
        }
    }
    
    // Cancel snooze reminder for a task by ID
    private void cancelSnoozeReminder(int taskId) {
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                Log.e("MyToDo", "cancelSnoozeReminder: AlarmManager is null");
                return;
            }
            
            // Create the same intent that was used to schedule the snooze
            Intent snoozeIntent = new Intent(this, NotificationReceiver.class);
            snoozeIntent.putExtra("task_id", taskId);
            snoozeIntent.putExtra("task_description", ""); // Description doesn't matter for cancellation
            
            // Use the same request code as in NotificationReceiver.handleSnoozeAction
            int snoozeRequestCode = taskId; // Same as task ID
            
            PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                this, 
                snoozeRequestCode, 
                snoozeIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            alarmManager.cancel(snoozePendingIntent);
            Log.d("MyToDo", "cancelSnoozeReminder: Canceled snooze alarm for taskId: " + taskId + " with requestCode: " + snoozeRequestCode);
            
        } catch (Exception e) {
            Log.e("MyToDo", "cancelSnoozeReminder: Error canceling snooze reminder for taskId: " + taskId, e);
        }
    }

    private String getReminderOptionString(int offset) {
        try {
            String[] reminderOptions = getResources().getStringArray(R.array.reminder_options);
            if (reminderOptions == null || reminderOptions.length == 0) {
                Log.e("MyToDo", "getReminderOptionString: reminderOptions array is null or empty");
                return "Unknown";
            }
            
            switch (offset) {
                case 0:
                    return reminderOptions.length > 0 ? reminderOptions[0] : "At time"; // At time
                case 15:
                    return reminderOptions.length > 1 ? reminderOptions[1] : "15 minutes before";
                case 30:
                    return reminderOptions.length > 2 ? reminderOptions[2] : "30 minutes before";
                case 60:
                    return reminderOptions.length > 3 ? reminderOptions[3] : "60 minutes before";
                default:
                    return reminderOptions.length > 0 ? reminderOptions[0] : "Unknown";
            }
        } catch (Exception e) {
            Log.e("MyToDo", "getReminderOptionString: Error getting reminder option string for offset: " + offset, e);
            return "Unknown";
        }
    }

    private int getReminderOffsetPosition(int offset) {
        try {
            // Map minutes to spinner position
            switch (offset) {
                case 0:
                    return 0; // At time
                case 15:
                    return 1;
                case 30:
                    return 2;
                case 60:
                    return 3;
                default:
                    Log.w("MyToDo", "getReminderOffsetPosition: Invalid offset: " + offset + ", defaulting to 0");
                    return 0;
            }
        } catch (Exception e) {
            Log.e("MyToDo", "getReminderOffsetPosition: Error getting reminder position for offset: " + offset, e);
            return 0; // Default to "at time"
        }
    }

    private int getReminderOffsetFromPosition(int position) {
        try {
            // Map spinner position to minutes
            switch (position) {
                case 0:
                    return 0; // At time
                case 1:
                    return 15;
                case 2:
                    return 30;
                case 3:
                    return 60;
                default:
                    Log.w("MyToDo", "getReminderOffsetFromPosition: Invalid position: " + position + ", defaulting to 0");
                    return 0;
            }
        } catch (Exception e) {
            Log.e("MyToDo", "getReminderOffsetFromPosition: Error getting reminder offset for position: " + position, e);
            return 0; // Default to "at time"
        }
    }

    private String getSelectedRecurrenceType(Spinner spinner) {
        try {
            String[] recurrenceTypes = getResources().getStringArray(R.array.recurrence_types);
            if (recurrenceTypes == null || recurrenceTypes.length == 0) {
                Log.e("MyToDo", "getSelectedRecurrenceType: recurrenceTypes array is null or empty");
                return null;
            }
            return recurrenceTypes[spinner.getSelectedItemPosition()];
        } catch (Exception e) {
            Log.e("MyToDo", "getSelectedRecurrenceType: Error getting selected recurrence type", e);
            return null;
        }
    }

    private Integer getSelectedReminderOffset(Spinner spinner) {
        try {
            int selectedPosition = spinner.getSelectedItemPosition();
            if (selectedPosition >= 0) {
                return getReminderOffsetFromPosition(selectedPosition);
            } else {
                Log.w("MyToDo", "getSelectedReminderOffset: Invalid spinner position: " + selectedPosition);
                return 0; // Default to "at time"
            }
        } catch (Exception e) {
            Log.e("MyToDo", "getSelectedReminderOffset: Error getting selected reminder offset", e);
            return 0; // Default to "at time"
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (selectedContextMenuPosition == -1) {
            Log.e("MyToDo", "onContextItemSelected: No context menu position set, cannot process item: " + item.getTitle());
            return false;
        }

        Task task = adapter.getTaskAtPosition(selectedContextMenuPosition);
        if (task == null) {
            Log.e("MyToDo", "onContextItemSelected: No task found at position: " + selectedContextMenuPosition);
            // Log all visible view tags and adapter items for debugging
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                View child = recyclerView.getChildAt(i);
                RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
                if (holder instanceof TaskAdapter.TaskViewHolder) {
                    Object tag = child.getTag();
                    int holderPosition = holder.getAdapterPosition();
                    Log.d("MyToDo", "onContextItemSelected: Visible view at index " + i + ", position: " + holderPosition + ", tag: " + tag);
                    if (tag instanceof Integer) {
                        Task taggedTask = adapter.getTaskById((Integer) tag);
                        if (taggedTask != null) {
                            Log.d("MyToDo", "onContextItemSelected: Task for tag " + tag + ": " + taggedTask.description + ", id: " + taggedTask.id);
                        } else {
                            Log.w("MyToDo", "onContextItemSelected: No task found for tag: " + tag);
                        }
                    }
                }
            }
            // Log all adapter items
            for (int i = 0; i < adapter.getItemCount(); i++) {
                Object itemObj = adapter.getItem(i);
                Log.d("MyToDo", "onContextItemSelected: Adapter item at position " + i + ": " + (itemObj instanceof String ? itemObj : ((Task) itemObj).description + ", id: " + ((Task) itemObj).id));
            }
            selectedContextMenuPosition = -1; // Reset
            return false;
        }

        Log.d("MyToDo", "onContextItemSelected: Selected item: " + item.getTitle() + " for task: " + task.description + ", position: " + selectedContextMenuPosition + ", dueDate: " + (task.dueDate != null ? task.dueDate : "null") + ", id: " + task.id);
        selectedContextMenuPosition = -1; // Reset after use

        switch (item.getItemId()) {
            case 1: // Edit
                showTaskDialog(task);
                Log.d("MyToDo", "onContextItemSelected: Opening edit dialog for task: " + task.description + ", id: " + task.id);
                return true;
            case 2: // Move
                showMoveTaskDialog(task);
                Log.d("MyToDo", "onContextItemSelected: Opening move dialog for task: " + task.description + ", id: " + task.id);
                return true;
            case 3: // Complete
                // Check if this task can be completed
                if (!canCompleteTask(task)) {
                    Toast.makeText(this, getString(R.string.daily_task_wrong_day), Toast.LENGTH_LONG).show();
                    Log.d("MyToDo", "onContextItemSelected: Prevented completion of daily task " + task.description + " - can only be completed on current day");
                    return true;
                }
                
                task.isCompleted = !task.isCompleted;
                Calendar calendar = Calendar.getInstance();
                task.completionDate = task.isCompleted ? calendar.getTimeInMillis() : null;
                viewModel.update(task);
                Log.d("MyToDo", "onContextItemSelected: Completed task: " + task.description + ", isCompleted: " + task.isCompleted + ", id: " + task.id);
                
                // Sync task completion to cloud if user is authenticated
                if (authService.isUserSignedIn()) {
                    syncManager.forceSync(new SyncManager.SyncCallback() {
                        @Override
                        public void onSyncComplete(boolean success, String message) {
                            Log.d("MyToDo", "Task completion sync: " + (success ? "Success" : "Failed") + " - " + message);
                        }

                        @Override
                        public void onSyncProgress(String message) {
                            Log.d("MyToDo", "Task completion sync progress: " + message);
                        }
                    });
                }
                return true;
            case 4: // Delete
                showDeleteConfirmationDialog(task);
                Log.d("MyToDo", "onContextItemSelected: Showing delete confirmation for task: " + task.description + ", id: " + task.id);
                return true;
            default:
                Log.w("MyToDo", "onContextItemSelected: Unhandled context menu item: " + item.getTitle());
                return super.onContextItemSelected(item);
        }
    }

    private void checkLauncherIconResources() {
        Log.d("MyToDo", "checkLauncherIconResources: Checking launcher icon resources...");
        
        try {
            // Check if the launcher icon files exist
            android.graphics.drawable.Drawable launcherIcon = getResources().getDrawable(R.mipmap.ic_launcher);
            Log.d("MyToDo", "checkLauncherIconResources: Main launcher icon loaded: " + launcherIcon);
        } catch (Exception e) {
            Log.e("MyToDo", "checkLauncherIconResources: Error loading main launcher icon", e);
        }
        
        try {
            android.graphics.drawable.Drawable roundLauncherIcon = getResources().getDrawable(R.mipmap.ic_launcher_round);
            Log.d("MyToDo", "checkLauncherIconResources: Round launcher icon loaded: " + roundLauncherIcon);
        } catch (Exception e) {
            Log.e("MyToDo", "checkLauncherIconResources: Error loading round launcher icon", e);
        }
        
        try {
            android.graphics.drawable.Drawable foregroundIcon = getResources().getDrawable(R.drawable.ic_launcher_foreground);
            Log.d("MyToDo", "checkLauncherIconResources: Foreground icon loaded: " + foregroundIcon);
        } catch (Exception e) {
            Log.e("MyToDo", "checkLauncherIconResources: Error loading foreground icon", e);
        }
        
        try {
            android.graphics.drawable.Drawable monochromeIcon = getResources().getDrawable(R.drawable.ic_launcher_monochrome);
            Log.d("MyToDo", "checkLauncherIconResources: Monochrome icon loaded: " + monochromeIcon);
        } catch (Exception e) {
            Log.e("MyToDo", "checkLauncherIconResources: Error loading monochrome icon", e);
        }
        
        try {
            int backgroundColor = getResources().getColor(R.color.ic_launcher_background);
            Log.d("MyToDo", "checkLauncherIconResources: Background color loaded: " + String.format("#%06X", backgroundColor));
        } catch (Exception e) {
            Log.e("MyToDo", "checkLauncherIconResources: Error loading background color", e);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d("MyToDo", "onResume: Current intent action: " + (getIntent() != null ? getIntent().getAction() : "null"));
        Log.d("MyToDo", "onResume: isProcessingEditFromReminder flag: " + isProcessingEditFromReminder);
        
        // Check if we have a pending reminder intent that needs processing
        Intent currentIntent = getIntent();
        if (currentIntent != null && "EDIT_TASK_FROM_REMINDER".equals(currentIntent.getAction())) {
            Log.d("MyToDo", "onResume: Found pending EDIT_TASK_FROM_REMINDER intent, processing it");
            handleEditTaskFromReminder(currentIntent);
        } else if (currentIntent != null && "COMPLETE_TASK_FROM_REMINDER".equals(currentIntent.getAction())) {
            Log.d("MyToDo", "onResume: Found pending COMPLETE_TASK_FROM_REMINDER intent, processing it");
            handleCompleteTaskFromReminder(currentIntent);
        }
        
        // Auto-sync when app resumes (if user is authenticated and sync is needed)
        if (authService.isUserSignedIn() && syncManager.needsSync()) {
            Log.d("MyToDo", "onResume: Starting auto-sync");
            syncManager.syncTasks(new SyncManager.SyncCallback() {
                @Override
                public void onSyncComplete(boolean success, String message) {
                    if (success) {
                        Log.d("MyToDo", "Auto-sync completed: " + message);
                        // Refresh the task list after successful sync
                        runOnUiThread(() -> {
                            if (viewModel != null) {
                                viewModel.forceRefreshTasks();
                            }
                        });
                    } else {
                        Log.e("MyToDo", "Auto-sync failed: " + message);
                    }
                }

                @Override
                public void onSyncProgress(String message) {
                    Log.d("MyToDo", "Sync progress: " + message);
                }
            });
        }
        
        // Log.d("MyToDo", "onResume: Stack trace for onResume call:");
        // Thread.dumpStack();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d("MyToDo", "onNewIntent: Current activity intent action: " + (getIntent() != null ? getIntent().getAction() : "null"));
        Log.d("MyToDo", "onNewIntent: isProcessingEditFromReminder flag: " + isProcessingEditFromReminder);
        
        // Handle reminder intents when app is in background
        if (intent != null && "EDIT_TASK_FROM_REMINDER".equals(intent.getAction())) {
            Log.d("MyToDo", "onNewIntent: Processing EDIT_TASK_FROM_REMINDER intent");
            setIntent(intent); // Update the current intent
            handleEditTaskFromReminder(intent);
        } else if (intent != null && "COMPLETE_TASK_FROM_REMINDER".equals(intent.getAction())) {
            Log.d("MyToDo", "onNewIntent: Processing COMPLETE_TASK_FROM_REMINDER intent");
            setIntent(intent); // Update the current intent
            handleCompleteTaskFromReminder(intent);
        } else {
            Log.d("MyToDo", "onNewIntent: No reminder action to process, intent action: " + (intent != null ? intent.getAction() : "null"));
        }
        
        // Log.d("MyToDo", "onNewIntent: Stack trace for cancel button click:");
        // Thread.dumpStack();
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        Log.d("MyToDo", "onStart: Current intent action: " + (getIntent() != null ? getIntent().getAction() : "null"));
        Log.d("MyToDo", "onStart: isProcessingEditFromReminder flag: " + isProcessingEditFromReminder);
        // Log.d("MyToDo", "onStart: Stack trace for onStart call:");
        // Thread.dumpStack();
    }
    
    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d("MyToDo", "onRestart: Called");
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        Log.d("MyToDo", "onPostCreate: Called");
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d("MyToDo", "onWindowFocusChanged: Called with hasFocus: " + hasFocus);
    }
    
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        Log.d("MyToDo", "onUserInteraction: Called");
    }
    
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        Log.d("MyToDo", "onUserLeaveHint: Called");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d("MyToDo", "onPause: Called");
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        Log.d("MyToDo", "onStop: Called");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("MyToDo", "onDestroy: Called");
        
        // Shutdown executor service to prevent memory leaks
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            Log.d("MyToDo", "onDestroy: ExecutorService shutdown");
        }
        
        // Shutdown sync manager to prevent memory leaks
        if (syncManager != null) {
            syncManager.shutdown();
            Log.d("MyToDo", "onDestroy: SyncManager shutdown");
        }
    }
    
    /**
     * Helper method to check if a daily task can be completed on the current day
     */
    private boolean canCompleteTask(Task task) {
        if (task == null) return false;
        
        // Non-daily tasks can always be completed/uncompleted
        if (!task.isRecurring || task.recurrenceType == null || 
            (!task.recurrenceType.equals("Daily") && !task.recurrenceType.equals("יומי"))) {
            return true;
        }
        
        // For daily tasks, only allow completion on current day (or allow uncompleting anytime)
        if (task.isCompleted) {
            return true; // Can always uncomplete
        }
        
        // Check if trying to complete on the right day
        String taskDayCategory = getCurrentDayCategoryForTask(task);
        String todayCategory = getTodayDayCategory();
        return taskDayCategory.equals(todayCategory);
    }
    
    /**
     * Helper method to get the current day category name for comparison
     */
    private String getTodayDayCategory() {
        String[] daysOfWeek = getResources().getStringArray(R.array.days_of_week);
        if (daysOfWeek == null || daysOfWeek.length < 10) {
            Log.e("MyToDo", "getTodayDayCategory: daysOfWeek array is invalid");
            return "Monday"; // fallback
        }
        
        Calendar today = Calendar.getInstance();
        int todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK);
        
        // Map Calendar.DAY_OF_WEEK to our array indices
        switch (todayDayOfWeek) {
            case Calendar.SUNDAY: return daysOfWeek[3];    // Sunday
            case Calendar.MONDAY: return daysOfWeek[4];    // Monday  
            case Calendar.TUESDAY: return daysOfWeek[5];   // Tuesday
            case Calendar.WEDNESDAY: return daysOfWeek[6]; // Wednesday
            case Calendar.THURSDAY: return daysOfWeek[7];  // Thursday
            case Calendar.FRIDAY: return daysOfWeek[8];    // Friday
            case Calendar.SATURDAY: return daysOfWeek[9];  // Saturday
            default: return daysOfWeek[4]; // Default to Monday
        }
    }
    
    /**
     * Helper method to determine which day category a task currently appears in
     * For daily tasks, this should match the current expanded category where user selected it
     */
    private String getCurrentDayCategoryForTask(Task task) {
        // For daily recurring tasks, we need to determine which day category the user 
        // selected this task from, since they appear in all day categories
        if (task.isRecurring && (task.recurrenceType != null && 
                (task.recurrenceType.equals("Daily") || task.recurrenceType.equals("יומי")))) {
            // Check which day category is currently expanded or was last interacted with
            String[] daysOfWeek = getResources().getStringArray(R.array.days_of_week);
            if (daysOfWeek == null || daysOfWeek.length < 10) {
                return getTodayDayCategory(); // fallback to today
            }
            
            // Check if any day category is currently expanded in the adapter
            if (adapter != null) {
                for (int i = 3; i < 10; i++) { // Check weekday categories only
                    if (adapter.isCategoryExpanded(daysOfWeek[i])) {
                        Log.d("MyToDo", "getCurrentDayCategoryForTask: Found expanded category: " + daysOfWeek[i]);
                        return daysOfWeek[i];
                    }
                }
            }
            
            // If no category is expanded, assume the user is trying to complete today's instance
            return getTodayDayCategory();
        }
        
        // For non-daily tasks, use the task's actual dayOfWeek
        if (task.dayOfWeek != null) {
            String[] daysOfWeek = getResources().getStringArray(R.array.days_of_week);
            return mapDayOfWeekToCurrentLanguage(task.dayOfWeek, daysOfWeek);
        }
        
        return getTodayDayCategory(); // fallback
    }
    
    // Calculate priority for new tasks based on time and category
    private int calculateNewTaskPriority(Long dueTime, String dayOfWeek) {
        if (dueTime == null) {
            // Tasks without time get high priority values (appear after timed tasks)
            return (int) (System.currentTimeMillis() / 1000);
        }
        
        // Tasks with time get priority based on their time (in minutes since midnight)
        // This ensures time-based ordering in the database
        int timeBasedPriority = (int) (dueTime / (60 * 1000)); // Convert milliseconds to minutes
        
        Log.d("MyToDo", "calculateNewTaskPriority: dueTime=" + dueTime + " -> priority=" + timeBasedPriority + " (minutes since midnight)");
        return timeBasedPriority;
    }
    
    public void unpinTask(Task task) {
        Log.d("MyToDo", "Unpinning task: " + task.description + " (removing manualPosition)");
        
        // Update the task in the database
        executorService.execute(() -> {
            try {
                viewModel.update(task);
                Log.d("MyToDo", "Task unpinned successfully: " + task.description);
                
                // No need to manually refresh - viewModel.update() already triggers a refresh
            } catch (Exception e) {
                Log.e("MyToDo", "Error unpinning task: " + task.description, e);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, getString(R.string.error_saving_task), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    // Helper methods for reminder days functionality
    private void updateReminderDaysVisibility(LinearLayout reminderDaysLayout, boolean reminderEnabled, boolean isRecurring, Spinner recurrenceTypeSpinner) {
        // Only show reminder days selection for daily recurring tasks with reminders enabled
        boolean shouldShow = false;
        
        if (reminderEnabled && isRecurring && recurrenceTypeSpinner != null) {
            try {
                String[] recurrenceTypes = getResources().getStringArray(R.array.recurrence_types);
                if (recurrenceTypes != null && recurrenceTypes.length > 0) {
                    String selectedRecurrenceType = recurrenceTypes[recurrenceTypeSpinner.getSelectedItemPosition()];
                    String dailyRecurrenceType = recurrenceTypes[0]; // "Daily" or "יומי"
                    shouldShow = dailyRecurrenceType.equals(selectedRecurrenceType) || 
                               "Daily".equals(selectedRecurrenceType) || 
                               "יומי".equals(selectedRecurrenceType);
                }
            } catch (Exception e) {
                Log.e("MyToDo", "updateReminderDaysVisibility: Error checking recurrence type", e);
            }
        }
        
        reminderDaysLayout.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
        Log.d("MyToDo", "updateReminderDaysVisibility: reminderEnabled=" + reminderEnabled + 
              ", isRecurring=" + isRecurring + ", isDaily=" + shouldShow + ", shouldShow=" + shouldShow);
    }
    
    private void initializeReminderDays(Task task, CheckBox[] reminderDayCheckBoxes) {
        // Initialize all checkboxes to checked (default = all days)
        for (CheckBox checkBox : reminderDayCheckBoxes) {
            checkBox.setChecked(true);
        }
        
        if (task != null && task.reminderDays != null && !task.reminderDays.isEmpty()) {
            // Clear all first
            for (CheckBox checkBox : reminderDayCheckBoxes) {
                checkBox.setChecked(false);
            }
            
            // Parse the comma-separated day indices
            String[] dayIndices = task.reminderDays.split(",");
            for (String dayIndexStr : dayIndices) {
                try {
                    int dayIndex = Integer.parseInt(dayIndexStr.trim());
                    if (dayIndex >= 0 && dayIndex < reminderDayCheckBoxes.length) {
                        reminderDayCheckBoxes[dayIndex].setChecked(true);
                    }
                } catch (NumberFormatException e) {
                    Log.e("MyToDo", "initializeReminderDays: Invalid day index: " + dayIndexStr, e);
                }
            }
        }
    }
    
    private void setReminderDays(CheckBox[] reminderDayCheckBoxes, boolean[] days) {
        for (int i = 0; i < reminderDayCheckBoxes.length && i < days.length; i++) {
            reminderDayCheckBoxes[i].setChecked(days[i]);
        }
    }
    
    private String getReminderDaysString(CheckBox[] reminderDayCheckBoxes, boolean isRecurring) {
        if (!isRecurring) {
            return null; // Non-recurring tasks don't need reminder days
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reminderDayCheckBoxes.length; i++) {
            if (reminderDayCheckBoxes[i].isChecked()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(i); // 0=Sunday, 1=Monday, etc.
            }
        }
        
        String result = sb.toString();
        return result.isEmpty() ? null : result;
    }


}