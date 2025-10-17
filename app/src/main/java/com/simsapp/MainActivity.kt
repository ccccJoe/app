/*
 * File: MainActivity.kt
 * Description: Main launcher activity using Jetpack Compose. Annotated with Hilt entry point for DI.
 * Author: SIMS Team
 */
package com.simsapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.simsapp.ui.dashboard.DashboardScreen
import com.simsapp.ui.dashboard.ProjectCardData
import com.example.sims_android.ui.event.EventFormScreen
import com.simsapp.ui.project.ProjectDetailScreen
import com.simsapp.ui.theme.SIMSAndroidTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Observer
import com.simsapp.ui.storage.StoragePickerScreen
import androidx.activity.viewModels
import com.simsapp.ui.dashboard.DashboardViewModel
import com.simsapp.ui.project.ProjectInfoScreen
import com.simsapp.ui.defect.DefectDetailScreen
import com.example.sims_android.ui.project.DefectSortScreen
import com.simsapp.ui.project.HistoryDefectItem
import com.simsapp.ui.storage.ProjectCleanupScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * MainActivity
 *
 * App entry Activity. Hosts Compose navigation graph (Navigation Compose).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // 注入 Activity 作用域的 DashboardViewModel，供 Compose 使用
    private val dashboardViewModel: DashboardViewModel by viewModels()
    /**
     * AppDestination
     *
     * Type-safe destination definitions for Navigation Compose.
     * Use builder functions to compose a full route with encoded parameters.
     */
    private sealed class AppDestination(val route: String) {
        /** Dashboard (start) */
        data object Dashboard : AppDestination("dashboard")
        /** Project detail page with required argument: projectName and optional projectUid (query) */
        data object ProjectDetail : AppDestination("project?projectName={projectName}&projectUid={projectUid}") {
            const val ARG_PROJECT_NAME = "projectName"
            const val ARG_PROJECT_UID = "projectUid"
            /** Build route with encoded project name and projectUid (may be blank) */
            fun route(projectName: String, projectUid: String?): String {
                val name = Uri.encode(projectName)
                val uid = Uri.encode(projectUid ?: "")
                return "project?projectName=$name&projectUid=$uid"
            }
        }
        /** Event detail/form page with optional projectName, eventId and defectId as query */
        data object Event : AppDestination("event?projectName={projectName}&eventId={eventId}&defectId={defectId}") {
            const val ARG_PROJECT_NAME = "projectName"
            const val ARG_EVENT_ID = "eventId"
            const val ARG_DEFECT_ID = "defectId"
            /** Build route; all params are optional */
            fun route(projectName: String? = null, eventId: String? = null, defectId: String? = null): String {
                val qs = buildList {
                    if (!projectName.isNullOrBlank()) add("projectName=${Uri.encode(projectName)}")
                    if (!eventId.isNullOrBlank()) add("eventId=${Uri.encode(eventId)}")
                    if (!defectId.isNullOrBlank()) add("defectId=${Uri.encode(defectId)}")
                }.joinToString("&")
                return if (qs.isBlank()) "event" else "event?$qs"
            }
        }
        /** StoragePicker page: show storage browser for selecting files in a project scope */
        data object StoragePicker : AppDestination("storagePicker?projectName={projectName}&projectUid={projectUid}") {
            const val ARG_PROJECT_NAME = "projectName"
            const val ARG_PROJECT_UID = "projectUid"
            fun route(projectName: String? = null, projectUid: String? = null): String {
                val name = Uri.encode(projectName ?: "")
                val uid = Uri.encode(projectUid ?: "")
                val qs = buildList {
                    if (projectName != null) add("projectName=$name")
                    if (projectUid != null) add("projectUid=$uid")
                }.joinToString("&")
                return if (qs.isBlank()) "storagePicker" else "storagePicker?$qs"
            }
        }
        /** ProjectInfo page: show grouped key-value project details */
        data object ProjectInfo : AppDestination("projectInfo?projectName={projectName}&projectUid={projectUid}") {
            const val ARG_PROJECT_NAME = "projectName"
            const val ARG_PROJECT_UID = "projectUid"
            fun route(projectName: String?, projectUid: String?): String {
                val name = Uri.encode(projectName ?: "")
                val uid = Uri.encode(projectUid ?: "")
                val qs = buildList {
                    if (projectName != null) add("projectName=$name")
                    if (projectUid != null) add("projectUid=$uid")
                }.joinToString("&")
                return if (qs.isBlank()) "projectInfo" else "projectInfo?$qs"
            }
        }
        /** Defect detail page with required argument: defectNo */
        data object DefectDetail : AppDestination("defect?no={no}&projectUid={projectUid}") {
            const val ARG_NO = "no"
            const val ARG_PROJECT_UID = "projectUid"
            fun route(no: String, projectUid: String?): String {
                val encodedNo = Uri.encode(no)
                val uid = Uri.encode(projectUid ?: "")
                return "defect?no=$encodedNo&projectUid=$uid"
            }
        }
        
        /** Defect sort page with required argument: projectUid */
        data object DefectSort : AppDestination("defect_sort?projectUid={projectUid}") {
            const val ARG_PROJECT_UID = "projectUid"
            fun route(projectUid: String?): String {
                val uid = Uri.encode(projectUid ?: "")
                return "defect_sort?projectUid=$uid"
            }
        }
        
        /** Project cleanup page: show finished projects for cleanup */
        data object ProjectCleanup : AppDestination("project_cleanup") {
            fun route(): String = "project_cleanup"
        }
    }

    /**
     * onCreate
     *
     * Sets up the Compose content and Navigation Host.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SIMSAndroidTheme {
                // 订阅DashboardViewModel的加载状态
                val dashboardLoadingState by dashboardViewModel.dashboardLoadingState.collectAsState()
                
                // 如果首页数据正在加载，显示全屏loading
                if (dashboardLoadingState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Initializing SIMS...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                } else {
                    // 数据加载完成后显示正常导航
                    // NavController for in-app navigation graph
                    val navController = rememberNavController()

                    // 新增：支持通过 Intent Extras 直达页面（一次性导航）
                    // 函数级注释：根据启动 Intent 中的参数，构造首屏路由
                    // - 参数：no（缺陷编号）、projectUid（项目唯一ID，可为空）
                    // - 返回：当存在 no 时，返回缺陷详情路由；否则返回仪表盘路由
                    val startRoute = remember {
                        val no = intent?.getStringExtra(AppDestination.DefectDetail.ARG_NO)
                        val uid = intent?.getStringExtra(AppDestination.DefectDetail.ARG_PROJECT_UID)
                        if (!no.isNullOrBlank()) AppDestination.DefectDetail.route(no, uid) else AppDestination.Dashboard.route
                    }
                    // 仅在首次组合后触发导航，避免重复导航导致 BackStack 异常
                    androidx.compose.runtime.LaunchedEffect(startRoute) {
                        if (startRoute != AppDestination.Dashboard.route) {
                            navController.navigate(startRoute)
                        }
                    }

                    // Core NavHost: dashboard -> project detail -> event
                    NavHost(
                        navController = navController,
                        startDestination = AppDestination.Dashboard.route
                    ) {
                        // 1) Dashboard
                        composable(route = AppDestination.Dashboard.route) {
                            DashboardScreen(
                                onProjectClick = { clicked ->
                                    navController.navigate(
                                        AppDestination.ProjectDetail.route(clicked.name, clicked.projectUid)
                                    )
                                },
                                onEventCreate = {
                                    navController.navigate(AppDestination.Event.route())
                                },
                                onCleanClick = {
                                    navController.navigate(AppDestination.ProjectCleanup.route())
                                },
                                viewModel = dashboardViewModel
                            )
                        }

                    // 2) Project Detail - required arg: projectName
                    composable(
                        route = AppDestination.ProjectDetail.route,
                        arguments = listOf(
                            navArgument(AppDestination.ProjectDetail.ARG_PROJECT_NAME) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(AppDestination.ProjectDetail.ARG_PROJECT_UID) {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val encoded = backStackEntry.arguments?.getString(AppDestination.ProjectDetail.ARG_PROJECT_NAME).orEmpty()
                        val projectName = Uri.decode(encoded)
                        val uidEncoded = backStackEntry.arguments?.getString(AppDestination.ProjectDetail.ARG_PROJECT_UID).orEmpty()
                        val projectUid = if (uidEncoded.isBlank()) null else Uri.decode(uidEncoded)
                        ProjectDetailScreen(
                            projectName = projectName,
                            projectUid = projectUid,
                            onBack = { navController.popBackStack() },
                            onCreateEvent = {
                                navController.navigate(AppDestination.Event.route(projectName))
                            },
                            onCreateEventForDefect = { defectNo ->
                                navController.navigate(AppDestination.Event.route(projectName, defectId = defectNo))
                            },
                            onOpenEvent = { eventId ->
                                navController.navigate(AppDestination.Event.route(projectName, eventId))
                            },
                            onOpenProjectInfo = {
                                navController.navigate(AppDestination.ProjectInfo.route(projectName, projectUid))
                            },
                            onOpenDefect = { no ->
                                navController.navigate(AppDestination.DefectDetail.route(no, projectUid))
                            },
                            onOpenDefectSort = { defects ->
                                // 安全地将缺陷列表保存到当前导航条目的savedStateHandle中
                                try {
                                    navController.currentBackStackEntry?.savedStateHandle?.set("defects", defects)
                                    navController.navigate(AppDestination.DefectSort.route(projectUid))
                                } catch (e: Exception) {
                                    // 记录错误并显示提示
                                    e.printStackTrace()
                                    Toast.makeText(this@MainActivity, "Failed to open sort page", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    // 3) Event page - optional query arg: projectName, eventId & defectId
                    composable(
                        route = AppDestination.Event.route,
                        arguments = listOf(
                            navArgument(AppDestination.Event.ARG_PROJECT_NAME) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(AppDestination.Event.ARG_EVENT_ID) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(AppDestination.Event.ARG_DEFECT_ID) {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val encoded = backStackEntry.arguments?.getString(AppDestination.Event.ARG_PROJECT_NAME).orEmpty()
                        val projectName = if (encoded.isBlank()) "" else Uri.decode(encoded)
                        val eventIdEncoded = backStackEntry.arguments?.getString(AppDestination.Event.ARG_EVENT_ID).orEmpty()
                        val eventId = if (eventIdEncoded.isBlank()) "" else Uri.decode(eventIdEncoded)
                        val defectIdEncoded = backStackEntry.arguments?.getString(AppDestination.Event.ARG_DEFECT_ID).orEmpty()
                        val defectId = if (defectIdEncoded.isBlank()) "" else Uri.decode(defectIdEncoded)
                        // 缓存从文件选择器返回的选项
                        var selectedStorage by remember { mutableStateOf(listOf<String>()) }
                        var selectedStorageFileIds by remember { mutableStateOf(listOf<String>()) }
                        // 观察从 StoragePicker 返回的结果
                        val currentBackStackEntry = navController.currentBackStackEntry
                        DisposableEffect(currentBackStackEntry) {
                            val handle = currentBackStackEntry?.savedStateHandle
                            val liveData = handle?.getLiveData<List<String>>("selectedStorage")
                            val fileIdsLiveData = handle?.getLiveData<List<String>>("selectedStorageFileIds")
                            val observer = Observer<List<String>> { names ->
                                if (names != null) {
                                    selectedStorage = names
                                    handle?.remove<List<String>>("selectedStorage")
                                }
                            }
                            val fileIdsObserver = Observer<List<String>> { fileIds ->
                                if (fileIds != null) {
                                    selectedStorageFileIds = fileIds
                                    handle?.remove<List<String>>("selectedStorageFileIds")
                                }
                            }
                            liveData?.observeForever(observer)
                            fileIdsLiveData?.observeForever(fileIdsObserver)
                            onDispose { 
                                liveData?.removeObserver(observer)
                                fileIdsLiveData?.removeObserver(fileIdsObserver)
                            }
                        }
                        EventFormScreen(
                            projectName = projectName,
                            eventId = eventId,
                            defectId = defectId,
                            onBack = { navController.popBackStack() },
                            onOpenStorage = { projectUid, selectedAssetFileIds ->
                                android.util.Log.d("MainActivity", "接收到数字资产选择请求，projectUid: $projectUid, fileIds: ${selectedAssetFileIds.joinToString()}")
                                // 保存当前选中的资产fileId到savedStateHandle，用于回显
                                navController.currentBackStackEntry?.savedStateHandle?.set("selectedStorageFileIds", selectedAssetFileIds)
                                // 直接使用传递的projectUid导航到StoragePicker
                                navController.navigate(AppDestination.StoragePicker.route(null, projectUid))
                            },
                            selectedStorage = selectedStorage,
                            selectedStorageFileIds = selectedStorageFileIds, // 新增：传递数字资产file_id列表
                            onOpenDefect = { defect ->
                                // 从defect对象中获取projectUid和defectNo来导航到详情页面
                                val projectUid = defect.projectUid
                                val defectNo = defect.defectNo
                                navController.navigate(AppDestination.DefectDetail.route(defectNo, projectUid))
                            }
                        )
                    }

                    // 4) StoragePicker page
                    composable(
                        route = AppDestination.StoragePicker.route,
                        arguments = listOf(
                            navArgument(AppDestination.StoragePicker.ARG_PROJECT_NAME) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(AppDestination.StoragePicker.ARG_PROJECT_UID) {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val encodedProject = backStackEntry.arguments?.getString(AppDestination.StoragePicker.ARG_PROJECT_NAME).orEmpty()
                        val projectName = if (encodedProject.isNullOrBlank()) "" else Uri.decode(encodedProject)
                        val encodedProjectUid = backStackEntry.arguments?.getString(AppDestination.StoragePicker.ARG_PROJECT_UID).orEmpty()
                        val projectUid = if (encodedProjectUid.isNullOrBlank()) "" else Uri.decode(encodedProjectUid)
                        
                        // 获取之前选中的资产名称列表（用于回显）
                        val previousSelectedStorage = navController.previousBackStackEntry?.savedStateHandle?.get<List<String>>("selectedStorage") ?: emptyList()
                        val previousSelectedStorageFileIds = navController.previousBackStackEntry?.savedStateHandle?.get<List<String>>("selectedStorageFileIds") ?: emptyList()
                        
                        android.util.Log.d("MainActivity", "StoragePicker - 获取到的回显数据: fileIds=${previousSelectedStorageFileIds.joinToString()}")
                        android.util.Log.d("MainActivity", "StoragePicker - 获取到的回显数据: names=${previousSelectedStorage.joinToString()}")
                        
                        StoragePickerScreen(
                            projectUid = projectUid,
                            selectedAssetFileIds = previousSelectedStorageFileIds, // 传递当前选中的资产fileId列表用于回显
                            onBackClick = { navController.popBackStack() },
                            onItemSelected = { node ->
                                // 处理单选的数字资产项目（保持向后兼容）
                                navController.previousBackStackEntry?.savedStateHandle?.set("selectedAsset", node.name)
                                navController.popBackStack()
                            },
                            onMultipleItemsSelected = { selectedItems ->
                                // 处理多选的数字资产项目列表
                                val selectedNames = selectedItems.map { it.name }
                                val selectedFileIds = selectedItems.mapNotNull { it.fileId } // 提取fileId列表
                                navController.previousBackStackEntry?.savedStateHandle?.set("selectedStorage", selectedNames)
                                navController.previousBackStackEntry?.savedStateHandle?.set("selectedStorageFileIds", selectedFileIds) // 新增：保存fileId列表
                                navController.popBackStack()
                            }
                        )
                    }

                    // 5) ProjectInfo page
                    composable(
                        route = AppDestination.ProjectInfo.route,
                        arguments = listOf(
                            navArgument(AppDestination.ProjectInfo.ARG_PROJECT_NAME) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(AppDestination.ProjectInfo.ARG_PROJECT_UID) {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val nameEncoded = backStackEntry.arguments?.getString(AppDestination.ProjectInfo.ARG_PROJECT_NAME).orEmpty()
                        val projectName = if (nameEncoded.isBlank()) "" else Uri.decode(nameEncoded)
                        val uidEncoded = backStackEntry.arguments?.getString(AppDestination.ProjectInfo.ARG_PROJECT_UID).orEmpty()
                        val projectUid = if (uidEncoded.isBlank()) null else Uri.decode(uidEncoded)
                        ProjectInfoScreen(
                            projectName = projectName,
                            projectUid = projectUid,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    // 6) DefectDetail page
                    composable(
                        route = AppDestination.DefectDetail.route,
                        arguments = listOf(
                            navArgument(AppDestination.DefectDetail.ARG_NO) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(AppDestination.DefectDetail.ARG_PROJECT_UID) {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val noEncoded = backStackEntry.arguments?.getString(AppDestination.DefectDetail.ARG_NO).orEmpty()
                        val defectNo = Uri.decode(noEncoded)
                        val uidEncoded = backStackEntry.arguments?.getString(AppDestination.DefectDetail.ARG_PROJECT_UID).orEmpty()
                        val projectUidArg = Uri.decode(uidEncoded)
                        DefectDetailScreen(
                            defectNo = defectNo,
                            projectUid = projectUidArg.ifBlank { null },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    
                    // 7) DefectSort page
                    composable(
                        route = AppDestination.DefectSort.route,
                        arguments = listOf(
                            navArgument(AppDestination.DefectSort.ARG_PROJECT_UID) {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val uidEncoded = backStackEntry.arguments?.getString(AppDestination.DefectSort.ARG_PROJECT_UID).orEmpty()
                        val projectUid = Uri.decode(uidEncoded)
                        
                        // 获取 ProjectDetailViewModel 实例
                        val projectDetailViewModel: com.simsapp.ui.project.ProjectDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                        
                        // 从前一个页面获取缺陷列表数据，添加安全检查
                        val defects: List<HistoryDefectItem> = try {
                            navController.previousBackStackEntry?.savedStateHandle?.get<List<HistoryDefectItem>>("defects") ?: emptyList()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            emptyList()
                        }
                        
                        DefectSortScreen(
                            defects = defects,
                            viewModel = projectDetailViewModel,
                            onBack = { navController.popBackStack() },
                            onConfirm = { sortedDefects: List<HistoryDefectItem> ->
                                // 将排序结果返回给前一个页面，添加安全检查
                                try {
                                    navController.previousBackStackEntry?.savedStateHandle?.set("sortedDefects", sortedDefects)
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    // 即使出错也要返回，避免用户卡在排序页面
                                    navController.popBackStack()
                                }
                            }
                        )
                    }
                    
                    // 8) ProjectCleanup page
                    composable(route = AppDestination.ProjectCleanup.route) {
                        ProjectCleanupScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    }
                }
            }
        }
    }
}