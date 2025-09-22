import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Box from '@mui/material/Box';
import { onAuthStateChanged } from 'firebase/auth';
import { auth } from './firebase';
import { TaskService } from './services/TaskService';
import { db } from './firebase';
import { LanguageProvider } from './contexts/LanguageContext';

// Components
import LoginPage from './components/LoginPage';
import Dashboard from './components/Dashboard';
import LoadingSpinner from './components/LoadingSpinner';

// Create theme
const theme = createTheme({
  palette: {
    primary: {
      main: '#2196f3',
    },
    secondary: {
      main: '#ff9800',
    },
    background: {
      default: '#f5f5f5',
    },
  },
  typography: {
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
  },
});

function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [taskService] = useState(new TaskService(db));

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (user) => {
      setUser(user);
      setLoading(false);
    });

    return () => unsubscribe();
  }, []);

  if (loading) {
    return <LoadingSpinner />;
  }

  return (
    <LanguageProvider>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
          <Router>
            <Routes>
              <Route
                path="/login"
                element={user ? <Navigate to="/" /> : <LoginPage />}
              />
              <Route
                path="/"
                element={user ? <Dashboard taskService={taskService} user={user} /> : <Navigate to="/login" />}
              />
            </Routes>
          </Router>
        </Box>
      </ThemeProvider>
    </LanguageProvider>
  );
}

export default App;
