package limor.tal.mytodo;

/**
 * Constants for task-related values that are always stored in English in the database.
 * Translation to Hebrew happens only in the UI layer.
 */
public class TaskConstants {
    
    // Day of week constants (always stored in English)
    public static final String DAY_NONE = "None";
    public static final String DAY_IMMEDIATE = "Immediate";
    public static final String DAY_SOON = "Soon";
    public static final String DAY_SUNDAY = "Sunday";
    public static final String DAY_MONDAY = "Monday";
    public static final String DAY_TUESDAY = "Tuesday";
    public static final String DAY_WEDNESDAY = "Wednesday";
    public static final String DAY_THURSDAY = "Thursday";
    public static final String DAY_FRIDAY = "Friday";
    public static final String DAY_SATURDAY = "Saturday";
    
    // Recurrence type constants (always stored in English)
    public static final String RECURRENCE_DAILY = "Daily";
    public static final String RECURRENCE_WEEKLY = "Weekly";
    public static final String RECURRENCE_BIWEEKLY = "Biweekly";
    public static final String RECURRENCE_MONTHLY = "Monthly";
    public static final String RECURRENCE_YEARLY = "Yearly";
    
    // Category constants (always stored in English)
    public static final String CATEGORY_WAITING = "Waiting";
    public static final String CATEGORY_COMPLETED = "Completed";
    
    // Array of all day names in order (matches days_of_week array structure)
    public static final String[] ALL_DAYS = {
        DAY_NONE,        // 0
        DAY_IMMEDIATE,   // 1
        DAY_SOON,        // 2
        DAY_SUNDAY,      // 3
        DAY_MONDAY,      // 4
        DAY_TUESDAY,     // 5
        DAY_WEDNESDAY,   // 6
        DAY_THURSDAY,    // 7
        DAY_FRIDAY,      // 8
        DAY_SATURDAY     // 9
    };
    
    // Array of recurrence types
    public static final String[] ALL_RECURRENCE_TYPES = {
        RECURRENCE_DAILY,
        RECURRENCE_WEEKLY,
        RECURRENCE_BIWEEKLY,
        RECURRENCE_MONTHLY,
        RECURRENCE_YEARLY
    };
    
    /**
     * Get the English day name for a Calendar.DAY_OF_WEEK value
     */
    public static String getEnglishDayName(int calendarDayOfWeek) {
        switch (calendarDayOfWeek) {
            case java.util.Calendar.SUNDAY: return DAY_SUNDAY;
            case java.util.Calendar.MONDAY: return DAY_MONDAY;
            case java.util.Calendar.TUESDAY: return DAY_TUESDAY;
            case java.util.Calendar.WEDNESDAY: return DAY_WEDNESDAY;
            case java.util.Calendar.THURSDAY: return DAY_THURSDAY;
            case java.util.Calendar.FRIDAY: return DAY_FRIDAY;
            case java.util.Calendar.SATURDAY: return DAY_SATURDAY;
            default: return DAY_FRIDAY; // Default fallback
        }
    }
    
    /**
     * Get the index of an English day name in the ALL_DAYS array
     */
    public static int getDayIndex(String englishDayName) {
        for (int i = 0; i < ALL_DAYS.length; i++) {
            if (ALL_DAYS[i].equals(englishDayName)) {
                return i;
            }
        }
        return 0; // Default to "None"
    }
    
    /**
     * Check if a day name is a valid English day name
     */
    public static boolean isValidEnglishDayName(String dayName) {
        for (String day : ALL_DAYS) {
            if (day.equals(dayName)) {
                return true;
            }
        }
        return false;
    }
}
