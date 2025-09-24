// Task categorization logic that matches the Android app
export class TaskCategorizer {
  constructor(t) {
    this.t = t || ((key) => key); // Fallback to key if no translation function
    this.daysOfWeek = [
      this.t('None'), this.t('Immediate'), this.t('Soon'), this.t('Sunday'), this.t('Monday'), this.t('Tuesday'), 
      this.t('Wednesday'), this.t('Thursday'), this.t('Friday'), this.t('Saturday')
    ];
    
    this.immediateOption = this.t('Immediate');
    this.soonOption = this.t('Soon');
    this.waitingCategory = this.t('Waiting');
    this.completedCategory = this.t('Completed');
    this.noneOption = this.t('None');
  }

  // Convert time to milliseconds since midnight (matches Android logic)
  convertToTimeOfDay(timeValue) {
    return timeValue; // Already in milliseconds since midnight
  }

  // Map day of week to current language (matches Android logic)
  mapDayOfWeekToCurrentLanguage(dayOfWeek, daysOfWeek) {
    // Handle special categories first - support both English and Hebrew values
    if (dayOfWeek === 'Waiting' || dayOfWeek === 'בהמתנה') {
      return this.t('Waiting');
    }
    if (dayOfWeek === 'Immediate' || dayOfWeek === 'מיידי') {
      return this.t('Immediate');
    }
    if (dayOfWeek === 'Soon' || dayOfWeek === 'בקרוב') {
      return this.t('Soon');
    }
    
    // Map day names between languages
    const englishDays = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
    const hebrewDays = ['ראשון', 'שני', 'שלישי', 'רביעי', 'חמישי', 'שישי', 'שבת'];
    
    // Check if it's a Hebrew day name and map to English
    const hebrewIndex = hebrewDays.indexOf(dayOfWeek);
    if (hebrewIndex >= 0) {
      return englishDays[hebrewIndex];
    }
    
    // Find the index of the stored day in English
    let englishIndex = -1;
    for (let i = 0; i < englishDays.length; i++) {
      if (englishDays[i] === dayOfWeek) {
        englishIndex = i;
        break;
      }
    }
    
    // If found in English, map to current language
    if (englishIndex >= 0) {
      return daysOfWeek[englishIndex + 3]; // Indices 3-9 in daysOfWeek array
    }
    
    // If found in Hebrew, map to current language
    if (hebrewIndex >= 0) {
      return daysOfWeek[hebrewIndex + 3]; // Indices 3-9 in daysOfWeek array
    }
    
    // If not found, return the original (fallback)
    console.warn('Could not map dayOfWeek:', dayOfWeek);
    return dayOfWeek;
  }

  // Check if recurring task should appear this week
  shouldRecurringTaskAppearThisWeek(task, todayMillis) {
    if (!task.isRecurring) return true;
    
    // For now, always show recurring tasks
    // In a full implementation, this would check recurrence patterns
    return true;
  }

