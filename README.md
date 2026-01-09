# MyToDo Android App & Widget

A comprehensive task management system consisting of a native Android app and home screen widget, with seamless integration across web platforms and FamilySync.

## 📱 Components

### 1. MyToDo Android App
- **Native Android Application** with full task management capabilities
- **Firebase Integration** for cloud synchronization
- **Offline Support** with local SQLite database
- **FamilySync Integration** for task import and bidirectional sync

### 2. MyToDo Android Widget
- **Home Screen Widget** for quick task access
- **Task Completion** directly from home screen
- **Scrollable Task List** with priority indicators
- **Toggle Views** (All Tasks / Today's Tasks)
- **FamilySync Sync** for imported tasks

### 3. MyToDo Web App
- **Browser-based Interface** (see `web/mytodo-web/README.md`)
- **Cross-platform Sync** with Android app
- **FamilySync Integration** with visual indicators

## ✨ Features

### Core Task Management
- ✅ **Create, Edit, Complete, Delete** tasks
- 📅 **Due Dates & Times** with date picker
- 🔄 **Recurring Tasks** (daily, weekly, monthly, yearly)
- 🏷️ **Priority Levels** (High, Medium, Low, Normal)
- 🔍 **Search & Filter** functionality
- 📱 **Offline Support** with local database

### FamilySync Integration
- 👥 **Task Import** from FamilySync with one-click export
- 🔄 **Bidirectional Sync** of completion status
- 🎯 **Source Tracking** with visual indicators
- 📊 **Sync Logging** for debugging and audit

### Android Widget
- 🏠 **Home Screen Access** to tasks without opening app
- ✅ **Quick Completion** by tapping tasks in widget
- 🔄 **Real-time Updates** when tasks change
- 📱 **Toggle Modes** between all tasks and today's tasks
- 👥 **FamilySync Indicators** for imported tasks

## 🏗️ Architecture

### Data Layer
- **Local Database**: Room (SQLite) for offline support
- **Cloud Database**: Firestore for synchronization
- **Hybrid Approach**: Local-first with cloud sync

### Key Components

#### Android App
```
app/src/main/java/limor/tal/mytodo/
├── MainActivity.java              # Main activity
├── Task.java                      # Local task model
├── FirestoreTask.java            # Cloud task model with FamilySync fields
├── TaskRepository.java           # Data access layer
├── FirestoreService.java        # Firestore operations
├── FamilySyncService.java       # FamilySync integration
├── TaskAdapter.java             # RecyclerView adapter
└── ui/                          # UI components
    ├── TaskDialog.java          # Task creation/editing
    ├── TaskListFragment.java    # Task list display
    └── TaskViewHolder.java      # Task item view holder
```

#### Android Widgets
```
app/src/main/java/limor/tal/mytodo/
├── SimpleTaskWidgetProvider.java           # MyToDo task widget provider
├── TaskRemoteViewsFactory.java            # Widget task list factory
├── TaskWidgetService.java                 # Widget service
├── FamilySyncWidgetProvider.java          # FamilySync 1x1 widget provider
├── FamilySyncSelectableWidgetProvider.java # FamilySync selectable widget provider
└── res/layout/
    ├── widget_layout.xml                   # MyToDo task widget layout
    ├── widget_task_item.xml                # Individual task item
    ├── familysync_widget_layout.xml        # FamilySync 1x1 widget layout
    └── familysync_selectable_widget_layout.xml # FamilySync selectable widget layout
```

### FamilySync Integration

#### Data Models
- **FirestoreTask**: Extended with FamilySync tracking fields
  - `sourceApp`: "familysync"
  - `sourceTaskId`: Original FamilySync task ID
  - `sourceGroupId`: FamilySync group ID
  - `familySyncAssigneeId`: User ID in FamilySync
  - `familySyncCreatorId`: Creator ID in FamilySync

#### Services
- **FamilySyncService**: Handles bidirectional sync
  - `toggleTaskCompletionWithSync()`: Enhanced completion with sync
  - `subscribeToFamilySyncChanges()`: Real-time sync listener
  - `isExportedTask()`: Check if task is from FamilySync

#### UI Integration
- **TaskAdapter**: Shows FamilySync indicators (👥 icon)
- **Widget**: FamilySync indicators and sync on completion
- **Task List**: Visual distinction for imported tasks

## 🚀 Setup & Installation

### Prerequisites
- **Android Studio** Arctic Fox or later
- **Android SDK** API 21+ (Android 5.0+)
- **Firebase Project** with Firestore enabled
- **Google Services** configuration

### Firebase Setup

1. **Create Firebase Project**
   ```bash
   # Add to Firebase Console
   - Enable Authentication (Google Sign-In)
   - Enable Firestore Database
   - Create Android app in project settings
   ```

2. **Download Configuration**
   ```bash
   # Download google-services.json
   # Place in app/ directory
   ```

3. **Firestore Security Rules**
   ```javascript
   // Rules for MyToDo collections
   match /mytodo_tasks/{document} {
     allow read, write: if request.auth != null && 
       request.auth.uid == resource.data.userId;
     allow create: if request.auth != null && 
       request.auth.uid == request.resource.data.userId;
   }
   
   match /task_sync_logs/{document} {
     allow read, write: if request.auth != null && 
       request.auth.uid == resource.data.userId;
     allow create: if request.auth != null && 
       request.auth.uid == request.resource.data.userId;
   }
   ```

### Build & Run

1. **Clone Repository**
   ```bash
   git clone <repository-url>
   cd MyToDo
   ```

2. **Open in Android Studio**
   ```bash
   # Open Android Studio
   # Open project from MyToDo directory
   ```

3. **Sync Project**
   ```bash
   # Android Studio will prompt to sync
   # Click "Sync Now"
   ```

4. **Build & Run**
   ```bash
   # Build debug APK
   ./gradlew assembleDebug
   
   # Run on device/emulator
   # Click Run button in Android Studio
   ```

### Version Management

The app displays its version number next to the app name in the top bar. This helps ensure family members are using the same version when sharing APKs manually.

**Version Configuration:**
- Version is defined in `app/build.gradle.kts`:
  ```kotlin
  versionCode = 1        // Internal version number (increment for each release)
  versionName = "1.0"   // User-visible version string
  ```

**Important Versioning Rules:**
- ⚠️ **DO NOT advance the version unless explicitly requested by the developer**
- The version appears as "MyToDo v1.0" (or current version) in the app header
- When sharing APKs with family, check that everyone has the same version displayed
- Only increment `versionCode` and update `versionName` when the developer specifically requests it

**To Update Version (when requested):**
1. Open `app/build.gradle.kts`
2. Increment `versionCode` (e.g., 1 → 2)
3. Update `versionName` (e.g., "1.0" → "1.1" or "2.0")
4. **Update the "What's New" dialog** in `MainActivity.java`:
   - Find the `showWhatsNewDialog()` method
   - Add a new section for the new version at the top of the changelog
   - **Include the release date** in the format: `v1.X - Latest (Month Day, Year)`
   - List the new features, fixes, and improvements for this version
   - Move previous versions down in the changelog (keep their dates)
   - For older versions, you can use month/year format: `v1.X (Month Year)`
5. Rebuild the APK
6. The new version will automatically appear in the app header
7. Users can click on the version number to see the changelog with dates

## 📱 Widget Setup

### MyToDo Task Widget

#### Adding Widget to Home Screen

1. **Long Press** on home screen
2. **Select "Widgets"** from menu
3. **Find "MyToDo"** in widget list
4. **Drag to Home Screen** and resize as needed

#### Widget Features

- **Task List**: Scrollable list of incomplete tasks
- **Toggle Buttons**: Switch between "All Tasks" and "Today's Tasks"
- **Add Task**: Quick access to create new tasks
- **Refresh**: Manual refresh of task list
- **FamilySync Indicators**: 👥 icon for imported tasks

### FamilySync Widgets

The app includes two FamilySync widgets that display pending task and question counts from your families:

#### 1. FamilySync 1x1 Widget (Combined Counts)

- **Size**: 1x1 (70dp minimum, fixed size)
- **Display**: Shows combined counts from all your families
  - Number of pending tasks assigned to you
  - Number of pending questions assigned to you
- **Features**:
  - Adaptive text sizing based on widget dimensions
  - Layout switches to horizontal (side-by-side) when widget is wider than tall
  - Color-coded counts: Gold for tasks > 0, Red-orange for questions > 0
  - Refresh button (⟳) for manual updates
  - Tapping widget opens FamilySync web app
- **Updates**: Automatic updates at midnight and noon, plus manual refresh

#### 2. FamilySync Selectable Widget (Family-Specific)

- **Size**: 3x2 default (180dp × 110dp), fully resizable horizontally and vertically
- **Display**: Shows counts for a selected family
  - Family name at the top
  - Number of pending tasks assigned to you in that family
  - Number of pending questions assigned to you in that family
- **Features**:
  - **Family Selection**: Tap the switch button (⇄) to cycle through your families
  - **Adaptive Layout**: 
    - Horizontal layout (side-by-side) when widget is wide
    - Vertical layout (stacked) when widget is narrow (1x1 or tall)
  - **Dynamic Sizing**: Text, numbers, and buttons scale based on widget size
  - **Smart Text**: Title hides automatically when widget height is too small
  - Color-coded counts: Gold for tasks > 0, Red-orange for questions > 0
  - Refresh button (⟳) for manual updates
  - Tapping widget opens FamilySync web app
- **Updates**: Automatic updates at midnight and noon, plus manual refresh

#### Adding FamilySync Widgets to Home Screen

1. **Long Press** on home screen
2. **Select "Widgets"** from menu
3. **Find "FamilySync"** in widget list (two widget options available)
4. **Select desired widget** (1x1 or Selectable)
5. **Drag to Home Screen** and resize (Selectable widget only)

#### Widget Requirements

- **User Authentication**: You must be signed in to the MyToDo app with Firebase Auth
- **FamilySync Account**: You must be a member of at least one family group in FamilySync
- **Internet Connection**: Required for fetching counts from FamilySync API

#### Widget Permissions

The widgets require:
- **Internet Permission**: For API calls to FamilySync backend
- **Network State**: To detect connectivity changes

## 🔄 FamilySync Integration Usage

### Exporting Tasks from FamilySync

1. **Open FamilySync** web app
2. **Find Assigned Task** with 📤 export button
3. **Click Export Button** to send to MyToDo
4. **Task Appears** in MyToDo with FamilySync indicator

### Completing Imported Tasks

1. **Complete in Android App**: Check task in main app
2. **Complete in Widget**: Tap task in home screen widget
3. **Complete in Web App**: Check task in browser interface
4. **Automatic Sync**: Completion syncs back to FamilySync

### Visual Indicators

- **Android App**: 👥 icon next to imported tasks
- **Widget**: 👥 icon in task list items
- **Web App**: Purple "FamilySync" chip
- **FamilySync**: "✓ MyToDo" indicator for exported tasks

## 🛠️ Development

### Project Structure
```
MyToDo/
├── app/                          # Android app module
│   ├── src/main/
│   │   ├── java/limor/tal/mytodo/
│   │   │   ├── MainActivity.java
│   │   │   ├── Task.java
│   │   │   ├── FirestoreTask.java
│   │   │   ├── TaskRepository.java
│   │   │   ├── FirestoreService.java
│   │   │   ├── FamilySyncService.java
│   │   │   ├── TaskAdapter.java
│   │   │   ├── SimpleTaskWidgetProvider.java
│   │   │   ├── TaskRemoteViewsFactory.java
│   │   │   ├── TaskWidgetService.java
│   │   │   └── ui/
│   │   └── res/
│   │       ├── layout/
│   │       │   ├── activity_main.xml
│   │       │   ├── item_task.xml
│   │       │   ├── widget_layout.xml
│   │       │   └── widget_task_item.xml
│   │       ├── values/
│   │       │   ├── strings.xml
│   │       │   └── colors.xml
│   │       └── xml/
│   │           └── widget_info.xml
│   └── build.gradle
├── web/                          # Web app (separate project)
│   └── mytodo-web/
├── build.gradle                  # Project build file
├── settings.gradle              # Project settings
└── README.md                    # This file
```

### Key Dependencies
```gradle
// Firebase
implementation 'com.google.firebase:firebase-firestore:24.0.0'
implementation 'com.google.firebase:firebase-auth:21.0.0'
implementation 'com.google.firebase:firebase-analytics:21.0.0'

// Room Database
implementation 'androidx.room:room-runtime:2.4.0'
implementation 'androidx.room:room-compiler:2.4.0'

// UI Components
implementation 'androidx.recyclerview:recyclerview:1.2.1'
implementation 'com.google.android.material:material:1.6.0'
```

### Building & Testing

1. **Debug Build**
   ```bash
   ./gradlew assembleDebug
   ```

2. **Release Build**
   ```bash
   ./gradlew assembleRelease
   ```

3. **Run Tests**
   ```bash
   ./gradlew test
   ```

4. **Lint Check**
   ```bash
   ./gradlew lint
   ```

## 🔄 Sync System & Data Management

### Soft Deletion System
MyToDo uses a **soft deletion** system to prevent sync conflicts and data loss:

- **How it works**: Tasks are marked with `deletedAt` timestamp instead of being permanently removed
- **Benefits**: Prevents race conditions when multiple devices sync simultaneously
- **UI filtering**: Tasks with `deletedAt` set are automatically hidden from the interface
- **Cloud sync**: Soft deletions are synced to Firestore to prevent re-downloading deleted tasks

### Conflict Resolution
The app uses **timestamp-based conflict resolution** to handle sync conflicts:

- **Primary method**: Uses `updatedAt` timestamp to determine which version is newer
- **Prevents data loss**: Completed tasks won't be overwritten by older cloud versions
- **Smart merging**: Local changes are preserved when they're more recent than cloud changes
- **Fallback logic**: When timestamps are equal, local version is preferred to avoid overwriting recent changes

### Database Schema
The app uses a hybrid local-cloud architecture:

- **Local storage**: Room (SQLite) database for offline support
- **Cloud storage**: Firestore for synchronization across devices
- **Key fields**: `deletedAt`, `updatedAt`, `firestoreDocumentId` for sync management
- **Migration support**: Automatic database schema updates with version management

## 🐛 Troubleshooting

### Common Issues

1. **Widget Not Showing Tasks**
   - Check Firestore permissions
   - Verify internet connectivity
   - Restart widget (remove and re-add)

2. **FamilySync Sync Not Working**
   - Verify Firestore security rules
   - Check FamilySync export status
   - Review sync logs in Firestore

3. **App Crashes on Startup**
   - Check google-services.json configuration
   - Verify Firebase project settings
   - Review Android logs for errors

4. **Tasks Not Syncing**
   - Check internet connectivity
   - Verify Firebase authentication
   - Review Firestore security rules

### Sync-Related Issues

5. **Tasks Reappear After Deletion**
   - **Cause**: Race condition between devices during sync
   - **Solution**: Soft deletion system prevents this - deleted tasks are marked with `deletedAt` timestamp
   - **Check**: Verify `deletedAt` field is set in Firestore for deleted tasks

6. **Duplicate Tasks After Sync**
   - **Cause**: Improper conflict resolution during merge operations
   - **Solution**: Improved merge logic using `updatedAt` timestamps
   - **Check**: Review sync logs for duplicate task creation

7. **Completed Tasks Reset to Incomplete**
   - **Cause**: Cloud version overwriting local completion status
   - **Solution**: Timestamp-based conflict resolution preserves newer changes
   - **Check**: Verify `updatedAt` timestamps are being set correctly

8. **Tasks Not Syncing Between Devices**
   - **Cause**: Authentication or network issues
   - **Solution**: Check Firebase authentication status and internet connectivity
   - **Check**: Review Firestore security rules and user permissions

### Debug Information

- **Logcat Tags**: `MyToDo`, `FirestoreService`, `FamilySyncService`, `SimpleTaskWidget`, `FamilySyncWidget`, `FamilySyncSelectableWidget`
- **Firestore Console**: Check `task_sync_logs` collection for sync operations
- **Android Studio**: Use Firebase Assistant for configuration validation

## 📊 Performance

### Optimization Features
- **Local-First Architecture**: Offline support with background sync
- **Efficient Queries**: Optimized Firestore queries with indexes
- **Widget Optimization**: Minimal memory usage for home screen widget
- **Background Processing**: Sync operations on background threads

### Resource Usage
- **App Size**: ~15MB APK
- **Memory Usage**: ~50MB typical usage
- **Battery Impact**: Minimal with optimized sync frequency
- **Network Usage**: Efficient with change-based syncing

## 🔒 Security

### Data Protection
- **Firebase Authentication**: Google Sign-In integration
- **Firestore Security Rules**: User-specific data access
- **Local Encryption**: Room database with encryption support
- **Network Security**: HTTPS for all communications

### Privacy
- **User Data**: Stored securely in Firebase
- **Local Storage**: Encrypted SQLite database
- **No Third-Party Tracking**: Privacy-focused design
- **Data Ownership**: Users control their data

## 🚀 Future Enhancements

### Planned Features
- [ ] **Offline-First Sync**: Enhanced offline capabilities
- [ ] **Task Categories**: Organize tasks by categories
- [ ] **Recurring Task Templates**: Predefined recurring patterns
- [ ] **Task Dependencies**: Link related tasks
- [ ] **Time Tracking**: Track time spent on tasks
- [ ] **Team Collaboration**: Share tasks with family members
- [x] **FamilySync Widgets**: Two widget types for displaying pending task/question counts (1x1 combined and resizable selectable)
- [ ] **Voice Commands**: Voice-based task creation
- [ ] **Smart Notifications**: AI-powered reminder suggestions
- [ ] **Analytics Dashboard**: Task completion insights

### Integration Improvements
- [ ] **Calendar Integration**: Sync with Google Calendar
- [ ] **Email Integration**: Create tasks from emails
- [ ] **SMS Integration**: Task creation via SMS
- [ ] **Smart Home Integration**: IoT device task triggers
- [ ] **Wear OS Support**: Smartwatch companion app

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 🆘 Support

### Getting Help
- **GitHub Issues**: Report bugs and request features
- **Documentation**: Check README files for setup guides
- **Firebase Console**: Monitor app performance and errors
- **Android Studio**: Use debugging tools for development

### Community
- **Discussions**: GitHub Discussions for questions
- **Wiki**: Community-maintained documentation
- **Examples**: Code examples and tutorials

---

**MyToDo** - Your personal task management companion across all devices! 📱✨




