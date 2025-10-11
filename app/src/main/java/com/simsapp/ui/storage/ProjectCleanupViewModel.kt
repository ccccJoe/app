/*
 * File: ProjectCleanupViewModel.kt
 * Description: ViewModel for managing project cleanup operations and UI state.
 * Author: SIMS Team
 */
package com.simsapp.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simsapp.data.local.entity.ProjectEntity
import com.simsapp.domain.usecase.CleanupProjectsUseCase
import com.simsapp.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ProjectCleanupViewModel
 * 
 * Manages the state and operations for project cleanup functionality.
 * Handles loading finished projects, selection management, and cleanup execution.
 */
@HiltViewModel
class ProjectCleanupViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val cleanupProjectsUseCase: CleanupProjectsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectCleanupUiState())
    val uiState: StateFlow<ProjectCleanupUiState> = _uiState.asStateFlow()

    init {
        loadFinishedProjects()
    }

    /**
     * Load all finished projects from the repository.
     */
    private fun loadFinishedProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                projectRepository.getFinishedProjects()
                    .collect { projects ->
                        _uiState.update { currentState ->
                            currentState.copy(
                                finishedProjects = projects,
                                isLoading = false,
                                // Clear selection if projects changed
                                selectedProjects = currentState.selectedProjects.filter { selectedId ->
                                    projects.any { project -> project.projectId == selectedId }
                                }.toSet()
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        cleanupResult = CleanupResult(
                            isSuccess = false,
                            message = "Failed to load finished projects: ${e.message}"
                        )
                    ) 
                }
            }
        }
    }

    /**
     * Select a project for cleanup.
     */
    fun selectProject(projectId: Long) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedProjects = currentState.selectedProjects + projectId
            )
        }
    }

    /**
     * Deselect a project from cleanup.
     */
    fun deselectProject(projectId: Long) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedProjects = currentState.selectedProjects - projectId
            )
        }
    }

    /**
     * Select all finished projects.
     */
    fun selectAllProjects() {
        _uiState.update { currentState ->
            currentState.copy(
                selectedProjects = currentState.finishedProjects.map { it.projectId }.toSet()
            )
        }
    }

    /**
     * Clear all project selections.
     */
    fun clearSelection() {
        _uiState.update { currentState ->
            currentState.copy(selectedProjects = emptySet())
        }
    }

    /**
     * Execute cleanup for selected projects.
     */
    fun cleanupSelectedProjects() {
        val selectedIds = _uiState.value.selectedProjects
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val result = cleanupProjectsUseCase.execute(selectedIds.toList())
                
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        selectedProjects = emptySet(),
                        cleanupResult = if (result.isSuccess) {
                            CleanupResult(
                                isSuccess = true,
                                message = "Successfully cleaned up ${selectedIds.size} projects and their associated data."
                            )
                        } else {
                            CleanupResult(
                                isSuccess = false,
                                message = result.errorMessage ?: "Unknown error occurred during cleanup."
                            )
                        }
                    )
                }
                
                // Reload projects after successful cleanup
                if (result.isSuccess) {
                    loadFinishedProjects()
                }
                
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        cleanupResult = CleanupResult(
                            isSuccess = false,
                            message = "Cleanup failed: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Clear the cleanup result message.
     */
    fun clearCleanupResult() {
        _uiState.update { it.copy(cleanupResult = null) }
    }
}

/**
 * UI state for project cleanup screen.
 */
data class ProjectCleanupUiState(
    val isLoading: Boolean = false,
    val finishedProjects: List<ProjectEntity> = emptyList(),
    val selectedProjects: Set<Long> = emptySet(),
    val cleanupResult: CleanupResult? = null
)

/**
 * Result of cleanup operation.
 */
data class CleanupResult(
    val isSuccess: Boolean,
    val message: String
)