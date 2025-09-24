import React, { createContext, useContext, useState, useEffect } from 'react';

const LanguageContext = createContext();

export const useLanguage = () => {
  const context = useContext(LanguageContext);
  if (!context) {
    throw new Error('useLanguage must be used within a LanguageProvider');
  }
  return context;
};

// Hebrew translations
const translations = {
  he: {
    // Navigation
    'MyToDo': 'MyToDo',
    'Sign Out': 'התנתק',
    'Sign in with Google': 'התחבר עם Google',
    
    // Task Dialog
    'Add New Task': 'הוסף משימה חדשה',
    'Edit Task': 'ערוך משימה',
    'Task Description': 'תיאור המשימה',
    'Due Date': 'תאריך יעד',
    'Due Time': 'שעת יעד',
    'When to Perform': 'מתי לבצע',
    'Recurring Task': 'משימה חוזרת',
    'Recurrence Type': 'סוג חזרה',
    'Reminder': 'תזכורת',
    'Cancel': 'בטל',
    'Save': 'שמור',
    'Add': 'הוסף',
    'Update': 'עדכן',
    
  // When to Perform options
  'Waiting': 'בהמתנה',
  'Immediate': 'מיידי',
  'Soon': 'בקרוב',
    'Sunday': 'יום ראשון',
    'Monday': 'יום שני',
    'Tuesday': 'יום שלישי',
    'Wednesday': 'יום רביעי',
    'Thursday': 'יום חמישי',
    'Friday': 'יום שישי',
    'Saturday': 'יום שבת',
    
    // Recurrence types
    'Daily': 'יומי',
    'Weekly': 'שבועי',
    'Biweekly': 'דו-שבועי',
    'Monthly': 'חודשי',
    'Yearly': 'שנתי',
    
    // Reminder options
    'No reminder': 'ללא תזכורת',
    'At due time': 'בשעת היעד',
    '15 minutes before': '15 דקות לפני',
    '30 minutes before': '30 דקות לפני',
    '60 minutes before': '60 דקות לפני',
    '1 hour before': 'שעה לפני',
    
    // Task List
    'Search tasks...': 'חפש משימות...',
    'All Tasks': 'כל המשימות',
    'Pending': 'ממתינות',
    'Completed': 'הושלמו',
    'No tasks found. Create your first task by clicking the + button!': 'לא נמצאו משימות. צור את המשימה הראשונה שלך על ידי לחיצה על כפתור ה-+!',
    'Status': 'סטטוס',
    'Recurring': 'חוזר',
    'Due': 'תאריך יעד',
    'Time': 'שעה',
    
    // Categories
    'Today': 'היום',
    'Tomorrow': 'מחר',
    
    // Messages
    'Task created successfully': 'המשימה נוצרה בהצלחה',
    'Task updated successfully': 'המשימה עודכנה בהצלחה',
    'Task deleted successfully': 'המשימה נמחקה בהצלחה',
    'Task completed': 'המשימה הושלמה',
    'Task uncompleted': 'המשימה לא הושלמה',
    'Failed to create task': 'נכשל ביצירת המשימה',
    'Failed to update task': 'נכשל בעדכון המשימה',
    'Failed to delete task': 'נכשל במחיקת המשימה',
    'Failed to load tasks': 'נכשל בטעינת המשימות',
    'Are you sure you want to delete this task?': 'האם אתה בטוח שברצונך למחוק את המשימה הזו?',
    'This action cannot be undone.': 'לא ניתן לבטל פעולה זו.',
    
    // Login
    'MyToDo Web': 'MyToDo Web',
    'Sign in to access your tasks across all devices': 'התחבר כדי לגשת למשימות שלך בכל המכשירים',
    'Failed to sign in. Please try again.': 'התחברות נכשלה. נסה שוב.',
    'Sign-in cancelled. Please try again.': 'התחברות בוטלה. נסה שוב.',
    'Popup blocked. Please allow popups and try again.': 'חלון קופץ חסום. אפשר חלונות קופצים ונסה שוב.',
    'Domain not authorized. Please contact support.': 'הדומיין לא מורשה. פנה לתמיכה.',
    
    // Loading
    'Loading...': 'טוען...',
    'Loading tasks...': 'טוען משימות...'
  },
  en: {
    // Navigation
    'MyToDo': 'MyToDo',
    'Sign Out': 'Sign Out',
    'Sign in with Google': 'Sign in with Google',
    
    // Task Dialog
    'Add New Task': 'Add New Task',
    'Edit Task': 'Edit Task',
    'Task Description': 'Task Description',
    'Due Date': 'Due Date',
    'Due Time': 'Due Time',
    'When to Perform': 'When to Perform',
    'Recurring Task': 'Recurring Task',
    'Recurrence Type': 'Recurrence Type',
    'Reminder': 'Reminder',
    'Cancel': 'Cancel',
    'Save': 'Save',
    'Add': 'Add',
    'Update': 'Update',
    
    // When to Perform options
    'Waiting': 'Waiting',
    'Immediate': 'Immediate',
    'Soon': 'Soon',
    'Sunday': 'Sunday',
    'Monday': 'Monday',
    'Tuesday': 'Tuesday',
    'Wednesday': 'Wednesday',
    'Thursday': 'Thursday',
    'Friday': 'Friday',
    'Saturday': 'Saturday',
    
    // Recurrence types
    'Daily': 'Daily',
    'Weekly': 'Weekly',
    'Biweekly': 'Biweekly',
    'Monthly': 'Monthly',
    'Yearly': 'Yearly',
    
    // Reminder options
    'No reminder': 'No reminder',
    'At due time': 'At due time',
    '15 minutes before': '15 minutes before',
    '30 minutes before': '30 minutes before',
    '60 minutes before': '60 minutes before',
    '1 hour before': '1 hour before',
    
    // Task List
    'Search tasks...': 'Search tasks...',
    'All Tasks': 'All Tasks',
    'Pending': 'Pending',
    'Completed': 'Completed',
    'No tasks found. Create your first task by clicking the + button!': 'No tasks found. Create your first task by clicking the + button!',
    'Status': 'Status',
    'Recurring': 'Recurring',
    'Due': 'Due',
    'Time': 'Time',
    
    // Categories
    'Today': 'Today',
    'Tomorrow': 'Tomorrow',
    
    // Messages
    'Task created successfully': 'Task created successfully',
    'Task updated successfully': 'Task updated successfully',
    'Task deleted successfully': 'Task deleted successfully',
    'Task completed': 'Task completed',
    'Task uncompleted': 'Task uncompleted',
    'Failed to create task': 'Failed to create task',
    'Failed to update task': 'Failed to update task',
    'Failed to delete task': 'Failed to delete task',
    'Failed to load tasks': 'Failed to load tasks',
    'Are you sure you want to delete this task?': 'Are you sure you want to delete this task?',
    'This action cannot be undone.': 'This action cannot be undone.',
    
    // Login
    'MyToDo Web': 'MyToDo Web',
    'Sign in to access your tasks across all devices': 'Sign in to access your tasks across all devices',
    'Failed to sign in. Please try again.': 'Failed to sign in. Please try again.',
    'Sign-in cancelled. Please try again.': 'Sign-in cancelled. Please try again.',
    'Popup blocked. Please allow popups and try again.': 'Popup blocked. Please allow popups and try again.',
    'Domain not authorized. Please contact support.': 'Domain not authorized. Please contact support.',
    
    // Loading
    'Loading...': 'Loading...',
    'Loading tasks...': 'Loading tasks...'
  }
};

