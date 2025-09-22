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
import TaskList from './TaskList';
import TaskDialog from './TaskDialog';

const Dashboard = ({ taskService, user }) => {
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
    
    // Show confirmation dialog
    if (window.confirm(`Are you sure you want to delete "${taskDescription}"? This action cannot be undone.`)) {
      try {
        await taskService.deleteTask(taskId);
        showSnackbar('Task deleted successfully', 'success');
        loadTasks();
      } catch (error) {
        console.error('Error deleting task:', error);
        showSnackbar('Failed to delete task', 'error');
      }
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
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            MyToDo
          </Typography>
          
          <IconButton
            color="inherit"
            onClick={loadTasks}
            disabled={loading}
            sx={{ mr: 1 }}
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
              Sign Out
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <Container maxWidth="lg" sx={{ mt: 3, mb: 3 }}>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        <TaskList
          tasks={tasks}
          loading={loading}
          onEditTask={handleEditTask}
          onDeleteTask={handleDeleteTask}
          onToggleCompletion={handleToggleCompletion}
        />
      </Container>

      <Fab
        color="primary"
        aria-label="add task"
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
