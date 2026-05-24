package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.ChatbotEngine
import com.example.database.AppDatabase
import com.example.database.Feedback
import com.example.database.MealMenu
import com.example.database.NotificationLog
import com.example.database.UserProfile
import com.example.database.GoogleFormSyncLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// --- Message Data Class for Chatbot ---
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// --- Repository Pattern ---
class MessRepository(private val context: Context) {
    private val db = androidx.room.Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "office_mess_db"
    ).fallbackToDestructiveMigration().build()

    val dao = db.messDao()

    val allMenus = dao.getAllMenusFlow()
    val allFeedback = dao.getAllFeedbackFlow()
    val allNotifications = dao.getAllNotificationsFlow()
    val allUserProfiles = dao.getAllUserProfilesFlow()
    val allSyncLogs = dao.getAllSyncLogsFlow()

    // Database pre-population with delectable menus
    suspend fun initializeMenuIfEmpty() {
        withContext(Dispatchers.IO) {
            val existingProfiles = dao.getAllUserProfiles()
            
            // Populate sameer.benzy.p@gmail.com as the active Super Admin in this workspace
            val hasSuperEmail = existingProfiles.any { it.email == "sameer.benzy.p@gmail.com" }
            if (!hasSuperEmail) {
                dao.insertUserProfile(
                    UserProfile(
                        email = "sameer.benzy.p@gmail.com",
                        name = "Sameer Benzy",
                        role = "Super Admin",
                        department = "Executive",
                        foodApprovalStatus = "Approved"
                    )
                )
            }

            val hasSuper = existingProfiles.any { it.email == "sameer" }
            if (!hasSuper) {
                dao.insertUserProfile(
                    UserProfile(
                        email = "sameer",
                        name = "Sameer",
                        role = "Super Admin",
                        department = "Executive",
                        foodApprovalStatus = "Approved"
                    )
                )
            }

            // Populating staff.member if it doesn't already exist and db was largely empty
            val hasStaff = existingProfiles.any { it.email == "staff.member@example.com" }
            if (!hasStaff && existingProfiles.isEmpty()) {
                dao.insertUserProfile(
                    UserProfile(
                        email = "staff.member@example.com",
                        name = "Alex Johnson",
                        role = "Staff",
                        department = "Engineering",
                        foodApprovalStatus = "Pending"
                    )
                )
            }

            val existing = dao.getAllMenus()
            if (existing.isEmpty()) {
                val defaultMenus = mutableListOf<MealMenu>()
                val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

                for (day in days) {
                    // --- Breakfast ---
                    val bfItems = when (day) {
                        "Monday" -> "Soft Idlis, Medu Vada, Sambar, Coconut Chutney, Hot Chai & Coffee"
                        "Tuesday" -> "Masala Dosa, Potato Masala, Sambar, Filter Coffee"
                        "Wednesday" -> "Indori Poha, Jalebi, Mint Chutney, Tea"
                        "Thursday" -> "Aloo Paratha, Butter, Fresh Curd, Mixed Pickle, Tea"
                        "Friday" -> "COOK OFF" // Friday Breakfast Cook Off
                        "Saturday" -> "Puri Bhaji, Suji Halwa, Coffee/Tea"
                        "Sunday" -> "Chole Bhature, Hot Almond Milk, Tea"
                        else -> "Continental Breakfast, Bread-Butter, Tea"
                    }
                    defaultMenus.add(
                        MealMenu(
                            id = "${day}_Breakfast",
                            dayOfWeek = day,
                            mealType = "Breakfast",
                            menuItems = bfItems,
                            isCookOff = (day == "Friday")
                        )
                    )

                    // --- Lunch ---
                    val lunchItems = when (day) {
                        "Monday" -> "Steamed Rice, Dal Tadka, Paneer Butter Masala, Butter Roti, Green Salad, Papad"
                        "Tuesday" -> "Veg Pulav, Onion Raita, Dum Aloo Curry, Chapatis, Pickle"
                        "Wednesday" -> "Jeera Rice, Dal Makhani, Bhindi fry, Phulkas, Fresh Buttermilk"
                        "Thursday" -> "Rice, Spicy Rasam, Beetroot Poriyal, Curd, Chapatis, Sweet Payasam"
                        "Friday" -> "Special Choice Biryani (Veg/Chicken), Gobi Manchurian, Cucumber Salad, Gulab Jamun"
                        "Saturday" -> "Dal Khichdi, Gujarati Kadhi, Baingan Bharta, Roti, Roasted Papad"
                        "Sunday" -> "Steamed Basmati Rice, Traditional Sambar, Chicken Curry / Shahi Paneer, Garlic Roti, Butterscotch Ice Cream"
                        else -> "Assorted Buffet Lunch"
                    }
                    defaultMenus.add(
                        MealMenu(
                            id = "${day}_Lunch",
                            dayOfWeek = day,
                            mealType = "Lunch",
                            menuItems = lunchItems,
                            isCookOff = false
                        )
                    )

                    // --- Dinner ---
                    val dinnerItems = when (day) {
                        "Monday" -> "Paneer Bhurji, Plain Paratha, Yellow Dal Fry, Salad"
                        "Tuesday" -> "Egg Curry / Malai Kofta, Kashmiri Pulao, Butter Tandoori Roti"
                        "Wednesday" -> "Veg Hakka Noodles, Gobi Manchurian Gravy, Veg Fried Rice, Hot & Sour Soup"
                        "Thursday" -> "Methi Thepla, Kashmiri Alco Dum, Sweet Yogurt, Fruit Custard"
                        "Friday" -> "Moong Dal Khichdi, Roasted papad, Kadhi, Gujarati Batata Nu Shaak"
                        "Saturday" -> "Creamy Pasta Alfredo, Cheesy Garlic Bread, Fresh Garden Salad"
                        "Sunday" -> "COOK OFF" // Sunday Dinner Cook Off
                        else -> "Light dinner buffet"
                    }
                    defaultMenus.add(
                        MealMenu(
                            id = "${day}_Dinner",
                            dayOfWeek = day,
                            mealType = "Dinner",
                            menuItems = dinnerItems,
                            isCookOff = (day == "Sunday")
                        )
                    )
                }
                
                dao.insertMenus(defaultMenus)

                // Log entry into notifications so they have something initially
                dao.insertNotification(
                    NotificationLog(
                        title = "Welcome to Office Mess!",
                        body = "The weekly menu has been loaded successfully. Friday Breakfast & Sunday Dinner are Cook-off timings.",
                        category = "Updates"
                    )
                )
            }
        }
    }
}