export const LanguageProvider = ({ children }) => {
  const [language, setLanguage] = useState(() => {
    // Detect device language (same logic as Android app)
    const deviceLang = navigator.language || navigator.languages[0];
    const defaultLang = (deviceLang.startsWith('he') || deviceLang.startsWith('iw')) ? 'he' : 'en';
    
    // Check localStorage for saved preference
    const savedLang = localStorage.getItem('mytodo_language');
    return savedLang || defaultLang;
  });

  const [isRTL, setIsRTL] = useState(language === 'he');

  useEffect(() => {
    // Update RTL state when language changes
    setIsRTL(language === 'he');
    
    // Save to localStorage
    localStorage.setItem('mytodo_language', language);
    
    // Update document direction
    document.documentElement.dir = language === 'he' ? 'rtl' : 'ltr';
    document.documentElement.lang = language === 'he' ? 'he-IL' : 'en-US';
  }, [language]);

  const t = (key) => {
    return translations[language][key] || translations.en[key] || key;
  };

  const toggleLanguage = () => {
    setLanguage(prevLang => prevLang === 'he' ? 'en' : 'he');
  };

  const value = {
    language,
    isRTL,
    t,
    toggleLanguage
  };

  return (
    <LanguageContext.Provider value={value}>
      {children}
    </LanguageContext.Provider>
  );
};
