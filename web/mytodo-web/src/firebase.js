import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';

// Firebase configuration - using the web app config from Firebase Console
const firebaseConfig = {
  apiKey: "AIzaSyBmAknTEJ8-u2JFWBg6alwkW4V8-DqDKfU",
  authDomain: "familysync-app-470820.firebaseapp.com",
  projectId: "familysync-app-470820",
  storageBucket: "familysync-app-470820.firebasestorage.app",
  messagingSenderId: "382070971886",
  appId: "1:382070971886:web:502b0b2fb084b01cf782ff"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);

// Initialize Firebase Authentication and get a reference to the service
export const auth = getAuth(app);

// Initialize Cloud Firestore and get a reference to the service
export const db = getFirestore(app);

export default app;
