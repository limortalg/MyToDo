// Task model that matches the Android app's Task class
import { TaskConstants } from '../constants/TaskConstants';

export class Task {
  constructor(data = {}) {
    this.id = data.id || null;
    this.description = data.description || '';
    this.dueDate = data.dueDate || null; // Timestamp
    this.dueTime = data.dueTime || null; // Milliseconds since midnight
    this.dayOfWeek = data.dayOfWeek || TaskConstants.DAY_NONE;
    this.isRecurring = data.isRecurring || false;
    this.recurrenceType = data.recurrenceType || null;
    this.isCompleted = data.isCompleted || false;
    this.priority = data.priority || 0;
    this.completionDate = data.completionDate || null;
    this.reminderOffset = data.reminderOffset !== undefined ? data.reminderOffset : null; // Minutes before due time
    this.reminderDays = data.reminderDays || null; // For daily recurring tasks
    this.manualPosition = data.manualPosition || null;
    this.createdAt = data.createdAt || Date.now();
    this.updatedAt = data.updatedAt || Date.now();
    this.deletedAt = data.deletedAt || null;
    
    // FamilySync integration fields
    this.sourceApp = data.sourceApp || null;
    this.sourceTaskId = data.sourceTaskId || null;
    this.sourceGroupId = data.sourceGroupId || null;
    this.familySyncAssigneeId = data.familySyncAssigneeId || null;
    this.familySyncCreatorId = data.familySyncCreatorId || null;
  }

  // Convert to Firestore format
  toFirestore() {
    const firestoreData = {
      description: this.description,
      dueDate: this.dueDate,
      dueTime: this.dueTime,
      dayOfWeek: this.dayOfWeek,
      isRecurring: this.isRecurring,
      recurrenceType: this.recurrenceType,
      isCompleted: this.isCompleted,
      priority: this.priority,
      completionDate: this.completionDate,
      reminderOffset: this.reminderOffset,
      reminderDays: this.reminderDays,
      manualPosition: this.manualPosition,
      createdAt: this.createdAt,
      updatedAt: this.updatedAt,
      deletedAt: this.deletedAt
    };

    // Include FamilySync fields if they exist
    if (this.sourceApp) firestoreData.sourceApp = this.sourceApp;
    if (this.sourceTaskId) firestoreData.sourceTaskId = this.sourceTaskId;
    if (this.sourceGroupId) firestoreData.sourceGroupId = this.sourceGroupId;
    if (this.familySyncAssigneeId) firestoreData.familySyncAssigneeId = this.familySyncAssigneeId;
    if (this.familySyncCreatorId) firestoreData.familySyncCreatorId = this.familySyncCreatorId;

    return firestoreData;
  }

  // Create from Firestore data
  static fromFirestore(id, data) {
    const task = new Task(data);
    task.id = id;
    return task;
  }

  // Format due date for display
  getFormattedDueDate() {
    if (!this.dueDate) return null;
    const date = new Date(this.dueDate);
    return date.toLocaleDateString();
  }

  // Format due time for display
  getFormattedDueTime() {
    if (!this.dueTime) return null;
    const hours = Math.floor(this.dueTime / (1000 * 60 * 60));
    const minutes = Math.floor((this.dueTime % (1000 * 60 * 60)) / (1000 * 60));
    return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`;
  }

  // Get priority color
  getPriorityColor() {
    switch (this.priority) {
      case 3: return '#f44336'; // High - Red
      case 2: return '#ff9800'; // Medium - Orange
      case 1: return '#4caf50'; // Low - Green
      default: return '#9e9e9e'; // Default - Grey
    }
  }

  // Get priority text
  getPriorityText() {
    switch (this.priority) {
      case 3: return 'High';
      case 2: return 'Medium';
      case 1: return 'Low';
      default: return 'Normal';
    }
  }

  // Check if task is overdue
  isOverdue() {
    if (!this.dueDate || this.isCompleted) return false;
    const now = new Date();
    const dueDate = new Date(this.dueDate);
    return dueDate < now;
  }

  // Check if task is due today
  isDueToday() {
    if (!this.dueDate) return false;
    const today = new Date();
    const dueDate = new Date(this.dueDate);
    return today.toDateString() === dueDate.toDateString();
  }

  // Check if task is imported from FamilySync
  isFromFamilySync() {
    return this.sourceApp === 'familysync' && this.sourceTaskId;
  }

  // Check if task is deleted
  isDeleted() {
    return this.deletedAt !== null && this.deletedAt !== undefined;
  }
}
