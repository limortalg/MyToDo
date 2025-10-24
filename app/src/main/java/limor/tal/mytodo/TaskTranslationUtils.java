package limor.tal.mytodo;

import android.content.Context;
import android.util.Log;

/**
 * Utility class for translating English database values to Hebrew for display.
 * All database values are stored in English, translation happens only in UI.
 */
public class TaskTranslationUtils {
    private static final String TAG = "TaskTranslationUtils";
    
    /**
     * Translate English day name to Hebrew for display
     */
    public static String translateDayName(Context context, String englishDayName) {
        if (englishDayName == null) {
            return null;
        }
        
        try {
            String[] daysOfWeek = context.getResources().getStringArray(R.array.days_of_week);
            
            // Map English day names to Hebrew using the days_of_week array
            switch (englishDayName) {
                case TaskConstants.DAY_NONE:
                    return daysOfWeek[0]; // "ללא" or "None"
                case TaskConstants.DAY_IMMEDIATE:
                    return daysOfWeek[1]; // "מיידי" or "Immediate"
                case TaskConstants.DAY_SOON:
                    return daysOfWeek[2]; // "בקרוב" or "Soon"
                case TaskConstants.DAY_SUNDAY:
                    return daysOfWeek[3]; // "ראשון" or "Sunday"
                case TaskConstants.DAY_MONDAY:
                    return daysOfWeek[4]; // "שני" or "Monday"
                case TaskConstants.DAY_TUESDAY:
                    return daysOfWeek[5]; // "שלישי" or "Tuesday"
                case TaskConstants.DAY_WEDNESDAY:
                    return daysOfWeek[6]; // "רביעי" or "Wednesday"
                case TaskConstants.DAY_THURSDAY:
                    return daysOfWeek[7]; // "חמישי" or "Thursday"
                case TaskConstants.DAY_FRIDAY:
                    return daysOfWeek[8]; // "שישי" or "Friday"
                case TaskConstants.DAY_SATURDAY:
                    return daysOfWeek[9]; // "שבת" or "Saturday"
                default:
                    Log.w(TAG, "Unknown English day name: " + englishDayName);
                    return englishDayName; // Return as-is if unknown
            }
        } catch (Exception e) {
            Log.e(TAG, "Error translating day name: " + englishDayName, e);
            return englishDayName; // Return as-is on error
        }
    }
    
    /**
     * Translate English recurrence type to Hebrew for display
     */
    public static String translateRecurrenceType(Context context, String englishRecurrenceType) {
        if (englishRecurrenceType == null) {
            return null;
        }
        
        try {
            String[] recurrenceTypes = context.getResources().getStringArray(R.array.recurrence_types);
            
            switch (englishRecurrenceType) {
                case TaskConstants.RECURRENCE_DAILY:
                    return recurrenceTypes[0]; // "יומי" or "Daily"
                case TaskConstants.RECURRENCE_WEEKLY:
                    return recurrenceTypes[1]; // "שבועי" or "Weekly"
                case TaskConstants.RECURRENCE_BIWEEKLY:
                    return recurrenceTypes[2]; // "דו-שבועי" or "Biweekly"
                case TaskConstants.RECURRENCE_MONTHLY:
                    return recurrenceTypes[3]; // "חודשי" or "Monthly"
                case TaskConstants.RECURRENCE_YEARLY:
                    return recurrenceTypes[4]; // "שנתי" or "Yearly"
                default:
                    Log.w(TAG, "Unknown English recurrence type: " + englishRecurrenceType);
                    return englishRecurrenceType; // Return as-is if unknown
            }
        } catch (Exception e) {
            Log.e(TAG, "Error translating recurrence type: " + englishRecurrenceType, e);
            return englishRecurrenceType; // Return as-is on error
        }
    }
    
    /**
     * Translate English category to Hebrew for display
     */
    public static String translateCategory(Context context, String englishCategory) {
        if (englishCategory == null) {
            return null;
        }
        
        try {
            switch (englishCategory) {
                case TaskConstants.CATEGORY_WAITING:
                    return context.getString(R.string.category_waiting); // "בהמתנה" or "Waiting"
                case TaskConstants.CATEGORY_COMPLETED:
                    return context.getString(R.string.completed_category_title); // "הושלם" or "Completed"
                default:
                    Log.w(TAG, "Unknown English category: " + englishCategory);
                    return englishCategory; // Return as-is if unknown
            }
        } catch (Exception e) {
            Log.e(TAG, "Error translating category: " + englishCategory, e);
            return englishCategory; // Return as-is on error
        }
    }
    
    /**
     * Convert Hebrew day name back to English (for migration purposes)
     */
    public static String convertHebrewToEnglishDayName(String hebrewDayName) {
        if (hebrewDayName == null) {
            return null;
        }
        
        // Map Hebrew day names to English
        switch (hebrewDayName) {
            case "ללא":
                return TaskConstants.DAY_NONE;
            case "מיידי":
                return TaskConstants.DAY_IMMEDIATE;
            case "בקרוב":
                return TaskConstants.DAY_SOON;
            case "ראשון":
                return TaskConstants.DAY_SUNDAY;
            case "שני":
                return TaskConstants.DAY_MONDAY;
            case "שלישי":
                return TaskConstants.DAY_TUESDAY;
            case "רביעי":
                return TaskConstants.DAY_WEDNESDAY;
            case "חמישי":
                return TaskConstants.DAY_THURSDAY;
            case "שישי":
                return TaskConstants.DAY_FRIDAY;
            case "שבת":
                return TaskConstants.DAY_SATURDAY;
            default:
                // If it's already English or unknown, return as-is
                if (TaskConstants.isValidEnglishDayName(hebrewDayName)) {
                    return hebrewDayName;
                }
                Log.w(TAG, "Unknown Hebrew day name: " + hebrewDayName);
                return TaskConstants.DAY_NONE; // Default fallback
        }
    }
    
    /**
     * Convert Hebrew recurrence type back to English (for migration purposes)
     */
    public static String convertHebrewToEnglishRecurrenceType(String hebrewRecurrenceType) {
        if (hebrewRecurrenceType == null) {
            return null;
        }
        
        switch (hebrewRecurrenceType) {
            case "יומי":
                return TaskConstants.RECURRENCE_DAILY;
            case "שבועי":
                return TaskConstants.RECURRENCE_WEEKLY;
            case "דו-שבועי":
            case "דו שבועי":
            case "כל שבועיים":
                return TaskConstants.RECURRENCE_BIWEEKLY;
            case "חודשי":
                return TaskConstants.RECURRENCE_MONTHLY;
            case "שנתי":
                return TaskConstants.RECURRENCE_YEARLY;
            default:
                // If it's already English or unknown, return as-is
                for (String type : TaskConstants.ALL_RECURRENCE_TYPES) {
                    if (type.equals(hebrewRecurrenceType)) {
                        return hebrewRecurrenceType;
                    }
                }
                Log.w(TAG, "Unknown Hebrew recurrence type: " + hebrewRecurrenceType);
                return TaskConstants.RECURRENCE_DAILY; // Default fallback
        }
    }
}
