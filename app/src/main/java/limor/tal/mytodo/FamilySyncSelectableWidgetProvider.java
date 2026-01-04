package limor.tal.mytodo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;

/**
 * Widget provider for FamilySync - shows pending tasks and questions counts for a selected family
 * Allows switching between families if user has multiple
 */
public class FamilySyncSelectableWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "FamilySyncSelectableWidget";
    private static final String ACTION_REFRESH = "limor.tal.mytodo.FAMILYSYNC_SELECTABLE_REFRESH_WIDGET";
    private static final String ACTION_SWITCH_FAMILY = "limor.tal.mytodo.FAMILYSYNC_SWITCH_FAMILY";
    private static final String ACTION_SCHEDULED_UPDATE = "limor.tal.mytodo.FAMILYSYNC_SELECTABLE_SCHEDULED_UPDATE";
    private static final String FAMILYSYNC_API_URL = "https://familysync-api-382070971886.europe-west1.run.app/api";
    private static final String FAMILYSYNC_WEB_URL = "https://familysync-app-470820.web.app";
    private static final String PREF_SELECTED_GROUP_ID = "familysync_widget_selected_group_id";
    private static final String PREF_GROUP_NAME = "familysync_widget_group_name";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
        scheduleUpdates(context);
    }
    
    @Override
    public void onEnabled(Context context) {
        scheduleUpdates(context);
    }
    
    @Override
    public void onDisabled(Context context) {
        cancelScheduledUpdates(context);
    }
    
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        // Widget was resized - update it with new sizing
        updateWidget(context, appWidgetManager, appWidgetId);
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        
        String action = intent.getAction();
        
        if (ACTION_REFRESH.equals(action) || ACTION_SCHEDULED_UPDATE.equals(action)) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName componentName = new ComponentName(context, FamilySyncSelectableWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
            
            for (int appWidgetId : appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId);
            }
            
            if (ACTION_SCHEDULED_UPDATE.equals(action)) {
                scheduleUpdates(context);
            }
        } else if (ACTION_SWITCH_FAMILY.equals(action)) {
            String groupId = intent.getStringExtra("groupId");
            String groupName = intent.getStringExtra("groupName");
            if (groupId != null) {
                saveSelectedGroupStatic(context, groupId, groupName);
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                ComponentName componentName = new ComponentName(context, FamilySyncSelectableWidgetProvider.class);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
                
                for (int appWidgetId : appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId);
                }
            }
        }
    }
    
    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.familysync_selectable_widget_layout);
            
            // Ensure Firebase is initialized
            try {
                FirebaseApp.initializeApp(context);
            } catch (Exception e) {
                // Firebase may already be initialized, which is fine
            }
            
            // Get current user
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                views.setTextViewText(R.id.familysync_selectable_title, context.getString(R.string.familysync_widget_name));
                views.setTextViewText(R.id.familysync_selectable_group_name, context.getString(R.string.familysync_not_signed_in));
                views.setTextViewText(R.id.familysync_selectable_tasks_count, "?");
                views.setTextViewText(R.id.familysync_selectable_questions_count, "?");
                
                Intent openAppIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(FAMILYSYNC_WEB_URL));
                openAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.familysync_selectable_container, openAppPendingIntent);
                
                appWidgetManager.updateAppWidget(appWidgetId, views);
                return;
            }
            
            String userId = currentUser.getUid();
            
            // Apply size-based styling - always call it to ensure proper sizing
            Bundle options = null;
            boolean isWide = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                options = appWidgetManager.getAppWidgetOptions(appWidgetId);
                if (options != null) {
                    int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                    int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                    isWide = minWidth > minHeight * 1.5f;
                }
            }
            // Always apply styling, including for 2x1
            try {
                applySizeBasedStyling(context, views, appWidgetManager, appWidgetId, isWide, 0.0f);
            } catch (Exception e) {
                Log.e(TAG, "Error applying size-based styling", e);
            }
            
            // Set title
            views.setTextViewText(R.id.familysync_selectable_title, context.getString(R.string.familysync_widget_name));
            views.setTextColor(R.id.familysync_selectable_title, 0xFF03DAC5);
            
            // Show loading
            views.setTextViewText(R.id.familysync_selectable_group_name, context.getString(R.string.familysync_loading));
            views.setTextViewText(R.id.familysync_selectable_tasks_count, "...");
            views.setTextViewText(R.id.familysync_selectable_questions_count, "...");
            
            // Set up refresh button
            Intent refreshIntent = new Intent(context, FamilySyncSelectableWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH);
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 2, refreshIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.familysync_selectable_refresh, refreshPendingIntent);
            
            // Set up click to open app
            Intent openAppIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(FAMILYSYNC_WEB_URL));
            openAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.familysync_selectable_container, openAppPendingIntent);
            
            appWidgetManager.updateAppWidget(appWidgetId, views);
            
            // Load groups and counts
            // Pass 0.0f as multiplier - LoadGroupsAndCountsTask will recalculate from widget dimensions
            new LoadGroupsAndCountsTask(context, appWidgetManager, appWidgetId, userId, 0.0f).execute();
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget", e);
        }
    }
    
    private void saveSelectedGroup(Context context, String groupId, String groupName) {
        saveSelectedGroupStatic(context, groupId, groupName);
    }
    
    private static void saveSelectedGroupStatic(Context context, String groupId, String groupName) {
        SharedPreferences prefs = context.getSharedPreferences("FamilySyncWidgetPrefs", Context.MODE_PRIVATE);
        prefs.edit()
            .putString(PREF_SELECTED_GROUP_ID, groupId)
            .putString(PREF_GROUP_NAME, groupName)
            .apply();
    }
    
    private String getSelectedGroupId(Context context) {
        return getSelectedGroupIdStatic(context);
    }
    
    private static String getSelectedGroupIdStatic(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("FamilySyncWidgetPrefs", Context.MODE_PRIVATE);
        return prefs.getString(PREF_SELECTED_GROUP_ID, null);
    }
    
    private String getSelectedGroupName(Context context) {
        return getSelectedGroupNameStatic(context);
    }
    
    private static String getSelectedGroupNameStatic(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("FamilySyncWidgetPrefs", Context.MODE_PRIVATE);
        return prefs.getString(PREF_GROUP_NAME, null);
    }
    
    private void applySizeBasedStyling(Context context, RemoteViews views, AppWidgetManager appWidgetManager, 
                                       int appWidgetId, boolean isWide, float sizeMultiplier) {
        try {
            Bundle options = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                options = appWidgetManager.getAppWidgetOptions(appWidgetId);
            }
            
            if (options == null) return;
            
            int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
            
            // Calculate text multiplier based on widget dimensions
            // Default 2x1 (110dp x 70dp) = 1.0x multiplier
            boolean isWiderThanOne = minWidth > 70;
            boolean isTallerThanOne = minHeight > 70;
            boolean heightIsTwo = minHeight > 70 && minHeight <= 150; // Height is 2 cells
            boolean isLargerThan2x2 = minWidth > 150 && minHeight > 150; // Larger than 2x2
            
            float textMultiplier = 1.0f;
            if (isLargerThan2x2) {
                // Larger than 2x2 - scale aggressively
                float area = minWidth * minHeight;
                if (area > 80000) { // Very large (3x3+)
                    textMultiplier = 3.0f;
                } else if (area > 60000) { // Large (3x2 or 2x3)
                    textMultiplier = 2.5f;
                } else {
                    textMultiplier = 2.0f; // Larger than 2x2
                }
            } else if (heightIsTwo) {
                // Height is 2 cells (1x2, 2x2) - 50% larger
                textMultiplier = 1.5f;
            } else {
                // Default 2x1 size - keep base sizes
                textMultiplier = 1.0f;
            }
            
            // Button multiplier - grows with both width and height (more with height), matching first widget logic
            // For 2x1, use smaller base button size to avoid hiding content
            // 2x1 widget height is exactly 70dp, so use <= 75 to catch it
            float baseButtonSize = (minHeight <= 75) ? 18f : 24f; // Even smaller in 2x1 (18dp), normal for larger (24dp)
            float buttonWidthFactor = isWiderThanOne ? Math.min((minWidth - 70) / 70f * 0.2f, 0.3f) : 0f; // Max 30% from width
            float buttonHeightFactor = isTallerThanOne ? Math.min((minHeight - 70) / 70f * 0.4f, 0.6f) : 0f; // Max 60% from height
            float buttonMultiplier = 1.0f + buttonWidthFactor + buttonHeightFactor;
            
            // Base sizes for 2x1 (matching first widget's proportions but slightly larger for this widget)
            float titleSize = 8f * textMultiplier;
            float groupNameSize = 7f * textMultiplier;
            float numberSize = 14f * textMultiplier;
            float labelSize = 7f * textMultiplier;
            
            // Button size - smaller base for 2x1 to avoid hiding content, grows with multiplier
            float buttonSize = baseButtonSize * buttonMultiplier;
            float buttonTextSize = (baseButtonSize == 18f ? 9f : 12f) * buttonMultiplier; // 9sp for 18dp button, 12sp for 24dp button
            
            views.setTextViewTextSize(R.id.familysync_selectable_title, TypedValue.COMPLEX_UNIT_SP, titleSize);
            views.setTextViewTextSize(R.id.familysync_selectable_group_name, TypedValue.COMPLEX_UNIT_SP, groupNameSize);
            views.setTextViewTextSize(R.id.familysync_selectable_tasks_count, TypedValue.COMPLEX_UNIT_SP, numberSize);
            views.setTextViewTextSize(R.id.familysync_selectable_questions_count, TypedValue.COMPLEX_UNIT_SP, numberSize);
            views.setTextViewTextSize(R.id.familysync_selectable_tasks_label, TypedValue.COMPLEX_UNIT_SP, labelSize);
            views.setTextViewTextSize(R.id.familysync_selectable_questions_label, TypedValue.COMPLEX_UNIT_SP, labelSize);
            
            // Scale button sizes
            views.setViewLayoutWidth(R.id.familysync_selectable_refresh, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutHeight(R.id.familysync_selectable_refresh, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
            views.setTextViewTextSize(R.id.familysync_selectable_refresh, TypedValue.COMPLEX_UNIT_SP, buttonTextSize);
            
            // Scale switch family button size
            views.setViewLayoutWidth(R.id.familysync_selectable_switch_family, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutHeight(R.id.familysync_selectable_switch_family, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
            views.setTextViewTextSize(R.id.familysync_selectable_switch_family, TypedValue.COMPLEX_UNIT_SP, buttonTextSize);
            
        } catch (Exception e) {
            Log.e(TAG, "Error applying size-based styling", e);
        }
    }
    
    private void scheduleUpdates(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;
            
            Intent updateIntent = new Intent(context, FamilySyncSelectableWidgetProvider.class);
            updateIntent.setAction(ACTION_SCHEDULED_UPDATE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, updateIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
            Calendar now = Calendar.getInstance();
            int currentHour = now.get(Calendar.HOUR_OF_DAY);
            
            Calendar nextUpdate = Calendar.getInstance();
            
            if (currentHour < 12) {
                nextUpdate.set(Calendar.HOUR_OF_DAY, 12);
                nextUpdate.set(Calendar.MINUTE, 0);
                nextUpdate.set(Calendar.SECOND, 0);
                nextUpdate.set(Calendar.MILLISECOND, 0);
            } else {
                nextUpdate.set(Calendar.HOUR_OF_DAY, 0);
                nextUpdate.set(Calendar.MINUTE, 0);
                nextUpdate.set(Calendar.SECOND, 0);
                nextUpdate.set(Calendar.MILLISECOND, 0);
                nextUpdate.add(Calendar.DAY_OF_MONTH, 1);
            }
            
            if (nextUpdate.getTimeInMillis() <= now.getTimeInMillis()) {
                nextUpdate.add(Calendar.DAY_OF_MONTH, 1);
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextUpdate.getTimeInMillis(), pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextUpdate.getTimeInMillis(), pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, nextUpdate.getTimeInMillis(), pendingIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling widget updates", e);
        }
    }
    
    private void cancelScheduledUpdates(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;
            
            Intent updateIntent = new Intent(context, FamilySyncSelectableWidgetProvider.class);
            updateIntent.setAction(ACTION_SCHEDULED_UPDATE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, updateIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
            alarmManager.cancel(pendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error canceling scheduled widget updates", e);
        }
    }
    
    /**
     * AsyncTask to load groups and counts
     */
    private class LoadGroupsAndCountsTask extends AsyncTask<Void, Void, GroupsAndCounts> {
        private Context context;
        private AppWidgetManager appWidgetManager;
        private int appWidgetId;
        private String userId;
        private float sizeMultiplier;
        
        public LoadGroupsAndCountsTask(Context context, AppWidgetManager appWidgetManager, 
                                      int appWidgetId, String userId, float sizeMultiplier) {
            this.context = context;
            this.appWidgetManager = appWidgetManager;
            this.appWidgetId = appWidgetId;
            this.userId = userId;
            this.sizeMultiplier = sizeMultiplier;
        }
        
        @Override
        protected GroupsAndCounts doInBackground(Void... params) {
            HttpURLConnection groupsConnection = null;
            HttpURLConnection countsConnection = null;
            BufferedReader reader = null;
            try {
                // Load groups
                String groupsUrl = FAMILYSYNC_API_URL + "/groups?userId=" + URLEncoder.encode(userId, "UTF-8");
                URL groupsUrlObj = new URL(groupsUrl);
                groupsConnection = (HttpURLConnection) groupsUrlObj.openConnection();
                groupsConnection.setRequestMethod("GET");
                groupsConnection.setConnectTimeout(10000);
                groupsConnection.setReadTimeout(10000);
                
                int groupsResponseCode = groupsConnection.getResponseCode();
                if (groupsResponseCode != HttpURLConnection.HTTP_OK) {
                    return null;
                }
                
                reader = new BufferedReader(new InputStreamReader(groupsConnection.getInputStream()));
                StringBuilder groupsResponse = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    groupsResponse.append(line);
                }
                reader.close();
                groupsConnection.disconnect();
                
                JSONArray groupsArray = new JSONArray(groupsResponse.toString());
                if (groupsArray.length() == 0) {
                    return new GroupsAndCounts(null, null, 0, 0);
                }
                
                // Determine selected group
                String selectedGroupId = FamilySyncSelectableWidgetProvider.getSelectedGroupIdStatic(context);
                String selectedGroupName = FamilySyncSelectableWidgetProvider.getSelectedGroupNameStatic(context);
                JSONObject selectedGroup = null;
                
                // If no group selected, use first group
                if (selectedGroupId == null) {
                    selectedGroup = groupsArray.getJSONObject(0);
                    selectedGroupId = selectedGroup.getString("id");
                    selectedGroupName = selectedGroup.getString("name");
                    FamilySyncSelectableWidgetProvider.saveSelectedGroupStatic(context, selectedGroupId, selectedGroupName);
                } else {
                    // Find the selected group in the array
                    for (int i = 0; i < groupsArray.length(); i++) {
                        JSONObject group = groupsArray.getJSONObject(i);
                        if (selectedGroupId.equals(group.getString("id"))) {
                            selectedGroup = group;
                            selectedGroupName = group.getString("name");
                            break;
                        }
                    }
                    
                    // If selected group not found, use first group
                    if (selectedGroup == null) {
                        selectedGroup = groupsArray.getJSONObject(0);
                        selectedGroupId = selectedGroup.getString("id");
                        selectedGroupName = selectedGroup.getString("name");
                        FamilySyncSelectableWidgetProvider.saveSelectedGroupStatic(context, selectedGroupId, selectedGroupName);
                    }
                }
                
                // Load counts for selected group
                String countsUrl = FAMILYSYNC_API_URL + "/widget/counts?userId=" + URLEncoder.encode(userId, "UTF-8") 
                    + "&groupId=" + URLEncoder.encode(selectedGroupId, "UTF-8");
                URL countsUrlObj = new URL(countsUrl);
                countsConnection = (HttpURLConnection) countsUrlObj.openConnection();
                countsConnection.setRequestMethod("GET");
                countsConnection.setConnectTimeout(10000);
                countsConnection.setReadTimeout(10000);
                
                int countsResponseCode = countsConnection.getResponseCode();
                if (countsResponseCode != HttpURLConnection.HTTP_OK) {
                    return new GroupsAndCounts(selectedGroupId, selectedGroupName, 0, 0);
                }
                
                reader = new BufferedReader(new InputStreamReader(countsConnection.getInputStream()));
                StringBuilder countsResponse = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    countsResponse.append(line);
                }
                reader.close();
                
                JSONObject countsJson = new JSONObject(countsResponse.toString());
                int tasksCount = countsJson.getInt("tasksCount");
                int questionsCount = countsJson.getInt("questionsCount");
                
                return new GroupsAndCounts(selectedGroupId, selectedGroupName, tasksCount, questionsCount, groupsArray);
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading groups and counts", e);
                return null;
            } finally {
                try {
                    if (reader != null) reader.close();
                    if (groupsConnection != null) groupsConnection.disconnect();
                    if (countsConnection != null) countsConnection.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing connections", e);
                }
            }
        }
        
        @Override
        protected void onPostExecute(GroupsAndCounts result) {
            try {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.familysync_selectable_widget_layout);
                
                // Re-apply size-based styling to ensure layout and sizes are correct
                Bundle options = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    options = appWidgetManager.getAppWidgetOptions(appWidgetId);
                }
                
                if (options != null) {
                    int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                    int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                    
                    // Calculate multipliers based on current widget dimensions
                    boolean isWiderThanOne = minWidth > 70;
                    boolean isTallerThanOne = minHeight > 70;
                    boolean heightIsTwo = minHeight > 70 && minHeight <= 150;
                    boolean isLargerThan2x2 = minWidth > 150 && minHeight > 150;
                    
                    float textMultiplier = 1.0f;
                    if (isLargerThan2x2) {
                        float area = minWidth * minHeight;
                        if (area > 80000) {
                            textMultiplier = 3.0f;
                        } else if (area > 60000) {
                            textMultiplier = 2.5f;
                        } else {
                            textMultiplier = 2.0f;
                        }
                    } else if (heightIsTwo) {
                        textMultiplier = 1.5f;
                    }
                    
                    // For 2x1, use smaller base button size to avoid hiding content
                    // 2x1 widget height is exactly 70dp, so use <= 75 to catch it
                    float baseButtonSize = (minHeight <= 75) ? 18f : 24f; // Even smaller in 2x1 (18dp), normal for larger (24dp)
                    float buttonWidthFactor = isWiderThanOne ? Math.min((minWidth - 70) / 70f * 0.2f, 0.3f) : 0f;
                    float buttonHeightFactor = isTallerThanOne ? Math.min((minHeight - 70) / 70f * 0.4f, 0.6f) : 0f;
                    float buttonMultiplier = 1.0f + buttonWidthFactor + buttonHeightFactor;
                    
                    // Apply text sizes
                    float titleSize = 8f * textMultiplier;
                    float groupNameSize = 7f * textMultiplier;
                    float numberSize = 14f * textMultiplier;
                    float labelSize = 7f * textMultiplier;
                    float buttonSize = baseButtonSize * buttonMultiplier;
                    float buttonTextSize = (baseButtonSize == 18f ? 9f : 12f) * buttonMultiplier; // 9sp for 18dp button, 12sp for 24dp button
                    
                    // Apply all text sizes (always apply, even for 2x1, to ensure consistency)
                    views.setTextViewTextSize(R.id.familysync_selectable_title, TypedValue.COMPLEX_UNIT_SP, titleSize);
                    views.setTextViewTextSize(R.id.familysync_selectable_group_name, TypedValue.COMPLEX_UNIT_SP, groupNameSize);
                    views.setTextViewTextSize(R.id.familysync_selectable_tasks_count, TypedValue.COMPLEX_UNIT_SP, numberSize);
                    views.setTextViewTextSize(R.id.familysync_selectable_questions_count, TypedValue.COMPLEX_UNIT_SP, numberSize);
                    views.setTextViewTextSize(R.id.familysync_selectable_tasks_label, TypedValue.COMPLEX_UNIT_SP, labelSize);
                    views.setTextViewTextSize(R.id.familysync_selectable_questions_label, TypedValue.COMPLEX_UNIT_SP, labelSize);
                    views.setViewLayoutWidth(R.id.familysync_selectable_refresh, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
                    views.setViewLayoutHeight(R.id.familysync_selectable_refresh, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
                    views.setTextViewTextSize(R.id.familysync_selectable_refresh, TypedValue.COMPLEX_UNIT_SP, buttonTextSize);
                    views.setViewLayoutWidth(R.id.familysync_selectable_switch_family, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
                    views.setViewLayoutHeight(R.id.familysync_selectable_switch_family, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
                    views.setTextViewTextSize(R.id.familysync_selectable_switch_family, TypedValue.COMPLEX_UNIT_SP, buttonTextSize);
                    
                    // Update sizeMultiplier for count styling below
                    sizeMultiplier = textMultiplier;
                }
                
                // Set title color
                views.setTextColor(R.id.familysync_selectable_title, 0xFF03DAC5);
                
                if (result != null) {
                    // Set group name
                    views.setTextViewText(R.id.familysync_selectable_group_name, result.groupName != null ? result.groupName : "Family");
                    
                    // Update counts
                    views.setTextViewText(R.id.familysync_selectable_tasks_count, String.valueOf(result.tasksCount));
                    views.setTextViewText(R.id.familysync_selectable_questions_count, String.valueOf(result.questionsCount));
                    views.setTextViewText(R.id.familysync_selectable_tasks_label, "tasks");
                    views.setTextViewText(R.id.familysync_selectable_questions_label, "questions");
                    
                    // Style counts - use recalculated multiplier
                    float baseNumberSize = 14f * sizeMultiplier; // Base 14sp for 2x1
                    int tasksTextColor = result.tasksCount > 0 ? 0xFFFFD700 : 0xFFFFFFFF;
                    int questionsTextColor = result.questionsCount > 0 ? 0xFFFF6B6B : 0xFFFFFFFF;
                    
                    views.setTextColor(R.id.familysync_selectable_tasks_count, tasksTextColor);
                    views.setTextColor(R.id.familysync_selectable_questions_count, questionsTextColor);
                    
                    // Only make numbers bigger if multiplier > 1.0 (not 2x1 default)
                    if (sizeMultiplier > 1.0f && result.tasksCount > 0) {
                        float tasksNumberSize = baseNumberSize * 1.1f;
                        views.setTextViewTextSize(R.id.familysync_selectable_tasks_count, TypedValue.COMPLEX_UNIT_SP, tasksNumberSize);
                    } else {
                        views.setTextViewTextSize(R.id.familysync_selectable_tasks_count, TypedValue.COMPLEX_UNIT_SP, baseNumberSize);
                    }
                    
                    if (sizeMultiplier > 1.0f && result.questionsCount > 0) {
                        float questionsNumberSize = baseNumberSize * 1.1f;
                        views.setTextViewTextSize(R.id.familysync_selectable_questions_count, TypedValue.COMPLEX_UNIT_SP, questionsNumberSize);
                    } else {
                        views.setTextViewTextSize(R.id.familysync_selectable_questions_count, TypedValue.COMPLEX_UNIT_SP, baseNumberSize);
                    }
                    
                    // Set up family switcher if multiple groups
                    if (result.groupsArray != null && result.groupsArray.length() > 1) {
                        views.setViewVisibility(R.id.familysync_selectable_switch_family, android.view.View.VISIBLE);
                        
                        // Find next group to switch to
                        String nextGroupId = null;
                        String nextGroupName = null;
                        try {
                            for (int i = 0; i < result.groupsArray.length(); i++) {
                                JSONObject group = result.groupsArray.getJSONObject(i);
                                if (result.groupId.equals(group.getString("id"))) {
                                    // Get next group (circular)
                                    int nextIndex = (i + 1) % result.groupsArray.length();
                                    JSONObject nextGroup = result.groupsArray.getJSONObject(nextIndex);
                                    nextGroupId = nextGroup.getString("id");
                                    nextGroupName = nextGroup.getString("name");
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error finding next group", e);
                        }
                        
                        if (nextGroupId != null) {
                            Intent switchIntent = new Intent(context, FamilySyncSelectableWidgetProvider.class);
                            switchIntent.setAction(ACTION_SWITCH_FAMILY);
                            switchIntent.putExtra("groupId", nextGroupId);
                            switchIntent.putExtra("groupName", nextGroupName);
                            PendingIntent switchPendingIntent = PendingIntent.getBroadcast(context, 3, switchIntent, 
                                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                            views.setOnClickPendingIntent(R.id.familysync_selectable_switch_family, switchPendingIntent);
                        }
                    } else {
                        views.setViewVisibility(R.id.familysync_selectable_switch_family, android.view.View.GONE);
                    }
                } else {
                    views.setTextViewText(R.id.familysync_selectable_group_name, "Error");
                    views.setTextViewText(R.id.familysync_selectable_tasks_count, "?");
                    views.setTextViewText(R.id.familysync_selectable_questions_count, "?");
                }
                
                // Set up refresh button
                Intent refreshIntent = new Intent(context, FamilySyncSelectableWidgetProvider.class);
                refreshIntent.setAction(ACTION_REFRESH);
                PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 2, refreshIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.familysync_selectable_refresh, refreshPendingIntent);
                
                // Set up click to open app
                Intent openAppIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(FAMILYSYNC_WEB_URL));
                openAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.familysync_selectable_container, openAppPendingIntent);
                
                appWidgetManager.updateAppWidget(appWidgetId, views);
            } catch (Exception e) {
                Log.e(TAG, "Error updating widget after load", e);
            }
        }
    }
    
    private static class GroupsAndCounts {
        String groupId;
        String groupName;
        int tasksCount;
        int questionsCount;
        JSONArray groupsArray;
        
        GroupsAndCounts(String groupId, String groupName, int tasksCount, int questionsCount) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.tasksCount = tasksCount;
            this.questionsCount = questionsCount;
        }
        
        GroupsAndCounts(String groupId, String groupName, int tasksCount, int questionsCount, JSONArray groupsArray) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.tasksCount = tasksCount;
            this.questionsCount = questionsCount;
            this.groupsArray = groupsArray;
        }
    }
}

