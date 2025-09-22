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
        const task = Task.fromFirestore(doc.id, doc.data());
        tasks.push(task);
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
          tasks.push(task);
        });
        callback(tasks);
      });
    } catch (error) {
      console.error('Error subscribing to tasks:', error);
      throw error;
    }
  }

  // Toggle task completion
  async toggleTaskCompletion(taskId, isCompleted) {
    try {
      const taskRef = doc(this.db, this.collectionName, taskId);
      const updates = {
        isCompleted: isCompleted,
        completionDate: isCompleted ? Date.now() : null,
        updatedAt: Date.now()
      };
      
      await updateDoc(taskRef, updates);
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
