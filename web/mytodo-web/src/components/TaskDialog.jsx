import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  FormControlLabel,
  Checkbox,
  Grid,
  Box,
  Typography
} from '@mui/material';
import { DatePicker, TimePicker } from '@mui/x-date-pickers';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { Task } from '../models/Task';

const TaskDialog = ({ open, onClose, onSave, task }) => {
  const [formData, setFormData] = useState({
    description: '',
    dueDate: null,
    dueTime: null,
    dayOfWeek: '',
    isRecurring: false,
    recurrenceType: '',
    priority: 0,
    reminderOffset: null,
    reminderDays: null
  });

  const [errors, setErrors] = useState({});

  const weekDays = [
    'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'
  ];

  const recurrenceTypes = [
    'Daily', 'Weekly', 'Monthly', 'Yearly'
  ];

  const reminderOffsets = [
    { value: null, label: 'No reminder' },
    { value: 0, label: 'At due time' },
    { value: 15, label: '15 minutes before' },
    { value: 30, label: '30 minutes before' },
    { value: 60, label: '1 hour before' }
  ];

  useEffect(() => {
    if (task) {
      setFormData({
        description: task.description || '',
        dueDate: task.dueDate ? new Date(task.dueDate) : null,
        dueTime: task.dueTime ? new Date(task.dueTime) : null,
        dayOfWeek: task.dayOfWeek || '',
        isRecurring: task.isRecurring || false,
        recurrenceType: task.recurrenceType || '',
        priority: task.priority || 0,
        reminderOffset: task.reminderOffset || null,
        reminderDays: task.reminderDays || null
      });
    } else {
      setFormData({
        description: '',
        dueDate: null,
        dueTime: null,
        dayOfWeek: '',
        isRecurring: false,
        recurrenceType: '',
        priority: 0,
        reminderOffset: null,
        reminderDays: null
      });
    }
    setErrors({});
  }, [task, open]);

  const handleChange = (field) => (event) => {
    const value = event.target.value;
    setFormData(prev => ({
      ...prev,
      [field]: value
    }));
    
    // Clear error when user starts typing
    if (errors[field]) {
      setErrors(prev => ({
        ...prev,
        [field]: null
      }));
    }
  };

  const handleCheckboxChange = (field) => (event) => {
    setFormData(prev => ({
      ...prev,
      [field]: event.target.checked
    }));
  };

  const handleDateChange = (field) => (date) => {
    setFormData(prev => ({
      ...prev,
      [field]: date
    }));
  };

  const validateForm = () => {
    const newErrors = {};
    
    if (!formData.description.trim()) {
      newErrors.description = 'Description is required';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSave = () => {
    if (!validateForm()) {
      return;
    }

    const taskData = {
      description: formData.description.trim(),
      dueDate: formData.dueDate ? formData.dueDate.getTime() : null,
      dueTime: formData.dueTime ? formData.dueTime.getTime() : null,
      dayOfWeek: formData.dayOfWeek || null,
      isRecurring: formData.isRecurring,
      recurrenceType: formData.isRecurring ? formData.recurrenceType : null,
      priority: parseInt(formData.priority),
      reminderOffset: formData.reminderOffset,
      reminderDays: formData.reminderDays
    };

    onSave(taskData);
  };

  const handleClose = () => {
    setErrors({});
    onClose();
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDateFns}>
      <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
        <DialogTitle>
          {task ? 'Edit Task' : 'Create New Task'}
        </DialogTitle>
        
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="Task Description"
                  value={formData.description}
                  onChange={handleChange('description')}
                  error={!!errors.description}
                  helperText={errors.description}
                  multiline
                  rows={3}
                />
              </Grid>

              <Grid item xs={12} md={6}>
                <DatePicker
                  label="Due Date"
                  value={formData.dueDate}
                  onChange={handleDateChange('dueDate')}
                  renderInput={(params) => <TextField {...params} fullWidth />}
                />
              </Grid>

              <Grid item xs={12} md={6}>
                <TimePicker
                  label="Due Time"
                  value={formData.dueTime}
                  onChange={handleDateChange('dueTime')}
                  renderInput={(params) => <TextField {...params} fullWidth />}
                />
              </Grid>

              <Grid item xs={12} md={6}>
                <FormControl fullWidth>
                  <InputLabel>Day of Week</InputLabel>
                  <Select
                    value={formData.dayOfWeek}
                    onChange={handleChange('dayOfWeek')}
                    label="Day of Week"
                  >
                    <MenuItem value="">None</MenuItem>
                    {weekDays.map((day) => (
                      <MenuItem key={day} value={day}>
                        {day}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>

              <Grid item xs={12} md={6}>
                <FormControl fullWidth>
                  <InputLabel>Priority</InputLabel>
                  <Select
                    value={formData.priority}
                    onChange={handleChange('priority')}
                    label="Priority"
                  >
                    <MenuItem value={0}>Normal</MenuItem>
                    <MenuItem value={1}>Low</MenuItem>
                    <MenuItem value={2}>Medium</MenuItem>
                    <MenuItem value={3}>High</MenuItem>
                  </Select>
                </FormControl>
              </Grid>

              <Grid item xs={12}>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={formData.isRecurring}
                      onChange={handleCheckboxChange('isRecurring')}
                    />
                  }
                  label="Recurring Task"
                />
              </Grid>

              {formData.isRecurring && (
                <Grid item xs={12} md={6}>
                  <FormControl fullWidth>
                    <InputLabel>Recurrence Type</InputLabel>
                    <Select
                      value={formData.recurrenceType}
                      onChange={handleChange('recurrenceType')}
                      label="Recurrence Type"
                    >
                      {recurrenceTypes.map((type) => (
                        <MenuItem key={type} value={type}>
                          {type}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
              )}

              <Grid item xs={12} md={6}>
                <FormControl fullWidth>
                  <InputLabel>Reminder</InputLabel>
                  <Select
                    value={formData.reminderOffset}
                    onChange={handleChange('reminderOffset')}
                    label="Reminder"
                  >
                    {reminderOffsets.map((option) => (
                      <MenuItem key={option.value} value={option.value}>
                        {option.label}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            </Grid>
          </Box>
        </DialogContent>

        <DialogActions>
          <Button onClick={handleClose}>
            Cancel
          </Button>
          <Button onClick={handleSave} variant="contained">
            {task ? 'Update' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>
    </LocalizationProvider>
  );
};

export default TaskDialog;
