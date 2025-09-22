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
  Typography,
  IconButton
} from '@mui/material';
import ClearIcon from '@mui/icons-material/Clear';
import { DatePicker, TimePicker } from '@mui/x-date-pickers';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { Task } from '../models/Task';
import { useLanguage } from '../contexts/LanguageContext';

const TaskDialog = ({ open, onClose, onSave, task }) => {
  const { t, language, isRTL } = useLanguage();
  const [formData, setFormData] = useState({
    description: '',
    dueDate: null,
    dueTime: null,
    whenToPerform: 'Waiting', // When to Perform field
    isRecurring: false,
    recurrenceType: '',
    priority: 0, // Android calculates this automatically
    reminderOffset: null,
    reminderDays: null
  });

  const [errors, setErrors] = useState({});

  const whenToPerformOptions = [
    { value: 'Waiting', label: t('Waiting') },
    { value: 'Immediate', label: t('Immediate') },
    { value: 'Soon', label: t('Soon') },
    { value: 'Sunday', label: t('Sunday') },
    { value: 'Monday', label: t('Monday') },
    { value: 'Tuesday', label: t('Tuesday') },
    { value: 'Wednesday', label: t('Wednesday') },
    { value: 'Thursday', label: t('Thursday') },
    { value: 'Friday', label: t('Friday') },
    { value: 'Saturday', label: t('Saturday') }
  ];

  const recurrenceTypes = [
    { value: 'Daily', label: t('Daily') },
    { value: 'Weekly', label: t('Weekly') },
    { value: 'Biweekly', label: t('Biweekly') },
    { value: 'Monthly', label: t('Monthly') },
    { value: 'Yearly', label: t('Yearly') }
  ];

  const reminderOffsets = [
    { value: null, label: t('No reminder') },
    { value: 0, label: t('At due time') },
    { value: 15, label: t('15 minutes before') },
    { value: 30, label: t('30 minutes before') },
    { value: 60, label: t('1 hour before') }
  ];

  useEffect(() => {
    if (task) {
      setFormData({
        description: task.description || '',
        dueDate: task.dueDate ? new Date(task.dueDate) : null,
        dueTime: task.dueTime ? new Date(task.dueTime) : null,
        whenToPerform: task.dayOfWeek || 'Waiting',
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
        whenToPerform: 'Waiting',
        isRecurring: false,
        recurrenceType: '',
        priority: 0, // Android calculates this automatically
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
      dayOfWeek: formData.whenToPerform,
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
      <Dialog open={open} onClose={handleClose} maxWidth="lg" fullWidth>
        <DialogTitle>
          {task ? t('Edit Task') : t('Add New Task')}
        </DialogTitle>
        
        <DialogContent sx={{ px: 3, py: 2 }}>
          <Box sx={{ pt: 1 }}>
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label={t('Task Description')}
                  value={formData.description}
                  onChange={handleChange('description')}
                  error={!!errors.description}
                  helperText={errors.description}
                  multiline
                  rows={3}
                />
              </Grid>

              <Grid item xs={12} md={6}>
                <Box sx={{ 
                  display: 'flex', 
                  gap: 1, 
                  alignItems: 'flex-start',
                  flexDirection: isRTL ? 'row-reverse' : 'row'
                }}>
                  <Box sx={{ flex: 1 }}>
                    <DatePicker
                      label={t('Due Date')}
                      value={formData.dueDate}
                      onChange={handleDateChange('dueDate')}
                      renderInput={(params) => (
                        <TextField 
                          {...params} 
                          fullWidth 
                          sx={{
                            '& .MuiInputBase-input': {
                              textAlign: isRTL ? 'right' : 'left'
                            },
                            '& .MuiInputLabel-root': {
                              transformOrigin: isRTL ? 'top right' : 'top left',
                              left: isRTL ? 'auto' : 0,
                              right: isRTL ? 0 : 'auto'
                            },
                            '& .MuiInputLabel-shrink': {
                              transform: isRTL ? 'translate(-14px, -9px) scale(0.75)' : 'translate(14px, -9px) scale(0.75)'
                            }
                          }}
                        />
                      )}
                    />
                  </Box>
                  {formData.dueDate && (
                    <IconButton 
                      onClick={() => handleDateChange('dueDate')(null)}
                      color="error"
                      size="small"
                      sx={{ mt: 1 }}
                    >
                      <ClearIcon />
                    </IconButton>
                  )}
                </Box>
              </Grid>

              <Grid item xs={12} md={6}>
                <Box sx={{ 
                  display: 'flex', 
                  gap: 1, 
                  alignItems: 'flex-start',
                  flexDirection: isRTL ? 'row-reverse' : 'row'
                }}>
                  <Box sx={{ flex: 1 }}>
                    <TimePicker
                      label={t('Due Time')}
                      value={formData.dueTime}
                      onChange={(newTime) => {
                        handleDateChange('dueTime')(newTime);
                        // Clear reminder when time is removed
                        if (!newTime) {
                          handleChange('reminderOffset')(null);
                        }
                      }}
                      renderInput={(params) => (
                        <TextField 
                          {...params} 
                          fullWidth 
                          sx={{
                            '& .MuiInputBase-input': {
                              textAlign: isRTL ? 'right' : 'left'
                            },
                            '& .MuiInputLabel-root': {
                              transformOrigin: isRTL ? 'top right' : 'top left',
                              left: isRTL ? 'auto' : 0,
                              right: isRTL ? 0 : 'auto'
                            },
                            '& .MuiInputLabel-shrink': {
                              transform: isRTL ? 'translate(-14px, -9px) scale(0.75)' : 'translate(14px, -9px) scale(0.75)'
                            }
                          }}
                        />
                      )}
                    />
                  </Box>
                  {formData.dueTime && (
                    <IconButton 
                      onClick={() => {
                        handleDateChange('dueTime')(null);
                        handleChange('reminderOffset')(null); // Clear reminder when time is cleared
                      }}
                      color="error"
                      size="small"
                      sx={{ mt: 1 }}
                    >
                      <ClearIcon />
                    </IconButton>
                  )}
                </Box>
              </Grid>

              <Grid item xs={12}>
                <FormControl fullWidth>
                  <InputLabel>{t('When to Perform')}</InputLabel>
                  <Select
                    value={formData.whenToPerform}
                    onChange={handleChange('whenToPerform')}
                    label={t('When to Perform')}
                  >
                    {whenToPerformOptions.map((option) => (
                      <MenuItem key={option.value} value={option.value}>
                        {option.label}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>

              {/* Priority is calculated automatically by Android app, removed from web interface */}

              <Grid item xs={12}>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={formData.isRecurring}
                      onChange={handleCheckboxChange('isRecurring')}
                    />
                  }
                  label={t('Recurring Task')}
                />
              </Grid>

              {formData.isRecurring && (
                <Grid item xs={12} md={6}>
                  <FormControl fullWidth>
                    <InputLabel>{t('Recurrence Type')}</InputLabel>
                    <Select
                      value={formData.recurrenceType}
                      onChange={handleChange('recurrenceType')}
                      label={t('Recurrence Type')}
                    >
                      {recurrenceTypes.map((type) => (
                        <MenuItem key={type.value} value={type.value}>
                          {type.label}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
              )}

              {formData.dueTime && (
                <Grid item xs={12} md={6}>
                  <FormControl fullWidth>
                    <InputLabel>{t('Reminder')}</InputLabel>
                    <Select
                      value={formData.reminderOffset}
                      onChange={handleChange('reminderOffset')}
                      label={t('Reminder')}
                    >
                      {reminderOffsets.map((option) => (
                        <MenuItem key={option.value} value={option.value}>
                          {option.label}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
              )}
            </Grid>
          </Box>
        </DialogContent>

        <DialogActions>
          <Button onClick={handleClose}>
            {t('Cancel')}
          </Button>
          <Button onClick={handleSave} variant="contained">
            {task ? t('Update') : t('Add')}
          </Button>
        </DialogActions>
      </Dialog>
    </LocalizationProvider>
  );
};

export default TaskDialog;
