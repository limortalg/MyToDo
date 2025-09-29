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
        } else {
            Log.e("MyToDo", "TaskAdapter: Invalid dayIndex: " + dayIndex + ", todayDayOfWeek: " + todayDayOfWeek);
        }
    }

    public Task getTaskAtPosition(int position) {
        int currentPos = 0;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof String) {
                String category = (String) item;
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
                    if (expandedCategories.contains(category)) {
                        if (currentPos == position) {
                            return task;
                        }
                        currentPos++;
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
        
        // Store the current expanded categories before clearing
        List<String> previouslyExpanded = new ArrayList<>(expandedCategories);
        
        this.items.clear();
        this.items.addAll(items);
        
        
        // Check if there's an active search query
        String searchQuery = viewModel.getSearchQuery().getValue();
        boolean hasActiveSearch = searchQuery != null && !searchQuery.trim().isEmpty();
        
        if (hasActiveSearch) {
            // When searching, expand all categories that have tasks
            expandedCategories.clear();
            
            // Add all categories that have tasks
            for (Object item : items) {
                if (item instanceof String) {
                    String category = (String) item;
                    if (hasTasksInCategory(category)) {
                        expandedCategories.add(category);
                    }
                }
            }
        } else {
            // When not searching, preserve manually expanded categories and add defaults
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
            }
            
            // If there was a previously expanded category, use that instead
            if (!previouslyExpanded.isEmpty()) {
                String lastExpanded = previouslyExpanded.get(previouslyExpanded.size() - 1);
                if (hasTasksInCategory(lastExpanded)) {
                    expandedCategories.clear();
                    expandedCategories.add(lastExpanded);
                }
            }
        }
        
        notifyDataSetChanged();
    }

    public void moveItem(int fromPosition, int toPosition) {
        
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
        
        
        // COMPLETELY REWRITTEN: Only set manualPosition for the specific task that was dragged
        // This ensures that automatic tasks retain their automatic status (manualPosition = null)
        // while only the explicitly dragged task gets a manual position
        
        for (int i = 0; i < categoryTasks.size(); i++) {
            Task task = categoryTasks.get(i);
            
            // Check if this is the task that was dragged
            boolean isTheDraggedTask = (draggedTask != null && task.id == draggedTask.id);
            
            
            if (isTheDraggedTask) {
                // This is the task that was dragged - set its manualPosition to its new position
                Integer newManualPosition = i;
                if (!newManualPosition.equals(task.manualPosition)) {
                    task.manualPosition = newManualPosition;
                    tasksToUpdate.add(task);
                }
            } else if (task.manualPosition != null) {
                // This task already has a manual position - update it to reflect new order
                Integer newManualPosition = i;
                if (!newManualPosition.equals(task.manualPosition)) {
                    task.manualPosition = newManualPosition;
                    tasksToUpdate.add(task);
                }
            } else {
                // This task has no manual position and wasn't dragged - keep it automatic
            }
        }
        
        // Only update database if there are actual changes
        if (!tasksToUpdate.isEmpty() && viewModel != null) {
            viewModel.updateTaskOrder(tasksToUpdate);
        } else {
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
                                    return item;
                                }
                                currentPos++;
                                taskCount++;
                            }
                        }
                    }
                }
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
        
        for (String category : allCategories) {
            count++; // Always include header
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
                            }
                        }
                    }
                }
            }
        }
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = getItem(position);
        int type = item instanceof String ? TYPE_HEADER : TYPE_TASK;
        return type;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_task, parent, false);
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
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((String) item);
        } else if (holder instanceof TaskViewHolder) {
            ((TaskViewHolder) holder).bind((Task) item, position);
        }
    }

    // Check if a category has any tasks
    private boolean hasTasksInCategory(String category) {
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof Task) {
                Task task = (Task) item;
                if (task.description != null && !task.description.isEmpty()) {
                    String taskCategory = getCategoryForTask(task, i);
                    if (taskCategory.equals(category)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void setSelectedTask(Task selectedTask) {
        this.selectedTask = selectedTask;
        // Notify all items to update their radio button states
        notifyDataSetChanged();
    }

    public void setSelectedDayIndex(int selectedDayIndex) {
        this.selectedDayIndex = selectedDayIndex;
        notifyDataSetChanged();
    }
    
    public int getSelectedDayIndex() {
        return selectedDayIndex;
    }
    
    public boolean isCategoryExpanded(String category) {
        return expandedCategories.contains(category);
    }
    
    public void expandCategory(String category) {
        
        // If this category is already expanded, collapse it
        if (expandedCategories.contains(category)) {
            expandedCategories.remove(category);
        } else {
            // Close all other categories first, then expand this one
            expandedCategories.clear();
            expandedCategories.add(category);
        }
        
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
        }

        void bind(String category) {
            headerTextView.setText(category);
            // Set bold typeface if category has tasks
            boolean hasTasks = hasTasksInCategory(category);
            headerTextView.setTypeface(null, hasTasks ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    public class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView descriptionTextView;
        RadioButton selectedRadioButton;
        TextView dueDateTextView;
        TextView reminderIcon;
        TextView familySyncIcon;
        ImageView pinIcon;

        Task task;

        TaskViewHolder(View itemView) {
            super(itemView);
            descriptionTextView = itemView.findViewById(R.id.descriptionTextView);
            selectedRadioButton = itemView.findViewById(R.id.selectedRadioButton);
            dueDateTextView = itemView.findViewById(R.id.dueDateTextView);
            reminderIcon = itemView.findViewById(R.id.reminderIcon);
            familySyncIcon = itemView.findViewById(R.id.familySyncIcon);
            pinIcon = itemView.findViewById(R.id.pinIcon);

            
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
                        lastClickTime = 0; // Reset to prevent triple-click from triggering edit
                        return;
                    }
                    
                    // Single click - handle selection
                    lastClickTime = clickTime;
                    
                    // Determine selectedDayIndex for daily recurring tasks
                    String[] recurrenceTypes = context.getResources().getStringArray(R.array.recurrence_types);
                    String dailyRecurrenceType = recurrenceTypes[0];
                    boolean isDailyRecurring = task.isRecurring && TaskConstants.RECURRENCE_DAILY.equals(task.recurrenceType);

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
                boolean isDailyRecurring = task.isRecurring && TaskConstants.RECURRENCE_DAILY.equals(task.recurrenceType);
                
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
            } else if (task.dayOfWeek != null && task.dayOfWeek.equals(TaskConstants.DAY_IMMEDIATE)) {
                dueText = daysOfWeek[1]; // Immediate
            }
            // Removed dayOfWeek display since it's redundant when task is already in the correct category
            
            if (task.dueTime != null && !task.isCompleted) {
                // Convert dueTime to HH:mm format, handling both old and new formats
                String timeText = formatTimeOfDay(task.dueTime);
                dueText += " " + timeText;
            }
            
            if (task.isRecurring && task.recurrenceType != null && !task.recurrenceType.equals(TaskConstants.DAY_IMMEDIATE)) {
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
            
            // Show FamilySync icon if task is imported from FamilySync
            Log.d("MyToDo", "TaskAdapter: Checking FamilySync status for task: " + task.description);
            Log.d("MyToDo", "TaskAdapter: sourceApp: " + task.sourceApp + ", sourceTaskId: " + task.sourceTaskId);
            Log.d("MyToDo", "TaskAdapter: isExportedFromFamilySync: " + task.isExportedFromFamilySync());
            
            if (task.isExportedFromFamilySync()) {
                familySyncIcon.setVisibility(View.VISIBLE);
                Log.d("MyToDo", "TaskAdapter: Showing FamilySync icon for task: " + task.description);
            } else {
                familySyncIcon.setVisibility(View.GONE);
                Log.d("MyToDo", "TaskAdapter: Hiding FamilySync icon for task: " + task.description);
            }
            
            // Show pin icon if task was manually positioned (has manualPosition set)
            if (task.manualPosition != null) {
                pinIcon.setVisibility(View.VISIBLE);
                pinIcon.setOnClickListener(v -> {
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

            // Handle radio button selection
            selectedRadioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // Check if this is a daily recurring task
                    String[] recurrenceTypes = context.getResources().getStringArray(R.array.recurrence_types);
                    String dailyRecurrenceType = recurrenceTypes[0]; // "Daily" or "יומי"
                    boolean isDailyRecurring = task.isRecurring && TaskConstants.RECURRENCE_DAILY.equals(task.recurrenceType);
                    
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
                    }
                    
                    setSelectedTask(task);
                    onTaskSelected.accept(task);
                } else {
                    // Only allow unselection if this was the previously selected task
                    if (selectedTask != null && selectedTask.id == task.id) {
                        selectedDayIndex = -1; // Clear day selection
                        setSelectedTask(null);
                        onTaskSelected.accept(null);
                    }
                }
            });

            // Set task ID as tag
            itemView.setTag(task.id);
            
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
            // 1. It has dayOfWeek set to "Immediate" (English) or "מיידי" (Hebrew)
            // 2. It has a due date that is overdue (past today)
            
            // Check if task is explicitly marked as immediate (compare with English constant)
            if (task.dayOfWeek != null && task.dayOfWeek.equals(TaskConstants.DAY_IMMEDIATE)) {
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