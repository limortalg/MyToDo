package limor.tal.mytodo;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TaskViewModel extends AndroidViewModel {
    private TaskRepository repository;
    private LiveData<List<Task>> allTasks;
    private MutableLiveData<List<Object>> tasksByCategory = new MutableLiveData<>();
    private MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private MutableLiveData<Boolean> includeCompleted = new MutableLiveData<>(false);

    public TaskViewModel(Application application) {
        super(application);
        repository = new TaskRepository(application);
        allTasks = repository.getAllTasks();
        // Initial empty state - will be populated when observer gets data
        tasksByCategory.setValue(new ArrayList<>());
    }

    public LiveData<List<Task>> getAllTasks() {
        return allTasks;
    }

    public LiveData<List<Object>> getTasksByCategory() {
        return tasksByCategory;
    }

    public LiveData<String> getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String query) {
        searchQuery.setValue(query);
        // Trigger refresh through the observer
        forceRefreshAllTasks();
    }

    public LiveData<Boolean> getIncludeCompleted() {
        return includeCompleted;
    }

    public void setIncludeCompleted(boolean include) {
        includeCompleted.setValue(include);
        // Trigger refresh through the observer
        forceRefreshAllTasks();
    }

    public void insert(Task task) {
        // Set timestamps before inserting
        long currentTime = System.currentTimeMillis();
        task.createdAt = currentTime;
        task.updatedAt = currentTime;
        
        Log.d("MyToDo", "TASK INSERT DEBUG: Inserting new task: " + task.description + 
              " (ID: " + task.id + 
              ", FirestoreID: " + (task.firestoreDocumentId != null ? task.firestoreDocumentId : "NULL") + 
              ", createdAt: " + currentTime + 
              ", reminderOffset: " + task.reminderOffset + ")");
        
        repository.insert(task);
    }

    public void update(Task task) {
        // Set updatedAt timestamp before updating
        long currentTime = System.currentTimeMillis();
        task.updatedAt = currentTime;
        
        Log.d("MyToDo", "VIEWMODEL UPDATE DEBUG: Updating task: " + task.description + 
              " (ID: " + task.id + 
              ", FirestoreID: " + (task.firestoreDocumentId != null ? task.firestoreDocumentId : "NULL") + 
              ", updatedAt: " + currentTime + 
              ", reminderOffset: " + task.reminderOffset + 
              ", isRecurring: " + task.isRecurring + 
              ", recurrenceType: " + task.recurrenceType + 
              ", dueDate: " + task.dueDate + 
              ", dayOfWeek: " + task.dayOfWeek + 
              ", isCompleted: " + task.isCompleted + ")");
        
        repository.update(task);
        // Force refresh of allTasks to ensure UI gets updated data
        // This will trigger the observer in MainActivity which calls updateTasksByCategory()
        forceRefreshAllTasks();
    }
    
    private void forceRefreshAllTasks() {
        // Force refresh by triggering the observer chain
        // The MainActivity observer will call updateTasksByCategory(tasks) with the current tasks
        if (allTasks.getValue() != null) {
            updateTasksByCategory(allTasks.getValue());
        }
    }

    public void delete(Task task) {
        repository.delete(task);
    }

    public void updateTaskOrder(List<Task> tasks) {
        long currentTime = System.currentTimeMillis();
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).priority = i;
            tasks.get(i).updatedAt = currentTime; // Set updatedAt for all tasks being reordered
        }
        repository.updateTasks(tasks);
        // Force refresh of allTasks to ensure UI gets updated data
        // This will trigger the observer in MainActivity which calls updateTasksByCategory()
        forceRefreshAllTasks();
    }

    public void updateTasksByCategory() {
        // This method should only be called from the observer with actual tasks
        Log.w("MyToDo", "updateTasksByCategory() called without tasks parameter - this shouldn't happen");
        tasksByCategory.setValue(new ArrayList<>());
    }
    
    public void updateTasksByCategory(List<Task> tasks) {
        if (tasks == null) {
            tasksByCategory.setValue(new ArrayList<>());
            Log.d("MyToDo", "Tasks list is null, setting empty tasksByCategory");
            return;
        }
        // Log.d("MyToDo", "updateTasksByCategory: Processing " + tasks.size() + " tasks");
        
        // Force refresh if tasks list is empty but we expect tasks
        if (tasks.isEmpty()) {
            Log.w("MyToDo", "Tasks list is empty, this might indicate a loading issue");
        }
        
        processTasksByCategory(tasks);
    }
    
    private void processTasksByCategory(List<Task> tasks) {

        // Always get fresh string resources to handle language changes
        String[] daysOfWeek = getApplication().getResources().getStringArray(R.array.days_of_week);
        String noneOption = daysOfWeek[0]; // "Waiting" or "בהמתנה"
        String immediateOption = daysOfWeek[1]; // "Immediate" or "מיידי"
        String soonOption = daysOfWeek[2]; // "Soon" or "בקרוב"
        String waitingCategory = getApplication().getString(R.string.category_waiting); // "Waiting" or "בהמתנה"
        String completedCategory = getApplication().getString(R.string.category_completed); // "Completed" or "הושלם"
        
        Log.d("MyToDo", "processTasksByCategory: Using language resources - noneOption: " + noneOption + 
                ", immediateOption: " + immediateOption + ", soonOption: " + soonOption + 
                ", waitingCategory: " + waitingCategory + ", completedCategory: " + completedCategory);

        // Define category order: Current day (with immediate tasks), Next 6 days, Soon, Waiting
        // NOTE: Immediate tasks will now be merged into the current day category
        List<String> categoryOrder = new ArrayList<>();
        // Do not add immediateOption as a separate category anymore
        
        // Get current day and create order starting from today
        Calendar today = Calendar.getInstance();
        int todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK); // 1=Sunday, ..., 7=Saturday
        
        Log.d("MyToDo", "Current day of week: " + todayDayOfWeek + " (1=Sunday, 2=Monday, ..., 7=Saturday)");
        
        // Map Calendar.DAY_OF_WEEK to days_of_week array indices
        int todayIndex;
        switch (todayDayOfWeek) {
            case Calendar.SUNDAY: todayIndex = 3; break;    // Sunday
            case Calendar.MONDAY: todayIndex = 4; break;    // Monday
            case Calendar.TUESDAY: todayIndex = 5; break;   // Tuesday
            case Calendar.WEDNESDAY: todayIndex = 6; break; // Wednesday
            case Calendar.THURSDAY: todayIndex = 7; break;  // Thursday
            case Calendar.FRIDAY: todayIndex = 8; break;    // Friday
            case Calendar.SATURDAY: todayIndex = 9; break;  // Saturday
            default: todayIndex = 8; break; // Default to Friday
        }
        
        Log.d("MyToDo", "Today index in days_of_week array: " + todayIndex);
        Log.d("MyToDo", "Today's day name: " + daysOfWeek[todayIndex]);
        
        // Create dayIndices array for the current week order
        int[] dayIndices = new int[7];
        for (int i = 0; i < 7; i++) {
            int dayIndex = todayIndex + i;
            // Handle wrapping around from Saturday (9) to Sunday (3)
            if (dayIndex > 9) {
                dayIndex = 3 + (dayIndex - 10); // Wrap to Sunday (3)
            }
            dayIndices[i] = dayIndex;
            categoryOrder.add(daysOfWeek[dayIndex]);
            Log.d("MyToDo", "Day " + i + ": index=" + dayIndex + ", name=" + daysOfWeek[dayIndex]);
        }
        
        categoryOrder.add(soonOption);
        categoryOrder.add(waitingCategory);
        categoryOrder.add(completedCategory); // Add Completed category
        Log.d("MyToDo", "Final category order: " + categoryOrder);
        Log.d("MyToDo", "Day indices array: " + Arrays.toString(dayIndices));

        List<Object> items = new ArrayList<>();
        String query = searchQuery.getValue() != null ? searchQuery.getValue().toLowerCase() : "";
        boolean hasActiveSearch = !query.isEmpty();
        boolean includeCompletedTasks = includeCompleted.getValue() != null ? includeCompleted.getValue() : false;
        
        Log.d("MyToDo", "processTasksByCategory: Search query: '" + query + "', includeCompleted: " + includeCompletedTasks);

        // Get current date and next week's date for categorization
        Calendar todayCal = Calendar.getInstance();
        todayCal.set(Calendar.HOUR_OF_DAY, 0);
        todayCal.set(Calendar.MINUTE, 0);
        todayCal.set(Calendar.SECOND, 0);
        todayCal.set(Calendar.MILLISECOND, 0);
        long todayMillis = todayCal.getTimeInMillis();
        Calendar nextWeek = Calendar.getInstance();
        nextWeek.setTimeInMillis(todayMillis);
        nextWeek.add(Calendar.DAY_OF_MONTH, 7); // One week from today
        long nextWeekMillis = nextWeek.getTimeInMillis();

        // Map tasks to categories
        List<Task> immediateTasks = new ArrayList<>();
        List<Task> soonTasks = new ArrayList<>();
        List<Task> waitingTasks = new ArrayList<>();
        List<Task> completedTasks = new ArrayList<>(); // New list for completed tasks
        List<List<Task>> dayTasks = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            dayTasks.add(new ArrayList<>());
        }

        for (Task task : tasks) {
            // Skip soft-deleted tasks
            if (task.deletedAt != null && task.deletedAt > 0) {
                Log.d("MyToDo", "processTasksByCategory: Skipping soft-deleted task: " + task.description + " (deletedAt: " + task.deletedAt + ")");
                continue;
            }
            
            // Add detailed logging for each task being processed
            // Log.d("MyToDo", "processTasksByCategory: Processing task ID " + task.id + " - " + task.description + 
            //       ", isCompleted: " + task.isCompleted + ", completionDate: " + task.completionDate + 
            //       ", isRecurring: " + task.isRecurring + ", dayOfWeek: " + task.dayOfWeek);
            
            // For all recurring tasks, always process them regardless of completion status
            boolean isRecurring = task.isRecurring;
            
            // Apply search filter first so Completed is searchable too
            if (hasActiveSearch && (task.description == null || !task.description.toLowerCase().contains(query))) {
                continue;
            }
            
            // Always route non-recurring completed tasks to Completed category list
            if (!isRecurring && task.isCompleted) {
                Log.d("MyToDo", "processTasksByCategory: Non-recurring completed task " + task.description + " (id: " + task.id + ") routed to completed category, completionDate: " + task.completionDate);
                completedTasks.add(task);
                continue;
            }
            
            // Log.d("MyToDo", "Processing task for search: " + task.description + ", query: '" + query + "'");
            Log.d("MyToDo", "Task: " + task.description + ", isRecurring: " + task.isRecurring + 
                    ", recurrenceType: " + task.recurrenceType);

            // Check if this is a daily recurring task
            String[] recurrenceTypes = getApplication().getResources().getStringArray(R.array.recurrence_types);
            String dailyRecurrenceType = recurrenceTypes[0]; // "Daily" or "יומי"
            boolean isDailyRecurring = isRecurring && (dailyRecurrenceType.equals(task.recurrenceType) || 
                    "Daily".equals(task.recurrenceType) || "יומי".equals(task.recurrenceType));

            // For recurring tasks, check if they should be completable for this day
            boolean isCompletableForThisDay = true;
            if (isDailyRecurring && task.isCompleted) {
                // Check if this task was completed for a future day
                if (task.completionDate != null) {
                    Calendar completionCal = Calendar.getInstance();
                    completionCal.setTimeInMillis(task.completionDate);
                    Calendar checkDayCal = Calendar.getInstance();
                    
                    // If completed for a future day, don't show as completed for current/past days
                    if (completionCal.get(Calendar.DAY_OF_YEAR) > checkDayCal.get(Calendar.DAY_OF_YEAR) ||
                        completionCal.get(Calendar.YEAR) > checkDayCal.get(Calendar.YEAR)) {
                        isCompletableForThisDay = false;
                    }
                }
            }
            
            // For daily recurring tasks, create a copy for each day with appropriate completion state
            if (isDailyRecurring) {
                // Add one copy to each day category
                for (int i = 0; i < 7; i++) {
                    Task taskCopy = new Task(task.description, task.dueDate, task.dayOfWeek, 
                            task.isRecurring, task.recurrenceType, task.isCompleted, task.priority);
                    taskCopy.id = task.id;
                    taskCopy.dueTime = task.dueTime;
                    taskCopy.reminderOffset = task.reminderOffset;
                    taskCopy.completionDate = task.completionDate;
                    
                    // For daily recurring tasks, only show as completed for today
                    // Future days should show as not completed since they haven't happened yet
                    Calendar currentDate = Calendar.getInstance();
                    int currentDayOfWeek = currentDate.get(Calendar.DAY_OF_WEEK);
                    
                    // Map the current day index to Calendar.DAY_OF_WEEK
                    int dayOfWeekForThisCopy = -1;
                    switch (dayIndices[i]) {
                        case 3: dayOfWeekForThisCopy = Calendar.SUNDAY; break;    // Sunday
                        case 4: dayOfWeekForThisCopy = Calendar.MONDAY; break;    // Monday
                        case 5: dayOfWeekForThisCopy = Calendar.TUESDAY; break;   // Tuesday
                        case 6: dayOfWeekForThisCopy = Calendar.WEDNESDAY; break; // Wednesday
                        case 7: dayOfWeekForThisCopy = Calendar.THURSDAY; break;  // Thursday
                        case 8: dayOfWeekForThisCopy = Calendar.FRIDAY; break;    // Friday
                        case 9: dayOfWeekForThisCopy = Calendar.SATURDAY; break;  // Saturday
                    }
                    
                    // Only show as completed if this is today AND the task is completed
                    if (dayOfWeekForThisCopy == currentDayOfWeek && task.isCompleted) {
                        taskCopy.isCompleted = true;
                    } else {
                        taskCopy.isCompleted = false;
                    }
                    
                    // Add a special field to track which day this copy represents
                    taskCopy.dayOfWeek = daysOfWeek[dayIndices[i]];
                    
                    // Add directly to the specific day category
                    dayTasks.get(i).add(taskCopy);
                    String dayName = daysOfWeek[dayIndices[i]];
                    Log.d("MyToDo", "Daily recurring task added to " + dayName + " (index " + i + "): " + taskCopy.description + ", isCompleted: " + taskCopy.isCompleted);
                }
                Log.d("MyToDo", "Daily recurring task: " + task.description + " added to all day categories");
                Log.d("MyToDo", "Day tasks sizes after adding daily task: " + Arrays.toString(dayTasks.stream().mapToInt(List::size).toArray()));
            } else if (isRecurring) {
                // For weekly, monthly recurring tasks - process them directly without creating copies
                // The completion logic in MainActivity now handles due date progression
                processSingleTask(task, immediateTasks, soonTasks, waitingTasks, completedTasks, dayTasks, 
                        daysOfWeek, dayIndices, immediateOption, soonOption, waitingCategory, completedCategory, 
                        noneOption, todayMillis, nextWeekMillis, query, includeCompletedTasks);
                
                Log.d("MyToDo", "Recurring task (non-daily): " + task.description + " processed directly, isCompleted: " + task.isCompleted);
            } else {
                // Process regular task
                processSingleTask(task, immediateTasks, soonTasks, waitingTasks, completedTasks, dayTasks, 
                        daysOfWeek, dayIndices, immediateOption, soonOption, waitingCategory, completedCategory, 
                        noneOption, todayMillis, nextWeekMillis, query, includeCompletedTasks);
            }
        }

        // Task comparator using manualPosition field - COMPLETELY REWRITTEN
        // Core principle: Manual tasks stay exactly where user put them, auto tasks sort by time
        Comparator<Task> taskComparator = (t1, t2) -> {
            boolean t1IsManual = (t1.manualPosition != null);
            boolean t2IsManual = (t2.manualPosition != null);
            
            Log.d("MyToDo", "TaskComparator: t1=" + t1.description + " (manual=" + t1IsManual + 
                  (t1IsManual ? ", pos=" + t1.manualPosition : ", time=" + t1.dueTime) + 
                  ") vs t2=" + t2.description + " (manual=" + t2IsManual + 
                  (t2IsManual ? ", pos=" + t2.manualPosition : ", time=" + t2.dueTime) + ")");
            
            // Case 1: Both manual - sort by manualPosition
            if (t1IsManual && t2IsManual) {
                Log.d("MyToDo", "TaskComparator: Both manual - sort by position");
                return Integer.compare(t1.manualPosition, t2.manualPosition);
            }
            
            // Case 2: Both automatic - sort by time, then priority
            if (!t1IsManual && !t2IsManual) {
                Log.d("MyToDo", "TaskComparator: Both auto - sort by time");
                
                // Both have time - sort by time
                if (t1.dueTime != null && t2.dueTime != null) {
                    long t1TimeOfDay = convertToTimeOfDay(t1.dueTime);
                    long t2TimeOfDay = convertToTimeOfDay(t2.dueTime);
                    int timeResult = Long.compare(t1TimeOfDay, t2TimeOfDay);
                    if (timeResult != 0) return timeResult;
                    return Integer.compare(t1.priority, t2.priority); // tie-breaker
                }
                
                // Mixed time/no-time among auto tasks
                if (t1.dueTime != null && t2.dueTime == null) return -1; // timed first
                if (t1.dueTime == null && t2.dueTime != null) return 1;  // timed first
                
                // Both have no time - sort by priority
                return Integer.compare(t1.priority, t2.priority);
            }
            
            // Case 3: Mixed manual/auto - find correct insertion point
            Task autoTask = t1IsManual ? t2 : t1;
            Task manualTask = t1IsManual ? t1 : t2;
            boolean autoIsT1 = !t1IsManual;
            
            Log.d("MyToDo", "TaskComparator: Mixed - auto=" + autoTask.description + 
                  " (time=" + autoTask.dueTime + "), manual=" + manualTask.description + 
                  " (pos=" + manualTask.manualPosition + ")");
            
            // Auto task with no time - always goes after manual tasks
            if (autoTask.dueTime == null) {
                Log.d("MyToDo", "TaskComparator: Auto has no time - goes after manual");
                return autoIsT1 ? 1 : -1;
            }
            
            // Auto task has time - convert to hour for comparison with manual position
            long autoTimeOfDay = convertToTimeOfDay(autoTask.dueTime);
            int autoHour = (int) (autoTimeOfDay / 3600000); // convert to hours (0-23)
            
            // Map manual positions to representative hours:
            // pos 0 -> hour 3 (early morning)
            // pos 1 -> hour 9 (morning) 
            // pos 2 -> hour 15 (afternoon)
            // pos 3+ -> hour 21 (evening)
            int manualHour;
            switch (manualTask.manualPosition) {
                case 0: manualHour = 3; break;  // early morning
                case 1: manualHour = 9; break;  // morning
                case 2: manualHour = 15; break; // afternoon
                default: manualHour = 21; break; // evening
            }
            
            Log.d("MyToDo", "TaskComparator: Auto hour=" + autoHour + ", manual maps to hour=" + manualHour);
            
            if (autoHour < manualHour) {
                Log.d("MyToDo", "TaskComparator: Auto time comes before manual position");
                return autoIsT1 ? -1 : 1; // auto comes first
            } else if (autoHour > manualHour) {
                Log.d("MyToDo", "TaskComparator: Auto time comes after manual position");
                return autoIsT1 ? 1 : -1; // manual comes first
            } else {
                // Same hour range - manual task wins (stays in its dragged position)
                Log.d("MyToDo", "TaskComparator: Same hour range - manual stays in position");
                return autoIsT1 ? 1 : -1; // manual comes first
            }
        };
        
        // Custom comparator for completed tasks - sort by completion time (descending)
        Comparator<Task> completedTaskComparator = (t1, t2) -> {
            if (t1.completionDate == null && t2.completionDate == null) return 0;
            if (t1.completionDate == null) return 1; // null completion dates go last
            if (t2.completionDate == null) return -1;
            return Long.compare(t2.completionDate, t1.completionDate); // Descending order (most recent first)
        };
        
        immediateTasks.sort(taskComparator);
        soonTasks.sort(taskComparator);
        waitingTasks.sort(taskComparator);
        completedTasks.sort(completedTaskComparator); // Use custom comparator for completed tasks
        for (List<Task> dayTaskList : dayTasks) {
            dayTaskList.sort(taskComparator);
        }

        // Build items list in category order
        Log.d("MyToDo", "Building final category list. Category order: " + categoryOrder);
        Log.d("MyToDo", "Day indices: " + Arrays.toString(dayIndices));
        Log.d("MyToDo", "Days of week: " + Arrays.toString(daysOfWeek));
        
        for (String category : categoryOrder) {
            List<Task> categoryTasks = new ArrayList<>();
            if (category.equals(soonOption)) {
                categoryTasks = soonTasks;
                Log.d("MyToDo", "Getting Soon tasks: " + soonTasks.size());
            } else if (category.equals(waitingCategory)) {
                categoryTasks = waitingTasks;
                Log.d("MyToDo", "Getting Waiting tasks: " + waitingTasks.size());
            } else if (category.equals(completedCategory)) {
                // Only include Completed in search results when includeCompletedTasks is true
                if (hasActiveSearch && !includeCompletedTasks) {
                    categoryTasks = new ArrayList<>();
                } else {
                    categoryTasks = completedTasks;
                }
                Log.d("MyToDo", "Getting Completed tasks: " + completedTasks.size());
            } else {
                for (int i = 0; i < dayIndices.length; i++) {
                    if (category.equals(daysOfWeek[dayIndices[i]])) {
                        categoryTasks = new ArrayList<>(dayTasks.get(i));
                        
                        // If this is the current day (first in the list), add immediate tasks
                        if (i == 0) {
                            categoryTasks.addAll(immediateTasks);
                            // Sort the merged list using TaskComparator which handles manual overrides correctly
                            // Log task order before sorting for debugging
                            Log.d("MyToDo", "Before sorting " + category + " tasks - " + categoryTasks.size() + " total");
                            
                            categoryTasks.sort(taskComparator);
                            
                            Log.d("MyToDo", "After sorting " + category + " tasks - " + categoryTasks.size() + " total");
                            
                            Log.d("MyToDo", "Getting " + category + " tasks (index " + i + ") + " + immediateTasks.size() + " immediate tasks: " + categoryTasks.size() + " total");
                        } else {
                            Log.d("MyToDo", "Getting " + category + " tasks (index " + i + "): " + dayTasks.get(i).size());
                        }
                        break;
                    }
                }
            }
            if (!categoryTasks.isEmpty()) {
                items.add(category);
                items.addAll(categoryTasks);
                Log.d("MyToDo", "Added category: " + category + " with " + categoryTasks.size() + " tasks");
                for (Task task : categoryTasks) {
                    Log.d("MyToDo", "  - Task in " + category + ": " + task.description + " (id: " + task.id + ")");
                }
            } else {
                Log.d("MyToDo", "Category " + category + " is empty");
            }
        }

        Log.d("MyToDo", "Sorted categories: " + categoryOrder);
        Log.d("MyToDo", "Items size: " + items.size());
        tasksByCategory.setValue(items);
    }
    
    // Helper method to get time of day (now always milliseconds since midnight)
    private long convertToTimeOfDay(long timeValue) {
        // All dueTime values are now stored as milliseconds since midnight
        Log.d("MyToDo", "convertToTimeOfDay: Using " + timeValue + " as milliseconds since midnight");
        return timeValue;
    }
    
    public void forceRefreshTasks() {
        // Force a complete refresh by getting fresh data directly from the database
        // and updating the categorized tasks immediately
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // Get fresh data directly from the database on background thread
                List<Task> freshTasks = repository.getAllTasksSync();
                if (freshTasks != null) {
                    // Update UI on main thread with fresh data
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        processTasksByCategory(freshTasks);
                    });
                } else {
                    // Fallback to empty update
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        updateTasksByCategory(new ArrayList<>());
                    });
                }
            } catch (Exception e) {
                Log.e("MyToDo", "Error getting fresh tasks", e);
                // Fallback to empty update
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    updateTasksByCategory(new ArrayList<>());
                });
            }
        });
    }
    
    private void processSingleTask(Task task, List<Task> immediateTasks, List<Task> soonTasks, List<Task> waitingTasks, List<Task> completedTasks, 
            List<List<Task>> dayTasks, String[] daysOfWeek, int[] dayIndices, String immediateOption, String soonOption, 
            String waitingCategory, String completedCategory, String noneOption, long todayMillis, long nextWeekMillis, String query, boolean includeCompletedTasks) {

        // Check if this is a daily recurring task (now only English values in database)
        boolean isDailyRecurring = task.isRecurring && TaskConstants.RECURRENCE_DAILY.equals(task.recurrenceType);
        // Log.d("MyToDo", "Processing single task: " + task.description + ", isRecurring: " + task.isRecurring + 
        //         ", recurrenceType: " + task.recurrenceType + ", isDailyRecurring: " + isDailyRecurring);

        // For recurring tasks, check if they should be completable for this day
        boolean isCompletableForThisDay = true;
        if (isDailyRecurring && task.isCompleted) {
            // Check if this task was completed for a future day
            if (task.completionDate != null) {
                Calendar completionCal = Calendar.getInstance();
                completionCal.setTimeInMillis(task.completionDate);
                Calendar checkDayCal = Calendar.getInstance();
                
                // If completed for a future day, don't show as completed for current/past days
                if (completionCal.get(Calendar.DAY_OF_YEAR) > checkDayCal.get(Calendar.DAY_OF_YEAR) ||
                    completionCal.get(Calendar.YEAR) > checkDayCal.get(Calendar.YEAR)) {
                    isCompletableForThisDay = false;
                }
            }
        }

        String category = waitingCategory; // Default fallback category
        if (task.isCompleted && task.completionDate != null && !task.isRecurring) {
            // Only NON-recurring completed tasks go to the Completed category
            category = completedCategory;
            Log.d("MyToDo", "processSingleTask: Non-recurring completed task " + task.description + " (id: " + task.id + ") assigned to Completed category, completionDate: " + task.completionDate);
        } else if (task.isCompleted && !task.isRecurring) {
            // Task is completed but missing completion date - this shouldn't happen with our fixes
            Log.w("MyToDo", "processSingleTask: WARNING - Completed task " + task.description + " (id: " + task.id + ") missing completion date, will be categorized by day instead");
            // Fall through to normal categorization
        } else if (task.dayOfWeek != null) {
            // Task dayOfWeek is now always stored in English, map to current language for display
            String mappedDayOfWeek = mapDayOfWeekToCurrentLanguage(task.dayOfWeek, daysOfWeek);
            Log.d("MyToDo", "Task dayOfWeek: " + task.dayOfWeek + " mapped to: " + mappedDayOfWeek);
            
            if (TaskConstants.DAY_NONE.equals(task.dayOfWeek)) {
                // Task has "waiting" status - prioritize due date for categorization
                if (task.dueDate != null) {
                    if (task.dueDate < todayMillis) {
                        category = immediateOption;
                    } else if (task.dueDate >= todayMillis && task.dueDate < nextWeekMillis) {
                        // Due date is this week - show in specific day category
                        Calendar dueDateCal = Calendar.getInstance();
                        dueDateCal.setTimeInMillis(task.dueDate);
                        int dayOfWeek = dueDateCal.get(Calendar.DAY_OF_WEEK); // 1=Sunday, ..., 7=Saturday
                        // Map to current language day index
                        int dayIndex;
                        switch (dayOfWeek) {
                            case Calendar.SUNDAY: dayIndex = 3; break;    // Sunday
                            case Calendar.MONDAY: dayIndex = 4; break;    // Monday
                            case Calendar.TUESDAY: dayIndex = 5; break;   // Tuesday
                            case Calendar.WEDNESDAY: dayIndex = 6; break; // Wednesday
                            case Calendar.THURSDAY: dayIndex = 7; break;  // Thursday
                            case Calendar.FRIDAY: dayIndex = 8; break;    // Friday
                            case Calendar.SATURDAY: dayIndex = 9; break;  // Saturday
                            default: dayIndex = 8; break; // Default to Friday
                        }
                        category = daysOfWeek[dayIndex];
                    } else {
                        // Due date is beyond this week - show in waiting category
                        category = waitingCategory;
                    }
                } else {
                    // No due date - show in waiting category
                    category = waitingCategory;
                }
            } else if (TaskConstants.DAY_IMMEDIATE.equals(task.dayOfWeek)) {
                category = immediateOption;
            } else if (TaskConstants.DAY_SOON.equals(task.dayOfWeek)) {
                category = soonOption;
            } else {
                // Task has a specific day of the week - "when to perform" takes precedence over due date
                category = mappedDayOfWeek;
            }
        } else if (task.dueDate != null) {
            // Categorize based on due date
            if (task.dueDate < todayMillis) {
                // Past due date, assign to Immediate
                category = immediateOption;
            } else if (task.dueDate >= todayMillis && task.dueDate < nextWeekMillis) {
                // Due date within next week
                Calendar dueDateCal = Calendar.getInstance();
                dueDateCal.setTimeInMillis(task.dueDate);
                int dayOfWeek = dueDateCal.get(Calendar.DAY_OF_WEEK); // 1=Sunday, ..., 7=Saturday
                // Map to current language day index
                int dayIndex;
                switch (dayOfWeek) {
                    case Calendar.SUNDAY: dayIndex = 3; break;    // Sunday
                    case Calendar.MONDAY: dayIndex = 4; break;    // Monday
                    case Calendar.TUESDAY: dayIndex = 5; break;   // Tuesday
                    case Calendar.WEDNESDAY: dayIndex = 6; break; // Wednesday
                    case Calendar.THURSDAY: dayIndex = 7; break;  // Thursday
                    case Calendar.FRIDAY: dayIndex = 8; break;    // Friday
                    case Calendar.SATURDAY: dayIndex = 9; break;  // Saturday
                    default: dayIndex = 8; break; // Default to Friday
                }
                category = daysOfWeek[dayIndex];
            } else {
                // Future due date beyond next week, assign to Waiting
                category = waitingCategory;
            }
        } else {
            // No due date or day, assign to Waiting
            category = waitingCategory;
        }

        // Add task to appropriate category(ies)
        
        if (category.equals(immediateOption)) {
            immediateTasks.add(task);
            Log.d("MyToDo", "Task added to Immediate: " + task.description);
        } else if (category.equals(soonOption)) {
            soonTasks.add(task);
            Log.d("MyToDo", "Task added to Soon: " + task.description);
        } else if (category.equals(waitingCategory)) {
            waitingTasks.add(task);
            Log.d("MyToDo", "Task added to Waiting: " + task.description);
        } else if (category.equals(completedCategory)) {
            completedTasks.add(task);
            Log.d("MyToDo", "Task added to Completed: " + task.description);
        } else {
            // Regular task - add to specific day category
            boolean added = false;
            for (int i = 0; i < dayIndices.length; i++) {
                if (category.equals(daysOfWeek[dayIndices[i]])) {
                    dayTasks.get(i).add(task);
                    Log.d("MyToDo", "Task added to " + category + " (index " + i + "): " + task.description);
                    added = true;
                    break;
                }
            }
            if (!added) {
                Log.w("MyToDo", "Task not added to any day category: " + task.description + ", category: " + category);
            }
        }
        Log.d("MyToDo", "Task: " + task.description + ", Category: " + category +
                ", DueDate: " + (task.dueDate != null ? task.dueDate : "null") +
                ", DayOfWeek: " + (task.dayOfWeek != null ? task.dayOfWeek : "null") +
                ", IsDailyRecurring: " + isDailyRecurring);
    }
    
    private String mapDayOfWeekToCurrentLanguage(String storedDayOfWeek, String[] currentDaysOfWeek) {
        // All database values are now stored in English, so we just need to map to display language
        // Handle special categories first
        if (TaskConstants.DAY_NONE.equals(storedDayOfWeek)) {
            return currentDaysOfWeek[0]; // "None" or "ללא"
        }
        if (TaskConstants.DAY_IMMEDIATE.equals(storedDayOfWeek)) {
            return currentDaysOfWeek[1]; // "Immediate" or "מיידי"
        }
        if (TaskConstants.DAY_SOON.equals(storedDayOfWeek)) {
            return currentDaysOfWeek[2]; // "Soon" or "בקרוב"
        }
        
        // Map English day names to current language
        String[] englishDays = {TaskConstants.DAY_SUNDAY, TaskConstants.DAY_MONDAY, TaskConstants.DAY_TUESDAY, 
                               TaskConstants.DAY_WEDNESDAY, TaskConstants.DAY_THURSDAY, TaskConstants.DAY_FRIDAY, TaskConstants.DAY_SATURDAY};
        
        // Find the English day and map to current language
        for (int i = 0; i < englishDays.length; i++) {
            if (englishDays[i].equals(storedDayOfWeek)) {
                if (i + 3 < currentDaysOfWeek.length) {
                    return currentDaysOfWeek[i + 3];
                }
            }
        }
        
        // If not found, return the original (fallback)
        Log.w("MyToDo", "Could not map dayOfWeek: " + storedDayOfWeek);
        return storedDayOfWeek;
    }

    private boolean shouldRecurringTaskAppearThisWeek(Task task, long todayMillis) {
        // This method determines if a recurring task should appear in the current week
        // For now, all recurring tasks appear every week
        // In the future, this could be enhanced to handle bi-weekly, monthly, yearly patterns
        
        if (!task.isRecurring) {
            return true; // Non-recurring tasks always appear
        }
        
        // For now, all recurring tasks appear every week
        // This ensures weekly, bi-weekly, monthly, yearly tasks always show up
        return true;
    }
}