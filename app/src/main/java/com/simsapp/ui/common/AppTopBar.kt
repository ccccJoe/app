/*
 * File: AppTopBar.kt
 * Purpose: Provide a unified top app bar component with centered title,
 *          standardized back icon, and consistent spacing across screens.
 * Author: SIMS-Android Development Team
 */
package com.simsapp.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AppTopBar
 *
 * A reusable, center-aligned top app bar with a standardized back icon.
 * This component unifies toolbar styling across the app while allowing
 * per-screen color customization and action slots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    /** Title text to display in the center of the top bar. */
    title: String,
    /** Optional back navigation handler; if null, no back icon is shown. */
    onBack: (() -> Unit)? = null,
    /** Optional custom back icon; defaults to keyboard-style left arrow to match dashboard list. */
    backIconVector: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
    /** Controls the size of the navigation icon for better visibility. */
    navigationIconSize: Dp = 28.dp,
    /** Optional action content placed on the right side of the top bar. */
    actions: @Composable (RowScope.() -> Unit) = {},
    /** Background color of the top bar; default uses white to match Material design. */
    containerColor: Color = Color.White,
    /** Title color; defaults to a dark text color for contrast on white. */
    titleColor: Color = Color(0xFF1C1C1E),
    /** Navigation icon tint color. */
    navigationIconColor: Color = titleColor,
    /** Title font size; defaults to 18sp to match typical toolbar height. */
    titleFontSizeSp: Int = 18,
    /** Optional modifier for the top bar. */
    modifier: Modifier = Modifier,
    /** Elevation-like shadow control; Material3 top app bar uses tonal/shadow elevation via colors. */
    shadowElevation: Dp = 0.dp,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                fontSize = (titleFontSizeSp).sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = backIconVector,
                        contentDescription = "Back",
                        tint = navigationIconColor,
                        modifier = Modifier.size(navigationIconSize)
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = containerColor,
            titleContentColor = titleColor,
            navigationIconContentColor = navigationIconColor
        )
    )
}