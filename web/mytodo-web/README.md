# MyToDo Web Interface

A responsive web interface for the MyToDo Android app, allowing users to manage their tasks from any PC or web browser.

## Features

- 🔐 **Google Authentication** - Sign in with your Google account
- 📱 **Cross-Platform Sync** - Tasks sync automatically with the Android app and widget
- ✅ **Task Management** - Create, edit, complete, and delete tasks
- 📅 **Date & Time** - Set due dates and times for tasks
- 🔄 **Recurring Tasks** - Support for daily, weekly, monthly, and yearly tasks
- 🔔 **Reminders** - Set reminders for tasks
- 🏷️ **Priority Levels** - High, Medium, Low, and Normal priority
- 🔍 **Search & Filter** - Find tasks quickly with search and filtering
- 📱 **Responsive Design** - Works on desktop, tablet, and mobile
- 👥 **FamilySync Integration** - Import and sync tasks from FamilySync with bidirectional completion sync

## Setup Instructions

### 1. Firebase Configuration

1. Go to your Firebase project console
2. Add a new Web app to your project
3. Copy the Firebase configuration
4. Update `src/firebase.js` with your actual Firebase config:

```javascript
const firebaseConfig = {
  apiKey: "your-api-key",
  authDomain: "your-project.firebaseapp.com",
  projectId: "your-project-id",
  storageBucket: "your-project.appspot.com",
  messagingSenderId: "your-sender-id",
  appId: "your-app-id",
  measurementId: "your-measurement-id"
};
```

### 2. Install Dependencies

```bash
npm install
```

### 3. Start Development Server

```bash
npm run dev
```

### 4. Build for Production

```bash
npm run build
```

## Usage

1. **Sign In**: Use your Google account to sign in
2. **View Tasks**: All your tasks from the Android app will appear here
3. **Create Tasks**: Click the + button to create new tasks
4. **Edit Tasks**: Click the edit icon on any task
5. **Complete Tasks**: Check the checkbox to mark tasks as complete
6. **Delete Tasks**: Click the delete icon to remove tasks
7. **Search**: Use the search bar to find specific tasks
8. **Filter**: Filter by priority or completion status

## 👥 FamilySync Integration

MyToDo web app integrates seamlessly with FamilySync for task import and synchronization:

### Imported Tasks
- **FamilySync Indicator**: Tasks imported from FamilySync show a purple "FamilySync" chip
- **Source Tracking**: Clear visual indication of task origin
- **Automatic Sync**: Task completion automatically syncs back to FamilySync

### How It Works
1. **Export from FamilySync**: Users export tasks from FamilySync using the 📤 button
2. **Import to MyToDo**: Tasks appear in MyToDo web app with FamilySync indicator
3. **Bidirectional Sync**: Completing tasks in MyToDo automatically updates FamilySync
4. **Real-time Updates**: Changes sync across all platforms instantly

### Visual Indicators
- **Purple "FamilySync" Chip**: Shows on imported tasks
- **FamilySync Icon**: 👥 icon in the chip for easy identification
- **Hover Tooltip**: "Imported from FamilySync" tooltip on hover

### Technical Details
- **Firestore Collections**: Uses `mytodo_tasks` and `task_sync_logs` collections
- **Source Fields**: Tracks `sourceApp`, `sourceTaskId`, `sourceGroupId`
- **Sync Service**: `FamilySyncService` handles bidirectional synchronization
- **Error Handling**: Graceful handling of sync failures with user feedback

## Technical Details

- **Framework**: React 18 with Vite
- **UI Library**: Material-UI (MUI)
- **Authentication**: Firebase Auth
- **Database**: Firestore (same as Android app)
- **Date Handling**: date-fns with MUI X Date Pickers
- **Routing**: React Router

## Project Structure

```
src/
├── components/          # React components
│   ├── Dashboard.jsx    # Main dashboard
│   ├── LoginPage.jsx    # Authentication page
│   ├── TaskList.jsx     # Task list display
│   └── TaskDialog.jsx   # Task creation/editing
├── models/              # Data models
│   └── Task.js          # Task model
├── services/            # Firebase services
│   └── TaskService.js   # Task CRUD operations
└── firebase.js          # Firebase configuration
```

## Deployment

The web app can be deployed to any static hosting service:

- **Firebase Hosting**: `npm run build` then `firebase deploy`
- **Vercel**: Connect your GitHub repository
- **Netlify**: Drag and drop the `dist` folder
- **GitHub Pages**: Use GitHub Actions to build and deploy

## Troubleshooting

### Common Issues

1. **Firebase Config Error**: Make sure all Firebase config values are correct
2. **Authentication Issues**: Ensure Google Sign-In is enabled in Firebase Console
3. **No Tasks Showing**: Check if you're signed in with the same Google account as the Android app
4. **Build Errors**: Make sure all dependencies are installed with `npm install`

### Support

For issues or questions, please check the Android app's Firebase project configuration and ensure the web app is properly configured to access the same Firestore database.