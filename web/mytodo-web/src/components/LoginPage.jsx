import React, { useState } from 'react';
import {
  Container,
  Paper,
  Box,
  Typography,
  Button,
  CircularProgress,
  Alert
} from '@mui/material';
import { Google as GoogleIcon } from '@mui/icons-material';
import { signInWithPopup, GoogleAuthProvider } from 'firebase/auth';
import { auth } from '../firebase';
import { useLanguage } from '../contexts/LanguageContext';

const LoginPage = () => {
  const { t, language, toggleLanguage } = useLanguage();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleGoogleSignIn = async () => {
    try {
      setLoading(true);
      setError('');
      
      const provider = new GoogleAuthProvider();
      await signInWithPopup(auth, provider);
    } catch (error) {
      console.error('Sign-in error:', error);
      let errorMessage = 'Failed to sign in. Please try again.';
      
      if (error.code === 'auth/unauthorized-domain') {
        errorMessage = 'Domain not authorized. Please contact support.';
      } else if (error.code === 'auth/popup-closed-by-user') {
        errorMessage = 'Sign-in cancelled. Please try again.';
      } else if (error.code === 'auth/popup-blocked') {
        errorMessage = 'Popup blocked. Please allow popups and try again.';
      } else if (error.message) {
        errorMessage = `Sign-in error: ${error.message}`;
      }
      
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container component="main" maxWidth="sm">
      <Box
        sx={{
          marginTop: 8,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
        }}
      >
        <Paper elevation={3} sx={{ padding: 4, width: '100%', textAlign: 'center' }}>
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
            <Button
              onClick={toggleLanguage}
              size="small"
              variant="outlined"
              sx={{ minWidth: 48, fontSize: '0.875rem', fontWeight: 'bold' }}
            >
              {language === 'he' ? 'EN' : 'HE'}
            </Button>
          </Box>
          
          <Typography component="h1" variant="h4" gutterBottom>
            {t('MyToDo Web')}
          </Typography>
          
          <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
            {t('Sign in to access your tasks across all devices')}
          </Typography>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          <Button
            variant="contained"
            size="large"
            startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <GoogleIcon />}
            onClick={handleGoogleSignIn}
            disabled={loading}
            sx={{
              mb: 2,
              backgroundColor: '#4285f4',
              '&:hover': {
                backgroundColor: '#3367d6',
              },
            }}
            fullWidth
          >
            {loading ? t('Loading...') : t('Sign in with Google')}
          </Button>

          <Typography variant="body2" color="text.secondary">
            {t('Your tasks will sync automatically with your mobile app')}
          </Typography>
        </Paper>
      </Box>
    </Container>
  );
};

export default LoginPage;