  // Process a single task and categorize it
  processSingleTask(task, immediateTasks, soonTasks, waitingTasks, completedTasks, dayTasks, 
                   daysOfWeek, dayIndices, immediateOption, soonOption, waitingCategory, 
                   completedCategory, noneOption, todayMillis, nextWeekMillis) {
    

    // Check if this is a daily recurring task
    const isDailyRecurring = task.isRecurring && 
      (task.recurrenceType === 'Daily' || task.recurrenceType === 'יומי');

    let category = waitingCategory; // Default fallback category
    
    if (task.isCompleted && task.completionDate != null && !task.isRecurring) {
      // Only NON-recurring completed tasks go to the Completed category
      category = completedCategory;
    } else if (task.isCompleted && !task.isRecurring) {
      // Task is completed but missing completion date
      console.warn('Completed task missing completion date, categorizing by day:', task.description);
      // Fall through to normal categorization
    } else if (task.dayOfWeek != null) {
      // Check if task has overdue date first (matches Android logic)
      if (task.dueDate != null && task.dueDate < todayMillis) {
        // Past due date, assign to Immediate (matches Android logic)
        category = immediateOption;
      } else {
        // Always map dayOfWeek to current language first
        const mappedDayOfWeek = this.mapDayOfWeekToCurrentLanguage(task.dayOfWeek, daysOfWeek);
        
        if (mappedDayOfWeek === waitingCategory) {
          // Task has "Waiting" as dayOfWeek, check due date or assign to Waiting (matches Android logic)
          if (task.dueDate != null) {
            if (task.dueDate < todayMillis) {
              category = immediateOption;
            } else if (task.dueDate >= todayMillis && task.dueDate < nextWeekMillis) {
              // Due date within next week - categorize by the day of the due date
              const dueDate = new Date(task.dueDate);
              const dayOfWeek = dueDate.getDay(); // 0=Sunday, ..., 6=Saturday
              const dayIndex = dayOfWeek + 3; // 0->3, 1->4, ..., 6->9 (matches Android mapping)
              category = daysOfWeek[dayIndex];
            } else {
              // Future due date beyond next week - assign to Waiting
              category = waitingCategory;
            }
          } else {
            category = waitingCategory;
          }
        } else if (mappedDayOfWeek === immediateOption) {
          category = immediateOption;
        } else if (mappedDayOfWeek === soonOption) {
          category = soonOption;
        } else {
          // Task has a specific day of the week
          category = mappedDayOfWeek;
        }
      }
    } else if (task.dueDate != null) {
      // Task has due date but no day of week - categorize based on due date (matches Android logic)
      if (task.dueDate < todayMillis) {
        category = immediateOption;
      } else if (task.dueDate >= todayMillis && task.dueDate < nextWeekMillis) {
        // Due date within next week - categorize by the day of the due date
        const dueDate = new Date(task.dueDate);
        const dayOfWeek = dueDate.getDay(); // 0=Sunday, ..., 6=Saturday
        const dayIndex = dayOfWeek + 3; // 0->3, 1->4, ..., 6->9 (matches Android mapping)
        category = daysOfWeek[dayIndex];
      } else {
        // Future due date beyond next week - assign to Waiting
        category = waitingCategory;
      }
    } else if (task.dayOfWeek === null || task.dayOfWeek === 'Waiting') {
      // No due date, no specific day - assign to Waiting
      category = waitingCategory;
    } else {
      // Fallback - assign to Waiting
      category = waitingCategory;
    }


    // Add task to appropriate category
    if (category === immediateOption) {
      immediateTasks.push(task);
    } else if (category === soonOption) {
      soonTasks.push(task);
    } else if (category === waitingCategory) {
      waitingTasks.push(task);
    } else if (category === completedCategory) {
      completedTasks.push(task);
    } else {
      // Find the day index for this category
      const dayIndex = daysOfWeek.indexOf(category);
      if (dayIndex >= 3 && dayIndex <= 9) {
        const dayTasksIndex = dayIndex - 3; // Convert to 0-6 range
        if (dayTasksIndex >= 0 && dayTasksIndex < dayTasks.length) {
          dayTasks[dayTasksIndex].push(task);
        }
      } else {
        console.warn('Unknown day category:', category);
        waitingTasks.push(task); // Fallback
      }
    }
  }

