import { 
  collection, 
  doc, 
  addDoc, 
  updateDoc, 
  deleteDoc, 
  getDocs, 
  query, 
  where, 
  orderBy, 
  onSnapshot 
} from 'firebase/firestore';
import { auth } from '../firebase';
import { Task } from '../models/Task';

export class TaskService {
  constructor(db) {
    this.db = db;
    this.collectionName = 'mytodo_tasks';
  }

  // Get current user ID
  getCurrentUserId() {
    const user = auth.currentUser;
    if (!user) {
      throw new Error('User not authenticated');
    }
    return user.uid;
  }

  // Create a new task
  async createTask(taskData) {
    try {
      const userId = this.getCurrentUserId();
      const task = new Task(taskData);
      task.updatedAt = Date.now();
      
      const taskRef = await addDoc(collection(this.db, this.collectionName), {
        ...task.toFirestore(),
        userId: userId
      });
      
      return { id: taskRef.id, ...task };
    } catch (error) {
      console.error('Error creating task:', error);
      throw error;
    }
  }

  // Update an existing task
  async updateTask(taskId, taskData) {
    try {
      const task = new Task(taskData);
      task.updatedAt = Date.now();
      
      const taskRef = doc(this.db, this.collectionName, taskId);
      await updateDoc(taskRef, task.toFirestore());
      
      return { id: taskId, ...task };
    } catch (error) {
      console.error('Error updating task:', error);
      throw error;
    }
  }

  // Delete a task (soft delete to prevent race conditions)
  async deleteTask(taskId) {
    try {
      const taskRef = doc(this.db, this.collectionName, taskId);
      await updateDoc(taskRef, {
        deletedAt: Date.now(),
        updatedAt: Date.now()
      });
    } catch (error) {
      console.error('Error deleting task:', error);
      throw error;
    }
  }

  // Get all tasks for the current user
  async getTasks() {
    try {
      const userId = this.getCurrentUserId();
      
      const q = query(
        collection(this.db, this.collectionName),
        where('userId', '==', userId),
        orderBy('updatedAt', 'desc')
      );
      
      const querySnapshot = await getDocs(q);
      const tasks = [];
      
      querySnapshot.forEach((doc) => {
        const rawData = doc.data();
        
        const task = Task.fromFirestore(doc.id, rawData);
        
        // Only include non-deleted tasks
        if (!task.isDeleted()) {
          tasks.push(task);
        } else {
        }
      });
      return tasks;
    } catch (error) {
      console.error('Error getting tasks:', error);
      throw error;
    }
  }

  // Subscribe to real-time task updates
  subscribeToTasks(callback) {
    try {
      const userId = this.getCurrentUserId();
      const q = query(
        collection(this.db, this.collectionName),
        where('userId', '==', userId),
        orderBy('updatedAt', 'desc')
      );
      
      return onSnapshot(q, (querySnapshot) => {
        const tasks = [];
        querySnapshot.forEach((doc) => {
          const task = Task.fromFirestore(doc.id, doc.data());
          // Only include non-deleted tasks
          if (!task.isDeleted()) {
            tasks.push(task);
          }
        });
        callback(tasks);
      });
    } catch (error) {
      console.error('Error subscribing to tasks:', error);
      throw error;
    }
  }

  // Fix empty descriptions for FamilySync tasks
  async fixEmptyFamilySyncTasks() {
    try {
      
      const tasks = await this.getTasks();
      const emptyFamilySyncTasks = tasks.filter(task => 
        task.isFromFamilySync() && 
        (!task.description || task.description.trim() === '')
      );
      
      
      for (const task of emptyFamilySyncTasks) {
        try {
          
          // Import Firestore functions
          const { doc, getDoc } = await import('firebase/firestore');
          
          // Get the original FamilySync task
          const familySyncTaskRef = doc(this.db, 'tasks', task.sourceTaskId);
          const familySyncTaskSnap = await getDoc(familySyncTaskRef);
          
          if (familySyncTaskSnap.exists()) {
            const familySyncTaskData = familySyncTaskSnap.data();
            const originalContent = familySyncTaskData.content || familySyncTaskData.title || familySyncTaskData.description || '';
            
            if (originalContent && originalContent.trim() !== '') {
              
              // Update the MyToDo task with the original content
              const taskRef = doc(this.db, this.collectionName, task.id);
              await updateDoc(taskRef, {
                description: originalContent,
                updatedAt: Date.now()
              });
              
            } else {
            }
          } else {
          }
        } catch (error) {
          console.error('Error fixing task', task.id, ':', error);
        }
      }
      
    } catch (error) {
      console.error('Error in fixEmptyFamilySyncTasks:', error);
    }
  }

