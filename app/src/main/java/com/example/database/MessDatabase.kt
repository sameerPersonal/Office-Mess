package com.example.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "meal_menu")
data class MealMenu(
    @PrimaryKey val id: String, // format: "DayOfWeek_MealType" (e.g. "Monday_Breakfast")
    val dayOfWeek: String,      // e.g. "Monday", "Tuesday"
    val mealType: String,       // "Breakfast", "Lunch", "Dinner"
    val menuItems: String,      // e.g. "Crispy Plain Dosa, Potato Sagu, Coconut Chutney, Coffee"
    val isCookOff: Boolean      // true for "Friday_Breakfast" or "Sunday_Dinner" by default
)

@Entity(tableName = "feedbacks")
data class Feedback(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateString: String,     // format: "YYYY-MM-DD" style
    val mealType: String,       // "Breakfast", "Lunch", "Dinner"
    val rating: Int,            // 1 to 5 stars
    val comment: String,        // Optional text feedback
    val submittedBy: String,    // User email (staff email)
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "notification_logs")
data class NotificationLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val category: String = "Updates" // "Updates", "Reminders"
)

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val email: String,
    val name: String,
    val role: String, // "Admin" or "Staff"
    val department: String,
    val foodApprovalStatus: String = "Pending", // "Approved", "Pending", "Rejected"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "system_configs")
data class SystemConfig(
    @PrimaryKey val configKey: String,
    val isEnabled: Boolean = true
)

@Entity(tableName = "google_form_sync_logs")
data class GoogleFormSyncLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val entityType: String, // "UserProfile", "FoodMenu", "Feedback"
    val entityId: String,
    val payload: String,
    val googleFormUrl: String,
    val status: String, // "Success", "Failed (Offline)", "Pending"
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MessDao {
    // Menu Queries
    @Query("SELECT * FROM meal_menu")
    fun getAllMenusFlow(): Flow<List<MealMenu>>

    @Query("SELECT * FROM meal_menu")
    suspend fun getAllMenus(): List<MealMenu>

    @Query("SELECT * FROM meal_menu WHERE dayOfWeek = :day")
    fun getMenusForDayFlow(day: String): Flow<List<MealMenu>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenus(menus: List<MealMenu>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenu(menu: MealMenu)

    // Feedback Queries
    @Query("SELECT * FROM feedbacks ORDER BY timestamp DESC")
    fun getAllFeedbackFlow(): Flow<List<Feedback>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: Feedback)

    @Query("DELETE FROM feedbacks WHERE id = :id")
    suspend fun deleteFeedbackById(id: Int)

    @Query("SELECT AVG(rating) FROM feedbacks WHERE mealType = :mealType AND dateString = :date")
    suspend fun getAverageRatingForMeal(mealType: String, date: String): Float?

    // Notification Queries
    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC")
    fun getAllNotificationsFlow(): Flow<List<NotificationLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationLog)

    @Query("DELETE FROM notification_logs")
    suspend fun clearNotificationLogs()

    // UserProfile Queries
    @Query("SELECT * FROM user_profiles ORDER BY timestamp DESC")
    fun getAllUserProfilesFlow(): Flow<List<UserProfile>>

    @Query("SELECT * FROM user_profiles")
    suspend fun getAllUserProfiles(): List<UserProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    @Query("DELETE FROM user_profiles WHERE email = :email")
    suspend fun deleteUserProfile(email: String)

    // GoogleFormSyncLog Queries
    @Query("SELECT * FROM google_form_sync_logs ORDER BY timestamp DESC")
    fun getAllSyncLogsFlow(): Flow<List<GoogleFormSyncLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(log: GoogleFormSyncLog)

    @Query("DELETE FROM google_form_sync_logs")
    suspend fun clearSyncLogs()

    // SystemConfig Queries
    @Query("SELECT * FROM system_configs")
    suspend fun getAllConfigs(): List<SystemConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: SystemConfig)
}

@Database(entities = [MealMenu::class, Feedback::class, NotificationLog::class, UserProfile::class, GoogleFormSyncLog::class, SystemConfig::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messDao(): MessDao
}
