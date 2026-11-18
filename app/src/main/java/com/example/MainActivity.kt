package com.example

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.UiState
import com.example.core.configureAppCheck
import com.example.data.model.AccountStatus
import com.example.data.repository.AuthRepository
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AudioLabScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BillingDashboardScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CodingStudioScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeveloperPortalScreen
import com.example.ui.screens.DocumentStudioScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.PhotoEditorScreen
import com.example.ui.screens.PricingScreen
import com.example.ui.screens.StudiosHubScreen
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.screens.WritingStudioScreen
import com.example.ui.theme.CerevixTheme
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.BillingViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.CreativeStudiosViewModel
import com.example.ui.viewmodel.DeveloperViewModel
import com.example.ui.viewmodel.WorkspaceViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            CerevixTheme {
                CerevixAppRoot()
            }
        }
    }
}

private data class TopNavDest(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
fun CerevixAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val currentUser by AuthRepository.authStateFlow(Firebase.auth)
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var autoSignInChecked by remember { mutableStateOf(currentUser != null) }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            AuthRepository.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                scope = scope,
                onComplete = { autoSignInChecked = true }
            )
        } else {
            autoSignInChecked = true
        }
    }

    if (!autoSignInChecked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = LuminousCyan)
        }
        return
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            CerevixAuthenticatedShell(currentUserId = user.uid)
        }
    }
}

