package limor.tal.mytodo;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class Task {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String description;
    public Long dueDate;
    public Long dueTime; // Milliseconds since midnight
    public String dayOfWeek;
    public boolean isRecurring;
    public String recurrenceType;
    public boolean isCompleted;
    public int priority;
    public Long completionDate;
    public Integer reminderOffset; // Minutes before due time: null (no reminder), 0 (at time), 15, 30, or 60
    public String reminderDays; // For daily recurring tasks: null (all days), or comma-separated day indices (0=Sunday, 1=Monday, etc.)
    public Integer manualPosition; // null = automatic ordering, integer = manual position from drag operations

    public Task(String description, Long dueDate, String dayOfWeek, boolean isRecurring, String recurrenceType, boolean isCompleted, int priority) {
        this.description = description;
        this.dueDate = dueDate;
        this.dueTime = null;
        this.dayOfWeek = dayOfWeek;
        this.isRecurring = isRecurring;
        this.recurrenceType = recurrenceType;
        this.isCompleted = isCompleted;
        this.priority = priority;
        this.completionDate = null;
        this.reminderOffset = null; // No reminder by default
        this.reminderDays = null; // All days by default
        this.manualPosition = null; // Automatic ordering by default
    }

    @Override
    public String toString() {
        return "Task{" +
                "id=" + id +
                ", description='" + description + '\'' +
                ", dueDate=" + dueDate +
                ", dueTime=" + dueTime +
                ", dayOfWeek='" + dayOfWeek + '\'' +
                ", isRecurring=" + isRecurring +
                ", recurrenceType='" + recurrenceType + '\'' +
                ", isCompleted=" + isCompleted +
                ", priority=" + priority +
                ", completionDate=" + completionDate +
                ", reminderOffset=" + reminderOffset +
                ", reminderDays='" + reminderDays + '\'' +
                ", manualPosition=" + manualPosition +
                '}';
    }
}