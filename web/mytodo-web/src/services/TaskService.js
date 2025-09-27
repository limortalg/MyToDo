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

  // Delete a task
  async deleteTask(taskId) {
    try {
      const taskRef = doc(this.db, this.collectionName, taskId);
      await deleteDoc(taskRef);
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
        console.log('🔥 TASK SERVICE DEBUG: Raw Firestore data for task', doc.id, ':', rawData);
        
        const task = Task.fromFirestore(doc.id, rawData);
        console.log('🔥 TASK SERVICE DEBUG: Task after fromFirestore conversion:', {
          taskId: doc.id,
          taskDescription: task.description,
          sourceApp: task.sourceApp,
          sourceTaskId: task.sourceTaskId,
          isCompleted: task.isCompleted,
          rawData: rawData,
          fullTaskObject: task
        });
        tasks.push(task);
      });
      return tasks;
    } catch (error) {
      console.error('❌ TaskService: Error getting tasks:', error);
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
          tasks.push(task);
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
      console.log('🔧 TASK SERVICE: Starting fix for empty FamilySync task descriptions...');
      
      const tasks = await this.getTasks();
      const emptyFamilySyncTasks = tasks.filter(task => 
        task.isFromFamilySync() && 
        (!task.description || task.description.trim() === '')
      );
      
      console.log('🔧 TASK SERVICE: Found', emptyFamilySyncTasks.length, 'empty FamilySync tasks to fix');
      
      for (const task of emptyFamilySyncTasks) {
        try {
          console.log('🔧 TASK SERVICE: Fixing task', task.id, 'with sourceTaskId', task.sourceTaskId);
          
          // Import Firestore functions
          const { doc, getDoc } = await import('firebase/firestore');
          
          // Get the original FamilySync task
          const familySyncTaskRef = doc(this.db, 'tasks', task.sourceTaskId);
          const familySyncTaskSnap = await getDoc(familySyncTaskRef);
          
          if (familySyncTaskSnap.exists()) {
            const familySyncTaskData = familySyncTaskSnap.data();
            const originalContent = familySyncTaskData.content || familySyncTaskData.title || familySyncTaskData.description || '';
            
            if (originalContent && originalContent.trim() !== '') {
              console.log('🔧 TASK SERVICE: Found original content:', originalContent);
              
              // Update the MyToDo task with the original content
              const taskRef = doc(this.db, this.collectionName, task.id);
              await updateDoc(taskRef, {
                description: originalContent,
                updatedAt: Date.now()
              });
              
              console.log('🔧 TASK SERVICE: Successfully updated task', task.id, 'with content:', originalContent);
            } else {
              console.log('🔧 TASK SERVICE: Original FamilySync task also has no content for task', task.id);
            }
          } else {
            console.log('🔧 TASK SERVICE: Original FamilySync task not found for task', task.id);
          }
        } catch (error) {
          console.error('🔧 TASK SERVICE: Error fixing task', task.id, ':', error);
        }
      }
      
      console.log('🔧 TASK SERVICE: Finished fixing empty FamilySync task descriptions');
    } catch (error) {
      console.error('🔧 TASK SERVICE: Error in fixEmptyFamilySyncTasks:', error);
    }
  }

  // Toggle task completion
  async toggleTaskCompletion(taskId, isCompleted) {
    try {
      console.log('🔥 TASK SERVICE DEBUG: Starting toggleTaskCompletion for taskId:', taskId, 'isCompleted:', isCompleted);
      
      // First, get the current task data to preserve it
      const currentTasks = await this.getTasks();
      console.log('🔥 TASK SERVICE DEBUG: Loaded', currentTasks.length, 'tasks from database');
      
      const currentTask = currentTasks.find(task => task.id === taskId);
      console.log('🔥 TASK SERVICE DEBUG: Found current task:', {
        taskId,
        found: !!currentTask,
        description: currentTask?.description,
        sourceApp: currentTask?.sourceApp,
        sourceTaskId: currentTask?.sourceTaskId,
        isCompleted: currentTask?.isCompleted,
        newIsCompleted: isCompleted,
        fullTaskObject: currentTask
      });
      
      if (!currentTask) {
        console.error('🔥 TASK SERVICE ERROR: Could not find task with ID:', taskId);
        throw new Error(`Task with ID ${taskId} not found`);
      }
      
      const taskRef = doc(this.db, this.collectionName, taskId);
      const updates = {
        isCompleted: isCompleted,
        completionDate: isCompleted ? Date.now() : null,
        updatedAt: Date.now()
      };
      
      // Preserve description and FamilySync fields - ALWAYS preserve description
      console.log('🔥 TASK SERVICE DEBUG: Preserving task data...');
      if (currentTask.description !== undefined && currentTask.description !== null) {
        updates.description = currentTask.description;
        console.log('🔥 TASK SERVICE DEBUG: Preserved description:', currentTask.description);
      } else {
        console.warn('🔥 TASK SERVICE WARNING: Task has no description to preserve!');
      }
      
      if (currentTask.sourceApp) {
        updates.sourceApp = currentTask.sourceApp;
        console.log('🔥 TASK SERVICE DEBUG: Preserved sourceApp:', currentTask.sourceApp);
      }
      if (currentTask.sourceTaskId) {
        updates.sourceTaskId = currentTask.sourceTaskId;
        console.log('🔥 TASK SERVICE DEBUG: Preserved sourceTaskId:', currentTask.sourceTaskId);
      }
      if (currentTask.sourceGroupId) {
        updates.sourceGroupId = currentTask.sourceGroupId;
        console.log('🔥 TASK SERVICE DEBUG: Preserved sourceGroupId:', currentTask.sourceGroupId);
      }
      if (currentTask.familySyncAssigneeId) {
        updates.familySyncAssigneeId = currentTask.familySyncAssigneeId;
        console.log('🔥 TASK SERVICE DEBUG: Preserved familySyncAssigneeId:', currentTask.familySyncAssigneeId);
      }
      if (currentTask.familySyncCreatorId) {
        updates.familySyncCreatorId = currentTask.familySyncCreatorId;
        console.log('🔥 TASK SERVICE DEBUG: Preserved familySyncCreatorId:', currentTask.familySyncCreatorId);
      }
      
      console.log('🔥 TASK SERVICE DEBUG: Final updates being applied:', updates);
      
      await updateDoc(taskRef, updates);
      
      console.log('🔥 TASK SERVICE DEBUG: Successfully updated task in Firestore');
      
      // Verify the update by re-fetching the task
      console.log('🔥 TASK SERVICE DEBUG: Verifying update by re-fetching task...');
      const verifyTasks = await this.getTasks();
      const verifyTask = verifyTasks.find(task => task.id === taskId);
      console.log('🔥 TASK SERVICE DEBUG: Verified task after update:', {
        taskId,
        description: verifyTask?.description,
        sourceApp: verifyTask?.sourceApp,
        sourceTaskId: verifyTask?.sourceTaskId,
        isCompleted: verifyTask?.isCompleted,
        fullTaskObject: verifyTask
      });
      
    } catch (error) {
      console.error('🔥 TASK SERVICE ERROR: Error toggling task completion:', error);
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
        tasks.push(task);
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
        if (task.description.toLowerCase().includes(searchTerm.toLowerCase())) {
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
