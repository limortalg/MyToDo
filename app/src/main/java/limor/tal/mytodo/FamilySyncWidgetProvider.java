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
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, 
                                         int appWidgetId, Bundle newOptions) {
        // Widget was resized - update it with new layout
        updateWidget(context, appWidgetManager, appWidgetId);
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
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
            float sizeMultiplier = 1.0f; // Default - will be recalculated in applySizeBasedStyling and onPostExecute
            
            if (options != null) {
                minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                // Consider widget "wide" if width is more than 1.5x height
                isWide = minWidth > minHeight * 1.5f;
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
            
            // Set title with accent color first (before applying styling)
            views.setTextViewText(R.id.familysync_widget_title, context.getString(R.string.familysync_widget_name));
            views.setTextColor(R.id.familysync_widget_title, 0xFF03DAC5); // Cyan/Teal accent color
            
            // Show loading state immediately
            views.setTextViewText(R.id.familysync_tasks_count, "...");
            views.setTextViewText(R.id.familysync_questions_count, "...");
            views.setTextViewText(R.id.familysync_tasks_label, "tasks");
            views.setTextViewText(R.id.familysync_questions_label, "questions");
            
            // Update widget immediately with loading state before applying styling
            appWidgetManager.updateAppWidget(appWidgetId, views);
            
            // Apply size-based adjustments only if widget is larger than 1x1
            // For 1x1, keep XML defaults - don't modify anything
            try {
                Bundle checkOptions = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    checkOptions = appWidgetManager.getAppWidgetOptions(appWidgetId);
                }
                if (checkOptions != null) {
                    int checkHeight = checkOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                    // Only apply styling if height > 70dp (larger than 1x1)
                    if (checkHeight > 70) {
                        applySizeBasedStyling(context, views, appWidgetManager, appWidgetId, isWide, sizeMultiplier);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error applying size-based styling", e);
                // Continue even if styling fails - widget already shows loading state
            }
            
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
            // Pass 0.0f as multiplier - FetchCountsTask will recalculate from widget dimensions
            new FetchCountsTask(context, appWidgetManager, appWidgetId, 0.0f).execute(userId);
            
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
            
            // Determine if widget is wider than 1 cell (70dp) or taller than 1 cell (70dp)
            boolean isWiderThanOne = minWidth > 70;
            boolean isTallerThanOne = minHeight > 70;
            boolean heightIsTwo = minHeight > 70 && minHeight <= 150; // Height is 2 cells (between 70 and 150dp)
            boolean isLargerThan2x2 = minWidth > 150 && minHeight > 150; // Larger than 2x2 (both > 150dp)
            
            // Calculate multipliers for different elements
            // Text/numbers should grow when height is 2, and even more when larger than 2x2
            float textMultiplier = 1.0f;
            if (isLargerThan2x2) {
                // Larger than 2x2 - text should be even larger (at least twice as big, and can grow more)
                float area = minWidth * minHeight;
                if (area > 80000) { // Very large (3x3+)
                    textMultiplier = 3.0f;
                } else if (area > 60000) { // Large (3x2 or 2x3)
                    textMultiplier = 2.5f;
                } else {
                    textMultiplier = 2.0f; // Larger than 2x2 - at least twice as big
                }
            } else if (heightIsTwo) {
                // Height is 2 cells (1x2, 2x2) - make it larger than 1x1 but not huge
                textMultiplier = 1.5f; // 50% larger when height is 2
            } else {
                // Height is 1 (1x1, 2x1) - DO NOT modify text sizes, keep XML defaults
                // Only adjust button size slightly if needed
                float buttonHeightFactor = isTallerThanOne ? Math.min((minHeight - 70) / 70f * 0.4f, 0.6f) : 0f;
                float buttonMultiplier = 1.0f + buttonHeightFactor;
                float buttonSize = 24f * buttonMultiplier;
                float buttonTextSize = 12f * buttonMultiplier;
                views.setViewLayoutWidth(R.id.familysync_widget_refresh, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
                views.setViewLayoutHeight(R.id.familysync_widget_refresh, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
                views.setTextViewTextSize(R.id.familysync_widget_refresh, TypedValue.COMPLEX_UNIT_SP, buttonTextSize);
                return; // Don't modify text sizes for 1x1 - keep XML defaults
            }
            
            // Button grows with both width and height, but more with height
            float buttonWidthFactor = isWiderThanOne ? Math.min((minWidth - 70) / 70f * 0.2f, 0.3f) : 0f; // Max 30% from width
            float buttonHeightFactor = isTallerThanOne ? Math.min((minHeight - 70) / 70f * 0.4f, 0.6f) : 0f; // Max 60% from height
            float buttonMultiplier = 1.0f + buttonWidthFactor + buttonHeightFactor;
            
            // Adjust title size - headline grows with text multiplier
            float titleSize = 6f * textMultiplier;
            views.setTextViewTextSize(R.id.familysync_widget_title, TypedValue.COMPLEX_UNIT_SP, titleSize);
            
            // Adjust number sizes - scale with text multiplier
            float numberSize = 12f * textMultiplier;
            views.setTextViewTextSize(R.id.familysync_tasks_count, TypedValue.COMPLEX_UNIT_SP, numberSize);
            views.setTextViewTextSize(R.id.familysync_questions_count, TypedValue.COMPLEX_UNIT_SP, numberSize);
            
            // Adjust label sizes - scale with text multiplier
            float labelSize = 7f * textMultiplier;
            views.setTextViewTextSize(R.id.familysync_tasks_label, TypedValue.COMPLEX_UNIT_SP, labelSize);
            views.setTextViewTextSize(R.id.familysync_questions_label, TypedValue.COMPLEX_UNIT_SP, labelSize);
            
            // Adjust spacing between numbers and labels - scales with text multiplier
            // Base spacing is 2dp for 1x1, scales proportionally with text size
            try {
                float spacingWidth = 2f * textMultiplier; // Base 2dp, scales with text multiplier
                views.setViewLayoutWidth(R.id.familysync_tasks_spacer, (int)(spacingWidth), TypedValue.COMPLEX_UNIT_DIP);
                views.setViewLayoutWidth(R.id.familysync_questions_spacer, (int)(spacingWidth), TypedValue.COMPLEX_UNIT_DIP);
            } catch (Exception e) {
                // If dynamic spacing fails, the XML default (2dp) will be used
                Log.e(TAG, "Could not set dynamic spacing", e);
            }
            
            // Scale refresh button size - grows with both width and height (more with height)
            float buttonSize = 24f * buttonMultiplier;
            float buttonTextSize = 12f * buttonMultiplier;
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
                
                // Re-apply size-based styling to ensure layout and sizes are correct
                Bundle options = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    options = appWidgetManager.getAppWidgetOptions(appWidgetId);
                }
                
                if (options != null) {
                    int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                    int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                    boolean isWiderThanOne = minWidth > 70;
                    boolean isTallerThanOne = minHeight > 70;
                    boolean heightIsTwo = minHeight > 70 && minHeight <= 150; // Height is 2 cells
                    boolean isLargerThan2x2 = minWidth > 150 && minHeight > 150; // Larger than 2x2
                    
                    // Calculate text multiplier based on height
                    // Sizes should be larger when height is 2, and even larger when larger than 2x2
                    float currentTextMultiplier = 1.0f;
                    if (isLargerThan2x2) {
                        // Larger than 2x2 - text should be even larger (at least twice as big, and can grow more)
                        float area = minWidth * minHeight;
                        if (area > 80000) { // Very large (3x3+)
                            currentTextMultiplier = 3.0f;
                        } else if (area > 60000) { // Large (3x2 or 2x3)
                            currentTextMultiplier = 2.5f;
                        } else {
                            currentTextMultiplier = 2.0f; // Larger than 2x2 - at least twice as big
                        }
                    } else if (heightIsTwo) {
                        // Height is 2 cells (1x2, 2x2) - make it larger than 1x1 but not huge
                        currentTextMultiplier = 1.5f; // 50% larger when height is 2
                    } else {
                        // Height is 1 (1x1, 2x1) - DO NOT modify text sizes, keep XML defaults
                        // Only adjust button size slightly if needed
                        float buttonHeightFactor = isTallerThanOne ? Math.min((minHeight - 70) / 70f * 0.4f, 0.6f) : 0f;
                        float currentButtonMultiplier = 1.0f + buttonHeightFactor;
                        float buttonSize = 24f * currentButtonMultiplier;
                        float buttonTextSize = 12f * currentButtonMultiplier;
                        views.setViewLayoutWidth(R.id.familysync_widget_refresh, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
                        views.setViewLayoutHeight(R.id.familysync_widget_refresh, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
                        views.setTextViewTextSize(R.id.familysync_widget_refresh, TypedValue.COMPLEX_UNIT_SP, buttonTextSize);
                        // Don't modify text sizes - keep XML defaults for 1x1
                    }
                    
                    if (currentTextMultiplier > 1.0f) {
                        // Only apply text sizing if multiplier is > 1.0 (not 1x1)
                        // Calculate button multiplier - grows with both width and height (more with height)
                        float buttonWidthFactor = isWiderThanOne ? Math.min((minWidth - 70) / 70f * 0.2f, 0.3f) : 0f;
                        float buttonHeightFactor = isTallerThanOne ? Math.min((minHeight - 70) / 70f * 0.4f, 0.6f) : 0f;
                        float currentButtonMultiplier = 1.0f + buttonWidthFactor + buttonHeightFactor;
                        
                        float effectiveTextMultiplier = currentTextMultiplier;
                        float effectiveButtonMultiplier = currentButtonMultiplier;
                        
                        float titleSize = 6f * effectiveTextMultiplier;
                        float numberSize = 12f * effectiveTextMultiplier;
                        float labelSize = 7f * effectiveTextMultiplier;
                        float buttonSize = 24f * effectiveButtonMultiplier;
                        float buttonTextSize = 12f * effectiveButtonMultiplier;
                        
                        views.setTextViewTextSize(R.id.familysync_widget_title, TypedValue.COMPLEX_UNIT_SP, titleSize);
                        views.setTextViewTextSize(R.id.familysync_tasks_count, TypedValue.COMPLEX_UNIT_SP, numberSize);
                        views.setTextViewTextSize(R.id.familysync_questions_count, TypedValue.COMPLEX_UNIT_SP, numberSize);
                        views.setTextViewTextSize(R.id.familysync_tasks_label, TypedValue.COMPLEX_UNIT_SP, labelSize);
                        views.setTextViewTextSize(R.id.familysync_questions_label, TypedValue.COMPLEX_UNIT_SP, labelSize);
                        
                        // Adjust spacing between numbers and labels - scales with text multiplier
                        try {
                            float spacingWidth = 2f * effectiveTextMultiplier; // Base 2dp, scales with text multiplier
                            views.setViewLayoutWidth(R.id.familysync_tasks_spacer, (int)(spacingWidth), TypedValue.COMPLEX_UNIT_DIP);
                            views.setViewLayoutWidth(R.id.familysync_questions_spacer, (int)(spacingWidth), TypedValue.COMPLEX_UNIT_DIP);
                        } catch (Exception e) {
                            // If dynamic spacing fails, the XML default (2dp) will be used
                        }
                        
                        views.setViewLayoutWidth(R.id.familysync_widget_refresh, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
                        views.setViewLayoutHeight(R.id.familysync_widget_refresh, (int)(buttonSize), TypedValue.COMPLEX_UNIT_DIP);
                        views.setTextViewTextSize(R.id.familysync_widget_refresh, TypedValue.COMPLEX_UNIT_SP, buttonTextSize);
                    }
                }
                
                // Set title color
                views.setTextColor(R.id.familysync_widget_title, 0xFF03DAC5); // Cyan/Teal accent color
                
                if (counts != null) {
                    // Get the effective multiplier for styling counts (only if > 1x1)
                    float effectiveTextMultiplier = 1.0f;
                    if (options != null) {
                        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                        boolean heightIsTwo = minHeight > 70 && minHeight <= 150;
                        boolean isLargerThan2x2 = minWidth > 150 && minHeight > 150;
                        
                        if (isLargerThan2x2) {
                            float area = minWidth * minHeight;
                            if (area > 80000) {
                                effectiveTextMultiplier = 3.0f;
                            } else if (area > 60000) {
                                effectiveTextMultiplier = 2.5f;
                            } else {
                                effectiveTextMultiplier = 2.0f;
                            }
                        } else if (heightIsTwo) {
                            effectiveTextMultiplier = 1.5f;
                        }
                        // else keep 1.0f for 1x1 - don't modify sizes
                    }
                    
                    // Update tasks count
                    views.setTextViewText(R.id.familysync_tasks_count, String.valueOf(counts.tasksCount));
                    
                    // Calculate base sizes for styling (use multiplier, but only apply if > 1x1)
                    float baseNumberSize = 12f * effectiveTextMultiplier;
                    float baseLabelSize = 7f * effectiveTextMultiplier;
                    
                    // Style tasks line differently if count > 0 - make it stand out with gold color
                    int tasksTextColor = counts.tasksCount > 0 ? 0xFFFFD700 : 0xFFFFFFFF; // Gold if > 0, white if 0
                    views.setTextColor(R.id.familysync_tasks_count, tasksTextColor);
                    
                    // Update questions count
                    views.setTextViewText(R.id.familysync_questions_count, String.valueOf(counts.questionsCount));
                    
                    // Update labels
                    views.setTextViewText(R.id.familysync_tasks_label, "tasks");
                    views.setTextViewText(R.id.familysync_questions_label, "questions");
                    
                    // Style questions line differently if count > 0 - make it stand out with red-orange color
                    int questionsTextColor = counts.questionsCount > 0 ? 0xFFFF6B6B : 0xFFFFFFFF; // Red-orange if > 0, white if 0
                    views.setTextColor(R.id.familysync_questions_count, questionsTextColor);
                    
                    // Only apply size modifications if multiplier > 1.0 (not 1x1)
                    // For 1x1, XML defaults apply (12sp numbers, 7sp labels) - don't override at all
                    if (effectiveTextMultiplier > 1.0f) {
                        // Apply base sizes
                        views.setTextViewTextSize(R.id.familysync_tasks_count, TypedValue.COMPLEX_UNIT_SP, baseNumberSize);
                        views.setTextViewTextSize(R.id.familysync_questions_count, TypedValue.COMPLEX_UNIT_SP, baseNumberSize);
                        views.setTextViewTextSize(R.id.familysync_tasks_label, TypedValue.COMPLEX_UNIT_SP, baseLabelSize);
                        views.setTextViewTextSize(R.id.familysync_questions_label, TypedValue.COMPLEX_UNIT_SP, baseLabelSize);
                        
                        // Make numbers bigger if non-zero (only for sizes > 1x1)
                        if (counts.tasksCount > 0) {
                            float tasksNumberSize = baseNumberSize * 1.1f;
                            views.setTextViewTextSize(R.id.familysync_tasks_count, TypedValue.COMPLEX_UNIT_SP, tasksNumberSize);
                        }
                        if (counts.questionsCount > 0) {
                            float questionsNumberSize = baseNumberSize * 1.1f;
                            views.setTextViewTextSize(R.id.familysync_questions_count, TypedValue.COMPLEX_UNIT_SP, questionsNumberSize);
                        }
                    }
                    // For 1x1 (effectiveTextMultiplier == 1.0f), do NOT modify any text sizes - keep XML defaults
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

