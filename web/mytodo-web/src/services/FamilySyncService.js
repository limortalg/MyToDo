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
   */
  async toggleTaskCompletionWithSync(taskId, isCompleted) {
    try {
      // Update MyToDo task
      await this.taskService.toggleTaskCompletion(taskId, isCompleted);
      
      // Sync with FamilySync if this is an exported task
      await this.syncCompletionToFamilySync(taskId, isCompleted);
      
      return { success: true };
    } catch (error) {
      console.error('Error toggling task completion with sync:', error);
      return { 
        success: false, 
        error: error.message 
      };
    }
  }
}

export const familySyncService = new FamilySyncService(db);