  // Toggle task completion
  async toggleTaskCompletion(taskId, isCompleted) {
    try {
      
      // First, get the current task data to preserve it
      const currentTasks = await this.getTasks();
      
      const currentTask = currentTasks.find(task => task.id === taskId);
      
      if (!currentTask) {
        console.error('Could not find task with ID:', taskId);
        throw new Error(`Task with ID ${taskId} not found`);
      }
      
      const taskRef = doc(this.db, this.collectionName, taskId);
      const updates = {
        isCompleted: isCompleted,
        completionDate: isCompleted ? Date.now() : null,
        updatedAt: Date.now()
      };
      
      // Preserve description and FamilySync fields - ALWAYS preserve description
      if (currentTask.description !== undefined && currentTask.description !== null) {
        updates.description = currentTask.description;
      } else {
        console.warn('Task has no description to preserve!');
      }
      
      if (currentTask.sourceApp) {
        updates.sourceApp = currentTask.sourceApp;
      }
      if (currentTask.sourceTaskId) {
        updates.sourceTaskId = currentTask.sourceTaskId;
      }
      if (currentTask.sourceGroupId) {
        updates.sourceGroupId = currentTask.sourceGroupId;
      }
      if (currentTask.familySyncAssigneeId) {
        updates.familySyncAssigneeId = currentTask.familySyncAssigneeId;
      }
      if (currentTask.familySyncCreatorId) {
        updates.familySyncCreatorId = currentTask.familySyncCreatorId;
      }
      
      
      await updateDoc(taskRef, updates);
      
      
      // Verify the update by re-fetching the task
      const verifyTasks = await this.getTasks();
      const verifyTask = verifyTasks.find(task => task.id === taskId);
      
    } catch (error) {
      console.error('Error toggling task completion:', error);
      throw error;
    }
  }

  // Update task priority
  async updateTaskPriority(taskId, priority) {
    try {
      const taskRef = doc(this.db, this.collectionName, taskId);
      await updateDoc(taskRef, {
        priority: priority,
        updatedAt: Date.now()
      });
    } catch (error) {
      console.error('Error updating task priority:', error);
      throw error;
    }
  }

  // Get tasks by day of week
  async getTasksByDay(dayOfWeek) {
    try {
      const userId = this.getCurrentUserId();
      const q = query(
        collection(this.db, this.collectionName),
        where('userId', '==', userId),
        where('dayOfWeek', '==', dayOfWeek),
        orderBy('updatedAt', 'desc')
      );
      
      const querySnapshot = await getDocs(q);
      const tasks = [];
      
      querySnapshot.forEach((doc) => {
        const task = Task.fromFirestore(doc.id, doc.data());
        // Only include non-deleted tasks
        if (!task.isDeleted()) {
          tasks.push(task);
        }
      });
      
      return tasks;
    } catch (error) {
      console.error('Error getting tasks by day:', error);
      throw error;
    }
  }

  // Search tasks
  async searchTasks(searchTerm) {
    try {
      const userId = this.getCurrentUserId();
      const q = query(
        collection(this.db, this.collectionName),
        where('userId', '==', userId),
        orderBy('updatedAt', 'desc')
      );
      
      const querySnapshot = await getDocs(q);
      const tasks = [];
      
      querySnapshot.forEach((doc) => {
        const task = Task.fromFirestore(doc.id, doc.data());
        // Only include non-deleted tasks that match search term
        if (!task.isDeleted() && task.description.toLowerCase().includes(searchTerm.toLowerCase())) {
          tasks.push(task);
        }
      });
      
      return tasks;
    } catch (error) {
      console.error('Error searching tasks:', error);
      throw error;
    }
  }
}