  // Main categorization method
  categorizeTasks(tasks) {

    // Get current date and next week's date for categorization
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayMillis = today.getTime();
    
    const nextWeek = new Date(today);
    nextWeek.setDate(today.getDate() + 7);
    const nextWeekMillis = nextWeek.getTime();

    // Get current day and create day indices array
    const todayDayOfWeek = today.getDay(); // 0=Sunday, ..., 6=Saturday
    const todayIndex = todayDayOfWeek + 3; // Map to daysOfWeek array indices (3-9)
    
    
    // Create dayIndices array for the current week order
    const dayIndices = [];
    for (let i = 0; i < 7; i++) {
      let dayIndex = todayIndex + i;
      // Handle wrapping around from Saturday (9) to Sunday (3)
      if (dayIndex > 9) {
        dayIndex = 3 + (dayIndex - 10);
      }
      dayIndices.push(dayIndex);
    }
    

    // Create category order
    const categoryOrder = [];
    dayIndices.forEach(dayIndex => {
      categoryOrder.push(this.daysOfWeek[dayIndex]);
    });
    categoryOrder.push(this.soonOption);
    categoryOrder.push(this.waitingCategory);
    categoryOrder.push(this.completedCategory);


    // Initialize category lists
    const immediateTasks = [];
    const soonTasks = [];
    const waitingTasks = [];
    const completedTasks = [];
    const dayTasks = [[], [], [], [], [], [], []]; // 7 days

    // Process each task
    tasks.forEach(task => {

      // For all recurring tasks, always process them regardless of completion status
      const isRecurring = task.isRecurring;

      // Always route non-recurring completed tasks to Completed category
      if (!isRecurring && task.isCompleted) {
        completedTasks.push(task);
        return;
      }

      // Check if this is a daily recurring task
      const isDailyRecurring = isRecurring && 
        (task.recurrenceType === 'Daily' || task.recurrenceType === 'יומי');

      if (isDailyRecurring) {
        // Add one copy to each day category
        for (let i = 0; i < 7; i++) {
          const taskCopy = {
            ...task,
            dayOfWeek: this.daysOfWeek[dayIndices[i]]
          };
          
          // Only show as completed if this is today AND the task is completed
          const currentDayOfWeek = today.getDay();
          const dayOfWeekForThisCopy = dayIndices[i] - 3; // Convert back to 0-6 range
          
          if (dayOfWeekForThisCopy === currentDayOfWeek && task.isCompleted) {
            taskCopy.isCompleted = true;
          } else {
            taskCopy.isCompleted = false;
          }
          
          // Add directly to the specific day category
          dayTasks[i].push(taskCopy);
        }
      } else if (isRecurring) {
        // For weekly, bi-weekly, monthly, yearly recurring tasks
        const shouldAppearThisWeek = this.shouldRecurringTaskAppearThisWeek(task, todayMillis);
        
        if (shouldAppearThisWeek) {
          // Create a copy for the current week with appropriate completion status
          const taskCopy = { ...task };
          taskCopy.isCompleted = task.isCompleted;
          
          // Process the task copy to add it to the appropriate category
          this.processSingleTask(taskCopy, immediateTasks, soonTasks, waitingTasks, 
                               completedTasks, dayTasks, this.daysOfWeek, dayIndices, 
                               this.immediateOption, this.soonOption, this.waitingCategory, 
                               this.completedCategory, this.noneOption, todayMillis, nextWeekMillis);
          
        }
      } else {
        // Process regular task
        this.processSingleTask(task, immediateTasks, soonTasks, waitingTasks, 
                             completedTasks, dayTasks, this.daysOfWeek, dayIndices, 
                             this.immediateOption, this.soonOption, this.waitingCategory, 
                             this.completedCategory, this.noneOption, todayMillis, nextWeekMillis);
      }
    });

    // Sort tasks within each category
    const sortedCategories = this.sortTasksInCategories({
      immediateTasks,
      soonTasks,
      waitingTasks,
      completedTasks,
      dayTasks
    });

    // Build final categorized tasks (matches Android logic exactly)
    const categorizedTasks = [];
    
    categoryOrder.forEach((category, categoryIndex) => {
      let categoryTasks = [];
      
      if (category === this.soonOption) {
        categoryTasks = sortedCategories.soonTasks;
      } else if (category === this.waitingCategory) {
        categoryTasks = sortedCategories.waitingTasks;
      } else if (category === this.completedCategory) {
        categoryTasks = sortedCategories.completedTasks;
      } else {
        // Find the day index for this category in the dayIndices array
        for (let i = 0; i < dayIndices.length; i++) {
          if (category === this.daysOfWeek[dayIndices[i]]) {
            categoryTasks = [...sortedCategories.dayTasks[i]];
            
            // If this is the current day (first in the list), add immediate tasks (matches Android logic)
            if (i === 0) {
              categoryTasks = [...categoryTasks, ...sortedCategories.immediateTasks];
            }
            break;
          }
        }
      }
      
      if (categoryTasks.length > 0) {
        categorizedTasks.push({
          type: 'header',
          name: category,
          taskCount: categoryTasks.length
        });
        
        categoryTasks.forEach(task => {
          categorizedTasks.push({
            type: 'task',
            ...task
          });
        });
      }
    });

    return categorizedTasks;
  }

