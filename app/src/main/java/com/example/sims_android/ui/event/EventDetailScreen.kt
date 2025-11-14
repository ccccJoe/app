package com.simsapp.ui.event

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding

/**
 * File: EventDetailScreen.kt
 * Purpose: Placeholder Event detail page to be navigated from Project Detail page.
 * Author: SIMS-Android Development Team
 */

/**
 * Composable for Event detail page (placeholder).
 * @param projectName Optional project name passed from ProjectDetailScreen.
 * @param onBack Back navigation callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    projectName: String,
    onBack: () -> Unit
) {
    val title = remember(projectName) { if (projectName.isNotBlank()) "New Event - $projectName" else "New Event" }
    Scaffold(
        topBar = {
            com.simsapp.ui.common.AppTopBar(
                title = title,
                onBack = onBack,
                containerColor = Color.White,
                titleColor = Color(0xFF1C1C1E)
            )
        }
    ) { padding ->
        // Placeholder content
        Text(
            text = "Event detail form goes here...",
            modifier = Modifier.padding(padding)
        )
    }
}