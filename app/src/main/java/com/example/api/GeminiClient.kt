package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.example.database.MealMenu

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>,
    @Json(name = "role") val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }
}

class ChatbotEngine {

    suspend fun askGemini(
        prompt: String,
        apiKey: String,
        currentMenuState: List<MealMenu>,
        chatHistory: List<com.example.ChatMessage> = emptyList()
    ): String {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return offlineFallbackAnswer(prompt, currentMenuState) + "\n\n*(Note: Running in Offline Mode because the Gemini API key is not configured in Secrets)*"
        }

        val menuContext = currentMenuState.joinToString("\n") { menu ->
            val status = if (menu.isCookOff) "COOK OFF (No food served)" else menu.menuItems
            "${menu.dayOfWeek} ${menu.mealType}: $status"
        }

        // Dynamically compute the current date/day of the week in user locale
        val todayDayOfWeek = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
        val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

        val systemPrompt = """
            You are "BiteBot", the helpful AI assistant for the Office Mess.
            You provide concise, friendly information about the office weekly menu and guidelines.
            
            CRITICAL TIMING INFORMATION:
            - Today's Day of the Week is: $todayDayOfWeek
            - Today's Current Date is: $todayDate
            Whenever the user references "today", "tomorrow", or any specific day, resolve it carefully using this timing info and compare it with the dinner/lunch/breakfast menu details below.

            Here is the current weekly food menu defined by the kitchen administrator:
            
            $menuContext
            
            Guidelines:
            - Employees get 3 meals a day: Breakfast, Lunch, and Dinner.
            - Food is completely free for all team members. There are no orders, payments, or purchase options.
            - Every Friday, there is NO Breakfast (it's Cook Off time).
            - Every Sunday, there is NO Dinner (it's Cook Off time).
            - Keep your responses polite, helpful, and short (max 2-3 sentences where possible).
            - If an employee asks what is served, read the exact menu details above.
            - If a user asks a question not related to the food menu, politely steer them back to mess-related queries.
        """.trimIndent()

        // Build perfect alternating multi-turn conversation history
        val contents = mutableListOf<GeminiContent>()
        var lastAddedRole: String? = null

        chatHistory.takeLast(10).forEach { msg ->
            val role = if (msg.isUser) "user" else "model"
            if (role != lastAddedRole) {
                contents.add(
                    GeminiContent(
                        parts = listOf(GeminiPart(msg.text)),
                        role = role
                    )
                )
                lastAddedRole = role
            } else {
                // Merge consecutive messages of same role to avoid Gemini API validation errors
                if (contents.isNotEmpty()) {
                    val lastContent = contents.removeAt(contents.size - 1)
                    val newParts = lastContent.parts + GeminiPart(msg.text)
                    contents.add(GeminiContent(parts = newParts, role = role))
                } else {
                    contents.add(GeminiContent(parts = listOf(GeminiPart(msg.text)), role = role))
                }
            }
        }

        // Always ensure the final message is the active prompt from the user
        if (lastAddedRole != "user") {
            contents.add(
                GeminiContent(
                    parts = listOf(GeminiPart(prompt)),
                    role = "user"
                )
            )
        } else {
            // If the last added message in history was user, append this prompt's text to it
            if (contents.isNotEmpty()) {
                val lastContent = contents.removeAt(contents.size - 1)
                val newParts = lastContent.parts + GeminiPart(prompt)
                contents.add(GeminiContent(parts = newParts, role = "user"))
            } else {
                contents.add(GeminiContent(parts = listOf(GeminiPart(prompt)), role = "user"))
            }
        }

        val request = GeminiRequest(
            contents = contents,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(systemPrompt)))
        )

        return try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "I apologize, but I received an empty response. Let me know if you want me to try again!"
        } catch (e: Exception) {
            "I could not connect to Gemini API (${e.localizedMessage}). Let me answer from my offline schedule:\n\n" + 
                    offlineFallbackAnswer(prompt, currentMenuState)
        }
    }

    /**
     * A high-quality keyword-based local parsing engine to guarantee answers 
     * search-queries like "what is lunch today", "dinner on Sunday", etc., offline.
     */
    fun offlineFallbackAnswer(prompt: String, menuState: List<MealMenu>): String {
        val query = prompt.lowercase()
        
        // Resolve target day
        var targetDay = ""
        if (query.contains("monday")) targetDay = "Monday"
        else if (query.contains("tuesday")) targetDay = "Tuesday"
        else if (query.contains("wednesday")) targetDay = "Wednesday"
        else if (query.contains("thursday")) targetDay = "Thursday"
        else if (query.contains("friday")) targetDay = "Friday"
        else if (query.contains("saturday")) targetDay = "Saturday"
        else if (query.contains("sunday")) targetDay = "Sunday"
        else if (query.contains("today")) {
            // Find today's weekday name
            targetDay = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
        } else if (query.contains("tomorrow")) {
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            targetDay = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(calendar.time)
        }

        // Resolve target meal
        var targetMeal = ""
        if (query.contains("breakfast")) targetMeal = "Breakfast"
        else if (query.contains("lunch")) targetMeal = "Lunch"
        else if (query.contains("dinner")) targetMeal = "Dinner"

        if (targetDay.isEmpty()) {
            // Just return general description
            return "Hi! I am BiteBot. Ask me about menus (e.g., 'What is lunch today?' or 'What's the menu of tomorrow?')."
        }

        if (targetMeal.isEmpty()) {
            // Return all meals for that day
            val meals = menuState.filter { it.dayOfWeek.equals(targetDay, ignoreCase = true) }
            if (meals.isEmpty()) {
                return "I couldn't find any menu for $targetDay."
            }
            val formatted = meals.joinToString("\n") { meal ->
                val desc = if (meal.isCookOff) "COOK OFF 🍳 (Kitchen Closed)" else meal.menuItems
                "• *${meal.mealType}*: $desc"
            }
            return "Here is the menu schedule for **$targetDay**:\n$formatted"
        }

        // Return specific meal
        val meal = menuState.find { 
            it.dayOfWeek.equals(targetDay, ignoreCase = true) && it.mealType.equals(targetMeal, ignoreCase = true) 
        }

        return if (meal != null) {
            if (meal.isCookOff) {
                "🍳 **$targetDay $targetMeal** is a **COOK OFF**! No meals are served because the cooks are off duty."
            } else {
                "🍱 For **$targetDay $targetMeal**, we are serving:\n${meal.menuItems}\n\nEnjoy your meal!"
            }
        } else {
            "I couldn't find a entry for $targetDay $targetMeal."
        }
    }
}
