package limor.tal.mytodo;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Helper class for updating widgets when data changes
 */
public class WidgetUpdateHelper {
    private static final String TAG = "WidgetUpdateHelper";
    
    /**
     * Refresh all widget instances
     */
    public static void refreshAllWidgets(Context context) {
        try {
            Log.d(TAG, "Refreshing all widgets");
            
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName componentName = new ComponentName(context, SimpleTaskWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
            
            if (appWidgetIds.length > 0) {
                Log.d(TAG, "Found " + appWidgetIds.length + " widget instances to update");
                
                Intent intent = new Intent(context, SimpleTaskWidgetProvider.class);
                intent.setAction(SimpleTaskWidgetProvider.ACTION_REFRESH);
                context.sendBroadcast(intent);
            } else {
                Log.d(TAG, "No widget instances found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing widgets", e);
        }
    }
    
    /**
     * Check if any widgets are currently installed
     * TEMPORARILY DISABLED FOR PHONES TO DEBUG CRASH ISSUE
     */
    public static boolean hasWidgets(Context context) {
        try {
            // Check if this is a phone (screen width < 600dp)
            float screenWidthDp = context.getResources().getDisplayMetrics().widthPixels / 
                                 context.getResources().getDisplayMetrics().density;
            
            if (screenWidthDp < 600) {
                Log.d(TAG, "Phone detected (width: " + screenWidthDp + "dp), disabling widgets for debugging");
                return false;
            }
            
            Log.d(TAG, "Tablet detected (width: " + screenWidthDp + "dp), checking for widgets");
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName componentName = new ComponentName(context, SimpleTaskWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
            return appWidgetIds.length > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking for widgets", e);
            return false;
        }
    }
}
