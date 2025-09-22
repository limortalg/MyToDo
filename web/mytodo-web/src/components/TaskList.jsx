import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Checkbox,
  IconButton,
  Chip,
  CircularProgress,
  Alert,
  TextField,
  InputAdornment,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Grid
} from '@mui/material';
import {
  Edit as EditIcon,
  Delete as DeleteIcon,
  Search as SearchIcon,
  FilterList as FilterIcon
} from '@mui/icons-material';
import { format } from 'date-fns';

const TaskList = ({ 
  tasks, 
  loading, 
  onEditTask, 
  onDeleteTask, 
  onToggleCompletion 
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [filterPriority, setFilterPriority] = useState('all');
  const [filterStatus, setFilterStatus] = useState('all');

  const filteredTasks = tasks.filter(task => {
    const matchesSearch = task.description.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesPriority = filterPriority === 'all' || task.priority.toString() === filterPriority;
    const matchesStatus = filterStatus === 'all' || 
      (filterStatus === 'completed' && task.isCompleted) ||
      (filterStatus === 'pending' && !task.isCompleted);
    
    return matchesSearch && matchesPriority && matchesStatus;
  });

  const getPriorityChip = (priority) => {
    const priorityMap = {
      3: { label: 'High', color: 'error' },
      2: { label: 'Medium', color: 'warning' },
      1: { label: 'Low', color: 'success' },
      0: { label: 'Normal', color: 'default' }
    };
    
    const { label, color } = priorityMap[priority] || priorityMap[0];
    return <Chip label={label} color={color} size="small" />;
  };

  const formatDueDate = (dueDate, dueTime) => {
    if (!dueDate) return null;
    
    const date = new Date(dueDate);
    let formatted = format(date, 'MMM dd, yyyy');
    
    if (dueTime) {
      const hours = Math.floor(dueTime / (1000 * 60 * 60));
      const minutes = Math.floor((dueTime % (1000 * 60 * 60)) / (1000 * 60));
      formatted += ` at ${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`;
    }
    
    return formatted;
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="200px">
        <CircularProgress />
      </Box>
    );
  }

  if (tasks.length === 0) {
    return (
      <Alert severity="info">
        No tasks found. Create your first task by clicking the + button!
      </Alert>
    );
  }

  return (
    <Box>
      {/* Search and Filter Controls */}
      <Box sx={{ mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              placeholder="Search tasks..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon />
                  </InputAdornment>
                ),
              }}
            />
          </Grid>
          
          <Grid item xs={6} md={3}>
            <FormControl fullWidth>
              <InputLabel>Priority</InputLabel>
              <Select
                value={filterPriority}
                onChange={(e) => setFilterPriority(e.target.value)}
                label="Priority"
              >
                <MenuItem value="all">All Priorities</MenuItem>
                <MenuItem value="3">High</MenuItem>
                <MenuItem value="2">Medium</MenuItem>
                <MenuItem value="1">Low</MenuItem>
                <MenuItem value="0">Normal</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          
          <Grid item xs={6} md={3}>
            <FormControl fullWidth>
              <InputLabel>Status</InputLabel>
              <Select
                value={filterStatus}
                onChange={(e) => setFilterStatus(e.target.value)}
                label="Status"
              >
                <MenuItem value="all">All Tasks</MenuItem>
                <MenuItem value="pending">Pending</MenuItem>
                <MenuItem value="completed">Completed</MenuItem>
              </Select>
            </FormControl>
          </Grid>
        </Grid>
      </Box>

      {/* Task List */}
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {filteredTasks.map((task) => (
          <Card 
            key={task.id} 
            sx={{ 
              opacity: task.isCompleted ? 0.7 : 1,
              textDecoration: task.isCompleted ? 'line-through' : 'none'
            }}
          >
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
                <Checkbox
                  checked={task.isCompleted}
                  onChange={(e) => onToggleCompletion(task.id, e.target.checked)}
                  color="primary"
                />
                
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                  <Typography 
                    variant="h6" 
                    component="div" 
                    sx={{ 
                      mb: 1,
                      textDecoration: task.isCompleted ? 'line-through' : 'none'
                    }}
                  >
                    {task.description}
                  </Typography>
                  
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 1 }}>
                    {getPriorityChip(task.priority)}
                    
                    {task.dayOfWeek && (
                      <Chip label={task.dayOfWeek} variant="outlined" size="small" />
                    )}
                    
                    {task.isRecurring && (
                      <Chip label={task.recurrenceType || 'Recurring'} variant="outlined" size="small" />
                    )}
                  </Box>
                  
                  {formatDueDate(task.dueDate, task.dueTime) && (
                    <Typography variant="body2" color="text.secondary">
                      Due: {formatDueDate(task.dueDate, task.dueTime)}
                    </Typography>
                  )}
                </Box>
                
                <Box sx={{ display: 'flex', gap: 1 }}>
                  <IconButton
                    onClick={() => onEditTask(task)}
                    color="primary"
                    size="small"
                  >
                    <EditIcon />
                  </IconButton>
                  
                  <IconButton
                    onClick={() => onDeleteTask(task.id)}
                    color="error"
                    size="small"
                  >
                    <DeleteIcon />
                  </IconButton>
                </Box>
              </Box>
            </CardContent>
          </Card>
        ))}
      </Box>

      {filteredTasks.length === 0 && tasks.length > 0 && (
        <Alert severity="info">
          No tasks match your current filters.
        </Alert>
      )}
    </Box>
  );
};

export default TaskList;
