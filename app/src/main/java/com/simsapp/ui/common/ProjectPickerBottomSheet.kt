/*
 * File: ProjectPickerBottomSheet.kt
 * Description: Reusable bottom sheet component to pick a target project before event sync.
 * Author: SIMS Team
 */
package com.simsapp.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simsapp.data.local.entity.ProjectEntity

/**
 * Class-level comment: ProjectPickerBottomSheet
 *
 * Responsibilities:
 * - Present a list of non-finished projects for user to choose as the target project
 * - Default selection is the provided `defaultProjectUid`
 * - On confirm, returns the chosen `ProjectEntity` to caller
 *
 * Design:
 * - Material3 `ModalBottomSheet` with radio-style single selection list
 * - Caller controls visibility; component is stateless except local selection state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPickerBottomSheet(
    projects: List<ProjectEntity>,
    defaultProjectUid: String?,
    onConfirm: (ProjectEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedUid = remember { mutableStateOf(defaultProjectUid ?: projects.firstOrNull()?.projectUid) }

    LaunchedEffect(defaultProjectUid, projects) {
        // ensure default selection is valid
        selectedUid.value = when {
            projects.any { it.projectUid == defaultProjectUid } -> defaultProjectUid
            projects.isNotEmpty() -> projects.first().projectUid
            else -> null
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Select Target Project",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Divider()
        LazyColumn {
            items(projects) { p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedUid.value = p.projectUid }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = selectedUid.value == p.projectUid,
                        onClick = { selectedUid.value = p.projectUid }
                    )
                    Text(text = p.name, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = p.status ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Divider()
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            val uid = selectedUid.value
                            val target = projects.firstOrNull { it.projectUid == uid }
                            if (target != null) onConfirm(target) else onDismiss()
                        },
                        enabled = selectedUid.value != null
                    ) { Text("Confirm") }
                }
            }
        }
    }
}

/**
 * Function-level comment: ProjectPickerPreviewData
 * 提供预览数据的便捷方法（非生产调用）。
 */
fun ProjectPickerPreviewData(): List<ProjectEntity> = listOf(
    ProjectEntity(
        projectId = 1,
        name = "Project A",
        endDate = 0L,
        status = "ACTIVE",
        defectCount = 0,
        eventCount = 0,
        projectUid = "uid_a",
        projectHash = "",
        isDeleted = false
    ),
    ProjectEntity(
        projectId = 2,
        name = "Project B",
        endDate = 0L,
        status = "COLLECTING",
        defectCount = 3,
        eventCount = 5,
        projectUid = "uid_b",
        projectHash = "",
        isDeleted = false
    )
)