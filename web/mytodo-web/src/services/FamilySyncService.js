import { 
  collection, 
  doc, 
  updateDoc, 
  query, 
  where, 
  getDocs,
  onSnapshot,
  addDoc
} from 'firebase/firestore';
import { auth, db } from '../firebase';
import { TaskService } from './TaskService';

export class FamilySyncService {
  constructor(db) {
    this.db = db;
    this.familySyncTasksCollection = 'tasks';
    this.syncLogCollection = 'task_sync_logs';
    this.taskService = new TaskService(db);
  }

  // Get current user ID
  getCurrentUserId() {
    const user = auth.currentUser;
    if (!user) {
      throw new Error('User not authenticated');
    }
    return user.uid;
  }

  /**
   * Update FamilySync task completion status when MyToDo task is completed
   */
  async syncCompletionToFamilySync(mytodoTaskId, isCompleted) {
    try {
      // Get the MyToDo task to find the source FamilySync task ID
      const mytodoTasks = await this.taskService.getTasks();
      const mytodoTask = mytodoTasks.find(task => task.id === mytodoTaskId);
      
      if (!mytodoTask || !mytodoTask.sourceTaskId) {
        console.log('No FamilySync source task found for MyToDo task:', mytodoTaskId);
        return { success: true }; // Not an exported task, no sync needed
      }

      // Update the FamilySync task
      const familySyncTaskRef = doc(this.db, this.familySyncTasksCollection, mytodoTask.sourceTaskId);
      await updateDoc(familySyncTaskRef, {
        status: isCompleted ? 'DONE' : 'PENDING',
        completedAt: isCompleted ? new Date().toISOString() : null,
        updatedAt: new Date().toISOString()
      });

      // Log the sync action
      await this.logSyncAction({
        action: 'completion_sync_from_mytodo',
        familySyncTaskId: mytodoTask.sourceTaskId,
        mytodoTaskId: mytodoTaskId,
        userId: this.getCurrentUserId(),
        timestamp: Date.now(),
        completionStatus: isCompleted
      });

      console.log('Successfully synced completion status to FamilySync');
      return { success: true };
    } catch (error) {
      console.error('Error syncing completion to FamilySync:', error);
      return { 
        success: false, 
        error: error.message 
      };
    }
  }

  /**
   * Listen for FamilySync task completion changes and update MyToDo accordingly
   */
  subscribeToFamilySyncChanges(mytodoTaskId, callback) {
    try {
      // Get the MyToDo task to find the source FamilySync task ID
      this.taskService.getTasks().then(mytodoTasks => {
        const mytodoTask = mytodoTasks.find(task => task.id === mytodoTaskId);
        
        if (!mytodoTask || !mytodoTask.sourceTaskId) {
          console.log('No FamilySync source task found for MyToDo task:', mytodoTaskId);
          return;
        }

        // Listen to FamilySync task changes
        const familySyncTaskRef = doc(this.db, this.familySyncTasksCollection, mytodoTask.sourceTaskId);
        
        return onSnapshot(familySyncTaskRef, (doc) => {
          if (doc.exists()) {
            const familySyncTask = doc.data();
            const isCompleted = familySyncTask.status === 'DONE';
            
            // Update MyToDo task if status changed
            if (mytodoTask.isCompleted !== isCompleted) {
              // Preserve all existing task data when updating
              const updatedTaskData = {
                ...mytodoTask,
                isCompleted: isCompleted,
                completionDate: isCompleted ? Date.now() : null,
                updatedAt: Date.now()
              };
              
              // Remove the id field as updateTask will set it
              delete updatedTaskData.id;
              
              console.log('🔥 FAMILYSYNC SERVICE DEBUG: Updating MyToDo task with preserved data:', {
                taskId: mytodoTaskId,
                originalDescription: mytodoTask.description,
                preservedDescription: updatedTaskData.description,
                isCompleted: isCompleted,
                fullTaskData: updatedTaskData
              });
              
              this.taskService.updateTask(mytodoTaskId, updatedTaskData);
              
              // Log the sync action
              this.logSyncAction({
                action: 'completion_sync_from_familysync',
                familySyncTaskId: mytodoTask.sourceTaskId,
                mytodoTaskId: mytodoTaskId,
                userId: this.getCurrentUserId(),
                timestamp: Date.now(),
                completionStatus: isCompleted
              });
              
              if (callback) {
                callback({
                  type: 'completion_changed',
                  isCompleted: isCompleted,
                  familySyncTask: familySyncTask
                });
              }
            }
          }
        });
      });
    } catch (error) {
      console.error('Error subscribing to FamilySync changes:', error);
    }
  }

