package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.database.Feedback
import com.example.database.MealMenu
import com.example.database.NotificationLog
import com.example.database.UserProfile
import com.example.database.GoogleFormSyncLog
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.testTag

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Safely request push notification runtime permissions on Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        setContent {
            val viewModel: MessViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = themeMode) {
                MainDashboardScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainDashboardScreen(
    viewModel: MessViewModel = viewModel()
) {
    val currentDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val menus by viewModel.allMenus.collectAsStateWithLifecycle()
    val feedbacks by viewModel.allFeedback.collectAsStateWithLifecycle()
    val notifications by viewModel.allNotifications.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdminMode.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()

    val profiles by viewModel.allUserProfiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val syncLogs by viewModel.allSyncLogs.collectAsStateWithLifecycle()
    val googleFormId by viewModel.googleFormId.collectAsStateWithLifecycle()
    val googleAppsScriptUrl by viewModel.googleAppsScriptUrl.collectAsStateWithLifecycle()

    val isChatEnabled by viewModel.isChatEnabled.collectAsStateWithLifecycle()
    val isFeedbackEnabled by viewModel.isFeedbackEnabled.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0 = Menu/Ratings, 1 = Chatbot, 2 = Notifications, 3 = Profile

    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogTargetProfile by remember { mutableStateOf<UserProfile?>(null) }
    var pinDialogTargetToggleAdminMode by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinErrorText by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Restaurant, contentDescription = "Menu") },
                    label = { Text("Menu") }
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.ChatBubble, contentDescription = "BiteBot AI") },
                    label = { Text("BiteBot AI") }
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Activity Log") },
                    label = { 
                        BadgedBox(
                            badge = { 
                                val unreadCount = notifications.size
                                if (unreadCount > 0) {
                                    Badge { Text("$unreadCount") }
                                }
                            }
                        ) {
                            Text("Log")
                        }
                    }
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Profile Management") },
                    label = { Text("Profile") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    )
                )
        ) {
            // --- HEADER WITH LOGO AND ADMIN TOGGLE ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RestaurantMenu,
                                contentDescription = "App logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Office Mess",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (isAdmin) "Admin Terminal • Managing" else "Staff Portal • $userEmail",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // --- ROLE QUICK SWITCHER ---
                    Button(
                        onClick = { 
                            if (isAdmin) {
                                // Demoting to Staff happens instantly
                                // If using Admin/SuperAdmin, toggle the mode so we retain our email/profile
                                if (activeProfile?.role == "Super Admin" || activeProfile?.role == "Admin") {
                                    viewModel.toggleAdminMode()
                                } else {
                                    val targetStaff = profiles.find { it.role == "Staff" }
                                    if (targetStaff != null) {
                                        viewModel.switchProfile(targetStaff)
                                    } else {
                                        viewModel.toggleAdminMode()
                                    }
                                }
                            } else {
                                // Elevating to Admin requires Verification PIN!
                                if (activeProfile?.role == "Super Admin" || activeProfile?.role == "Admin") {
                                    pinDialogTargetProfile = null
                                    pinDialogTargetToggleAdminMode = true
                                } else {
                                    val targetAdmin = profiles.find { it.role == "Admin" || it.role == "Super Admin" }
                                    pinDialogTargetProfile = targetAdmin
                                    pinDialogTargetToggleAdminMode = false
                                }
                                enteredPin = ""
                                pinErrorText = ""
                                showPinDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (isAdmin) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.PersonOutline,
                            contentDescription = "Role Mode Icon",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAdmin) "ADMIN" else "STAFF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> MenuTabContent(viewModel, currentDay, menus, feedbacks, isAdmin, isFeedbackEnabled, activeProfile)
                    1 -> ChatbotTabContent(viewModel, isChatEnabled, isAdmin, activeProfile)
                    2 -> NotificationTabContent(viewModel, notifications, feedbacks, isAdmin, profiles)
                    3 -> ProfileTabContent(
                        viewModel = viewModel,
                        profiles = profiles,
                        activeProfile = activeProfile,
                        syncLogs = syncLogs,
                        googleFormId = googleFormId,
                        googleAppsScriptUrl = googleAppsScriptUrl,
                        onProfileSwitchRequested = { targetProfile ->
                            if (targetProfile.role == "Admin" || targetProfile.role == "Super Admin") {
                                pinDialogTargetProfile = targetProfile
                                pinDialogTargetToggleAdminMode = false
                                enteredPin = ""
                                pinErrorText = ""
                                showPinDialog = true
                            } else {
                                viewModel.switchProfile(targetProfile)
                            }
                        }
                    )
                }
            }
        }

        if (showPinDialog) {
            val adminPinVal by viewModel.adminPin.collectAsStateWithLifecycle()
            var pinVisible by remember { mutableStateOf(false) }
            
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Administrator PIN Check",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "This action requires entering the 4-digit Administrator Access PIN (Default: 8888).",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        OutlinedTextField(
                            value = enteredPin,
                            onValueChange = { 
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                    enteredPin = it
                                    if (pinErrorText.isNotEmpty()) pinErrorText = ""
                                }
                            },
                            label = { Text("4-Digit Access PIN") },
                            modifier = Modifier.fillMaxWidth().testTag("admin_pin_input"),
                            singleLine = true,
                            visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = {
                                IconButton(onClick = { pinVisible = !pinVisible }) {
                                    Icon(
                                        imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (pinVisible) "Hide PIN" else "Show PIN"
                                    )
                                }
                            }
                        )
                        
                        if (pinErrorText.isNotEmpty()) {
                            Text(
                                text = pinErrorText,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (viewModel.verifyAdminPin(enteredPin)) {
                                // Pin verification success!
                                if (pinDialogTargetProfile != null) {
                                    viewModel.switchProfile(pinDialogTargetProfile!!)
                                } else if (pinDialogTargetToggleAdminMode) {
                                    viewModel.toggleAdminMode()
                                }
                                showPinDialog = false
                            } else {
                                pinErrorText = "❌ Incorrect Access PIN. Please try again."
                                enteredPin = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("verify_pin_button")
                    ) {
                        Text("Verify Access")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}

// ============================================
// TAB 1: MENU AND RATINGS
// ============================================
@Composable
fun MenuTabContent(
    viewModel: MessViewModel,
    currentDay: String,
    menus: List<MealMenu>,
    feedbacks: List<Feedback>,
    isAdmin: Boolean,
    isFeedbackEnabled: Boolean,
    activeProfile: UserProfile?
) {
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    var editingMealState by remember { mutableStateOf<MealMenu?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- HORIZONTAL DAY PICKER ---
        Text(
            text = "Select Day of Week",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(daysOfWeek) { day ->
                val isSelected = day.equals(currentDay, ignoreCase = true)
                val systemToday = SimpleDateFormat("EEEE", Locale.US).format(Date())
                val isToday = day.equals(systemToday, ignoreCase = true)

                Card(
                    modifier = Modifier
                        .clickable { viewModel.selectDay(day) }
                        .border(
                            width = if (isToday) 2.dp else 0.dp,
                            color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = day.take(3),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isToday) {
                                Text(
                                    text = "TODAY",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 8.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Filter menus and display details
        val selectedDayMenus = menus.filter { it.dayOfWeek.equals(currentDay, ignoreCase = true) }

        if (selectedDayMenus.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Populating fresh menus for $currentDay...")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Staff Food Access Status Banner
                if (!isAdmin && activeProfile != null) {
                    item {
                        val status = activeProfile.foodApprovalStatus
                        val bgColor: Color
                        val textColor: Color
                        val icon: androidx.compose.ui.graphics.vector.ImageVector
                        val title: String
                        val description: String

                        when (status) {
                            "Approved" -> {
                                bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                textColor = MaterialTheme.colorScheme.primary
                                icon = Icons.Default.CheckCircle
                                title = "Food Ticket Active ✅"
                                description = "Your food service access is approved. You're clear to enjoy cafeteria meals & rate your experience."
                            }
                            "Rejected" -> {
                                bgColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                textColor = MaterialTheme.colorScheme.error
                                icon = Icons.Default.Dangerous
                                title = "Food Ticket Rejected ❌"
                                description = "Your food service request has been rejected. Cafeteria meal scans and operations are currently suspended."
                            }
                            else -> {
                                bgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                textColor = MaterialTheme.colorScheme.secondary
                                icon = Icons.Default.Pending
                                title = "Awaiting Admin Approval ⏳"
                                description = "Your registration is recorded. Cafeteria meals and ratings will unlock once an administrator approves your profile."
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = bgColor),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }

                // Info Banner
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Celebration, contentDescription = "Free Menu Badge", tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "🌱 Complete Cook-free mess! All meals are free for employees. No ordering or checkout required.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                items(selectedDayMenus) { mealMenu ->
                    val mealFeedbacks = feedbacks.filter { 
                        it.mealType.equals(mealMenu.mealType, ignoreCase = true) && 
                        it.dateString.equals(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) // Today's review
                    }
                    val averageRating = if (mealFeedbacks.isNotEmpty()) {
                        mealFeedbacks.map { it.rating }.average().toFloat()
                    } else 0f

                    MealMenuCard(
                        viewModel = viewModel,
                        mealMenu = mealMenu,
                        averageRating = averageRating,
                        reviewCount = mealFeedbacks.size,
                        isAdmin = isAdmin,
                        activeProfile = activeProfile,
                        isFeedbackEnabled = isFeedbackEnabled,
                        onEditClick = { editingMealState = mealMenu },
                        onFeedbackSubmit = { rating, comment ->
                            viewModel.submitFeedback(mealMenu.mealType, rating, comment)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    // --- ADMIN EDIT MEAL SHEET DIALOG ---
    if (editingMealState != null) {
        val meal = editingMealState!!
        var foodInput by remember { mutableStateOf(meal.menuItems) }
        var isCookOffSwitch by remember { mutableStateOf(meal.isCookOff) }

        AlertDialog(
            onDismissRequest = { editingMealState = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EditCalendar, contentDescription = "Edit Menu", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configure ${meal.dayOfWeek} ${meal.mealType}")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Set up current items or flag this meal as a cook-off.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = foodInput,
                        onValueChange = { foodInput = it },
                        label = { Text("Food Menu Items") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        placeholder = { Text("e.g. Rice, Dal Tadka, Roti") },
                        enabled = !isCookOffSwitch
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { isCookOffSwitch = !isCookOffSwitch }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Cook Off (Kitchen Closed)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Toggle to disable food service", fontSize = 11.sp)
                        }
                        Switch(
                            checked = isCookOffSwitch,
                            onCheckedChange = { isCookOffSwitch = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalFood = if (isCookOffSwitch) "COOK OFF" else foodInput
                        viewModel.updateMenu(meal.dayOfWeek, meal.mealType, finalFood, isCookOffSwitch)
                        editingMealState = null
                    }
                ) {
                    Text("Save & Notify Staff")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMealState = null }) {
                    Text("Discard")
                }
            }
        )
    }
}

@Composable
fun MealMenuCard(
    viewModel: MessViewModel,
    mealMenu: MealMenu,
    averageRating: Float,
    reviewCount: Int,
    isAdmin: Boolean,
    activeProfile: UserProfile?,
    isFeedbackEnabled: Boolean,
    onEditClick: () -> Unit,
    onFeedbackSubmit: (Int, String) -> Unit
) {
    val context = LocalContext.current
    var staffRatingClicked by remember { mutableStateOf(0) }
    var staffCommentText by remember { mutableStateOf("") }
    var showRatingForm by remember { mutableStateOf(false) }

    val bt by viewModel.breakfastTime.collectAsStateWithLifecycle()
    val lt by viewModel.lunchTime.collectAsStateWithLifecycle()
    val dt by viewModel.dinnerTime.collectAsStateWithLifecycle()
    val st by viewModel.snacksTime.collectAsStateWithLifecycle()

    val mealColorPair = when (mealMenu.mealType) {
        "Breakfast" -> Pair(Color(0xFFFFF9C4), Color(0xFFF57F17)) // Yellow morning
        "Lunch" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))     // Emerald lunch
        "Dinner" -> Pair(Color(0xFFE8EAF6), Color(0xFF283593))    // Evening indigo
        "Snacks", "Tea", "Snacks/Tea" -> Pair(Color(0xFFFFE0B2), Color(0xFFE65100)) // Warm orange tea
        else -> Pair(Color(0xFFF5F5F5), Color(0xFF424242))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Type + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(mealColorPair.first),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (mealMenu.mealType) {
                                "Breakfast" -> Icons.Default.LocalCafe
                                "Lunch" -> Icons.Default.Restaurant
                                "Dinner" -> Icons.Default.Fastfood
                                "Snacks", "Tea", "Snacks/Tea" -> Icons.Default.FreeBreakfast
                                else -> Icons.Default.FoodBank
                            },
                            contentDescription = mealMenu.mealType,
                            tint = mealColorPair.second,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = mealMenu.mealType,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val timing = when (mealMenu.mealType) {
                            "Breakfast" -> bt
                            "Lunch" -> lt
                            "Dinner" -> dt
                            "Snacks", "Tea", "Snacks/Tea" -> st
                            else -> "Meal hours"
                        }
                        Text(
                            text = timing,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Average Rating Badge
                if (averageRating > 0f) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(mealColorPair.first)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = mealColorPair.second,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f", averageRating),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = mealColorPair.second
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Menu Details
            if (mealMenu.isCookOff) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DoNotDisturb,
                        contentDescription = "Cook Off Icon",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "COOK OFF DAY",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Kitchen staff is on rest leave. Food is not scheduled.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                Text(
                    text = mealMenu.menuItems,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row (Edit or Rate toggle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAdmin) {
                    Button(
                        onClick = onEditClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Menu", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Configure Menu", fontSize = 12.sp)
                    }
                } else if (!mealMenu.isCookOff) {
                    if (!isFeedbackEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ratings paused by admin", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                        }
                    } else if (activeProfile?.foodApprovalStatus != "Approved") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Awaiting food activation", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                        }
                    } else {
                        TextButton(
                            onClick = { showRatingForm = !showRatingForm },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (showRatingForm) Icons.Default.Close else Icons.Default.RateReview,
                                contentDescription = "Rate Button",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showRatingForm) "Close Review" else "Submit Rating & Review", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Inline rating submission form for employees
            AnimatedVisibility(visible = showRatingForm) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        "How was today's ${mealMenu.mealType}?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Star Select Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (i in 1..5) {
                            Icon(
                                imageVector = if (i <= staffRatingClicked) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "$i Stars",
                                tint = if (i <= staffRatingClicked) Color(0xFFFFB300) else Color.LightGray,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { staffRatingClicked = i }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = staffCommentText,
                        onValueChange = { staffCommentText = it },
                        placeholder = { Text("What did you like or dislike? (Optional feedback)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (staffRatingClicked == 0) {
                                Toast.makeText(context, "Please tap stars to select a rating first", Toast.LENGTH_SHORT).show()
                            } else {
                                onFeedbackSubmit(staffRatingClicked, staffCommentText)
                                Toast.makeText(context, "Thank you for rating our meals!", Toast.LENGTH_SHORT).show()
                                staffRatingClicked = 0
                                staffCommentText = ""
                                showRatingForm = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Submit Meal Rating")
                    }
                }
            }
        }
    }
}

// ============================================
// TAB 2: BITEBOT AI CHATBOT
// ============================================
@Composable
fun ChatbotTabContent(
    viewModel: MessViewModel,
    isChatEnabled: Boolean,
    isAdmin: Boolean,
    activeProfile: UserProfile?
) {
    if (!isChatEnabled && !isAdmin) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "BiteBot AI Suspended 🔒",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The chat assistant feature has been disabled globally by the Office Mess Administrator.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    if (!isAdmin && activeProfile?.foodApprovalStatus != "Approved") {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Pending,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Chat Access Locked ⏳",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your profile is registered, but BiteBot can only assist you once your food service is approved by an administrator.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isSearching by viewModel.isChatbotLoading.collectAsStateWithLifecycle()
    var promptInput by remember { mutableStateOf("") }
    val apiKey = BuildConfig.GEMINI_API_KEY
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Chat Headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BiteBot Assistant 🤖",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Powered by Gemini AI • Connected to current menu",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
            TextButton(
                onClick = { viewModel.clearChat() },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear", fontSize = 12.sp)
            }
        }

        // Suggestions Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val suggestions = listOf("What is lunch today?", "Friday breakfast?", "Lunch tomorrow")
            suggestions.forEach { suggestion ->
                val activeBg = MaterialTheme.colorScheme.surfaceVariant
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(activeBg)
                        .clickable {
                            promptInput = suggestion
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(suggestion, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Chat History List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatMessages) { msg ->
                ChatBubble(msg)
            }
            if (isSearching) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("BiteBot is thinking...", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Bottom text bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                placeholder = { Text("Ask BiteBot about food timings/menu...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (promptInput.isNotEmpty()) {
                                viewModel.sendChatMessage(promptInput, apiKey)
                                promptInput = ""
                                keyboardController?.hide()
                            }
                        },
                        enabled = promptInput.isNotEmpty() && !isSearching
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = if (promptInput.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.LightGray
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val bubbleColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val AlignmentAlign = if (message.isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = AlignmentAlign
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(if (message.isUser) 1.dp else 2.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!message.isUser) {
                    Row(
                        modifier = Modifier.padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Bot",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "BiteBot",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ============================================
// TAB 3: REAL-TIME NOTIFICATION LOGS & ALERTS
// ============================================
@Composable
fun NotificationTabContent(
    viewModel: MessViewModel,
    notifications: List<NotificationLog>,
    feedbacks: List<Feedback>,
    isAdmin: Boolean,
    profiles: List<UserProfile> = emptyList()
) {
    var feedbackSelectedView by remember { mutableStateOf(false) } // False = Logs/Alert Simulators, True = Admin Staff Reviews

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Admin Segment Selection: Logs and Trigger vs Staff Feedback reviews
        if (isAdmin) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (!feedbackSelectedView) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { feedbackSelectedView = false }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Alert Simulator",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (!feedbackSelectedView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (feedbackSelectedView) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { feedbackSelectedView = true }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Staff Reviews (${feedbacks.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (feedbackSelectedView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (feedbackSelectedView && isAdmin) {
            // View ratings submitted by employees
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Employee Ratings Log 📊",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Total reviews: ${feedbacks.size}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }

            if (feedbacks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.RateReview,
                            contentDescription = "Empty",
                            tint = Color.LightGray,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No feedback ratings received yet.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(feedbacks) { review ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Campaign,
                                            contentDescription = "Review MealType",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            review.mealType,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Print stars
                                    Row {
                                        for (i in 1..5) {
                                            Icon(
                                                imageVector = if (i <= review.rating) Icons.Default.Star else Icons.Outlined.StarBorder,
                                                contentDescription = "star",
                                                tint = Color(0xFFFFB300),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }

                                if (review.comment.isNotEmpty()) {
                                    Text(
                                        "\"${review.comment}\"",
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val matchingProfile = profiles.find { it.email.equals(review.submittedBy, ignoreCase = true) }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "By: ${matchingProfile?.name ?: review.submittedBy}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (matchingProfile != null) {
                                            Text(
                                                text = "${review.submittedBy} • ${matchingProfile.department}",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteFeedback(review.id) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "delete", tint = Color.Red, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Logs and push Notification triggers UI
            Text(
                text = "⚡ Real-time Push Alert Simulator",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Tap below to instantly broadcast simulated meal-time alert push notifications to the device system tray tray:",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.simulateMealTimingNotification("Breakfast") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Breakfast 🌅", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { viewModel.simulateMealTimingNotification("Lunch") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Lunch 🍱", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { viewModel.simulateMealTimingNotification("Dinner") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Dinner 🌙", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Notification History List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Notification History Bell 🔔",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { viewModel.clearNotifications() }) {
                    Text("Clear Logs", fontSize = 11.sp)
                }
            }

            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = "Notification Empty",
                            tint = Color.LightGray,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No notifications received yet.",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(notifications) { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (log.category == "Reminders") Icons.Default.Campaign else Icons.Default.Update,
                                    contentDescription = "Category symbol",
                                    tint = if (log.category == "Reminders") Color.Red else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        log.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        log.body,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                    Text(
                                        SimpleDateFormat("hh:mm a • EEE, dd MMM", Locale.US).format(Date(log.timestamp)),
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================
// TAB 4: PROFILE MANAGEMENT AND SYNC BACKEND
// ============================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTabContent(
    viewModel: MessViewModel,
    profiles: List<UserProfile>,
    activeProfile: UserProfile?,
    syncLogs: List<GoogleFormSyncLog>,
    googleFormId: String,
    googleAppsScriptUrl: String,
    onProfileSwitchRequested: (UserProfile) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var formName by remember { mutableStateOf("") }
    var formEmail by remember { mutableStateOf("") }
    var formRole by remember { mutableStateOf("Staff") }
    var formDept by remember { mutableStateOf("Engineering") }
    
    var tempFormId by remember(googleFormId) { mutableStateOf(googleFormId) }
    var tempAppsScriptUrl by remember(googleAppsScriptUrl) { mutableStateOf(googleAppsScriptUrl) }
    var formsSyncExpanded by remember { mutableStateOf(true) }
    var appsScriptSyncExpanded by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val bt by viewModel.breakfastTime.collectAsStateWithLifecycle()
    val lt by viewModel.lunchTime.collectAsStateWithLifecycle()
    val dt by viewModel.dinnerTime.collectAsStateWithLifecycle()
    val st by viewModel.snacksTime.collectAsStateWithLifecycle()

    var tempBreakfastTime by remember(bt) { mutableStateOf(bt) }
    var tempLunchTime by remember(lt) { mutableStateOf(lt) }
    var tempDinnerTime by remember(dt) { mutableStateOf(dt) }
    var tempSnacksTime by remember(st) { mutableStateOf(st) }
    var timingsSyncExpanded by remember { mutableStateOf(true) }

    val chatEnabled by viewModel.isChatEnabled.collectAsStateWithLifecycle()
    val feedbackEnabled by viewModel.isFeedbackEnabled.collectAsStateWithLifecycle()
    val bulkSyncStatus by viewModel.bulkSyncStatus.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // --- ACTIVE PROFILE SUMMARY ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Monogram Avatar
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (activeProfile?.name ?: "U").take(1).uppercase(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = activeProfile?.name ?: "Guest User",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Text(
                        text = activeProfile?.email ?: "Sign up to track details",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Role badge
                        // Role badge
                        val roleIcon = when (activeProfile?.role) {
                            "Super Admin" -> Icons.Default.Star
                            "Admin" -> Icons.Default.AdminPanelSettings
                            else -> Icons.Default.Person
                        }
                        SuggestionChip(
                            onClick = {},
                            label = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = roleIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(activeProfile?.role ?: "Guest", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                        // Department badge
                        if (activeProfile?.department != null) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(activeProfile.department, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // --- VISUAL APP THEME MODE BAR ---
        item {
            val currentThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🎨 Visual App Theme Mode",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Choose light, dark, or system-wide adaptive layouts.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf("System", "Light", "Dark")
                        modes.forEach { mode ->
                            val isSelected = currentThemeMode == mode
                            val bg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            val tc = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            val borderCol = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bg)
                                    .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.setThemeMode(mode) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    val icon = when(mode) {
                                        "Light" -> Icons.Default.LightMode
                                        "Dark" -> Icons.Default.DarkMode
                                        else -> Icons.Default.Settings
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = tc,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = mode,
                                        color = tc,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- SWITCH AND ADD PROFILE ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Switch Profile 👥",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        TextButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create Profile")
                        }
                    }
                    
                    if (profiles.isEmpty()) {
                        Text(
                            text = "No other profiles registered yet.",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(profiles) { profile ->
                                val isSelected = activeProfile?.email == profile.email
                                Card(
                                    modifier = Modifier
                                        .clickable { onProfileSwitchRequested(profile) }
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = profile.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val roleBadgeColor = when (profile.role) {
                                            "Super Admin" -> Color(0xFF9C27B0)
                                            "Admin" -> Color.Red
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                        Text(
                                            text = profile.role.uppercase(),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            color = roleBadgeColor,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
                         // --- ADMIN ONLY OPERATIONS: GOOGLE FORMS SYNC AND USER AUDIT ---
        if (activeProfile?.role == "Admin" || activeProfile?.role == "Super Admin") {
            // Google Form Sync Settings Panel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { formsSyncExpanded = !formsSyncExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⚙️ Google Forms Sync Settings",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = if (formsSyncExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (formsSyncExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        if (formsSyncExpanded) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Configure the Google Form ID. The application connects locally and automatically submits profiles, ratings, and food menu configurations.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )

                                OutlinedTextField(
                                    value = tempFormId,
                                    onValueChange = { tempFormId = it },
                                    label = { Text("Google Form Submission ID") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { 
                                            viewModel.setGoogleFormId(tempFormId)
                                            Toast.makeText(context, "Saved Google Form ID!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.Save, contentDescription = "Save ID")
                                        }
                                    }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.setGoogleFormId(tempFormId)
                                            Toast.makeText(context, "Saved Google Form ID: $tempFormId", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Apply Settings", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val currentId = tempFormId.trim().ifEmpty { googleFormId.trim() }
                                            val finalUrl = if (currentId.startsWith("http")) {
                                                currentId
                                            } else {
                                                "https://docs.google.com/forms/d/e/$currentId/viewform"
                                            }
                                            try {
                                                val intent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(finalUrl)
                                                )
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                try {
                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("Copied URL", finalUrl)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Link copied to clipboard (No web browser found on device)", Toast.LENGTH_LONG).show()
                                                } catch (clipEx: Exception) {
                                                    Toast.makeText(context, "Failed to open link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Open Link", fontSize = 12.sp)
                                    }
                                }

                                HorizontalDivider(
                                    color = Color.LightGray.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                Text(
                                    text = "📊 Bulk Synchronization Tool",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Text(
                                    text = "Submit a full week of pre-populated, realistic dataset (21 meals, 5 profiles, and 5 detailed food reviews) to Google Forms & Apps Script to instantly test your connected spreadsheet and charts.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )

                                if (bulkSyncStatus != null) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = bulkSyncStatus ?: "",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth().height(4.dp),
                                            color = MaterialTheme.colorScheme.secondary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            viewModel.syncOneWeekTestData { success, fail ->
                                                Toast.makeText(
                                                    context,
                                                    "Bulk sync finished! Successfully sent: $success, Failed: $fail",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sync One-Week Test Data", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Google Apps Script Sync Settings Panel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { appsScriptSyncExpanded = !appsScriptSyncExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⚡ Google Apps Script Sync Settings",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Icon(
                                imageVector = if (appsScriptSyncExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (appsScriptSyncExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        if (appsScriptSyncExpanded) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Configure your Google Apps Script Web App URL. Outbound data submissions (profiles, approvals, menus, reviews) will synchronize with this script dynamically in real-time.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )

                                OutlinedTextField(
                                    value = tempAppsScriptUrl,
                                    onValueChange = { tempAppsScriptUrl = it },
                                    label = { Text("Google Apps Script Web App URL") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { 
                                            viewModel.setGoogleAppsScriptUrl(tempAppsScriptUrl)
                                            Toast.makeText(context, "Saved Web App URL!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.Save, contentDescription = "Save Script URL")
                                        }
                                    }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.setGoogleAppsScriptUrl(tempAppsScriptUrl)
                                            Toast.makeText(context, "Saved Web App URL successfully!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Apply Script Endpoint", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val finalUrl = tempAppsScriptUrl.trim().ifEmpty { googleAppsScriptUrl.trim() }
                                            if (finalUrl.isNotEmpty() && finalUrl.startsWith("http")) {
                                                try {
                                                    val intent = android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse(finalUrl)
                                                    )
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    try {
                                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        val clip = android.content.ClipData.newPlainText("Copied URL", finalUrl)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "Script Link copied to clipboard (No web browser found on device)", Toast.LENGTH_LONG).show()
                                                    } catch (clipEx: Exception) {
                                                        Toast.makeText(context, "Failed to open link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, "No valid script URL configured", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Visit Web App", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ⏰ Meal Timings & Notification Broadcast Config Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { timingsSyncExpanded = !timingsSyncExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⏰ Meal Timings & Notification Broadcast",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = if (timingsSyncExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (timingsSyncExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (timingsSyncExpanded) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Configure official timings for breakfast, lunch, dinner, and snacks. These timings are shown on the menus and are sent in real-time alert logs.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )

                                OutlinedTextField(
                                    value = tempBreakfastTime,
                                    onValueChange = { tempBreakfastTime = it },
                                    label = { Text("Breakfast Timing") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { 
                                            viewModel.setMealTime("breakfast", tempBreakfastTime)
                                            Toast.makeText(context, "Saved Breakfast Time!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.Save, contentDescription = "Save Breakfast Time")
                                        }
                                    }
                                )

                                OutlinedTextField(
                                    value = tempLunchTime,
                                    onValueChange = { tempLunchTime = it },
                                    label = { Text("Lunch Timing") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { 
                                            viewModel.setMealTime("lunch", tempLunchTime)
                                            Toast.makeText(context, "Saved Lunch Time!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.Save, contentDescription = "Save Lunch Time")
                                        }
                                    }
                                )

                                OutlinedTextField(
                                    value = tempDinnerTime,
                                    onValueChange = { tempDinnerTime = it },
                                    label = { Text("Dinner Timing") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { 
                                            viewModel.setMealTime("dinner", tempDinnerTime)
                                            Toast.makeText(context, "Saved Dinner Time!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.Save, contentDescription = "Save Dinner Time")
                                        }
                                    }
                                )

                                OutlinedTextField(
                                    value = tempSnacksTime,
                                    onValueChange = { tempSnacksTime = it },
                                    label = { Text("Snacks/Tea Timing") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { 
                                            viewModel.setMealTime("snacks", tempSnacksTime)
                                            Toast.makeText(context, "Saved Snacks Time!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.Save, contentDescription = "Save Snacks Time")
                                        }
                                    }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.setMealTime("breakfast", tempBreakfastTime)
                                            viewModel.setMealTime("lunch", tempLunchTime)
                                            viewModel.setMealTime("dinner", tempDinnerTime)
                                            viewModel.setMealTime("snacks", tempSnacksTime)
                                            Toast.makeText(context, "All timings applied successfully!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Apply All Timings", fontSize = 12.sp)
                                    }
                                }

                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                                Text(
                                    text = "📣 Real-time Broadcast Simulator: Broadcast a notification for testing.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val list = listOf("Breakfast", "Lunch", "Dinner", "Snacks")
                                    list.forEach { meal ->
                                        OutlinedButton(
                                            onClick = { 
                                                viewModel.simulateMealTimingNotification(meal)
                                                Toast.makeText(context, "Simulated Broadcast for $meal Successful!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(meal, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Google Sheets Sync Logs (Form submissions logs)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📨 Server Integration Sync Logs",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            TextButton(onClick = { viewModel.clearAllSyncLogs() }) {
                                Text("Clear", fontSize = 11.sp)
                            }
                        }
                        
                        Text(
                            text = "Audits automatic submissions with payload details and server response statuses:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (syncLogs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No submissions synchronized yet.", color = Color.LightGray, fontSize = 12.sp)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                syncLogs.take(15).forEach { log ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Type: ${log.entityType} (${log.entityId})",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                
                                                // Status Indicator Chip
                                                val statusColor = if (log.status.startsWith("Success")) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.15f)),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = log.status.uppercase(),
                                                        color = statusColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 8.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Payload parameters: " + log.payload,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = SimpleDateFormat("hh:mm:ss a • dd MMM", Locale.US).format(Date(log.timestamp)),
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Core App Feature Control Panel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🔧 Core App Feature Control",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "Enable or disable major features globally for all standard Staff members.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )

                        // Chat Switcher
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubble,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("BiteBot AI Chat Option", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Toggle conversational menu Q&A helpers", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                                }
                            }
                            Switch(
                                checked = chatEnabled,
                                onCheckedChange = { viewModel.setChatEnabled(it) }
                            )
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))

                        // Feedback Switcher
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.RateReview,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Staff Feedback & Rating Option", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Toggle submission of daily meal reviews", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                                }
                            }
                            Switch(
                                checked = feedbackEnabled,
                                onCheckedChange = { viewModel.setFeedbackEnabled(it) }
                            )
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))

                        // Admin Access PIN Control section
                        var showChangePinState by remember { mutableStateOf(false) }
                        val currentAdminPin by viewModel.adminPin.collectAsStateWithLifecycle()
                        var newPinInput by remember { mutableStateOf("") }
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Administrator Access PIN", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Current: $currentAdminPin", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                                    }
                                }
                                TextButton(onClick = { showChangePinState = !showChangePinState }) {
                                    Text(if (showChangePinState) "Cancel" else "Change")
                                }
                            }
                            
                            if (showChangePinState) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newPinInput,
                                        onValueChange = { 
                                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                                newPinInput = it
                                            }
                                        },
                                        label = { Text("New 4-Digit PIN", fontSize = 11.sp) },
                                        placeholder = { Text("e.g. 1234", fontSize = 11.sp) },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 12.sp),
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    
                                    Button(
                                        onClick = {
                                            if (newPinInput.length == 4) {
                                                viewModel.updateAdminPin(newPinInput)
                                                Toast.makeText(context, "Admin Access PIN updated to $newPinInput!", Toast.LENGTH_SHORT).show()
                                                showChangePinState = false
                                                newPinInput = ""
                                            } else {
                                                Toast.makeText(context, "PIN must be exactly 4 digits!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("Save", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // User Profile audit management directory list
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "📋 User Profile Directory",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            profiles.forEach { profile ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            profile.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${profile.email} • ${profile.department}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))
                                        val statusColor = when (profile.foodApprovalStatus) {
                                            "Approved" -> Color(0xFF2E7D32)
                                            "Rejected" -> Color(0xFFC62828)
                                            else -> Color(0xFFEF6C00)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Food Access: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                                            Text(
                                                text = profile.foodApprovalStatus,
                                                color = statusColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }

                                        if (profile.role == "Staff") {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (profile.foodApprovalStatus != "Approved") {
                                                    OutlinedButton(
                                                        onClick = { viewModel.updateFoodApprovalStatus(profile.email, "Approved") },
                                                        modifier = Modifier.height(28.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32)),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Approve", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                                    }
                                                }
                                                if (profile.foodApprovalStatus != "Rejected") {
                                                    OutlinedButton(
                                                        onClick = { viewModel.updateFoodApprovalStatus(profile.email, "Rejected") },
                                                        modifier = Modifier.height(28.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC62828)),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(12.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Reject", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    Box(contentAlignment = Alignment.CenterEnd) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val badgeBg = when (profile.role) {
                                                "Super Admin" -> Color(0xFF9C27B0).copy(alpha = 0.1f)
                                                "Admin" -> Color.Red.copy(alpha = 0.1f)
                                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            }
                                            val badgeText = when (profile.role) {
                                                "Super Admin" -> Color(0xFF9C27B0)
                                                "Admin" -> Color.Red
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = badgeBg),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.padding(end = 8.dp)
                                            ) {
                                                Text(
                                                    text = profile.role.uppercase(),
                                                    color = badgeText,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }

                                            // Only show delete trash if it's not the currently active user and profile role is not Super Admin
                                            if (activeProfile?.email != profile.email && profile.role != "Super Admin") {
                                                IconButton(onClick = { viewModel.deleteProfile(profile.email) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                                                }
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }
    }

    // CREATE PROFILE DIALOG PANEL
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create User Profile 🎈") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = formName,
                        onValueChange = { formName = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formEmail,
                        onValueChange = { formEmail = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Role selection row
                    Column {
                        Text("Role Mapping", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { formRole = "Staff" }
                                    .border(
                                        width = if (formRole == "Staff") 2.dp else 0.dp,
                                        color = if (formRole == "Staff") MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (formRole == "Staff") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                                    Text("Staff Member", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (activeProfile?.role == "Super Admin") {
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { formRole = "Admin" }
                                        .border(
                                            width = if (formRole == "Admin") 2.dp else 0.dp,
                                            color = if (formRole == "Admin") Color.Red else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (formRole == "Admin") Color.Red.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                                        Text("Administrator", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (formRole == "Admin") Color.Red else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            } else {
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { 
                                            Toast.makeText(context, "Only the Super Admin can create more administrators!", Toast.LENGTH_SHORT).show()
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Admin Locked", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Department Chips selection
                    Column {
                        Text("Department", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        val depts = listOf("Engineering", "Management", "HR", "Sales", "Kitchen")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(depts) { d ->
                                val isSel = formDept == d
                                Card(
                                    modifier = Modifier.clickable { formDept = d },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = d,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (formName.trim().isEmpty() || formEmail.trim().isEmpty()) {
                            Toast.makeText(context, "Full Name and Email are required!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.registerUserProfile(
                                name = formName,
                                email = formEmail,
                                role = formRole,
                                department = formDept
                            )
                            Toast.makeText(context, "Profile created and registered!", Toast.LENGTH_SHORT).show()
                            showCreateDialog = false
                            formName = ""
                            formEmail = ""
                        }
                    }
                ) {
                    Text("Register & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
