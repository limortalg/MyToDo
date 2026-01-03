package limor.tal.mytodo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;
import java.util.Calendar;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Widget provider for FamilySync - shows pending tasks and questions counts
 */
public class FamilySyncWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "FamilySyncWidget";
    private static final String ACTION_REFRESH = "limor.tal.mytodo.FAMILYSYNC_REFRESH_WIDGET";
    private static final String ACTION_SCHEDULED_UPDATE = "limor.tal.mytodo.FAMILYSYNC_SCHEDULED_UPDATE";
    private static final String FAMILYSYNC_API_URL = "https://familysync-api-382070971886.europe-west1.run.app/api/widget/counts";
    private static final String FAMILYSYNC_WEB_URL = "https://familysync-app-470820.web.app";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
        // Schedule updates at midnight and noon
        scheduleUpdates(context);
    }
    
    @Override
    public void onEnabled(Context context) {
        // Widget added for the first time - schedule updates
        scheduleUpdates(context);
    }
    
    @Override
    public void onDisabled(Context context) {
        // Last widget removed - cancel scheduled updates
        cancelScheduledUpdates(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        
        String action = intent.getAction();
        
        if (ACTION_REFRESH.equals(action) || ACTION_SCHEDULED_UPDATE.equals(action)) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName componentName = new ComponentName(context, FamilySyncWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
            
            for (int appWidgetId : appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId);
            }
            
            // Reschedule after scheduled update
            if (ACTION_SCHEDULED_UPDATE.equals(action)) {
                scheduleUpdates(context);
            }
        }
    }
    
    /**
     * Schedule widget updates at midnight and noon daily
     */
    private void scheduleUpdates(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;
            
            Intent updateIntent = new Intent(context, FamilySyncWidgetProvider.class);
            updateIntent.setAction(ACTION_SCHEDULED_UPDATE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, updateIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
            Calendar now = Calendar.getInstance();
            int currentHour = now.get(Calendar.HOUR_OF_DAY);
            
            Calendar nextUpdate = Calendar.getInstance();
            
            // Determine next update time: midnight (0) or noon (12)
            if (currentHour < 12) {
                // Before noon - schedule noon today
                nextUpdate.set(Calendar.HOUR_OF_DAY, 12);
                nextUpdate.set(Calendar.MINUTE, 0);
                nextUpdate.set(Calendar.SECOND, 0);
                nextUpdate.set(Calendar.MILLISECOND, 0);
            } else {
                // After noon - schedule midnight next day
                nextUpdate.set(Calendar.HOUR_OF_DAY, 0);
                nextUpdate.set(Calendar.MINUTE, 0);
                nextUpdate.set(Calendar.SECOND, 0);
                nextUpdate.set(Calendar.MILLISECOND, 0);
                nextUpdate.add(Calendar.DAY_OF_MONTH, 1);
            }
            
            // Make sure it's in the future
            if (nextUpdate.getTimeInMillis() <= now.getTimeInMillis()) {
                nextUpdate.add(Calendar.DAY_OF_MONTH, 1);
            }
            
            // Schedule the update
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextUpdate.getTimeInMillis(), pendingIntent);
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextUpdate.getTimeInMillis(), pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, nextUpdate.getTimeInMillis(), pendingIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling widget updates", e);
        }
    }
    
    /**
     * Cancel scheduled widget updates
     */
    private void cancelScheduledUpdates(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;
            
            Intent updateIntent = new Intent(context, FamilySyncWidgetProvider.class);
            updateIntent.setAction(ACTION_SCHEDULED_UPDATE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, updateIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
            alarmManager.cancel(pendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error canceling scheduled widget updates", e);
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.familysync_widget_layout);
            
            // Get widget size to adjust layout and text sizes
            Bundle options = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                options = appWidgetManager.getAppWidgetOptions(appWidgetId);
            }
            
            int minWidth = 70; // Default minimum
            int minHeight = 70;
            boolean isWide = false;
            float sizeMultiplier = 1.0f;
            
            if (options != null) {
                minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                // Consider widget "wide" if width is more than 1.5x height
                isWide = minWidth > minHeight * 1.5f;
                // Calculate size multiplier based on widget area
                float area = minWidth * minHeight;
                if (area > 15000) { // Large widget
                    sizeMultiplier = 1.5f;
                } else if (area > 8000) { // Medium widget
                    sizeMultiplier = 1.2f;
                }
            }
            
            // Ensure Firebase is initialized
            try {
                FirebaseApp.initializeApp(context);
            } catch (Exception e) {
                // Firebase may already be initialized, which is fine
            }
            
            // Get current user
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                // User not logged in - show login message
                views.setTextViewText(R.id.familysync_widget_title, context.getString(R.string.familysync_widget_name));
                views.setTextColor(R.id.familysync_widget_title, 0xFF03DAC5); // Cyan/Teal accent color
                views.setTextViewText(R.id.familysync_tasks_count, "?");
                views.setTextViewText(R.id.familysync_questions_count, "?");
                views.setTextColor(R.id.familysync_tasks_count, 0xFFFFFFFF);
                views.setTextColor(R.id.familysync_questions_count, 0xFFFFFFFF);
                
                // Open FamilySync web app when clicked
                Intent openAppIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(FAMILYSYNC_WEB_URL));
                openAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.familysync_widget_container, openAppPendingIntent);
                
                appWidgetManager.updateAppWidget(appWidgetId, views);
                return;
            }
            
            String userId = currentUser.getUid();
            
            // Apply size-based adjustments
            applySizeBasedStyling(context, views, appWidgetManager, appWidgetId, isWide, sizeMultiplier);
            
            // Set title with accent color
            views.setTextViewText(R.id.familysync_widget_title, context.getString(R.string.familysync_widget_name));
            views.setTextColor(R.id.familysync_widget_title, 0xFF03DAC5); // Cyan/Teal accent color
            
            // Show loading
            views.setTextViewText(R.id.familysync_tasks_count, "...");
            views.setTextViewText(R.id.familysync_questions_count, "...");
            views.setTextViewText(R.id.familysync_tasks_label, "tasks");
            views.setTextViewText(R.id.familysync_questions_label, "questions");
            
            // Set up refresh button
            Intent refreshIntent = new Intent(context, FamilySyncWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH);
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 1, refreshIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.familysync_widget_refresh, refreshPendingIntent);
            
            // Set up click to open FamilySync web app
            Intent openAppIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(FAMILYSYNC_WEB_URL));
            openAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.familysync_widget_container, openAppPendingIntent);
            
            // Update widget immediately with loading state
            appWidgetManager.updateAppWidget(appWidgetId, views);
            
            // Fetch counts from API in background
            new FetchCountsTask(context, appWidgetManager, appWidgetId, sizeMultiplier).execute(userId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget", e);
        }
    }
    
    /**
     * Apply size-based styling to widget
     */
    private void applySizeBasedStyling(Context context, RemoteViews views, AppWidgetManager appWidgetManager, 
                                       int appWidgetId, boolean isWide, float sizeMultiplier) {
        try {
            Bundle options = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                options = appWidgetManager.getAppWidgetOptions(appWidgetId);
            }
            
            if (options == null) {
                // Fallback if options not available
                return;
            }
            
            int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
            isWide = minWidth > minHeight * 1.5f;
            
            // Calculate size multiplier based on widget area - more aggressive scaling
            float area = minWidth * minHeight;
            if (area > 20000) { // Very large widget
                sizeMultiplier = 2.0f;
            } else if (area > 12000) { // Large widget
                sizeMultiplier = 1.5f;
            } else if (area > 6000) { // Medium widget
                sizeMultiplier = 1.2f;
            } else {
                sizeMultiplier = 1.0f;
            }
            
            // Adjust title size - start smaller for 1x1
            float titleSize = 8f * sizeMultiplier;
            views.setTextViewTextSize(R.id.familysync_widget_title, TypedValue.COMPLEX_UNIT_SP, titleSize);
            
            // Adjust layout orientation based on width - fix logic to use width/height ratio
            // Consider wide if width is more than 1.3x height (more sensitive)
            boolean isWideLayout = minWidth > minHeight * 1.3f;
            
            if (isWideLayout) {
                // Wide layout: both tasks and questions side by side, and number/text side by side
                views.setInt(R.id.familysync_counts_container, "setOrientation", 0); // HORIZONTAL
                views.setInt(R.id.familysync_tasks_line, "setOrientation", 0); // HORIZONTAL (number and text side by side)
                views.setInt(R.id.familysync_questions_line, "setOrientation", 0); // HORIZONTAL (number and text side by side)
            } else {
                // Tall/narrow layout: tasks and questions stacked vertically, number above text
                views.setInt(R.id.familysync_counts_container, "setOrientation", 1); // VERTICAL
                views.setInt(R.id.familysync_tasks_line, "setOrientation", 1); // VERTICAL (number above text)
                views.setInt(R.id.familysync_questions_line, "setOrientation", 1); // VERTICAL (number above text)
            }
            
            // Adjust number sizes - start smaller for 1x1, scale aggressively
            float numberSize = 14f * sizeMultiplier;
            views.setTextViewTextSize(R.id.familysync_tasks_count, TypedValue.COMPLEX_UNIT_SP, numberSize);
            views.setTextViewTextSize(R.id.familysync_questions_count, TypedValue.COMPLEX_UNIT_SP, numberSize);
            
            // Adjust label sizes - start smaller for 1x1
            float labelSize = 7f * sizeMultiplier;
            views.setTextViewTextSize(R.id.familysync_tasks_label, TypedValue.COMPLEX_UNIT_SP, labelSize);
            views.setTextViewTextSize(R.id.familysync_questions_label, TypedValue.COMPLEX_UNIT_SP, labelSize);
            
            // Scale refresh button size - much more aggressive scaling
            float buttonSize = 18f * sizeMultiplier * sizeMultiplier; // Square scaling for much bigger buttons
            float buttonTextSize = 9f * sizeMultiplier * sizeMultiplier;
            views.setViewLayoutWidth(R.id.familysync_widget_refresh, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutHeight(R.id.familysync_widget_refresh, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
            views.setTextViewTextSize(R.id.familysync_widget_refresh, TypedValue.COMPLEX_UNIT_SP, buttonTextSize);
            
        } catch (Exception e) {
            Log.e(TAG, "Error applying size-based styling", e);
        }
    }
    
    /**
     * AsyncTask to fetch counts from FamilySync API
     */
    private static class FetchCountsTask extends AsyncTask<String, Void, WidgetCounts> {
        private Context context;
        private AppWidgetManager appWidgetManager;
        private int appWidgetId;
        private float sizeMultiplier;
        
        public FetchCountsTask(Context context, AppWidgetManager appWidgetManager, int appWidgetId, float sizeMultiplier) {
            this.context = context;
            this.appWidgetManager = appWidgetManager;
            this.appWidgetId = appWidgetId;
            this.sizeMultiplier = sizeMultiplier;
        }
        
        @Override
        protected WidgetCounts doInBackground(String... params) {
            String userId = params[0];
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                // URL encode the userId parameter
                String encodedUserId = URLEncoder.encode(userId, "UTF-8");
                String urlString = FAMILYSYNC_API_URL + "?userId=" + encodedUserId;
                
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Content-Type", "application/json");
                
                int responseCode = connection.getResponseCode();
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    String responseBody = response.toString();
                    
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    int tasksCount = jsonResponse.getInt("tasksCount");
                    int questionsCount = jsonResponse.getInt("questionsCount");
                    
                    return new WidgetCounts(tasksCount, questionsCount);
                } else {
                    // Read error response
                    reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    Log.e(TAG, "API request failed with code: " + responseCode + ", response: " + errorResponse.toString());
                    return null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching counts from API: " + e.getMessage(), e);
                return null;
            } finally {
                try {
                    if (reader != null) reader.close();
                    if (connection != null) connection.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing connections", e);
                }
            }
        }
        
        @Override
        protected void onPostExecute(WidgetCounts counts) {
            try {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.familysync_widget_layout);
                
                // Set title color
                views.setTextColor(R.id.familysync_widget_title, 0xFF03DAC5); // Cyan/Teal accent color
                
                if (counts != null) {
                    // Update tasks count
                    views.setTextViewText(R.id.familysync_tasks_count, String.valueOf(counts.tasksCount));
                    
                    // Calculate base sizes using multiplier
                    float baseNumberSize = 20f * sizeMultiplier;
                    float baseLabelSize = 9f * sizeMultiplier;
                    
                    // Style tasks line differently if count > 0 - make it stand out with gold color
                    int tasksTextColor = counts.tasksCount > 0 ? 0xFFFFD700 : 0xFFFFFFFF; // Gold if > 0, white if 0
                    views.setTextColor(R.id.familysync_tasks_count, tasksTextColor);
                    // Make number bigger if non-zero, using size multiplier
                    float tasksNumberSize = counts.tasksCount > 0 ? baseNumberSize * 1.1f : baseNumberSize;
                    views.setTextViewTextSize(R.id.familysync_tasks_count, TypedValue.COMPLEX_UNIT_SP, tasksNumberSize);
                    
                    // Update questions count
                    views.setTextViewText(R.id.familysync_questions_count, String.valueOf(counts.questionsCount));
                    
                    // Update labels
                    views.setTextViewText(R.id.familysync_tasks_label, "tasks");
                    views.setTextViewText(R.id.familysync_questions_label, "questions");
                    views.setTextViewTextSize(R.id.familysync_tasks_label, TypedValue.COMPLEX_UNIT_SP, baseLabelSize);
                    views.setTextViewTextSize(R.id.familysync_questions_label, TypedValue.COMPLEX_UNIT_SP, baseLabelSize);
                    
                    // Style questions line differently if count > 0 - make it stand out with red-orange color
                    int questionsTextColor = counts.questionsCount > 0 ? 0xFFFF6B6B : 0xFFFFFFFF; // Red-orange if > 0, white if 0
                    views.setTextColor(R.id.familysync_questions_count, questionsTextColor);
                    // Make number bigger if non-zero, using size multiplier
                    float questionsNumberSize = counts.questionsCount > 0 ? baseNumberSize * 1.1f : baseNumberSize;
                    views.setTextViewTextSize(R.id.familysync_questions_count, TypedValue.COMPLEX_UNIT_SP, questionsNumberSize);
                } else {
                    // Show error - set both counts to "?"
                    views.setTextViewText(R.id.familysync_tasks_count, "?");
                    views.setTextViewText(R.id.familysync_questions_count, "?");
                    views.setTextColor(R.id.familysync_tasks_count, 0xFFFFFFFF);
                    views.setTextColor(R.id.familysync_questions_count, 0xFFFFFFFF);
                    Log.e(TAG, "Widget shows error - counts is null");
                }
                
                // Update refresh button (hidden, but needed for click handling)
                Intent refreshIntent = new Intent(context, FamilySyncWidgetProvider.class);
                refreshIntent.setAction(ACTION_REFRESH);
                PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 1, refreshIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.familysync_widget_refresh, refreshPendingIntent);
                
                // Update click to open app - clicking anywhere on widget opens FamilySync
                Intent openAppIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(FAMILYSYNC_WEB_URL));
                openAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.familysync_widget_container, openAppPendingIntent);
                
                appWidgetManager.updateAppWidget(appWidgetId, views);
            } catch (Exception e) {
                Log.e(TAG, "Error updating widget after fetch", e);
            }
        }
    }
    
    /**
     * Helper class to hold widget counts
     */
    private static class WidgetCounts {
        int tasksCount;
        int questionsCount;
        
        WidgetCounts(int tasksCount, int questionsCount) {
            this.tasksCount = tasksCount;
            this.questionsCount = questionsCount;
        }
    }
}

