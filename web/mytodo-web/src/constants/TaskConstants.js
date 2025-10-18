/**
 * Constants for task-related values that are always stored in English in the database.
 * Translation to Hebrew happens only in the UI layer.
 * This matches the Android app's TaskConstants.java implementation.
 */
export const TaskConstants = {
    
    // Day of week constants (always stored in English)
    DAY_NONE: "None",
    DAY_IMMEDIATE: "Immediate", 
    DAY_SOON: "Soon",
    DAY_SUNDAY: "Sunday",
    DAY_MONDAY: "Monday",
    DAY_TUESDAY: "Tuesday",
    DAY_WEDNESDAY: "Wednesday",
    DAY_THURSDAY: "Thursday",
    DAY_FRIDAY: "Friday",
    DAY_SATURDAY: "Saturday",
    
    // Recurrence type constants (always stored in English)
    RECURRENCE_DAILY: "Daily",
    RECURRENCE_WEEKLY: "Weekly",
    RECURRENCE_BIWEEKLY: "Biweekly",
    RECURRENCE_MONTHLY: "Monthly",
    RECURRENCE_YEARLY: "Yearly",
    
    // Category constants (always stored in English)
    CATEGORY_WAITING: "Waiting",
    CATEGORY_COMPLETED: "Completed",
    
    // Array of all day names in order (matches days_of_week array structure)
    ALL_DAYS: [
        "None",        // 0
        "Immediate",   // 1
        "Soon",        // 2
        "Sunday",      // 3
        "Monday",      // 4
        "Tuesday",     // 5
        "Wednesday",   // 6
        "Thursday",    // 7
        "Friday",      // 8
        "Saturday"     // 9
    ],
    
    // Array of recurrence types
    ALL_RECURRENCE_TYPES: [
        "Daily",
        "Weekly", 
        "Biweekly",
        "Monthly",
        "Yearly"
    ],
    
    /**
     * Get the English day name for a Date.getDay() value (0=Sunday, 1=Monday, etc.)
     */
    getEnglishDayName: function(calendarDayOfWeek) {
        switch (calendarDayOfWeek) {
            case 0: return this.DAY_SUNDAY;    // Sunday
            case 1: return this.DAY_MONDAY;    // Monday
            case 2: return this.DAY_TUESDAY;   // Tuesday
            case 3: return this.DAY_WEDNESDAY; // Wednesday
            case 4: return this.DAY_THURSDAY;  // Thursday
            case 5: return this.DAY_FRIDAY;    // Friday
            case 6: return this.DAY_SATURDAY;  // Saturday
            default: return this.DAY_FRIDAY;   // Default fallback
        }
    },
    
    /**
     * Get the index of an English day name in the ALL_DAYS array
     */
    getDayIndex: function(englishDayName) {
        for (let i = 0; i < this.ALL_DAYS.length; i++) {
            if (this.ALL_DAYS[i] === englishDayName) {
                return i;
            }
        }
        return 0; // Default to "None"
    },
    
    /**
     * Check if a day name is a valid English day name
     */
    isValidEnglishDayName: function(dayName) {
        return this.ALL_DAYS.includes(dayName);
    }
};
