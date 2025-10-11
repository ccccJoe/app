/*
 * File: CleanupProjectsUseCase.kt
 * Description: Use case for cleaning up projects and their associated local data.
 * Author: SIMS Team
 */
package com.simsapp.domain.usecase

import android.content.Context
import com.simsapp.data.repository.ProjectRepository
import com.simsapp.data.repository.DefectRepository
import com.simsapp.data.repository.EventRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CleanupProjectsUseCase
 * 
 * Handles the complete cleanup of projects including:
 * - Database records (projects, defects, events)
 * - Local files (images, recordings, PDFs)
 * - Associated directories
 */
@Singleton
class CleanupProjectsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val defectRepository: DefectRepository,
    private val eventRepository: EventRepository
) {

    /**
     * Execute cleanup for the specified project IDs.
     * 
     * @param projectIds List of project IDs to clean up
     * @return CleanupResult indicating success/failure and details
     */
    suspend fun execute(projectIds: List<Long>): CleanupResult = withContext(Dispatchers.IO) {
        try {
            if (projectIds.isEmpty()) {
                return@withContext CleanupResult(
                    isSuccess = false,
                    errorMessage = "No projects selected for cleanup"
                )
            }

            // Get project details before deletion for file cleanup
            val projectsToClean = mutableListOf<ProjectCleanupInfo>()
            for (projectId in projectIds) {
                val project = projectRepository.getProjectById(projectId)
                if (project != null) {
                    // Get associated defects and events for file cleanup
                    val defects = defectRepository.getDefectsByProject(projectId).first()
                    val events = eventRepository.getEventsByProject(projectId).first()
                    
                    projectsToClean.add(
                        ProjectCleanupInfo(
                            projectId = projectId,
                            projectUid = project.projectUid,
                            defectIds = defects.map { it.defectId },
                            eventIds = events.map { it.eventId }
                        )
                    )
                }
            }

            // Step 1: Clean up local files first
            val fileCleanupResults = mutableListOf<String>()
            for (projectInfo in projectsToClean) {
                val fileResult = cleanupProjectFiles(projectInfo)
                if (fileResult.isNotEmpty()) {
                    fileCleanupResults.add(fileResult)
                }
            }

            // Step 2: Delete database records
            val dbResult = projectRepository.deleteProjectsAndRelatedData(projectIds)
            
            if (dbResult.isSuccess) {
                val totalFiles = fileCleanupResults.sumOf { it.split(",").size }
                CleanupResult(
                    isSuccess = true,
                    message = "Successfully cleaned up ${projectIds.size} projects" +
                            if (totalFiles > 0) " and $totalFiles associated files" else "",
                    cleanedProjectCount = projectIds.size,
                    cleanedFileCount = totalFiles
                )
            } else {
                CleanupResult(
                    isSuccess = false,
                    errorMessage = dbResult.errorMessage ?: "Database cleanup failed"
                )
            }

        } catch (e: Exception) {
            CleanupResult(
                isSuccess = false,
                errorMessage = "Cleanup failed: ${e.message}"
            )
        }
    }

    /**
     * Clean up local files associated with a project.
     * 
     * @param projectInfo Information about the project to clean
     * @return Comma-separated list of cleaned file paths
     */
    private suspend fun cleanupProjectFiles(projectInfo: ProjectCleanupInfo): String {
        val cleanedFiles = mutableListOf<String>()
        
        try {
            // Clean up project-specific directories
            val projectDir = File(context.filesDir, "projects/${projectInfo.projectUid}")
            if (projectDir.exists()) {
                val deleted = projectDir.deleteRecursively()
                if (deleted) {
                    cleanedFiles.add("project_dir:${projectDir.name}")
                }
            }

            // Clean up defect-related files
            for (defectId in projectInfo.defectIds) {
                val defectDir = File(context.filesDir, "defects/$defectId")
                if (defectDir.exists()) {
                    val deleted = defectDir.deleteRecursively()
                    if (deleted) {
                        cleanedFiles.add("defect_dir:$defectId")
                    }
                }
            }

            // Clean up event-related files
            for (eventId in projectInfo.eventIds) {
                val eventDir = File(context.filesDir, "events/$eventId")
                if (eventDir.exists()) {
                    val deleted = eventDir.deleteRecursively()
                    if (deleted) {
                        cleanedFiles.add("event_dir:$eventId")
                    }
                }
            }

            // Clean up shared media files that might be orphaned
            cleanupOrphanedMediaFiles(projectInfo)

        } catch (e: Exception) {
            // Log error but don't fail the entire cleanup
            cleanedFiles.add("error:${e.message}")
        }

        return cleanedFiles.joinToString(",")
    }

    /**
     * Clean up orphaned media files that are no longer referenced.
     * 
     * @param projectInfo Information about the cleaned project
     */
    private suspend fun cleanupOrphanedMediaFiles(projectInfo: ProjectCleanupInfo) {
        try {
            // Clean up images directory
            val imagesDir = File(context.filesDir, "images")
            if (imagesDir.exists()) {
                imagesDir.listFiles()?.forEach { file ->
                    if (file.name.contains(projectInfo.projectUid) || 
                        projectInfo.defectIds.any { file.name.contains(it.toString()) } ||
                        projectInfo.eventIds.any { file.name.contains(it.toString()) }) {
                        file.delete()
                    }
                }
            }

            // Clean up recordings directory
            val recordingsDir = File(context.filesDir, "recordings")
            if (recordingsDir.exists()) {
                recordingsDir.listFiles()?.forEach { file ->
                    if (file.name.contains(projectInfo.projectUid) || 
                        projectInfo.defectIds.any { file.name.contains(it.toString()) } ||
                        projectInfo.eventIds.any { file.name.contains(it.toString()) }) {
                        file.delete()
                    }
                }
            }

            // Clean up PDFs directory
            val pdfsDir = File(context.filesDir, "pdfs")
            if (pdfsDir.exists()) {
                pdfsDir.listFiles()?.forEach { file ->
                    if (file.name.contains(projectInfo.projectUid) || 
                        projectInfo.defectIds.any { file.name.contains(it.toString()) } ||
                        projectInfo.eventIds.any { file.name.contains(it.toString()) }) {
                        file.delete()
                    }
                }
            }

        } catch (e: Exception) {
            // Silently handle media cleanup errors
        }
    }
}

/**
 * Information needed for cleaning up a project's files.
 */
private data class ProjectCleanupInfo(
    val projectId: Long,
    val projectUid: String,
    val defectIds: List<Long>,
    val eventIds: List<Long>
)

/**
 * Result of cleanup operation.
 */
data class CleanupResult(
    val isSuccess: Boolean,
    val message: String = "",
    val errorMessage: String? = null,
    val cleanedProjectCount: Int = 0,
    val cleanedFileCount: Int = 0
)