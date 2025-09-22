package limor.tal.mytodo;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import android.graphics.Paint;

public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<Object> items;
    private TaskViewModel viewModel;
    private Context context;
    private List<String> expandedCategories;
    private Consumer<Task> onTaskSelected;
    private Task selectedTask = null;
    private int selectedDayIndex = -1; // Track which day instance is selected for daily tasks
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_TASK = 1;
    

    
    // Double-click detection
    private static final long DOUBLE_CLICK_TIME_DELTA = 300; // milliseconds
    private long lastClickTime = 0;

    public TaskAdapter(List<Object> items, TaskViewModel viewModel, Context context, Consumer<Task> onTaskSelected) {
        this.items = items;
        this.viewModel = viewModel;
        this.context = context;
        this.onTaskSelected = onTaskSelected;
        this.expandedCategories = new ArrayList<>();
        // Initialize only the current day's category as expanded
        String[] daysOfWeek = context.getResources().getStringArray(R.array.days_of_week);
        
        // Add current day's category (e.g., "שבת" for Saturday)
        Calendar today = Calendar.getInstance();
        int todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK); // 1=Sunday, ..., 7=Saturday
        // days_of_week: [None, Immediate, Soon, Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday]
        // Map Calendar.DAY_OF_WEEK to days_of_week array indices
        int dayIndex;
        switch (todayDayOfWeek) {
            case Calendar.SUNDAY: dayIndex = 3; break;    // Sunday
            case Calendar.MONDAY: dayIndex = 4; break;    // Monday
            case Calendar.TUESDAY: dayIndex = 5; break;   // Tuesday
            case Calendar.WEDNESDAY: dayIndex = 6; break; // Wednesday
            case Calendar.THURSDAY: dayIndex = 7; break;  // Thursday
            case Calendar.FRIDAY: dayIndex = 8; break;    // Friday
            case Calendar.SATURDAY: dayIndex = 9; break;  // Saturday
            default: dayIndex = 8; break; // Default to Friday
        }
        if (dayIndex >= 3 && dayIndex < daysOfWeek.length) {
            expandedCategories.add(daysOfWeek[dayIndex]);
            Log.d("MyToDo", "TaskAdapter: Added current day category: " + daysOfWeek[dayIndex] + " (index: " + dayIndex + ")");
        } else {
            Log.e("MyToDo", "TaskAdapter: Invalid dayIndex: " + dayIndex + ", todayDayOfWeek: " + todayDayOfWeek);
        }
        Log.d("MyToDo", "TaskAdapter: Initialized with expanded categories: " + expandedCategories + ", todayDayOfWeek: " + todayDayOfWeek);
    }

    public Task getTaskAtPosition(int position) {
        Log.d("MyToDo", "TaskAdapter: getTaskAtPosition: Looking for task at position: " + position);
        int currentPos = 0;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof String) {
                String category = (String) item;
                Log.d("MyToDo", "TaskAdapter: getTaskAtPosition: Found category at index " + i + ": " + category + ", currentPos: " + currentPos);
                if (currentPos == position) {
                    Log.e("MyToDo", "TaskAdapter: getTaskAtPosition: Position " + position + " is a header: " + item);
                    return null;
                }
                currentPos++;
            } else if (item instanceof Task) {
                Task task = (Task) item;
                if (task.description != null && !task.description.isEmpty()) {
                    // Use the corrected method to get the category for this specific task instance
                    String category = getCategoryForTask(task, i);
                    Log.d("MyToDo", "TaskAdapter: getTaskAtPosition: Found task at index " + i + ": " + task.description + " (id: " + task.id + ") in category: " + category + ", expanded: " + expandedCategories.contains(category));
                    if (expandedCategories.contains(category)) {
                        if (currentPos == position) {
                            Log.d("MyToDo", "TaskAdapter: getTaskAtPosition: Found task at position " + position + ": " + task.description + ", id: " + task.id + " in category: " + category);
                            return task;
                        }
                        currentPos++;
                        Log.d("MyToDo", "TaskAdapter: getTaskAtPosition: Incremented currentPos to: " + currentPos);
                    }
                } else {
                    Log.w("MyToDo", "TaskAdapter: getTaskAtPosition: Skipping invalid task at index: " + i + ", description: " + task.description);
                }
            }
        }
        Log.e("MyToDo", "TaskAdapter: getTaskAtPosition: No task found at position: " + position);
        return null;
    }

    public Task getTaskById(int taskId) {
        for (Object item : items) {
            if (item instanceof Task && ((Task) item).id == taskId) {
                Task task = (Task) item;
                if (task.description != null && !task.description.isEmpty()) {
                    Log.d("MyToDo", "TaskAdapter: getTaskById: Found task by id: " + taskId + ", task: " + task.description);
                    return task;
                } else {
                    Log.w("MyToDo", "TaskAdapter: getTaskById: Skipping invalid task with id: " + taskId);
                }
            }
        }
        Log.e("MyToDo", "TaskAdapter: getTaskById: No valid task found for id: " + taskId);
        return null;
    }

    public void setItems(List<Object> items) {
        Log.d("MyToDo", "TaskAdapter: setItems: Setting items in adapter, count: " + items.size());
        
        // Store the current expanded categories before clearing
        List<String> previouslyExpanded = new ArrayList<>(expandedCategories);
        Log.d("MyToDo", "TaskAdapter: setItems: Previously expanded categories: " + previouslyExpanded);
        
        this.items.clear();
        this.items.addAll(items);
        
        // Log the structure of the items list
        Log.d("MyToDo", "TaskAdapter: setItems: Items list structure:");
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof String) {
                Log.d("MyToDo", "  [" + i + "] Category: " + item);
            } else if (item instanceof Task) {
                Task task = (Task) item;
                Log.d("MyToDo", "  [" + i + "] Task: " + task.description + " (id: " + task.id + ")");
            }
        }
        
        for (Object item : items) {
            Log.d("MyToDo", "TaskAdapter: setItems: Item: " + (item instanceof String ? item : ((Task) item).description + ", isCompleted: " + ((Task) item).isCompleted + ", id: " + ((Task) item).id));
        }
        
        // Check if there's an active search query
        String searchQuery = viewModel.getSearchQuery().getValue();
        boolean hasActiveSearch = searchQuery != null && !searchQuery.trim().isEmpty();
        
        if (hasActiveSearch) {
            // When searching, expand all categories that have tasks
            Log.d("MyToDo", "TaskAdapter: setItems: Active search detected, expanding all categories with tasks");
            expandedCategories.clear();
            
            // Add all categories that have tasks
            for (Object item : items) {
                if (item instanceof String) {
                    String category = (String) item;
                    if (hasTasksInCategory(category)) {
                        expandedCategories.add(category);
                        Log.d("MyToDo", "TaskAdapter: setItems: Expanded category for search: " + category);
                    }
                }
            }
        } else {
            // When not searching, preserve manually expanded categories and add defaults
            Log.d("MyToDo", "TaskAdapter: setItems: No active search, preserving manually expanded categories");
            expandedCategories.clear();
            
            // Initialize with only the current day's category
            String[] daysOfWeek = context.getResources().getStringArray(R.array.days_of_week);
            
            // Add current day's category
            Calendar today = Calendar.getInstance();
            int todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK);
            int dayIndex;
            switch (todayDayOfWeek) {
                case Calendar.SUNDAY: dayIndex = 3; break;    // Sunday
                case Calendar.MONDAY: dayIndex = 4; break;    // Monday
                case Calendar.TUESDAY: dayIndex = 5; break;   // Tuesday
                case Calendar.WEDNESDAY: dayIndex = 6; break; // Wednesday
                case Calendar.THURSDAY: dayIndex = 7; break;  // Thursday
                case Calendar.FRIDAY: dayIndex = 8; break;    // Friday
                case Calendar.SATURDAY: dayIndex = 9; break;  // Saturday
                default: dayIndex = 8; break; // Default to Friday
            }
            if (dayIndex >= 3 && dayIndex < daysOfWeek.length) {
                expandedCategories.add(daysOfWeek[dayIndex]);
                Log.d("MyToDo", "TaskAdapter: setItems: Added current day category: " + daysOfWeek[dayIndex] + " (index: " + dayIndex + ")");
            }
            
            // If there was a previously expanded category, use that instead
            if (!previouslyExpanded.isEmpty()) {
                String lastExpanded = previouslyExpanded.get(previouslyExpanded.size() - 1);
                if (hasTasksInCategory(lastExpanded)) {
                    expandedCategories.clear();
                    expandedCategories.add(lastExpanded);
                    Log.d("MyToDo", "TaskAdapter: setItems: Restored previously expanded category: " + lastExpanded);
                }
            }
        }
        
        Log.d("MyToDo", "TaskAdapter: setItems: Final expanded categories: " + expandedCategories);
        notifyDataSetChanged();
    }

    public void moveItem(int fromPosition, int toPosition) {
        Log.d("MyToDo", "TaskAdapter: SIMPLE moveItem: Moving from " + fromPosition + " to " + toPosition);
        
        // Basic validation
        if (fromPosition == toPosition) return;
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= items.size() || toPosition >= items.size()) {
            Log.e("MyToDo", "TaskAdapter: moveItem: Invalid positions");
            return;
        }
        
        // Get items
        Object fromItem = items.get(fromPosition);
        Object toItem = items.get(toPosition);
        
        // Only allow moving tasks
        if (!(fromItem instanceof Task) || !(toItem instanceof Task)) {
            Log.e("MyToDo", "TaskAdapter: moveItem: Can only move tasks");
            return;
        }
        
        // Check same category
        String fromCategory = getCategoryForPosition(fromPosition);
        String toCategory = getCategoryForPosition(toPosition);
        if (!fromCategory.equals(toCategory)) {
            Log.e("MyToDo", "TaskAdapter: moveItem: Cannot move between categories");
            return;
        }
        
        // SIMPLE APPROACH: Just move the item in the list!
        Object movedItem = items.remove(fromPosition);
        items.add(toPosition, movedItem);
        
        // Update UI immediately
        notifyItemMoved(fromPosition, toPosition);
        
        // Don't update database here - will be done when drag completes
        
        Log.d("MyToDo", "TaskAdapter: SIMPLE moveItem: Successfully moved item");
    }
    
    // Backward compatibility method
    public void persistCategoryOrderToDatabase(String category, TaskViewModel viewModel) {
        persistCategoryOrderToDatabase(category, viewModel, null);
    }
    
    public void persistCategoryOrderToDatabase(String category, TaskViewModel viewModel, Task draggedTask) {
        List<Task> categoryTasks = new ArrayList<>();
        List<Task> tasksToUpdate = new ArrayList<>();
        
        // Find all tasks in this category
        for (Object item : items) {
            if (item instanceof Task) {
                Task task = (Task) item;
                String taskCategory = getCategoryForTask(task, items.indexOf(item));
                if (category.equals(taskCategory)) {
                    categoryTasks.add(task);
                }
            }
        }
        
        Log.d("MyToDo", "TaskAdapter: Found " + categoryTasks.size() + " tasks in category " + category);
        Log.d("MyToDo", "TaskAdapter: Dragged task: " + (draggedTask != null ? draggedTask.description : "null"));
        
        // COMPLETELY REWRITTEN: Only set manualPosition for the specific task that was dragged
        // This ensures that automatic tasks retain their automatic status (manualPosition = null)
        // while only the explicitly dragged task gets a manual position
        
        for (int i = 0; i < categoryTasks.size(); i++) {
            Task task = categoryTasks.get(i);
            
            // Check if this is the task that was dragged
            boolean isTheDraggedTask = (draggedTask != null && task.id == draggedTask.id);
            
            Log.d("MyToDo", "TaskAdapter: Task " + task.description + " at position " + i + 
                  " - isDragged=" + isTheDraggedTask + ", currentManualPos=" + task.manualPosition);
            
            if (isTheDraggedTask) {
                // This is the task that was dragged - set its manualPosition to its new position
                Integer newManualPosition = i;
                if (!newManualPosition.equals(task.manualPosition)) {
                    task.manualPosition = newManualPosition;
                    tasksToUpdate.add(task);
                    Log.d("MyToDo", "TaskAdapter: Set manualPosition for dragged task " + task.description + 
                          " to " + newManualPosition);
                }
            } else if (task.manualPosition != null) {
                // This task already has a manual position - update it to reflect new order
                Integer newManualPosition = i;
                if (!newManualPosition.equals(task.manualPosition)) {
                    task.manualPosition = newManualPosition;
                    tasksToUpdate.add(task);
                    Log.d("MyToDo", "TaskAdapter: Updated existing manualPosition for " + task.description + 
                          " from " + task.manualPosition + " to " + newManualPosition);
                }
            } else {
                // This task has no manual position and wasn't dragged - keep it automatic
                Log.d("MyToDo", "TaskAdapter: Task " + task.description + " remains automatic");
            }
        }
        
        // Only update database if there are actual changes
        if (!tasksToUpdate.isEmpty() && viewModel != null) {
            viewModel.updateTaskOrder(tasksToUpdate);
            Log.d("MyToDo", "TaskAdapter: Updated manualPosition for " + tasksToUpdate.size() + " tasks in " + category);
        } else {
            Log.d("MyToDo", "TaskAdapter: No manualPosition changes needed for category " + category);
        }
    }
    
    private boolean isValidPosition(int position) {
        return position >= 0 && position < getItemCount();
    }

    public String getCategoryForPosition(int position) {
        int currentPos = 0;
        String[] daysOfWeek = context.getResources().getStringArray(R.array.days_of_week);
        String waitingCategory = context.getString(R.string.category_waiting);
        String completedCategory = context.getString(R.string.completed_category_title);
        
        // Get dynamic category order (same as getItem method)
        // Note: Immediate tasks are now merged into current day, not a separate category
        List<String> allCategories = new ArrayList<>();
        // Do not add Immediate as separate category anymore
        
        // Get current day and create order starting from today
        Calendar today = Calendar.getInstance();
        int todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK); // 1=Sunday, ..., 7=Saturday
        
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
        
        // Add current day and next 6 days in order
        for (int i = 0; i < 7; i++) {
            int dayIndex = todayIndex + i;
            // Handle wrapping around from Saturday (9) to Sunday (3)
            if (dayIndex > 9) {
                dayIndex = 3 + (dayIndex - 10); // Wrap to Sunday (3)
            }
            allCategories.add(daysOfWeek[dayIndex]);
        }
        
        allCategories.add(daysOfWeek[2]); // Soon
        allCategories.add(waitingCategory); // Waiting
        allCategories.add(completedCategory); // Completed
        
        for (String category : allCategories) {
            if (currentPos == position) {
                Log.d("MyToDo", "TaskAdapter: getCategoryForPosition: Position " + position + " is in category: " + category);
                return category;
            }
            currentPos++;
            if (expandedCategories.contains(category)) {
                // Count tasks in this category by looking at the items list
                for (int i = 0; i < items.size(); i++) {
                    Object item = items.get(i);
                    if (item instanceof Task) {
                        Task task = (Task) item;
                        if (task.description != null && !task.description.isEmpty()) {
                            // Check if this task belongs to the current category
                            String taskCategory = getCategoryForTask(task, i);
                            if (taskCategory.equals(category)) {
                                if (currentPos == position) {
                                    Log.d("MyToDo", "TaskAdapter: getCategoryForPosition: Position " + position + " is task " + task.description + " in category: " + category);
                                    return category;
                                }
                                currentPos++;
                            }
                        }
                    }
                }
            }
        }
        Log.w("MyToDo", "TaskAdapter: getCategoryForPosition: No category found for position: " + position);
        return "";
    }
    
    private String getCategoryForTask(Task task, int itemIndex) {
        // Find which category this task belongs to by looking backwards from its position
        String currentCategory = "";
        for (int i = itemIndex; i >= 0; i--) {
            Object item = items.get(i);
            if (item instanceof String) {
                currentCategory = (String) item;
                break;
            }
        }
        return currentCategory;
    }

    public Object getItem(int position) {
        int currentPos = 0;
        String[] daysOfWeek = context.getResources().getStringArray(R.array.days_of_week);
        String waitingCategory = context.getString(R.string.category_waiting);
        String completedCategory = context.getString(R.string.completed_category_title);
        
        // Get dynamic category order (same as getItemCount)
        // Note: Immediate tasks are now merged into current day, not a separate category
        List<String> allCategories = new ArrayList<>();
        // Do not add Immediate as separate category anymore
        
        // Get current day and create order starting from today
        Calendar today = Calendar.getInstance();
        int todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK); // 1=Sunday, ..., 7=Saturday
        
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
        
        // Add current day and next 6 days in order
        for (int i = 0; i < 7; i++) {
            int dayIndex = todayIndex + i;
            // Handle wrapping around from Saturday (9) to Sunday (3)
            if (dayIndex > 9) {
                dayIndex = 3 + (dayIndex - 10); // Wrap to Sunday (3)
            }
            allCategories.add(daysOfWeek[dayIndex]);
        }
        
        allCategories.add(daysOfWeek[2]); // Soon
        allCategories.add(waitingCategory); // Waiting
        allCategories.add(completedCategory); // Completed
        
        for (String category : allCategories) {
            if (currentPos == position) {
                Log.d("MyToDo", "TaskAdapter: getItem: Position " + position + ": " + category);
                return category;
            }
            currentPos++;
            if (expandedCategories.contains(category)) {
                // Count tasks in this category by looking at the items list
                int taskCount = 0;
                for (int i = 0; i < items.size(); i++) {
                    Object item = items.get(i);
                    if (item instanceof Task) {
                        Task task = (Task) item;
                        if (task.description != null && !task.description.isEmpty()) {
                            // Check if this task belongs to the current category
                            String taskCategory = getCategoryForTask(task, i);
                            if (taskCategory.equals(category)) {
                                if (currentPos == position) {
                                    Log.d("MyToDo", "TaskAdapter: getItem: Position " + position + ": " + task.description + ", id: " + task.id + " in category: " + category);
                                    return item;
                                }
                                currentPos++;
                                taskCount++;
                            }
                        }
                    }
                }
                Log.d("MyToDo", "TaskAdapter: getItem: Category " + category + " has " + taskCount + " tasks");
            }
        }
        Log.e("MyToDo", "TaskAdapter: getItem: No item found at position: " + position);
        return null;
    }

    @Override
    public int getItemCount() {
        int count = 0;
        String[] daysOfWeek = context.getResources().getStringArray(R.array.days_of_week);
        String waitingCategory = context.getString(R.string.category_waiting);
        String completedCategory = context.getString(R.string.completed_category_title);
        
        // Get dynamic category order from TaskViewModel
        // Note: Immediate tasks are now merged into current day, not a separate category
        List<String> allCategories = new ArrayList<>();
        // Do not add Immediate as separate category anymore
        
        // Get current day and create order starting from today
        Calendar today = Calendar.getInstance();
        int todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK); // 1=Sunday, ..., 7=Saturday
        
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
        
        // Add current day and next 6 days in order
        for (int i = 0; i < 7; i++) {
            int dayIndex = todayIndex + i;
            // Handle wrapping around from Saturday (9) to Sunday (3)
            if (dayIndex > 9) {
                dayIndex = 3 + (dayIndex - 10); // Wrap to Sunday (3)
            }
            allCategories.add(daysOfWeek[dayIndex]);
        }
        
        allCategories.add(daysOfWeek[2]); // Soon
        allCategories.add(waitingCategory); // Waiting
        allCategories.add(completedCategory); // Completed
        
        Log.d("MyToDo", "TaskAdapter: getItemCount: All categories: " + allCategories);

        for (String category : allCategories) {
            count++; // Always include header
            Log.d("MyToDo", "TaskAdapter: getItemCount: Counting header: " + category);
            if (expandedCategories.contains(category)) {
                int taskCount = 0;
                for (int i = 0; i < items.size(); i++) {
                    Object item = items.get(i);
                    if (item instanceof Task) {
                        Task task = (Task) item;
                        if (task.description != null && !task.description.isEmpty()) {
                            // Check if this task belongs to the current category
                            String taskCategory = getCategoryForTask(task, i);
                            if (taskCategory.equals(category)) {
                                count++;
                                taskCount++;
                                Log.d("MyToDo", "TaskAdapter: getItemCount: Counting task: " + task.description + ", isCompleted: " + task.isCompleted + ", id: " + task.id + " in category: " + category);
                            }
                        }
                    }
                }
                Log.d("MyToDo", "TaskAdapter: getItemCount: Category " + category + " has " + taskCount + " tasks");
            }
        }
        Log.d("MyToDo", "TaskAdapter: getItemCount: Adapter item count: " + count);
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = getItem(position);
        int type = item instanceof String ? TYPE_HEADER : TYPE_TASK;
        Log.d("MyToDo", "TaskAdapter: getItemViewType: Position " + position + ", type: " + (type == TYPE_HEADER ? "HEADER" : "TASK"));
        return type;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_header, parent, false);
            Log.d("MyToDo", "TaskAdapter: onCreateViewHolder: Creating HeaderViewHolder");
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_task, parent, false);
            Log.d("MyToDo", "TaskAdapter: onCreateViewHolder: Creating TaskViewHolder");
            return new TaskViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Object item = getItem(position);
        if (item == null) {
            Log.e("MyToDo", "TaskAdapter: onBindViewHolder: Item at position " + position + " is null");
            return;
        }
        Log.d("MyToDo", "TaskAdapter: onBindViewHolder: Binding item at position " + position + ": " + (item instanceof String ? item : ((Task) item).description + ", isCompleted: " + ((Task) item).isCompleted + ", id: " + ((Task) item).id));
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((String) item);
        } else if (holder instanceof TaskViewHolder) {
            ((TaskViewHolder) holder).bind((Task) item, position);
        }
    }

    // Check if a category has any tasks
    private boolean hasTasksInCategory(String category) {
        Log.d("MyToDo", "TaskAdapter: hasTasksInCategory: Checking category: " + category);
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof Task) {
                Task task = (Task) item;
                if (task.description != null && !task.description.isEmpty()) {
                    String taskCategory = getCategoryForTask(task, i);
                    Log.d("MyToDo", "TaskAdapter: hasTasksInCategory: Task " + task.description + " (id: " + task.id + ") at index " + i + " belongs to category: " + taskCategory);
                    if (taskCategory.equals(category)) {
                        Log.d("MyToDo", "TaskAdapter: hasTasksInCategory: Category " + category + " has tasks!");
                        return true;
                    }
                }
            }
        }
        Log.d("MyToDo", "TaskAdapter: hasTasksInCategory: Category " + category + " has no tasks");
        return false;
    }

    public void setSelectedTask(Task selectedTask) {
        this.selectedTask = selectedTask;
        // Notify all items to update their radio button states
        notifyDataSetChanged();
        Log.d("MyToDo", "TaskAdapter: setSelectedTask: Selected task: " + (selectedTask != null ? selectedTask.description : "null") + ", id: " + (selectedTask != null ? selectedTask.id : "null"));
    }

    public void setSelectedDayIndex(int selectedDayIndex) {
        this.selectedDayIndex = selectedDayIndex;
        notifyDataSetChanged();
        Log.d("MyToDo", "TaskAdapter: setSelectedDayIndex: Selected day index: " + selectedDayIndex);
    }
    
    public int getSelectedDayIndex() {
        return selectedDayIndex;
    }
    
    public boolean isCategoryExpanded(String category) {
        return expandedCategories.contains(category);
    }
    
    public void expandCategory(String category) {
        Log.d("MyToDo", "TaskAdapter: expandCategory called with category: " + category);
        Log.d("MyToDo", "TaskAdapter: Current expanded categories: " + expandedCategories);
        
        // If this category is already expanded, collapse it
        if (expandedCategories.contains(category)) {
            expandedCategories.remove(category);
            Log.d("MyToDo", "TaskAdapter: Collapsed category: " + category);
        } else {
            // Close all other categories first, then expand this one
            expandedCategories.clear();
            expandedCategories.add(category);
            Log.d("MyToDo", "TaskAdapter: Expanded only category: " + category);
        }
        
        Log.d("MyToDo", "TaskAdapter: Updated expanded categories: " + expandedCategories);
        notifyDataSetChanged();
    }

    public class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerTextView;

        HeaderViewHolder(View itemView) {
            super(itemView);
            headerTextView = itemView.findViewById(R.id.headerTextView);
            // Add click listener to toggle category expansion
            itemView.setOnClickListener(v -> {
                String category = headerTextView.getText().toString();
                expandCategory(category);
            });
            Log.d("MyToDo", "TaskAdapter: HeaderViewHolder: Created");
        }

        void bind(String category) {
            headerTextView.setText(category);
            // Set bold typeface if category has tasks
            boolean hasTasks = hasTasksInCategory(category);
            headerTextView.setTypeface(null, hasTasks ? Typeface.BOLD : Typeface.NORMAL);
            Log.d("MyToDo", "TaskAdapter: HeaderViewHolder: Binding header: " + category + ", hasTasks: " + hasTasks);
        }
    }

    public class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView descriptionTextView;
        RadioButton selectedRadioButton;
        TextView dueDateTextView;
        TextView reminderIcon;
        ImageView pinIcon;

        Task task;

        TaskViewHolder(View itemView) {
            super(itemView);
            descriptionTextView = itemView.findViewById(R.id.descriptionTextView);
            selectedRadioButton = itemView.findViewById(R.id.selectedRadioButton);
            dueDateTextView = itemView.findViewById(R.id.dueDateTextView);
            reminderIcon = itemView.findViewById(R.id.reminderIcon);
            pinIcon = itemView.findViewById(R.id.pinIcon);

            
            Log.d("MyToDo", "TaskAdapter: TaskViewHolder: Created with drag handle");
        }

        void bind(Task task, int position) {
            this.task = task;
            
            // Check if this task is marked as immediate and add visual indicator
            String displayText = task.description != null ? task.description : "";
            if (isTaskImmediate(task)) {
                displayText = "⚡ " + displayText; // Add lightning bolt emoji for immediate tasks
            }
            descriptionTextView.setText(displayText);
            
            // Handle single click for selection (exclusive) and double-click for editing
            itemView.setOnClickListener(v -> {
                if (task != null) {
                    long clickTime = System.currentTimeMillis();
                    if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                        // Double-click detected - edit the task
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).openEditDialog(task);
                        }
                        Log.d("MyToDo", "TaskAdapter: TaskViewHolder: Double-click for editing: " + task.description + ", id: " + task.id);
                        lastClickTime = 0; // Reset to prevent triple-click from triggering edit
                        return;
                    }
                    
                    // Single click - handle selection
                    lastClickTime = clickTime;
                    
                    // Determine selectedDayIndex for daily recurring tasks
                    String[] recurrenceTypes = context.getResources().getStringArray(R.array.recurrence_types);
                    String dailyRecurrenceType = recurrenceTypes[0];
                    boolean isDailyRecurring = task.isRecurring && (dailyRecurrenceType.equals(task.recurrenceType) ||
                            "Daily".equals(task.recurrenceType) || "יומי".equals(task.recurrenceType));

                    if (isDailyRecurring) {
                        String taskCategory = getCategoryForTask(task, position);
                        String[] daysOfWeekArray = context.getResources().getStringArray(R.array.days_of_week);
                        int dayIndex = -1;
                        for (int i = 0; i < daysOfWeekArray.length; i++) {
                            if (daysOfWeekArray[i].equals(taskCategory)) {
                                dayIndex = i;
                                break;
                            }
                        }
                        selectedDayIndex = dayIndex;
                    } else {
                        selectedDayIndex = -1;
                    }

                    setSelectedTask(task);
                    onTaskSelected.accept(task);
                    Log.d("MyToDo", "TaskAdapter: TaskViewHolder: Single click for selection: " + task.description + ", id: " + task.id);
                }
            });
            
            // Handle double-click on radio button for editing
            selectedRadioButton.setOnClickListener(v -> {
                if (task != null) {
                    long clickTime = System.currentTimeMillis();
                    if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                        // Double-click detected - edit the task
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).openEditDialog(task);
                        }
                        Log.d("MyToDo", "TaskAdapter: TaskViewHolder: RadioButton double-click for editing: " + task.description + ", id: " + task.id);
                        lastClickTime = 0; // Reset to prevent triple-click from triggering edit
                        return;
                    }
                    
                    // Single click - handle selection (this will be handled by the radio button's OnCheckedChangeListener)
                    lastClickTime = clickTime;
                }
            });
            
            // Set radio button selection state
            selectedRadioButton.setOnCheckedChangeListener(null);
            
            // For daily recurring tasks, check if this specific day instance is selected
            boolean isSelected = false;
            
            if (selectedTask != null && selectedTask.id == task.id) {
                // Check if this is a daily recurring task
                String[] recurrenceTypes = context.getResources().getStringArray(R.array.recurrence_types);
                String dailyRecurrenceType = recurrenceTypes[0]; // "Daily" or "יומי"
                boolean isDailyRecurring = task.isRecurring && (dailyRecurrenceType.equals(task.recurrenceType) || 
                        "Daily".equals(task.recurrenceType) || "יומי".equals(task.recurrenceType));
                
                if (isDailyRecurring) {
                    // For daily tasks, check if this specific day instance is selected
                    // Find which day this task instance belongs to
                    String taskCategory = getCategoryForTask(task, position);
                    String[] daysOfWeekArray = context.getResources().getStringArray(R.array.days_of_week);
                    int dayIndex = -1;
                    for (int i = 0; i < daysOfWeekArray.length; i++) {
                        if (daysOfWeekArray[i].equals(taskCategory)) {
                            dayIndex = i;
                            break;
                        }
                    }
                    
                    isSelected = selectedDayIndex == dayIndex;
                    Log.d("MyToDo", "TaskAdapter: TaskViewHolder: Daily task selection check - dayIndex: " + dayIndex + ", selectedDayIndex: " + selectedDayIndex + ", isSelected: " + isSelected);
                } else {
                    // For regular tasks, use the normal selection logic
                    isSelected = selectedTask.id == task.id;
                }
            }
            
            selectedRadioButton.setChecked(isSelected);
            selectedRadioButton.setEnabled(true); // Always enabled - future days are selectable
            
            // Apply visual styling for completed tasks
            if (task.isCompleted) {
                descriptionTextView.setPaintFlags(descriptionTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                descriptionTextView.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
                dueDateTextView.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
            } else {
                descriptionTextView.setPaintFlags(descriptionTextView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                descriptionTextView.setTextColor(context.getResources().getColor(android.R.color.black));
                dueDateTextView.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
            }
            
            String[] daysOfWeek = context.getResources().getStringArray(R.array.days_of_week);
            String dueText = "";
            
            if (task.isCompleted && task.completionDate != null) {
                // For completed tasks, show localized "Completed:" label and completion time
                Calendar completionCal = Calendar.getInstance();
                completionCal.setTimeInMillis(task.completionDate);
                String label = context.getString(R.string.completed_label);
                dueText = label + ": " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(completionCal.getTime());
            } else if (task.dueDate != null) {
                dueText = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(task.dueDate);
            } else if (task.dayOfWeek != null && task.dayOfWeek.equals(daysOfWeek[1])) {
                dueText = daysOfWeek[1]; // Immediate
            }
            // Removed dayOfWeek display since it's redundant when task is already in the correct category
            
            if (task.dueTime != null && !task.isCompleted) {
                // Convert dueTime to HH:mm format, handling both old and new formats
                String timeText = formatTimeOfDay(task.dueTime);
                dueText += " " + timeText;
            }
            
            if (task.isRecurring && task.recurrenceType != null && !task.recurrenceType.equals(daysOfWeek[1])) {
                dueText += " (" + task.recurrenceType + ")";
            }
            
            if (task.reminderOffset != null && task.reminderOffset > 0 && (!task.isCompleted || task.isRecurring)) {
                dueText += " [" + getReminderOptionString(task.reminderOffset) + "]";
            }
            dueDateTextView.setText(dueText);
            
            // Show reminder icon if task has a reminder
            if (task.dueTime != null && task.reminderOffset != null && task.reminderOffset >= 0) {
                reminderIcon.setVisibility(View.VISIBLE);
            } else {
                reminderIcon.setVisibility(View.GONE);
            }
            
            // Show pin icon if task was manually positioned (has manualPosition set)
            if (task.manualPosition != null) {
                pinIcon.setVisibility(View.VISIBLE);
                pinIcon.setOnClickListener(v -> {
                    Log.d("MyToDo", "TaskAdapter: Pin clicked for task: " + task.description);
                    // Unpin the task (restore time-based order) by setting manualPosition to null
                    task.manualPosition = null;
                    
                    // Update the task in the database
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).unpinTask(task);
                    }
                    
                    // Hide the pin icon immediately for visual feedback
                    pinIcon.setVisibility(View.GONE);
                });
            } else {
                pinIcon.setVisibility(View.GONE);
                pinIcon.setOnClickListener(null); // Clear any previous listener
            }
            Log.d("MyToDo", "TaskAdapter: TaskViewHolder: Binding task at position " + position + ": " + task.description + ", isCompleted: " + task.isCompleted + ", dueText: " + dueText + ", id: " + task.id);

            // Handle radio button selection
            selectedRadioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // Check if this is a daily recurring task
                    String[] recurrenceTypes = context.getResources().getStringArray(R.array.recurrence_types);
                    String dailyRecurrenceType = recurrenceTypes[0]; // "Daily" or "יומי"
                    boolean isDailyRecurring = task.isRecurring && (dailyRecurrenceType.equals(task.recurrenceType) || 
                            "Daily".equals(task.recurrenceType) || "יומי".equals(task.recurrenceType));
                    
                    if (isDailyRecurring) {
                        // For daily tasks, find which day this instance belongs to
                        String taskCategory = getCategoryForTask(task, position);
                        String[] daysOfWeekArray = context.getResources().getStringArray(R.array.days_of_week);
                        int dayIndex = -1;
                        for (int i = 0; i < daysOfWeekArray.length; i++) {
                            if (daysOfWeekArray[i].equals(taskCategory)) {
                                dayIndex = i;
                                break;
                            }
                        }
                        selectedDayIndex = dayIndex;
                        Log.d("MyToDo", "TaskAdapter: TaskViewHolder: Daily task selected for day index: " + dayIndex + ", category: " + taskCategory);
                    }
                    
                    setSelectedTask(task);
                    onTaskSelected.accept(task);
                    Log.d("MyToDo", "TaskAdapter: TaskViewHolder: Task selected: " + task.description + ", id: " + task.id);
                } else {
                    // Only allow unselection if this was the previously selected task
                    if (selectedTask != null && selectedTask.id == task.id) {
                        selectedDayIndex = -1; // Clear day selection
                        setSelectedTask(null);
                        onTaskSelected.accept(null);
                        Log.d("MyToDo", "TaskAdapter: TaskViewHolder: Task unselected: " + task.description + ", id: " + task.id);
                    }
                }
            });

            // Set task ID as tag
            itemView.setTag(task.id);
            Log.d("MyToDo", "TaskAdapter: TaskViewHolder: Set view tag for task: " + task.description + ", id: " + task.id + ", position: " + position);
            
            // Setup drag handle

        }
        


        private String getReminderOptionString(int offset) {
            String[] reminderOptions = context.getResources().getStringArray(R.array.reminder_options);
            switch (offset) {
                case 0:
                    return reminderOptions[0]; // At the time of the task
                case 15:
                    return reminderOptions[1]; // 15 minutes before
                case 30:
                    return reminderOptions[2]; // 30 minutes before
                case 60:
                    return reminderOptions[3]; // 60 minutes before
                default:
                    return reminderOptions[0]; // At the time of the task
            }
        }
        
        private boolean isTaskImmediate(Task task) {
            // A task is immediate if:
            // 1. It has dayOfWeek set to "Immediate" or "מיידי" 
            // 2. It has a due date that is overdue (past today)
            
            String[] daysOfWeek = context.getResources().getStringArray(R.array.days_of_week);
            String immediateOption = daysOfWeek[1]; // "Immediate" or "מיידי"
            
            // Check if task is explicitly marked as immediate
            if (task.dayOfWeek != null && task.dayOfWeek.equals(immediateOption)) {
                return true;
            }
            
            // Check if task has overdue date
            if (task.dueDate != null) {
                Calendar today = Calendar.getInstance();
                today.set(Calendar.HOUR_OF_DAY, 0);
                today.set(Calendar.MINUTE, 0);
                today.set(Calendar.SECOND, 0);
                today.set(Calendar.MILLISECOND, 0);
                long todayMillis = today.getTimeInMillis();
                
                if (task.dueDate < todayMillis) {
                    return true; // Task is overdue
                }
            }
            
            return false;
        }
    }
    
    // Helper method to format time (now always milliseconds since midnight)
    private String formatTimeOfDay(long millisSinceMidnight) {
        // All dueTime values are now stored as milliseconds since midnight
        int hours = (int) (millisSinceMidnight / (60 * 60 * 1000));
        int minutes = (int) ((millisSinceMidnight % (60 * 60 * 1000)) / (60 * 1000));
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", hours, minutes);
    }
}