// --- ViewModel ---
class MessViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MessRepository(application)
    private val chatbotEngine = ChatbotEngine()

    // Collect DB flows statefully
    val allMenus: StateFlow<List<MealMenu>> = repository.allMenus.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allFeedback: StateFlow<List<Feedback>> = repository.allFeedback.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allNotifications: StateFlow<List<NotificationLog>> = repository.allNotifications.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allUserProfiles: StateFlow<List<UserProfile>> = repository.allUserProfiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allSyncLogs: StateFlow<List<GoogleFormSyncLog>> = repository.allSyncLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // UI Interactive States
    private val _selectedDay = MutableStateFlow("")
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    private val _isAdminMode = MutableStateFlow(false) // Default to false for better security, will be set on profile select
    val isAdminMode: StateFlow<Boolean> = _isAdminMode.asStateFlow()

    private val _userEmail = MutableStateFlow("") 
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()

    private val _adminPin = MutableStateFlow("8888")
    val adminPin: StateFlow<String> = _adminPin.asStateFlow()

    // Google Form Settings (Customizable parameters)
    private val _googleFormId = MutableStateFlow("1FAIpQLSfhVfC7tUu07-YfIsH7fG4v14cEx7O_R_fD_p_Q3p88")
    val googleFormId: StateFlow<String> = _googleFormId.asStateFlow()

    // Google Apps Script Web App URL
    private val _googleAppsScriptUrl = MutableStateFlow("https://script.google.com/macros/s/AKfycbz4uSi3z38TxijR_0XK9R5S-pnktL42-SVVwUsoWYJw/dev")
    val googleAppsScriptUrl: StateFlow<String> = _googleAppsScriptUrl.asStateFlow()

    private val prefs = getApplication<Application>().getSharedPreferences("mess_settings", Context.MODE_PRIVATE)

    // Configurable Meal Timings
    private val _breakfastTime = MutableStateFlow("08:15 AM - 09:30 AM")
    val breakfastTime: StateFlow<String> = _breakfastTime.asStateFlow()

    private val _lunchTime = MutableStateFlow("01:00 PM - 02:30 PM")
    val lunchTime: StateFlow<String> = _lunchTime.asStateFlow()

    private val _dinnerTime = MutableStateFlow("08:00 PM - 09:15 PM")
    val dinnerTime: StateFlow<String> = _dinnerTime.asStateFlow()

    private val _snacksTime = MutableStateFlow("04:30 PM - 05:30 PM")
    val snacksTime: StateFlow<String> = _snacksTime.asStateFlow()

    // Configurable Feature Toggles
    private val _isChatEnabled = MutableStateFlow(true)
    val isChatEnabled: StateFlow<Boolean> = _isChatEnabled.asStateFlow()

    private val _isFeedbackEnabled = MutableStateFlow(true)
    val isFeedbackEnabled: StateFlow<Boolean> = _isFeedbackEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow("System") // "System", "Light", "Dark"
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Chatbot States
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatbotLoading = MutableStateFlow(false)
    val isChatbotLoading: StateFlow<Boolean> = _isChatbotLoading.asStateFlow()

    init {
        // Set default day to current day of the week
        val today = SimpleDateFormat("EEEE", Locale.US).format(Date())
        _selectedDay.value = today

        // Load Persistent configurations from SharedPreferences matching admin settings
        val googleFormIdSaved = prefs.getString("google_form_id", "1FAIpQLSfhVfC7tUu07-YfIsH7fG4v14cEx7O_R_fD_p_Q3p88") ?: "1FAIpQLSfhVfC7tUu07-YfIsH7fG4v14cEx7O_R_fD_p_Q3p88"
        _googleFormId.value = googleFormIdSaved

        val googleAppsScriptUrlSaved = prefs.getString("google_apps_script_url", "https://script.google.com/macros/s/AKfycbz4uSi3z38TxijR_0XK9R5S-pnktL42-SVVwUsoWYJw/dev") ?: "https://script.google.com/macros/s/AKfycbz4uSi3z38TxijR_0XK9R5S-pnktL42-SVVwUsoWYJw/dev"
        _googleAppsScriptUrl.value = googleAppsScriptUrlSaved

        _breakfastTime.value = prefs.getString("breakfast_time", "08:15 AM - 09:30 AM") ?: "08:15 AM - 09:30 AM"
        _lunchTime.value = prefs.getString("lunch_time", "01:00 PM - 02:30 PM") ?: "01:00 PM - 02:30 PM"
        _dinnerTime.value = prefs.getString("dinner_time", "08:00 PM - 09:15 PM") ?: "08:00 PM - 09:15 PM"
        _snacksTime.value = prefs.getString("snacks_time", "04:30 PM - 05:30 PM") ?: "04:30 PM - 05:30 PM"
        _adminPin.value = prefs.getString("admin_pin", "8888") ?: "8888"

        viewModelScope.launch {
            repository.initializeMenuIfEmpty()
            
            // Load persistent configurations
            val configs = repository.dao.getAllConfigs()
            configs.forEach { config ->
                if (config.configKey == "chat_enabled") {
                    _isChatEnabled.value = config.isEnabled
                } else if (config.configKey == "feedback_enabled") {
                    _isFeedbackEnabled.value = config.isEnabled
                } else if (config.configKey == "theme_dark" && config.isEnabled) {
                    _themeMode.value = "Dark"
                } else if (config.configKey == "theme_light" && config.isEnabled) {
                    _themeMode.value = "Light"
                }
            }

            // Restore last active profile, or default to a Staff profile so normal users don't automatically become Admin!
            val savedEmail = prefs.getString("active_profile_email", null)
            val profiles = repository.dao.getAllUserProfiles()
            val savedProfile = profiles.find { it.email == savedEmail }
            if (savedProfile != null) {
                switchProfile(savedProfile)
            } else {
                val defaultStaff = profiles.find { it.email == "sameer.benzy.p@gmail.com" } ?: profiles.find { it.role == "Staff" } ?: profiles.find { it.email == "sameer" } ?: profiles.firstOrNull()
                if (defaultStaff != null) {
                    switchProfile(defaultStaff)
                }
            }
        }

        // Add initial bot greeting
        _chatMessages.value = listOf(
            ChatMessage(
                text = "Hello! I am BiteBot, your Office Mess digital assistant. Ask me anything about today's, tomorrow's, or any day's menu!",
                isUser = false
            )
        )
    }

    fun selectDay(day: String) {
        _selectedDay.value = day
    }

    fun toggleAdminMode() {
        _isAdminMode.value = !_isAdminMode.value
    }

    fun setAdminMode(isAdmin: Boolean) {
        _isAdminMode.value = isAdmin
    }

    fun setGoogleFormId(formId: String) {
        if (formId.trim().isNotEmpty()) {
            _googleFormId.value = formId.trim()
            prefs.edit().putString("google_form_id", formId.trim()).apply()
        }
    }

    fun setGoogleAppsScriptUrl(url: String) {
        if (url.trim().isNotEmpty()) {
            _googleAppsScriptUrl.value = url.trim()
            prefs.edit().putString("google_apps_script_url", url.trim()).apply()
        }
    }

    fun setMealTime(mealType: String, timeRange: String) {
        val trimmed = timeRange.trim()
        if (trimmed.isNotEmpty()) {
            when (mealType.lowercase()) {
                "breakfast" -> {
                    _breakfastTime.value = trimmed
                    prefs.edit().putString("breakfast_time", trimmed).apply()
                }
                "lunch" -> {
                    _lunchTime.value = trimmed
                    prefs.edit().putString("lunch_time", trimmed).apply()
                }
                "dinner" -> {
                    _dinnerTime.value = trimmed
                    prefs.edit().putString("dinner_time", trimmed).apply()
                }
                "snacks", "tea", "snacks/tea" -> {
                    _snacksTime.value = trimmed
                    prefs.edit().putString("snacks_time", trimmed).apply()
                }
            }
        }
    }

    // User Profile Actions
    fun registerUserProfile(name: String, email: String, role: String, department: String) {
        viewModelScope.launch {
            val status = if (role == "Admin" || role == "Super Admin") "Approved" else "Pending"
            val profile = UserProfile(
                email = email.trim(),
                name = name.trim(),
                role = role,
                department = department.trim(),
                foodApprovalStatus = status
            )
            repository.dao.insertUserProfile(profile)
            switchProfile(profile)

            triggerPushNotification(
                title = "🎁 Profile Created!",
                body = "Registered profile for ${profile.name} as ${profile.role} in ${profile.department}. Food status: $status.",
                category = "Updates"
            )

            // Sync User Registration with Google Form
            val params = mapOf(
                "entry.1001" to profile.name,
                "entry.1002" to profile.email,
                "entry.1003" to profile.role,
                "entry.1004" to profile.department,
                "entry.1005" to status,
                "entry.1006" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            )
            syncToGoogleForm("UserProfile", profile.email, params)
        }
    }

    fun setChatEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _isChatEnabled.value = enabled
            repository.dao.insertConfig(com.example.database.SystemConfig("chat_enabled", enabled))
        }
    }

    fun setFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _isFeedbackEnabled.value = enabled
            repository.dao.insertConfig(com.example.database.SystemConfig("feedback_enabled", enabled))
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            _themeMode.value = mode
            when (mode) {
                "Dark" -> {
                    repository.dao.insertConfig(com.example.database.SystemConfig("theme_dark", true))
                    repository.dao.insertConfig(com.example.database.SystemConfig("theme_light", false))
                }
                "Light" -> {
                    repository.dao.insertConfig(com.example.database.SystemConfig("theme_dark", false))
                    repository.dao.insertConfig(com.example.database.SystemConfig("theme_light", true))
                }
                else -> {
                    repository.dao.insertConfig(com.example.database.SystemConfig("theme_dark", false))
                    repository.dao.insertConfig(com.example.database.SystemConfig("theme_light", false))
                }
            }
        }
    }

    fun updateFoodApprovalStatus(email: String, status: String) {
        viewModelScope.launch {
            val profiles = repository.dao.getAllUserProfiles()
            val profile = profiles.find { it.email == email }
            if (profile != null) {
                val updated = profile.copy(foodApprovalStatus = status)
                repository.dao.insertUserProfile(updated)
                
                // If it is currently active, make sure ViewModel updates
                if (_activeProfile.value?.email == email) {
                    _activeProfile.value = updated
                }

                triggerPushNotification(
                    title = "Food Access Update!",
                    body = "${profile.name}'s food service is now: $status.",
                    category = "Updates"
                )

                // Sync action to Google Forms
                val params = mapOf(
                    "entry.1001" to profile.name,
                    "entry.1002" to profile.email,
                    "entry.1003" to profile.role,
                    "entry.1004" to profile.department,
                    "entry.1005" to status,
                    "entry.1006" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                )
                syncToGoogleForm("FoodServiceApproval", profile.email, params)
            }
        }
    }

    fun switchProfile(profile: UserProfile) {
        _activeProfile.value = profile
        _userEmail.value = profile.email
        _isAdminMode.value = (profile.role == "Admin" || profile.role == "Super Admin")
        prefs.edit().putString("active_profile_email", profile.email).apply()
    }

    fun updateAdminPin(newPin: String): Boolean {
        if (newPin.length == 4 && newPin.all { it.isDigit() }) {
            _adminPin.value = newPin
            prefs.edit().putString("admin_pin", newPin).apply()
            return true
        }
        return false
    }

    fun verifyAdminPin(pin: String): Boolean {
        return pin == _adminPin.value
    }

    fun deleteProfile(email: String) {
        viewModelScope.launch {
            repository.dao.deleteUserProfile(email)
            val all = repository.dao.getAllUserProfiles()
            if (all.isEmpty()) {
                _activeProfile.value = null
            } else {
                switchProfile(all.first())
            }
        }
    }

    // Update menus (Admin Action) + Sync to Google Form
    fun updateMenu(dayOfWeek: String, mealType: String, menuItems: String, isCookOff: Boolean) {
        viewModelScope.launch {
            val id = "${dayOfWeek}_${mealType}"
            val menu = MealMenu(
                id = id,
                dayOfWeek = dayOfWeek,
                mealType = mealType,
                menuItems = menuItems,
                isCookOff = isCookOff
            )
            repository.dao.insertMenu(menu)

            // Trigger simulated push notification for update
            val logTitle = "Menu Update: $dayOfWeek $mealType"
            val logBody = if (isCookOff) {
                "$dayOfWeek $mealType has been changed to Cook-off (No meal served)."
            } else {
                "Updated menu: $menuItems"
            }
            
            triggerPushNotification(logTitle, logBody, "Updates")

            // Sync Daily Food Menu to Google Form
            val params = mapOf(
                "entry.2001" to dayOfWeek,
                "entry.2002" to mealType,
                "entry.2003" to menuItems,
                "entry.2004" to if (isCookOff) "Yes" else "No",
                "entry.2005" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            )
            syncToGoogleForm("FoodMenu", id, params)
        }
    }

    // Submit Feedback and rating (Staff Action) + Sync to Google Form
    fun submitFeedback(mealType: String, rating: Int, comment: String) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val feedback = Feedback(
                dateString = dateStr,
                mealType = mealType,
                rating = rating,
                comment = comment,
                submittedBy = _userEmail.value
            )
            repository.dao.insertFeedback(feedback)

            // Sync Feedback/Rating to Google Form
            val params = mapOf(
                "entry.3001" to mealType,
                "entry.3002" to rating.toString(),
                "entry.3003" to comment,
                "entry.3004" to _userEmail.value,
                "entry.3005" to dateStr,
                "entry.3006" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            )
            syncToGoogleForm("Feedback", "${_userEmail.value}_${mealType}", params)
        }
    }

    fun clearAllSyncLogs() {
        viewModelScope.launch {
            repository.dao.clearSyncLogs()
        }
    }

    private val _bulkSyncStatus = MutableStateFlow<String?>(null)
    val bulkSyncStatus: StateFlow<String?> = _bulkSyncStatus.asStateFlow()

    fun syncOneWeekTestData(onComplete: (successCount: Int, errorCount: Int) -> Unit) {
        viewModelScope.launch {
            _bulkSyncStatus.value = "Starting one-week test data sync..."
            val menus = allMenus.value.ifEmpty {
                repository.initializeMenuIfEmpty()
                allMenus.value
            }

            var successCount = 0
            var errorCount = 0

            _bulkSyncStatus.value = "Preparing ${menus.size} meals, 5 profiles, and 5 feedbacks..."
            kotlinx.coroutines.delay(500)

            // 1. Sync Menus
            menus.forEachIndexed { index, menu ->
                _bulkSyncStatus.value = "Syncing menu ${index + 1}/${menus.size}: ${menu.dayOfWeek} ${menu.mealType}..."
                try {
                    val params = mapOf(
                        "entry.2001" to menu.dayOfWeek,
                        "entry.2002" to menu.mealType,
                        "entry.2003" to menu.menuItems,
                        "entry.2004" to if (menu.isCookOff) "Yes" else "No",
                        "entry.2005" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    )
                    val isSuccess = performSyncToGoogleFormSuspend("FoodMenu", menu.id, params)
                    if (isSuccess) successCount++ else errorCount++
                } catch (e: Exception) {
                    errorCount++
                }
                kotlinx.coroutines.delay(100)
            }

            // 2. Sync 5 mock user profiles representing office members
            val mockProfiles = listOf(
                UserProfile("rahul.sharma@example.com", "Rahul Sharma", "Staff", "Engineering", "Approved"),
                UserProfile("priya.patel@example.com", "Priya Patel", "Staff", "Design", "Approved"),
                UserProfile("amit.verma@example.com", "Amit Verma", "Admin", "Operations", "Approved"),
                UserProfile("sneha.reddy@example.com", "Sneha Reddy", "Staff", "Product", "Pending"),
                UserProfile("sameer.benzy@example.com", "Sameer Benzy", "Super Admin", "Management", "Approved")
            )

            mockProfiles.forEachIndexed { index, profile ->
                _bulkSyncStatus.value = "Syncing profile ${index + 1}/${mockProfiles.size}: ${profile.name}..."
                try {
                    val params = mapOf(
                        "entry.1001" to profile.name,
                        "entry.1002" to profile.email,
                        "entry.1003" to profile.role,
                        "entry.1004" to profile.department,
                        "entry.1005" to profile.foodApprovalStatus,
                        "entry.1006" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    )
                    val isSuccess = performSyncToGoogleFormSuspend("UserProfile", profile.email, params)
                    if (isSuccess) successCount++ else errorCount++
                } catch (e: Exception) {
                    errorCount++
                }
                kotlinx.coroutines.delay(100)
            }

            // 3. Sync 5 mock feedbacks/ratings representing staff dinner and lunch reviews
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val mockFeedbacks = listOf(
                SyncFeedbackData("Lunch", "5", "The Biryani was absolutely spectacular and had rich spices!", "rahul.sharma@example.com"),
                SyncFeedbackData("Breakfast", "4", "Soft idlis with amazing fresh coconut chutney, loved it.", "priya.patel@example.com"),
                SyncFeedbackData("Dinner", "5", "Alfredo Pasta was extremely creamy. Exceptional quality!", "amit.verma@example.com"),
                SyncFeedbackData("Lunch", "3", "Jeera Rice was decent, but bhindi fry was slightly oily.", "sneha.reddy@example.com"),
                SyncFeedbackData("Dinner", "2", "Too spicy for my taste today.", "sameer.benzy@example.com")
            )

            mockFeedbacks.forEachIndexed { index, feedback ->
                _bulkSyncStatus.value = "Syncing feedback ${index + 1}/${mockFeedbacks.size}: ${feedback.mealType} (${feedback.rating}★)..."
                try {
                    val params = mapOf(
                        "entry.3001" to feedback.mealType,
                        "entry.3002" to feedback.rating,
                        "entry.3003" to feedback.comment,
                        "entry.3004" to feedback.email,
                        "entry.3005" to dateStr,
                        "entry.3006" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    )
                    val isSuccess = performSyncToGoogleFormSuspend("Feedback", "${feedback.email}_${feedback.mealType}", params)
                    if (isSuccess) successCount++ else errorCount++
                } catch (e: Exception) {
                    errorCount++
                }
                kotlinx.coroutines.delay(100)
            }

            _bulkSyncStatus.value = null
            onComplete(successCount, errorCount)
        }
    }

    private data class SyncFeedbackData(val mealType: String, val rating: String, val comment: String, val email: String)

    // Background HTTP POST Google Form and Google Apps Script Sync Manager
    private fun syncToGoogleForm(entityType: String, entityId: String, parameters: Map<String, String>) {
        viewModelScope.launch {
            performSyncToGoogleFormSuspend(entityType, entityId, parameters)
        }
    }

    private suspend fun performSyncToGoogleFormSuspend(entityType: String, entityId: String, parameters: Map<String, String>): Boolean {
        val formId = _googleFormId.value
        val formUrl = "https://docs.google.com/forms/d/e/$formId/formResponse"
        val scriptUrl = _googleAppsScriptUrl.value
        
        // Build URL encoded request body
        val formBodyBuilder = okhttp3.FormBody.Builder()
        for ((key, value) in parameters) {
            formBodyBuilder.add(key, value)
        }
        // Add metadata fields to ease processing in Google Apps Script
        formBodyBuilder.add("entityType", entityType)
        formBodyBuilder.add("entityId", entityId)
        val requestBody = formBodyBuilder.build()

        val client = okhttp3.OkHttpClient()
        var formsSuccess = false
        var scriptSuccess = false
        var lastError = ""

        // 1. Submit to Google Form
        if (formId.isNotEmpty() && !formId.startsWith("http")) {
            withContext(Dispatchers.IO) {
                try {
                    val request = okhttp3.Request.Builder()
                        .url(formUrl)
                        .post(requestBody)
                        .build()
                    val response = client.newCall(request).execute()
                    formsSuccess = response.isSuccessful || response.code == 200 || response.code == 302
                } catch (e: Exception) {
                    lastError = "Form Error: " + (e.localizedMessage ?: "Unknown")
                }
            }
        } else {
            formsSuccess = true // Skip form send since it's not set
        }

        // 2. Submit to Google Apps Script Web App
        if (scriptUrl.isNotEmpty() && scriptUrl.startsWith("http")) {
            withContext(Dispatchers.IO) {
                try {
                    val request = okhttp3.Request.Builder()
                        .url(scriptUrl)
                        .post(requestBody)
                        .build()
                    val response = client.newCall(request).execute()
                    scriptSuccess = response.isSuccessful || response.code == 200 || response.code == 302
                } catch (e: Exception) {
                    lastError = "Apps Script Error: " + (e.localizedMessage ?: "Unknown")
                }
            }
        } else {
            scriptSuccess = true // Skip apps script send since it's not set
        }

        // Determine unified status
        val status = when {
            formsSuccess && scriptSuccess -> "Success"
            !formsSuccess && !scriptSuccess -> "Failed Both ($lastError)"
            !formsSuccess -> "Form Failed ($lastError)"
            else -> "Apps Script Failed ($lastError)"
        }

        withContext(Dispatchers.IO) {
            val syncLog = GoogleFormSyncLog(
                entityType = entityType,
                entityId = entityId,
                payload = parameters.entries.joinToString(", ") { "${it.key}=${it.value}" },
                googleFormUrl = if (scriptUrl.isNotEmpty() && scriptUrl.startsWith("http")) scriptUrl else "https://docs.google.com/forms/d/e/$formId/viewform?" + parameters.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" },
                status = status
            )
            repository.dao.insertSyncLog(syncLog)
            Log.d("GoogleFormSync", "Outbound sync executed: $entityType -> $status")
        }

        return formsSuccess && scriptSuccess
    }

    fun deleteFeedback(feedbackId: Int) {
        viewModelScope.launch {
            repository.dao.deleteFeedbackById(feedbackId)
        }
    }

    // Send chat question to Assistant chatbot
    fun sendChatMessage(text: String, apiKey: String) {
        if (text.trim().isEmpty()) return

        val userMsg = ChatMessage(text = text, isUser = true)
        val historyUntilNow = _chatMessages.value
        _chatMessages.value = _chatMessages.value + userMsg

        _isChatbotLoading.value = true

        viewModelScope.launch {
            try {
                // Fetch latest state from standard menus flow to feed state context
                val currentMenus = allMenus.value
                val reply = chatbotEngine.askGemini(
                    prompt = text,
                    apiKey = apiKey,
                    currentMenuState = currentMenus,
                    chatHistory = historyUntilNow
                )
                
                _chatMessages.value = _chatMessages.value + ChatMessage(text = reply, isUser = false)
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    text = "Sorry, I had trouble processing that query: ${e.localizedMessage}. Please try again!",
                    isUser = false
                )
            } finally {
                _isChatbotLoading.value = false
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = listOf(
            ChatMessage(
                text = "Chat cleared! Let me know what else I can help you with today.",
                isUser = false
            )
        )
    }

    // Triggers actual local system tray notification and logs it into database
    fun triggerPushNotification(title: String, body: String, category: String = "Updates") {
        viewModelScope.launch {
            // Standard db insert
            val dbNotification = NotificationLog(
                title = title,
                body = body,
                category = category
            )
            repository.dao.insertNotification(dbNotification)

            // Trigger Native System Notification Bar
            try {
                val context = getApplication<Application>().applicationContext
                val channelId = "office_mess_notifications"
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        "Office Mess Updates",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Triggers on daily menu updates and meal timings"
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val builder = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)

                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
                Log.d("MessViewModel", "Successfully fired native push notification: $title")
            } catch (e: Exception) {
                Log.e("MessViewModel", "Failed to fire native notification: ${e.localizedMessage}")
            }
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            repository.dao.clearNotificationLogs()
        }
    }

    // Simulates standard timing push notifications instantly for easy UX testing
    fun simulateMealTimingNotification(meal: String) {
        val today = SimpleDateFormat("EEEE", Locale.US).format(Date())
        val menuList = allMenus.value
        val mealMatch = menuList.find { m -> m.dayOfWeek.equals(today, ignoreCase = true) && m.mealType.equals(meal, ignoreCase = true) }

        val timing = when (meal.lowercase()) {
            "breakfast" -> _breakfastTime.value
            "lunch" -> _lunchTime.value
            "dinner" -> _dinnerTime.value
            "snacks", "tea", "snacks/tea" -> _snacksTime.value
            else -> ""
        }

        val timeString = if (timing.isNotEmpty()) " ($timing)" else ""
        val title = "🍽️ It's $meal Time!$timeString"
        val body = when {
            mealMatch == null -> "Head over to the mess hall to secure your free $meal meal."
            mealMatch.isCookOff -> "Notice: Today's $meal is a cook-off. Kitchen is closed!"
            else -> "Today's $meal menu: ${mealMatch.menuItems}. Tap to see details!"
        }

        triggerPushNotification(title, body, "Reminders")
    }
}
