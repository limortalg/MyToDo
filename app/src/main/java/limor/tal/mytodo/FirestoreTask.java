package limor.tal.mytodo;

import com.google.firebase.firestore.Exclude;

import java.util.HashMap;
import java.util.Map;

public class FirestoreTask {
    public String documentId; // Firestore document ID
    public String userId; // User who owns this task
    public String description;
    public Long dueDate;
    public Long dueTime; // Milliseconds since midnight
    public String dayOfWeek;
    public boolean isRecurring;
    public String recurrenceType;
    public boolean isCompleted;
    public int priority;
    public Long completionDate;
    public Integer reminderOffset; // Minutes before due time
    public String reminderDays; // For daily recurring tasks
    public Integer manualPosition; // Manual position from drag operations
    public Long createdAt; // Timestamp when task was created
    public Long updatedAt; // Timestamp when task was last updated
    public String syncStatus; // "pending", "synced", "conflict"
    
    // FamilySync export tracking fields
    public String sourceApp; // "familysync" if exported from FamilySync
    public String sourceTaskId; // FamilySync task ID if exported
    public String sourceGroupId; // FamilySync group ID if exported
    public String familySyncAssigneeId; // FamilySync assignee ID
    public String familySyncCreatorId; // FamilySync creator ID

    // Default constructor required for Firestore
    public FirestoreTask() {}

    // Constructor from existing Task model
    public FirestoreTask(limor.tal.mytodo.Task task, String userId) {
        this.userId = userId;
        this.description = task.description;
        this.dueDate = task.dueDate;
        this.dueTime = task.dueTime;
        this.dayOfWeek = task.dayOfWeek;
        this.isRecurring = task.isRecurring;
        this.recurrenceType = task.recurrenceType;
        this.isCompleted = task.isCompleted;
        this.priority = task.priority;
        this.completionDate = task.completionDate;
        this.reminderOffset = task.reminderOffset;
        this.reminderDays = task.reminderDays;
        this.manualPosition = task.manualPosition;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.syncStatus = "pending";
    }

    // Convert to existing Task model
    @Exclude
    public limor.tal.mytodo.Task toTask() {
        limor.tal.mytodo.Task task = new limor.tal.mytodo.Task(description, dueDate, dayOfWeek, isRecurring, recurrenceType, isCompleted, priority);
        task.firestoreDocumentId = this.documentId; // Store Firestore document ID
        task.dueTime = this.dueTime;
        task.completionDate = this.completionDate;
        task.reminderOffset = this.reminderOffset;
        task.reminderDays = this.reminderDays;
        task.manualPosition = this.manualPosition;
        task.createdAt = this.createdAt;
        task.updatedAt = this.updatedAt;
        
        // Sync FamilySync fields
        task.sourceApp = this.sourceApp;
        task.sourceTaskId = this.sourceTaskId;
        task.sourceGroupId = this.sourceGroupId;
        task.familySyncAssigneeId = this.familySyncAssigneeId;
        task.familySyncCreatorId = this.familySyncCreatorId;
        
        return task;
    }

    // Convert from existing Task model
    public static FirestoreTask fromTask(limor.tal.mytodo.Task task, String userId) {
        FirestoreTask firestoreTask = new FirestoreTask();
        firestoreTask.userId = userId;
        firestoreTask.description = task.description;
        firestoreTask.dueDate = task.dueDate;
        firestoreTask.dueTime = task.dueTime;
        firestoreTask.dayOfWeek = task.dayOfWeek;
        firestoreTask.isRecurring = task.isRecurring;
        firestoreTask.recurrenceType = task.recurrenceType;
        firestoreTask.isCompleted = task.isCompleted;
        firestoreTask.priority = task.priority;
        firestoreTask.completionDate = task.completionDate;
        firestoreTask.reminderOffset = task.reminderOffset;
        firestoreTask.reminderDays = task.reminderDays;
        firestoreTask.manualPosition = task.manualPosition;
        firestoreTask.createdAt = task.createdAt;
        firestoreTask.updatedAt = task.updatedAt;
        firestoreTask.syncStatus = "pending";
        
        // Sync FamilySync fields
        firestoreTask.sourceApp = task.sourceApp;
        firestoreTask.sourceTaskId = task.sourceTaskId;
        firestoreTask.sourceGroupId = task.sourceGroupId;
        firestoreTask.familySyncAssigneeId = task.familySyncAssigneeId;
        firestoreTask.familySyncCreatorId = task.familySyncCreatorId;
        
        return firestoreTask;
    }

    // Convert to Map for Firestore
    @Exclude
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("description", description);
        map.put("dueDate", dueDate);
        map.put("dueTime", dueTime);
        map.put("dayOfWeek", dayOfWeek);
        map.put("isRecurring", isRecurring);
        map.put("recurrenceType", recurrenceType);
        map.put("isCompleted", isCompleted);
        map.put("priority", priority);
        map.put("completionDate", completionDate);
        map.put("reminderOffset", reminderOffset);
        map.put("reminderDays", reminderDays);
        map.put("manualPosition", manualPosition);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        map.put("syncStatus", syncStatus);
        map.put("sourceApp", sourceApp);
        map.put("sourceTaskId", sourceTaskId);
        map.put("sourceGroupId", sourceGroupId);
        map.put("familySyncAssigneeId", familySyncAssigneeId);
        map.put("familySyncCreatorId", familySyncCreatorId);
        return map;
    }

    // Update timestamps
    public void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }

    // Check if task needs sync
    public boolean needsSync() {
        return "pending".equals(syncStatus);
    }
    
    // Check if task is exported from FamilySync
    public boolean isExportedFromFamilySync() {
        return "familysync".equals(sourceApp) && sourceTaskId != null;
    }
    
    // Get FamilySync source info for display
    public String getFamilySyncSourceInfo() {
        if (isExportedFromFamilySync()) {
            return "FamilySync";
        }
        return null;
    }

    @Override
    public String toString() {
        return "FirestoreTask{" +
                "documentId='" + documentId + '\'' +
                ", userId='" + userId + '\'' +
                ", description='" + description + '\'' +
                ", dueDate=" + dueDate +
                ", isCompleted=" + isCompleted +
                ", syncStatus='" + syncStatus + '\'' +
                '}';
    }
}