@Composable
fun CerevixAuthenticatedShell(currentUserId: String) {
    val context = LocalContext.current
    val databaseId = remember(context) {
        context.applicationContext.getString(R.string.firestore_database_id)
    }
    val db = remember(databaseId) { FirebaseFirestore.getInstance(databaseId) }

    val workspaceVm: WorkspaceViewModel = viewModel(
        key = "workspace_$currentUserId",
        factory = WorkspaceViewModel.provideFactory(db, currentUserId)
    )
    val chatVm: ChatViewModel = viewModel(
        key = "chat_$currentUserId",
        factory = ChatViewModel.provideFactory(db, currentUserId)
    )
    val studiosVm: CreativeStudiosViewModel = viewModel(
        key = "studios_$currentUserId",
        factory = CreativeStudiosViewModel.provideFactory(db, currentUserId)
    )
    val app = context.applicationContext as Application
    val billingVm: BillingViewModel = viewModel(
        key = "billing_$currentUserId",
        factory = BillingViewModel.provideFactory(app)
    )
    val developerVm: DeveloperViewModel = viewModel(
        key = "developer_$currentUserId",
        factory = DeveloperViewModel.provideFactory(app)
    )

    val profileState by workspaceVm.userProfileState.collectAsStateWithLifecycle()
    val projectsState by workspaceVm.projectsState.collectAsStateWithLifecycle()
    val transactionsState by workspaceVm.transactionsState.collectAsStateWithLifecycle()
    val plans by workspaceVm.plansState.collectAsStateWithLifecycle()
    val announcements by workspaceVm.announcementsState.collectAsStateWithLifecycle()
    val isAdmin by workspaceVm.isAdmin.collectAsStateWithLifecycle()
    val actionMsg by workspaceVm.actionMessage.collectAsStateWithLifecycle()

    LaunchedEffect(actionMsg) {
        if (!actionMsg.isNullOrBlank()) {
            Toast.makeText(context, actionMsg, Toast.LENGTH_SHORT).show()
            workspaceVm.clearMessage()
        }
    }

    val profile = (profileState as? UiState.Success)?.data
    val credits = profile?.creditBalance ?: 100L
    val planName = profile?.planId ?: "FREE"
    val accountSuspended = profile?.resolvedStatus() == AccountStatus.SUSPENDED

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "dashboard"

    val topDestinations = listOf(
        TopNavDest("dashboard", "Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
        TopNavDest("chat", "AI Chat", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "nav_chat"),
        TopNavDest("studios", "Studios", Icons.Filled.AutoAwesomeMosaic, Icons.Outlined.AutoAwesomeMosaic, "nav_studios"),
        TopNavDest("account", "Account", Icons.Filled.Person, Icons.Outlined.Person, "nav_account")
    )
    val showTopLevelNav = currentRoute in topDestinations.map { it.route }

    fun navigateTop(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(ObsidianBg, MidnightNavy, ObsidianBg))
            )
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showTopLevelNav && !useRail) {
                    NavigationBar(
                        containerColor = MidnightNavy
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTop(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.tag),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = LuminousCyan,
                                    selectedTextColor = LuminousCyan,
                                    indicatorColor = ElectricBlue.copy(alpha = 0.25f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useRail) {
                    NavigationRail(containerColor = MidnightNavy) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTop(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.tag),
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = LuminousCyan,
                                    selectedTextColor = LuminousCyan,
                                    indicatorColor = ElectricBlue.copy(alpha = 0.25f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 920.dp)
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                profileState = profileState,
                                projectsState = projectsState,
                                announcements = announcements,
                                isAdmin = isAdmin,
                                onOpenRoute = { route ->
                                    if (route in topDestinations.map { it.route }) navigateTop(route)
                                    else navController.navigate(route)
                                },
                                onDeleteProject = { workspaceVm.deleteProject(it) }
                            )
                        }
                        composable("chat") {
                            ChatScreen(
                                viewModel = chatVm,
                                credits = credits,
                                planName = planName,
                                accountSuspended = accountSuspended
                            )
                        }
                        composable("studios") {
                            StudiosHubScreen(
                                providerStatuses = studiosVm.providerStatuses,
                                onOpenStudio = { route -> navController.navigate(route) }
                            )
                        }
                        composable("account") {
                            AccountScreen(
                                profileState = profileState,
                                transactionsState = transactionsState,
                                isAdmin = isAdmin,
                                onUpdatePreferences = { name, model ->
                                    workspaceVm.updatePreferences(name, model)
                                },
                                onClearWorkspace = { workspaceVm.clearWorkspaceData() },
                                onOpenPricing = { navController.navigate("pricing") },
                                onOpenBilling = { navController.navigate("billing") },
                                onOpenDeveloper = { navController.navigate("developer") },
                                onOpenAdmin = {
                                    if (isAdmin) navController.navigate("admin")
                                }
                            )
                        }
                        composable("pricing") {
                            PricingScreen(
                                plans = plans,
                                currentPlanId = planName,
                                credits = credits,
                                billingViewModel = billingVm,
                                onOpenBilling = { navController.navigate("billing") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("billing") {
                            BillingDashboardScreen(
                                billingViewModel = billingVm,
                                credits = credits,
                                currentPlanId = planName,
                                onOpenPricing = { navController.navigate("pricing") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("developer") {
                            DeveloperPortalScreen(
                                viewModel = developerVm,
                                credits = credits,
                                planName = planName,
                                onOpenPricing = { navController.navigate("pricing") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("admin") {
                            if (isAdmin) {
                                val adminVm: AdminViewModel = viewModel(
                                    key = "admin_$currentUserId",
                                    factory = AdminViewModel.provideFactory(app, db)
                                )
                                AdminDashboardScreen(
                                    adminViewModel = adminVm,
                                    plans = plans,
                                    credits = credits,
                                    planName = planName,
                                    onBack = { navController.popBackStack() }
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Administrator access required", color = TextPrimary)
                                }
                            }
                        }
                        composable("studio_image") {
                            ImageStudioScreen(
                                viewModel = studiosVm,
                                credits = credits,
                                planName = planName,
                                accountSuspended = accountSuspended,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("studio_photo_editor") {
                            PhotoEditorScreen(
                                credits = credits,
                                planName = planName,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("studio_audio") {
                            AudioLabScreen(
                                viewModel = studiosVm,
                                credits = credits,
                                planName = planName,
                                accountSuspended = accountSuspended,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("studio_writing") {
                            WritingStudioScreen(
                                viewModel = studiosVm,
                                credits = credits,
                                planName = planName,
                                accountSuspended = accountSuspended,
                                onSaveProject = { t, cat, p, o, m, c ->
                                    workspaceVm.saveProject(t, cat, p, o, m, c)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("studio_coding") {
                            CodingStudioScreen(
                                viewModel = studiosVm,
                                credits = credits,
                                planName = planName,
                                accountSuspended = accountSuspended,
                                onSaveProject = { t, cat, p, o, m, c ->
                                    workspaceVm.saveProject(t, cat, p, o, m, c)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("studio_document") {
                            DocumentStudioScreen(
                                viewModel = studiosVm,
                                credits = credits,
                                planName = planName,
                                accountSuspended = accountSuspended,
                                onSaveProject = { t, cat, p, o, m, c ->
                                    workspaceVm.saveProject(t, cat, p, o, m, c)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("studio_video") {
                            VideoStudioScreen(
                                viewModel = studiosVm,
                                credits = credits,
                                planName = planName,
                                accountSuspended = accountSuspended,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
