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
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
// Using react-datepicker for proper locale support
import DatePicker from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';
import { registerLocale, setDefaultLocale } from 'react-datepicker';
import he from 'date-fns/locale/he';
import en from 'date-fns/locale/en-US';
import { parse, isValid } from 'date-fns';

// Register locales
registerLocale('he', he);
registerLocale('en', en);
import { Task } from '../models/Task';
import { useLanguage } from '../contexts/LanguageContext';
import { TaskConstants } from '../constants/TaskConstants';

const TaskDialog = ({ open, onClose, onSave, task }) => {
  const { t, language, isRTL } = useLanguage();
  
  
  const [formData, setFormData] = useState({
    description: '',
    dueDate: null,
    dueTime: null,
    whenToPerform: TaskConstants.DAY_NONE,
    isRecurring: false,
    recurrenceType: TaskConstants.RECURRENCE_DAILY,
    priority: 0,
    reminderOffset: null, // null = "No reminder"
    reminderDays: null
  });

  const [errors, setErrors] = useState({});

  const whenToPerformOptions = [
    { value: TaskConstants.DAY_NONE, label: t('Waiting') },
    { value: TaskConstants.DAY_IMMEDIATE, label: t('Immediate') },
    { value: TaskConstants.DAY_SOON, label: t('Soon') },
    { value: TaskConstants.DAY_SUNDAY, label: t('Sunday') },
    { value: TaskConstants.DAY_MONDAY, label: t('Monday') },
    { value: TaskConstants.DAY_TUESDAY, label: t('Tuesday') },
    { value: TaskConstants.DAY_WEDNESDAY, label: t('Wednesday') },
    { value: TaskConstants.DAY_THURSDAY, label: t('Thursday') },
    { value: TaskConstants.DAY_FRIDAY, label: t('Friday') },
    { value: TaskConstants.DAY_SATURDAY, label: t('Saturday') }
  ];

  const recurrenceTypes = [
    { value: TaskConstants.RECURRENCE_DAILY, label: t('Daily') },
    { value: TaskConstants.RECURRENCE_WEEKLY, label: t('Weekly') },
    { value: TaskConstants.RECURRENCE_BIWEEKLY, label: t('Biweekly') },
    { value: TaskConstants.RECURRENCE_MONTHLY, label: t('Monthly') },
    { value: TaskConstants.RECURRENCE_YEARLY, label: t('Yearly') }
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
        dueTime: task.dueTime ? convertMillisecondsSinceMidnightToTime(task.dueTime) : null,
        whenToPerform: task.dayOfWeek || TaskConstants.DAY_NONE,
        isRecurring: task.isRecurring || false,
        recurrenceType: task.recurrenceType || '',
        priority: task.priority || 0,
        reminderOffset: task.reminderOffset !== undefined ? task.reminderOffset : null,
        reminderDays: task.reminderDays || null
      });
    } else {
      setFormData({
        description: '',
        dueDate: null,
        dueTime: null,
        whenToPerform: TaskConstants.DAY_NONE,
        isRecurring: false,
        recurrenceType: TaskConstants.RECURRENCE_DAILY,
        priority: 0,
        reminderOffset: null, // null = "No reminder"
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
      dueTime: formData.dueTime ? calculateMillisecondsSinceMidnight(formData.dueTime) : null,
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

  // Calculate milliseconds since midnight (matches Android logic)
  const calculateMillisecondsSinceMidnight = (timeValue) => {
    const hours = timeValue.getHours();
    const minutes = timeValue.getMinutes();
    return hours * 60 * 60 * 1000 + minutes * 60 * 1000;
  };

  // Convert milliseconds since midnight back to a time object for display
  const convertMillisecondsSinceMidnightToTime = (milliseconds) => {
    const hours = Math.floor(milliseconds / (60 * 60 * 1000));
    const minutes = Math.floor((milliseconds % (60 * 60 * 1000)) / (60 * 1000));
    const time = new Date();
    time.setHours(hours, minutes, 0, 0);
    return time;
  };

  // RTL-aware styles
  const getInputStyle = () => ({
    direction: isRTL ? 'rtl' : 'ltr',
    textAlign: isRTL ? 'right' : 'left'
  });

  const getLabelStyle = () => ({
    direction: isRTL ? 'rtl' : 'ltr',
    textAlign: isRTL ? 'right' : 'left'
  });

  const getDialogStyle = () => ({
    direction: isRTL ? 'rtl' : 'ltr',
    '& .MuiDialog-paper': {
      direction: isRTL ? 'rtl' : 'ltr'
    }
  });

  return (
    <>
      <style>
        {`
          .react-datepicker-popper {
            z-index: 9999 !important;
          }
          .react-datepicker {
            z-index: 9999 !important;
          }
        `}
      </style>
      <Dialog 
        open={open} 
        onClose={handleClose} 
        maxWidth="lg" 
        fullWidth
        sx={getDialogStyle()}
      >
        <DialogTitle sx={getLabelStyle()}>
          {task ? t('Edit Task') : t('Add New Task')}
        </DialogTitle>
        
        <DialogContent sx={{ px: 3, py: 2 }}>
          <Box sx={{ pt: 1 }}>
            <Grid container spacing={2}>
              {/* Task Description */}
              <Grid item xs={12}>
                <Box>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      mb: 1, 
                      fontWeight: 'bold',
                      ...getLabelStyle()
                    }}
                  >
                    {t('Task Description')}
                  </Typography>
                <TextField
                  fullWidth
                    multiline
                    rows={3}
                  value={formData.description}
                  onChange={handleChange('description')}
                  error={!!errors.description}
                  helperText={errors.description}
                    sx={{
                      ...getInputStyle(),
                      '& .MuiInputBase-root': {
                        ...getInputStyle()
                      }
                    }}
                  />
                </Box>
              </Grid>

              {/* Due Date */}
              <Grid item xs={4} md={4}>
                <Box>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      mb: 1, 
                      fontWeight: 'bold',
                      ...getLabelStyle()
                    }}
                  >
                    {t('Due Date')}
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                  <Box sx={{ flex: 1, position: 'relative' }}>
                    <input
                      type="date"
                      value={formData.dueDate ? formData.dueDate.toISOString().split('T')[0] : ''}
                      onChange={(e) => {
                        const value = e.target.value;
                        if (value) {
                          // Create date in UTC to avoid timezone issues
                          const date = new Date(value + 'T00:00:00.000Z');
                          handleDateChange('dueDate')(date);
                        } else {
                          handleDateChange('dueDate')(null);
                        }
                      }}
                      style={{
                        width: '100%',
                        padding: '16.5px 14px',
                        border: '1px solid rgba(0, 0, 0, 0.23)',
                        borderRadius: '4px',
                        fontSize: '16px',
                        fontFamily: 'inherit',
                        direction: isRTL ? 'rtl' : 'ltr',
                        textAlign: isRTL ? 'right' : 'left',
                        backgroundColor: 'transparent'
                      }}
                    />
                  </Box>
                  {formData.dueDate && (
                    <IconButton 
                      onClick={() => {
                        handleDateChange('dueDate')(null);
                        setDateText('');
                      }}
                      color="error"
                      size="small"
                    >
                      <ClearIcon />
                    </IconButton>
                  )}
                  </Box>
                </Box>
              </Grid>

              {/* Due Time */}
              <Grid item xs={4} md={4}>
                <Box>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      mb: 1, 
                      fontWeight: 'bold',
                      ...getLabelStyle()
                    }}
                  >
                    {t('Due Time')}
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                  <Box sx={{ flex: 1 }}>
                    <TextField
                      type="time"
                      value={formData.dueTime ? formData.dueTime.toTimeString().slice(0, 5) : ''}
                      onChange={(e) => {
                        if (e.target.value) {
                          const [hours, minutes] = e.target.value.split(':').map(Number);
                          const time = new Date();
                          time.setHours(hours, minutes, 0, 0);
                          handleDateChange('dueTime')(time);
                        } else {
                          handleDateChange('dueTime')(null);
                          handleChange('reminderOffset')(null);
                        }
                      }}
                          fullWidth 
                      inputProps={{
                        lang: language === 'he' ? 'he-IL' : 'en-US',
                        dir: isRTL ? 'rtl' : 'ltr'
                      }}
                          sx={{
                        ...getInputStyle(),
                        '& .MuiInputBase-root': {
                          ...getInputStyle()
                        },
                        '& input': {
                          direction: isRTL ? 'rtl' : 'ltr',
                              textAlign: isRTL ? 'right' : 'left'
                        }
                      }}
                    />
                  </Box>
                  {formData.dueTime && (
                    <IconButton 
                      onClick={() => {
                        handleDateChange('dueTime')(null);
                          handleChange('reminderOffset')(null);
                      }}
                      color="error"
                      size="small"
                    >
                      <ClearIcon />
                    </IconButton>
                  )}
                  </Box>
                  
                  {/* Reminder - under Due Time */}
                  {formData.dueTime && (
                    <Box sx={{ mt: 2 }}>
                      <Typography 
                        variant="body2" 
                        sx={{ 
                          mb: 1, 
                          fontWeight: 'bold',
                          ...getLabelStyle()
                        }}
                      >
                        {t('Reminder')}
                      </Typography>
                      <FormControl fullWidth>
                        <Select
                          value={formData.reminderOffset}
                          onChange={handleChange('reminderOffset')}
                          displayEmpty
                          sx={{
                            ...getInputStyle(),
                            '& .MuiSelect-select': {
                              ...getInputStyle()
                            }
                          }}
                        >
                          {reminderOffsets.map((option) => (
                            <MenuItem key={option.value} value={option.value}>
                              {option.label}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Box>
                  )}
                  
                </Box>
              </Grid>

              {/* When to Perform */}
              <Grid item xs={12} md={6}>
                <Box>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      mb: 1, 
                      fontWeight: 'bold',
                      ...getLabelStyle()
                    }}
                  >
                    {t('When to Perform')}
                  </Typography>
                <FormControl fullWidth>
                  <Select
                    value={formData.whenToPerform}
                    onChange={handleChange('whenToPerform')}
                      sx={{
                        ...getInputStyle(),
                        '& .MuiSelect-select': {
                          ...getInputStyle()
                        }
                      }}
                  >
                    {whenToPerformOptions.map((option) => (
                      <MenuItem key={option.value} value={option.value}>
                        {option.label}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                </Box>
              </Grid>

              {/* Recurring Task Checkbox */}
              <Grid item xs={12} md={6}>
                <Box>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      mb: 1, 
                      fontWeight: 'bold',
                      ...getLabelStyle()
                    }}
                  >
                    {t('Recurring')}
                  </Typography>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={formData.isRecurring}
                      onChange={handleCheckboxChange('isRecurring')}
                    />
                  }
                    label=""
                    sx={getLabelStyle()}
                />
                </Box>
              </Grid>

              {/* Recurrence Type and Reminder - aligned side by side */}
              {formData.isRecurring && (
                <Grid item xs={12} md={6}>
                  <Box>
                    <Typography 
                      variant="body2" 
                      sx={{ 
                        mb: 1, 
                        fontWeight: 'bold',
                        ...getLabelStyle()
                      }}
                    >
                      {t('Recurrence Type')}
                    </Typography>
                  <FormControl fullWidth>
                    <Select
                      value={formData.recurrenceType}
                      onChange={handleChange('recurrenceType')}
                        sx={{
                          ...getInputStyle(),
                          '& .MuiSelect-select': {
                            ...getInputStyle()
                          }
                        }}
                    >
                      {recurrenceTypes.map((type) => (
                        <MenuItem key={type.value} value={type.value}>
                          {type.label}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  </Box>
                </Grid>
              )}

              {/* Spacer when not recurring to maintain alignment */}
              {!formData.isRecurring && (
                <Grid item xs={12} md={6}>
                  <Box />
                </Grid>
              )}


            </Grid>
          </Box>
        </DialogContent>

        <DialogActions sx={{ 
          flexDirection: isRTL ? 'row-reverse' : 'row',
          gap: 1
        }}>
          <Button onClick={handleClose}>
            {t('Cancel')}
          </Button>
          <Button onClick={handleSave} variant="contained">
            {task ? t('Update') : t('Add')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default TaskDialog;