  // Sort tasks within each category using Android logic
  sortTasksInCategories(categories) {
    const taskComparator = (t1, t2) => {
      const t1IsManual = t1.manualPosition != null;
      const t2IsManual = t2.manualPosition != null;
      
      // Case 1: Both manual - sort by manualPosition
      if (t1IsManual && t2IsManual) {
        return t1.manualPosition - t2.manualPosition;
      }
      
      // Case 2: Both automatic - sort by time, then priority
      if (!t1IsManual && !t2IsManual) {
        // Both have time - sort by time
        if (t1.dueTime != null && t2.dueTime != null) {
          const t1TimeOfDay = this.convertToTimeOfDay(t1.dueTime);
          const t2TimeOfDay = this.convertToTimeOfDay(t2.dueTime);
          const timeResult = t1TimeOfDay - t2TimeOfDay;
          if (timeResult !== 0) return timeResult;
          return t1.priority - t2.priority; // tie-breaker
        }
        
        // Mixed time/no-time among auto tasks
        if (t1.dueTime != null && t2.dueTime == null) return -1; // timed first
        if (t1.dueTime == null && t2.dueTime != null) return 1;  // timed first
        
        // Both have no time - sort by priority
        return t1.priority - t2.priority;
      }
      
      // Case 3: Mixed manual/auto - find correct insertion point
      const autoTask = t1IsManual ? t2 : t1;
      const manualTask = t1IsManual ? t1 : t2;
      const autoIsT1 = !t1IsManual;
      
      // Auto task with no time - always goes after manual tasks
      if (autoTask.dueTime == null) {
        return autoIsT1 ? 1 : -1;
      }
      
      // Auto task has time - convert to hour for comparison with manual position
      const autoTimeOfDay = this.convertToTimeOfDay(autoTask.dueTime);
      const autoHour = Math.floor(autoTimeOfDay / 3600000); // convert to hours (0-23)
      
      // Map manual positions to representative hours:
      let manualHour;
      switch (manualTask.manualPosition) {
        case 0: manualHour = 3; break;  // early morning
        case 1: manualHour = 9; break;  // morning
        case 2: manualHour = 15; break; // afternoon
        default: manualHour = 21; break; // evening
      }
      
      const result = autoHour - manualHour;
      return autoIsT1 ? result : -result;
    };

    // Custom comparator for completed tasks - sort by completion time (descending)
    const completedTaskComparator = (t1, t2) => {
      if (t1.completionDate == null && t2.completionDate == null) return 0;
      if (t1.completionDate == null) return 1; // null completion dates go last
      if (t2.completionDate == null) return -1;
      return t2.completionDate - t1.completionDate; // Descending order (most recent first)
    };

    // Sort each category
    categories.immediateTasks.sort(taskComparator);
    categories.soonTasks.sort(taskComparator);
    categories.waitingTasks.sort(taskComparator);
    categories.completedTasks.sort(completedTaskComparator);
    categories.dayTasks.forEach(dayTaskList => {
      dayTaskList.sort(taskComparator);
    });

    return categories;
  }
}
