import React, { useState, useMemo } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Checkbox,
  IconButton,
  Box,
  Chip,
  TextField,
  InputAdornment,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Grid,
  Collapse,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Tooltip
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import SearchIcon from '@mui/icons-material/Search';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import FamilyRestroom from '@mui/icons-material/FamilyRestroom';
import { format } from 'date-fns';
import { TaskCategorizer } from '../utils/taskCategorizer';
import { useLanguage } from '../contexts/LanguageContext';

const CategorizedTaskList = ({ 
  tasks, 
  loading, 
  onEditTask, 
  onDeleteTask, 
  onToggleCompletion 
}) => {
  const { t, language } = useLanguage();
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatus, setFilterStatus] = useState('all');
  const [expandedCategories, setExpandedCategories] = useState(new Set(['Today'])); // Default to today expanded

  const categorizer = useMemo(() => new TaskCategorizer(t), [t]);

  // Check if a task is immediate (matches Android logic)
  const isTaskImmediate = (task) => {
    // A task is immediate if:
    // 1. It has dayOfWeek set to "Immediate" 
    // 2. It has a due date that is overdue (past today)
    
    // Check if task is explicitly marked as immediate
    if (task.dayOfWeek === 'Immediate') {
      return true;
    }
    
    // Check if task has overdue date
    if (task.dueDate != null) {
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      const todayMillis = today.getTime();
      
      if (task.dueDate < todayMillis) {
        return true; // Task is overdue
      }
    }
    
    return false;
  };

  const getReminderText = (task) => {
    if (task.reminderOffset === null || task.reminderOffset === undefined || task.reminderOffset < 0 || task.isCompleted) {
      return null;
    }
    
    // Map reminder offset to text (matching Android app logic)
    switch (task.reminderOffset) {
      case 0:
        return t('At due time');
      case 15:
        return t('15 minutes before');
      case 30:
        return t('30 minutes before');
      case 60:
        return t('60 minutes before');
      default:
        return t('At due time');
    }
  };

  const filteredTasks = useMemo(() => {
    if (!tasks) return [];
    
    return tasks.filter(task => {
      const matchesSearch = task.description.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesStatus = filterStatus === 'all' || 
        (filterStatus === 'completed' && task.isCompleted) ||
        (filterStatus === 'pending' && !task.isCompleted);
      
      return matchesSearch && matchesStatus;
    });
  }, [tasks, searchTerm, filterStatus]);

  const categorizedTasks = useMemo(() => {
    if (!filteredTasks.length) return [];
    
    return categorizer.categorizeTasks(filteredTasks);
  }, [filteredTasks, categorizer]);

  const handleCategoryToggle = (categoryName) => {
    // Only allow one category to be expanded at a time
    if (expandedCategories.has(categoryName)) {
      setExpandedCategories(new Set()); // Close all if clicking the same category
    } else {
      setExpandedCategories(new Set([categoryName])); // Open only this category
    }
  };

  const formatDueDate = (dueDate, dueTime) => {
    const isHebrew = language === 'he';
    
    if (dueDate && dueTime) {
      // Both date and time
      const date = new Date(dueDate);
      // Use locale-aware date formatting
      const dateOptions = { 
        year: 'numeric', 
        month: isHebrew ? '2-digit' : 'short', 
        day: '2-digit' 
      };
      let formatted = date.toLocaleDateString(isHebrew ? 'he-IL' : 'en-US', dateOptions);
      
      // dueTime is milliseconds since midnight (matches Android logic)
      const hours = Math.floor(dueTime / (60 * 60 * 1000));
      const minutes = Math.floor((dueTime % (60 * 60 * 1000)) / (60 * 1000));
      const timeStr = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`;
      formatted += isHebrew ? ` ${timeStr}` : ` at ${timeStr}`;
      
      return formatted;
    } else if (dueDate) {
      // Date only
      const date = new Date(dueDate);
      const dateOptions = { 
        year: 'numeric', 
        month: isHebrew ? '2-digit' : 'short', 
        day: '2-digit' 
      };
      return date.toLocaleDateString(isHebrew ? 'he-IL' : 'en-US', dateOptions);
    } else if (dueTime) {
      // Time only
      const hours = Math.floor(dueTime / (60 * 60 * 1000));
      const minutes = Math.floor((dueTime % (60 * 60 * 1000)) / (60 * 1000));
      const timeStr = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`;
      return isHebrew ? timeStr : `at ${timeStr}`;
    }
    
    return null;
  };

  const getDueLabel = (dueDate, dueTime) => {
    if (dueDate && dueTime) {
      return t('Due'); // Both date and time
    } else if (dueDate) {
      return t('Due'); // Date only
    } else if (dueTime) {
      return t('Time'); // Time only
    }
    return null;
  };

  const formatCompletionDate = (completionDate) => {
    if (!completionDate) return null;
    
    const isHebrew = language === 'he';
    const date = new Date(completionDate);
    
    const dateOptions = { 
      year: 'numeric', 
      month: isHebrew ? '2-digit' : 'short', 
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    };
    
    return date.toLocaleDateString(isHebrew ? 'he-IL' : 'en-US', dateOptions);
  };

  const getCategoryDisplayName = (categoryName) => {
    // Map internal category names to display names
    const categoryMap = {
      'Immediate': 'Immediate',
      'Soon': 'Soon', 
      'Waiting': 'Waiting',
      'Completed': 'Completed',
      'Sunday': 'Sunday',
      'Monday': 'Monday',
      'Tuesday': 'Tuesday',
      'Wednesday': 'Wednesday',
      'Thursday': 'Thursday',
      'Friday': 'Friday',
      'Saturday': 'Saturday'
    };
    
    return categoryMap[categoryName] || categoryName;
  };

  const getCategoryColor = (categoryName) => {
    const colorMap = {
      'Immediate': 'error',
      'Soon': 'warning',
      'Waiting': 'info',
      'Completed': 'success',
      'Sunday': 'primary',
      'Monday': 'primary',
      'Tuesday': 'primary',
      'Wednesday': 'primary',
      'Thursday': 'primary',
      'Friday': 'primary',
      'Saturday': 'primary'
    };
    
    return colorMap[categoryName] || 'default';
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="200px">
        <Typography>{t('Loading tasks...')}</Typography>
      </Box>
    );
  }

  if (categorizedTasks.length === 0) {
    return (
      <Card sx={{ mt: 3, p: 3, textAlign: 'center' }}>
        <Typography variant="h6" color="text.secondary">
          {t('No tasks found. Create your first task by clicking the + button!')}
        </Typography>
      </Card>
    );
  }

  // Group items by category
  const categoryGroups = [];
  let currentCategory = null;
  let currentTasks = [];

  categorizedTasks.forEach(item => {
    if (item.type === 'header') {
      // Save previous category if it exists
      if (currentCategory) {
        categoryGroups.push({
          category: currentCategory,
          tasks: currentTasks
        });
      }
      
      // Start new category
      currentCategory = item;
      currentTasks = [];
    } else if (item.type === 'task') {
      currentTasks.push(item);
    }
  });

  // Don't forget the last category
  if (currentCategory) {
    categoryGroups.push({
      category: currentCategory,
      tasks: currentTasks
    });
  }

  return (
    <Box>
      {/* Search and Filter Controls */}
      <Box sx={{ mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              placeholder={t('Search tasks...')}
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
              <InputLabel>{t('Status')}</InputLabel>
              <Select
                value={filterStatus}
                onChange={(e) => setFilterStatus(e.target.value)}
                label={t('Status')}
              >
                <MenuItem value="all">{t('All Tasks')}</MenuItem>
                <MenuItem value="pending">{t('Pending')}</MenuItem>
                <MenuItem value="completed">{t('Completed')}</MenuItem>
              </Select>
            </FormControl>
          </Grid>
        </Grid>
      </Box>

      {/* Categorized Tasks */}
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {categoryGroups.map(({ category, tasks: categoryTasks }) => {
          const isExpanded = expandedCategories.has(category.name);
          const categoryColor = getCategoryColor(category.name);
          const displayName = getCategoryDisplayName(category.name);

          return (
            <Accordion 
              key={category.name}
              expanded={isExpanded}
              onChange={() => handleCategoryToggle(category.name)}
              sx={{ 
                '&:before': { display: 'none' }, // Remove default border
                boxShadow: 1
              }}
            >
              <AccordionSummary 
                expandIcon={<ExpandMoreIcon />}
                sx={{ 
                  backgroundColor: 'grey.50',
                  '&:hover': { backgroundColor: 'grey.100' }
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: '100%' }}>
                  <Typography variant="h6" sx={{ fontWeight: 'bold' }}>
                    {displayName}
                  </Typography>
                  <Chip 
                    label={category.taskCount} 
                    color={categoryColor}
                    size="small"
                  />
                </Box>
              </AccordionSummary>
              
              <AccordionDetails sx={{ p: 0 }}>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, p: 1 }}>
                  {categoryTasks.map((task) => (
                    <Card 
                      key={task.id}
                      sx={{ 
                        opacity: task.isCompleted ? 0.7 : 1,
                        textDecoration: task.isCompleted ? 'line-through' : 'none',
                        '&:hover': { 
                          boxShadow: 2,
                          transform: 'translateY(-1px)',
                          transition: 'all 0.2s ease-in-out'
                        }
                      }}
                    >
                      <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
                          <Checkbox
                            checked={task.isCompleted}
                            onChange={(e) => onToggleCompletion(task.id, e.target.checked)}
                            color="primary"
                          />
                          
                          <Box sx={{ flex: 1, minWidth: 0 }}>
                            <Typography 
                              variant="body1" 
                              sx={{ 
                                fontWeight: 'bold',
                                wordBreak: 'break-word'
                              }}
                            >
                              {isTaskImmediate(task) ? `⚡ ${task.description}` : task.description}
                            </Typography>
                            
                            {task.isRecurring && (
                              <Box sx={{ mb: 1 }}>
                                <Chip label={task.recurrenceType || t('Recurring')} variant="outlined" size="small" />
                              </Box>
                            )}

                            {formatDueDate(task.dueDate, task.dueTime) && (
                              <Typography variant="caption" color="text.secondary">
                                {getDueLabel(task.dueDate, task.dueTime)}: {formatDueDate(task.dueDate, task.dueTime)}
                              </Typography>
                            )}

                            {getReminderText(task) && (
                              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                                🔔 {getReminderText(task)}
                              </Typography>
                            )}

                            {task.isCompleted && formatCompletionDate(task.completionDate) && (
                              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                                ✅ {t('Completed')}: {formatCompletionDate(task.completionDate)}
                              </Typography>
                            )}

                            {/* FamilySync source indicator */}
                            {(() => {
                              console.log('🔥 CATEGORIZED TASKLIST DEBUG - Task being rendered:', task.description);
                              console.log('🔥 CATEGORIZED TASKLIST DEBUG - sourceApp:', task.sourceApp);
                              console.log('🔥 CATEGORIZED TASKLIST DEBUG - sourceTaskId:', task.sourceTaskId);
                              console.log('🔥 CATEGORIZED TASKLIST DEBUG - task object type:', typeof task);
                              console.log('🔥 CATEGORIZED TASKLIST DEBUG - task constructor:', task.constructor.name);
                              console.log('🔥 CATEGORIZED TASKLIST DEBUG - has isFromFamilySync method:', typeof task.isFromFamilySync);
                              
                              // Try to call the method if it exists
                              let isFromFS = false;
                              if (task.isFromFamilySync && typeof task.isFromFamilySync === 'function') {
                                isFromFS = task.isFromFamilySync();
                              } else {
                                // Fallback: manual check
                                isFromFS = task.sourceApp === 'familysync' && task.sourceTaskId;
                                console.log('🔥 CATEGORIZED TASKLIST DEBUG - Using fallback check:', isFromFS);
                              }
                              
                              console.log('🔥 CATEGORIZED TASKLIST DEBUG - isFromFamilySync result:', isFromFS);
                              
                              if (isFromFS) {
                                console.log('🎉 FAMILYSYNC TASK DETECTED IN CATEGORIZED LIST:', task.description);
                              }
                              
                              return isFromFS;
                            })() && (
                              <Box sx={{ mt: 1 }}>
                                <Tooltip title="Imported from FamilySync">
                                  <Chip 
                                    icon={<FamilyRestroom />}
                                    label="FamilySync" 
                                    variant="outlined" 
                                    size="small" 
                                    color="secondary"
                                    sx={{
                                      backgroundColor: 'rgba(156, 39, 176, 0.1)',
                                      borderColor: 'rgba(156, 39, 176, 0.3)',
                                      '& .MuiChip-icon': {
                                        color: '#9c27b0'
                                      }
                                    }}
                                  />
                                </Tooltip>
                              </Box>
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
              </AccordionDetails>
            </Accordion>
          );
        })}
      </Box>
    </Box>
  );
};

export default CategorizedTaskList;
