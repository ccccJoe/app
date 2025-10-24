/*
 * File: ProjectCleanupScreen.kt
 * Description: Screen for selecting finished projects to clean up their local data.
 * Author: SIMS Team
 */
package com.simsapp.ui.storage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simsapp.data.local.entity.ProjectEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ProjectCleanupScreen
 * 
 * Displays a list of finished projects that can be selected for cleanup.
 * Users can select multiple projects and confirm deletion of their local data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectCleanupScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProjectCleanupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clean Up Projects") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            if (uiState.selectedProjects.isNotEmpty()) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.selectedProjects.size} projects selected",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { showConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clean Up")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.finishedProjects.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No finished projects found",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "All projects are still in progress",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                // Use LazyColumn as the main container
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header with select all option as first item
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${uiState.finishedProjects.size} Finished projects found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Select All",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Checkbox(
                                        checked = uiState.selectedProjects.size == uiState.finishedProjects.size,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) {
                                                viewModel.selectAllProjects()
                                            } else {
                                                viewModel.clearSelection()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Project list items
                    items(uiState.finishedProjects) { project ->
                        ProjectCleanupItem(
                            project = project,
                            isSelected = uiState.selectedProjects.contains(project.projectId),
                            onSelectionChanged = { isSelected ->
                                if (isSelected) {
                                    viewModel.selectProject(project.projectId)
                                } else {
                                    viewModel.deselectProject(project.projectId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm Cleanup") },
            text = {
                Column {
                    Text("Are you sure you want to clean up the selected projects?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This will permanently delete:",
                        fontWeight = FontWeight.Medium
                    )
                    Text("• Project data")
                    Text("• Associated defects and events")
                    Text("• Local images and recordings")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This action cannot be undone.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cleanupSelectedProjects()
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clean Up")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Show cleanup result
    uiState.cleanupResult?.let { result ->
        LaunchedEffect(result) {
            // Auto dismiss after showing result
            kotlinx.coroutines.delay(2000)
            viewModel.clearCleanupResult()
            if (result.isSuccess) {
                // 延迟导航，确保状态清理完成后再执行导航
                delay(100) // 短暂延迟确保状态更新完成
                onNavigateBack()
            }
        }
        
        AlertDialog(
            onDismissRequest = { 
                viewModel.clearCleanupResult()
                if (uiState.cleanupResult?.isSuccess == true) {
                    // 延迟导航，确保状态清理完成后再执行导航
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(100) // 短暂延迟确保状态更新完成
                        onNavigateBack()
                    }
                }
            },
            title = { 
                Text(if (result.isSuccess) "Cleanup Complete" else "Cleanup Failed") 
            },
            text = { Text(result.message) },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.clearCleanupResult()
                    if (result.isSuccess) {
                        // 延迟导航，确保状态清理完成后再执行导航
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(100) // 短暂延迟确保状态更新完成
                            onNavigateBack()
                        }
                    }
                }) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * ProjectCleanupItem
 * 
 * Individual project item with checkbox for selection.
 */
@Composable
private fun ProjectCleanupItem(
    project: ProjectEntity,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChanged
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Defects: ${project.defectCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Events: ${project.eventCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}