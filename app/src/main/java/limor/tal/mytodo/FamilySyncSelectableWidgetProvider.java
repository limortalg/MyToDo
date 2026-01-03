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
            
            // Apply size-based styling
            Bundle options = null;
            boolean isWide = false;
            float sizeMultiplier = 1.0f;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                options = appWidgetManager.getAppWidgetOptions(appWidgetId);
                if (options != null) {
                    int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                    int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                    isWide = minWidth > minHeight * 1.5f;
                    float area = minWidth * minHeight;
                    if (area > 15000) {
                        sizeMultiplier = 1.5f;
                    } else if (area > 8000) {
                        sizeMultiplier = 1.2f;
                    }
                }
            }
            applySizeBasedStyling(context, views, appWidgetManager, appWidgetId, isWide, sizeMultiplier);
            
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
            new LoadGroupsAndCountsTask(context, appWidgetManager, appWidgetId, userId, sizeMultiplier).execute();
            
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
            isWide = minWidth > minHeight * 1.5f;
            boolean isNarrow = minWidth < minHeight * 1.2f; // Narrower than tall
            boolean isSmallHeight = minHeight < 100; // Very small height
            
            float area = minWidth * minHeight;
            if (area > 30000) {
                sizeMultiplier = 2.0f;
            } else if (area > 20000) {
                sizeMultiplier = 1.8f;
            } else if (area > 12000) {
                sizeMultiplier = 1.5f;
            } else if (area > 8000) {
                sizeMultiplier = 1.2f;
            } else {
                sizeMultiplier = 1.0f; // Default 3x2 size
            }
            
            // Hide or shrink title if height is too small
            if (isSmallHeight) {
                views.setViewVisibility(R.id.familysync_selectable_title, android.view.View.GONE);
            } else {
                views.setViewVisibility(R.id.familysync_selectable_title, android.view.View.VISIBLE);
            }
            
            // Adjust layout orientation based on width
            if (isNarrow) {
                // Narrow layout: tasks and questions stacked vertically (one above the other)
                views.setInt(R.id.familysync_selectable_counts_container, "setOrientation", 1); // VERTICAL
                views.setInt(R.id.familysync_selectable_tasks_container, "setOrientation", 1); // VERTICAL (number above label)
                views.setInt(R.id.familysync_selectable_questions_container, "setOrientation", 1); // VERTICAL (number above label)
            } else {
                // Wide layout: tasks and questions side by side
                views.setInt(R.id.familysync_selectable_counts_container, "setOrientation", 0); // HORIZONTAL
                views.setInt(R.id.familysync_selectable_tasks_container, "setOrientation", 1); // VERTICAL (number above label)
                views.setInt(R.id.familysync_selectable_questions_container, "setOrientation", 1); // VERTICAL (number above label)
            }
            
            // Adjust text sizes - start smaller for 3x2, scale aggressively
            float titleSize = 10f * sizeMultiplier;
            float groupNameSize = 9f * sizeMultiplier;
            float numberSize = 18f * sizeMultiplier;
            float labelSize = 9f * sizeMultiplier;
            // Much more aggressive button scaling - square the multiplier
            float buttonSize = 20f * sizeMultiplier * sizeMultiplier;
            float buttonTextSize = 10f * sizeMultiplier * sizeMultiplier;
            
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
                
                if (result != null) {
                    // Set group name
                    views.setTextViewText(R.id.familysync_selectable_group_name, result.groupName != null ? result.groupName : "Family");
                    
                    // Update counts
                    views.setTextViewText(R.id.familysync_selectable_tasks_count, String.valueOf(result.tasksCount));
                    views.setTextViewText(R.id.familysync_selectable_questions_count, String.valueOf(result.questionsCount));
                    views.setTextViewText(R.id.familysync_selectable_tasks_label, "tasks");
                    views.setTextViewText(R.id.familysync_selectable_questions_label, "questions");
                    
                    // Style counts
                    float baseNumberSize = 24f * sizeMultiplier;
                    int tasksTextColor = result.tasksCount > 0 ? 0xFFFFD700 : 0xFFFFFFFF;
                    int questionsTextColor = result.questionsCount > 0 ? 0xFFFF6B6B : 0xFFFFFFFF;
                    
                    views.setTextColor(R.id.familysync_selectable_tasks_count, tasksTextColor);
                    views.setTextColor(R.id.familysync_selectable_questions_count, questionsTextColor);
                    
                    float tasksNumberSize = result.tasksCount > 0 ? baseNumberSize * 1.1f : baseNumberSize;
                    float questionsNumberSize = result.questionsCount > 0 ? baseNumberSize * 1.1f : baseNumberSize;
                    
                    views.setTextViewTextSize(R.id.familysync_selectable_tasks_count, TypedValue.COMPLEX_UNIT_SP, tasksNumberSize);
                    views.setTextViewTextSize(R.id.familysync_selectable_questions_count, TypedValue.COMPLEX_UNIT_SP, questionsNumberSize);
                    
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

