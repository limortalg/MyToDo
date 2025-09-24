import React, { useState, useEffect } from 'react';
import {
  Container,
  AppBar,
  Toolbar,
  Typography,
  Box,
  Fab,
  IconButton,
  Menu,
  MenuItem,
  Alert,
  Snackbar
} from '@mui/material';
import {
  Add as AddIcon,
  AccountCircle as AccountIcon,
  Logout as LogoutIcon,
  Refresh as RefreshIcon
} from '@mui/icons-material';
import { signOut } from 'firebase/auth';
import { auth } from '../firebase';
import { TaskService } from '../services/TaskService';
import { Task } from '../models/Task';
import CategorizedTaskList from './CategorizedTaskList';
import TaskDialog from './TaskDialog';
import { useLanguage } from '../contexts/LanguageContext';

const Dashboard = ({ taskService, user }) => {
  const { t, language, toggleLanguage } = useLanguage();
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [anchorEl, setAnchorEl] = useState(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingTask, setEditingTask] = useState(null);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' });

  useEffect(() => {
    loadTasks();
  }, []);

  const loadTasks = async () => {
    try {
      setLoading(true);
      const userTasks = await taskService.getTasks();
      
      setTasks(userTasks);
    } catch (error) {
      console.error('Error loading tasks:', error);
      setError('Failed to load tasks');
    } finally {
      setLoading(false);
    }
  };

  const handleMenuOpen = (event) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleSignOut = async () => {
    try {
      await signOut(auth);
    } catch (error) {
      console.error('Sign out error:', error);
      showSnackbar('Failed to sign out', 'error');
    }
    handleMenuClose();
  };

  const handleAddTask = () => {
    setEditingTask(null);
    setDialogOpen(true);
  };

  const handleEditTask = (task) => {
    setEditingTask(task);
    setDialogOpen(true);
  };

  const handleSaveTask = async (taskData) => {
    try {
      if (editingTask) {
        await taskService.updateTask(editingTask.id, taskData);
        showSnackbar('Task updated successfully', 'success');
      } else {
        await taskService.createTask(taskData);
        showSnackbar('Task created successfully', 'success');
      }
      loadTasks();
      setDialogOpen(false);
    } catch (error) {
      console.error('Error saving task:', error);
      showSnackbar('Failed to save task', 'error');
    }
  };

  const handleDeleteTask = async (taskId) => {
    // Find the task to get its description
    const task = tasks.find(t => t.id === taskId);
    const taskDescription = task ? task.description : 'this task';
    
    console.log('Dashboard: Attempting to delete task:', taskDescription, 'with ID:', taskId);
    console.log('Dashboard: Task details:', task);
    
    // Show confirmation dialog
    if (window.confirm(`${t('Are you sure you want to delete this task?')} ${t('This action cannot be undone.')}`)) {
      try {
        console.log('Dashboard: User confirmed deletion, calling taskService.deleteTask');
        await taskService.deleteTask(taskId);
        console.log('Dashboard: Task deleted successfully, showing success message');
        showSnackbar('Task deleted successfully', 'success');
        console.log('Dashboard: Reloading tasks after deletion');
        loadTasks();
      } catch (error) {
        console.error('Dashboard: Error deleting task:', error);
        showSnackbar('Failed to delete task', 'error');
      }
    } else {
      console.log('Dashboard: User cancelled deletion');
    }
  };

  const handleToggleCompletion = async (taskId, isCompleted) => {
    try {
      await taskService.toggleTaskCompletion(taskId, isCompleted);
      showSnackbar(`Task ${isCompleted ? 'completed' : 'uncompleted'}`, 'success');
      loadTasks();
    } catch (error) {
      console.error('Error toggling task completion:', error);
      showSnackbar('Failed to update task', 'error');
    }
  };

  const showSnackbar = (message, severity) => {
    setSnackbar({ open: true, message, severity });
  };

  const handleSnackbarClose = () => {
    setSnackbar({ ...snackbar, open: false });
  };

  return (
    <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            {t('MyToDo')}
          </Typography>
          
          <IconButton
            color="inherit"
            onClick={toggleLanguage}
            sx={{ 
              mr: 1, 
              minWidth: 48, 
              fontSize: '0.875rem', 
              fontWeight: 'bold',
              border: '1px solid rgba(255,255,255,0.3)',
              borderRadius: 1
            }}
          >
            {language === 'he' ? 'EN' : 'HE'}
          </IconButton>
          
          <IconButton
            color="inherit"
            onClick={loadTasks}
            disabled={loading}
            sx={{ 
              mr: 1,
              animation: loading ? 'spin 1s linear infinite' : 'none',
              '@keyframes spin': {
                '0%': { transform: 'rotate(0deg)' },
                '100%': { transform: 'rotate(360deg)' }
              }
            }}
            title={t('Sync Now')}
          >
            <RefreshIcon />
          </IconButton>

          <IconButton
            color="inherit"
            onClick={handleMenuOpen}
          >
            <AccountIcon />
          </IconButton>

          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleMenuClose}
          >
            <MenuItem disabled>
              <Typography variant="body2">
                {user?.displayName || user?.email}
              </Typography>
            </MenuItem>
            <MenuItem onClick={handleSignOut}>
              <LogoutIcon sx={{ mr: 1 }} />
              {t('Sign Out')}
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <Container maxWidth="xl" sx={{ mb: 3, flexGrow: 1 }}>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

          <CategorizedTaskList 
            tasks={tasks}
            loading={loading}
            onEditTask={handleEditTask}
            onDeleteTask={handleDeleteTask}
            onToggleCompletion={handleToggleCompletion}
          />
      </Container>

      <Fab
        color="primary"
        aria-label={t('Add New Task')}
        sx={{ position: 'fixed', bottom: 16, right: 16 }}
        onClick={handleAddTask}
      >
        <AddIcon />
      </Fab>

      <TaskDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onSave={handleSaveTask}
        task={editingTask}
      />

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={handleSnackbarClose}
      >
        <Alert 
          onClose={handleSnackbarClose} 
          severity={snackbar.severity}
          sx={{ width: '100%' }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default Dashboard;