  /**
   * Get all MyToDo tasks that are exported from FamilySync
   */
  async getExportedTasks() {
    try {
      const userId = this.getCurrentUserId();
      const q = query(
        collection(this.db, 'mytodo_tasks'),
        where('userId', '==', userId),
        where('sourceApp', '==', 'familysync')
      );
      
      const querySnapshot = await getDocs(q);
      return querySnapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data()
      }));
    } catch (error) {
      console.error('Error getting exported tasks:', error);
      return [];
    }
  }

  /**
   * Check if a MyToDo task is exported from FamilySync
   */
  isExportedTask(task) {
    const isExported = task.isFromFamilySync ? task.isFromFamilySync() : (task.sourceApp === 'familysync' && task.sourceTaskId);
    return isExported;
  }

  /**
   * Get FamilySync task info for an exported MyToDo task
   */
  async getFamilySyncTaskInfo(mytodoTask) {
    if (!this.isExportedTask(mytodoTask)) {
      return null;
    }

    try {
      const familySyncTaskRef = doc(this.db, this.familySyncTasksCollection, mytodoTask.sourceTaskId);
      const familySyncDoc = await getDocs(query(collection(this.db, this.familySyncTasksCollection), where('__name__', '==', mytodoTask.sourceTaskId)));
      
      if (familySyncDoc.empty) {
        return null;
      }

      return familySyncDoc.docs[0].data();
    } catch (error) {
      console.error('Error getting FamilySync task info:', error);
      return null;
    }
  }

  /**
   * Log sync actions for debugging and audit
   */
  async logSyncAction(logData) {
    try {
      await addDoc(collection(this.db, this.syncLogCollection), logData);
    } catch (error) {
      console.error('Error logging sync action:', error);
    }
  }

  /**
   * Enhanced task completion toggle that syncs with FamilySync
   * Includes monthly task completion logic to align with Android app
   */
  async toggleTaskCompletionWithSync(taskId, isCompleted) {
    try {
      // Get current task data to check if it's a monthly recurring task
      const currentTasks = await this.taskService.getTasks();
      const currentTask = currentTasks.find(task => task.id === taskId);
      
      if (!currentTask) {
        throw new Error(`Task with ID ${taskId} not found`);
      }
      
      // Check if this is a recurring task being completed
      const isMonthlyRecurring = currentTask.isRecurring && currentTask.recurrenceType === 'Monthly';
      const isWeeklyRecurring = currentTask.isRecurring && currentTask.recurrenceType === 'Weekly';
      const isBiweeklyRecurring = currentTask.isRecurring && currentTask.recurrenceType === 'Biweekly';
      const isYearlyRecurring = currentTask.isRecurring && currentTask.recurrenceType === 'Yearly';
      
      if (isCompleted && (isMonthlyRecurring || isWeeklyRecurring || isBiweeklyRecurring || isYearlyRecurring)) {
        console.log('🔥 FAMILY SYNC SERVICE: Handling recurring task completion for:', currentTask.description, 'Type:', currentTask.recurrenceType);
        
        let newDueDate;
        if (isMonthlyRecurring) {
          // For monthly tasks, advance the due date to next month
          newDueDate = this.getNextMonthDate(currentTask.dueDate);
        } else if (isWeeklyRecurring) {
          // For weekly tasks, advance the due date to next week
          newDueDate = this.getNextWeekDate(currentTask.dueDate);
        } else if (isBiweeklyRecurring) {
          // For bi-weekly tasks, advance the due date to 2 weeks later
          newDueDate = this.getNextBiweekDate(currentTask.dueDate);
        } else if (isYearlyRecurring) {
          // For yearly tasks, advance the due date to next year
          newDueDate = this.getNextYearDate(currentTask.dueDate);
        }
        
        // Update task with new due date and reset completion status
        await this.taskService.updateTask(taskId, {
          ...currentTask,
          dueDate: newDueDate,
          dayOfWeek: 'None', // Reset to waiting
          isCompleted: false, // Reset for next occurrence
          completionDate: null, // Clear completion date
          updatedAt: Date.now()
        });
        
        console.log('🔥 FAMILY SYNC SERVICE: Recurring task advanced:', {
          taskDescription: currentTask.description,
          recurrenceType: currentTask.recurrenceType,
          originalDueDate: currentTask.dueDate,
          newDueDate: newDueDate,
          dayOfWeek: 'None'
        });
        
        // Sync the completion with FamilySync (the task is marked as completed in FamilySync)
        await this.syncCompletionToFamilySync(taskId, true);
        
      } else {
        // Regular task completion (non-monthly or uncompleting)
        await this.taskService.toggleTaskCompletion(taskId, isCompleted);
        
        // Sync with FamilySync if this is an exported task
        await this.syncCompletionToFamilySync(taskId, isCompleted);
      }
      
      return { success: true };
    } catch (error) {
      console.error('Error toggling task completion with sync:', error);
      return { 
        success: false, 
        error: error.message 
      };
    }
  }
  
  /**
   * Calculate the next month's date for monthly recurring tasks
   */
  getNextMonthDate(currentDueDate) {
    if (!currentDueDate) {
      // If no due date, set to next month from today
      const nextMonth = new Date();
      nextMonth.setMonth(nextMonth.getMonth() + 1);
      return nextMonth.getTime();
    }
    
    const currentDate = new Date(currentDueDate);
    const nextMonth = new Date(currentDate);
    
    // Add one month
    nextMonth.setMonth(nextMonth.getMonth() + 1);
    
    // Handle edge case where the day doesn't exist in next month (e.g., Jan 31 -> Feb 28/29)
    if (nextMonth.getDate() !== currentDate.getDate()) {
      // If the day changed, it means we went to a month with fewer days
      // Set to the last day of the target month
      nextMonth.setDate(0); // This gives us the last day of the previous month (which is our target month)
    }
    
    return nextMonth.getTime();
  }
  
  /**
   * Calculate the next week's date for weekly recurring tasks
   */
  getNextWeekDate(currentDueDate) {
    if (!currentDueDate) {
      // If no due date, set to next week from today
      const nextWeek = new Date();
      nextWeek.setDate(nextWeek.getDate() + 7);
      return nextWeek.getTime();
    }
    
    const currentDate = new Date(currentDueDate);
    const nextWeek = new Date(currentDate);
    
    // Add one week (7 days)
    nextWeek.setDate(nextWeek.getDate() + 7);
    
    return nextWeek.getTime();
  }
  
  /**
   * Calculate the next bi-week's date for bi-weekly recurring tasks
   */
  getNextBiweekDate(currentDueDate) {
    if (!currentDueDate) {
      // If no due date, set to 2 weeks from today
      const nextBiweek = new Date();
      nextBiweek.setDate(nextBiweek.getDate() + 14);
      return nextBiweek.getTime();
    }
    
    const currentDate = new Date(currentDueDate);
    const nextBiweek = new Date(currentDate);
    
    // Add two weeks (14 days)
    nextBiweek.setDate(nextBiweek.getDate() + 14);
    
    return nextBiweek.getTime();
  }
  
  /**
   * Calculate the next year's date for yearly recurring tasks
   */
  getNextYearDate(currentDueDate) {
    if (!currentDueDate) {
      // If no due date, set to next year from today
      const nextYear = new Date();
      nextYear.setFullYear(nextYear.getFullYear() + 1);
      return nextYear.getTime();
    }
    
    const currentDate = new Date(currentDueDate);
    const nextYear = new Date(currentDate);
    
    // Add one year
    nextYear.setFullYear(nextYear.getFullYear() + 1);
    
    // Handle edge case where the day doesn't exist in next year (e.g., Feb 29 in leap year)
    if (nextYear.getDate() !== currentDate.getDate()) {
      // If the day changed, it means we went to a year where that day doesn't exist
      // Set to the last day of the target month
      nextYear.setDate(0); // This gives us the last day of the previous month (which is our target month)
    }
    
    return nextYear.getTime();
  }
}

export const familySyncService = new FamilySyncService(db);
