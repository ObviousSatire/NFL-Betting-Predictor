package com.bestg.betting

import android.os.Bundle
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var testButton: Button
    private lateinit var teamSpinner: Spinner
    private lateinit var playerSpinner: Spinner
    private lateinit var loadStatsButton: Button
    private lateinit var updateTeamButton: Button
    private lateinit var loadPlayerButton: Button
    private lateinit var updatePlayerButton: Button
    private lateinit var weatherButton: Button
    private lateinit var injuriesButton: Button
    private lateinit var newsButton: Button
    private lateinit var recordButton: Button
    private lateinit var updateFormCard: CardView
    private lateinit var formTitle: TextView
    private lateinit var editField1: TextView
    private lateinit var editField2: TextView
    private lateinit var editField3: TextView
    private lateinit var submitUpdateButton: Button
    private lateinit var cancelUpdateButton: Button
    private lateinit var resultText: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var refreshAllButton: Button
    private lateinit var clearButton: Button
    private lateinit var helpButton: Button

    private val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("http://localhost:5000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        statusText = findViewById(R.id.statusText)
        testButton = findViewById(R.id.testButton)
        teamSpinner = findViewById(R.id.teamSpinner)
        playerSpinner = findViewById(R.id.playerSpinner)
        loadStatsButton = findViewById(R.id.loadStatsButton)
        updateTeamButton = findViewById(R.id.updateTeamButton)
        loadPlayerButton = findViewById(R.id.loadPlayerButton)
        updatePlayerButton = findViewById(R.id.updatePlayerButton)
        weatherButton = findViewById(R.id.weatherButton)
        injuriesButton = findViewById(R.id.injuriesButton)
        newsButton = findViewById(R.id.newsButton)
        recordButton = findViewById(R.id.recordButton)
        updateFormCard = findViewById(R.id.updateFormCard)
        formTitle = findViewById(R.id.formTitle)
        editField1 = findViewById(R.id.editField1)
        editField2 = findViewById(R.id.editField2)
        editField3 = findViewById(R.id.editField3)
        submitUpdateButton = findViewById(R.id.submitUpdateButton)
        cancelUpdateButton = findViewById(R.id.cancelUpdateButton)
        resultText = findViewById(R.id.resultText)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        refreshAllButton = findViewById(R.id.refreshAllButton)
        clearButton = findViewById(R.id.clearButton)
        helpButton = findViewById(R.id.helpButton)

        // Setup spinners
        setupSpinners()

        // Set click listeners
        testButton.setOnClickListener { testConnection() }
        loadStatsButton.setOnClickListener { loadTeamStats() }
        updateTeamButton.setOnClickListener { showUpdateForm("team") }
        loadPlayerButton.setOnClickListener { loadPlayerStats() }
        updatePlayerButton.setOnClickListener { showUpdateForm("player") }
        weatherButton.setOnClickListener { loadWeather() }
        injuriesButton.setOnClickListener { loadInjuries() }
        newsButton.setOnClickListener { loadNews() }
        recordButton.setOnClickListener { loadWinLoss() }
        submitUpdateButton.setOnClickListener { submitUpdate() }
        cancelUpdateButton.setOnClickListener { hideUpdateForm() }
        refreshAllButton.setOnClickListener { refreshAll() }
        clearButton.setOnClickListener { clearResults() }
        helpButton.setOnClickListener { showHelp() }
    }

    private fun setupSpinners() {
        val teams = arrayOf(
            "Buffalo Bills", "Kansas City Chiefs", "San Francisco 49ers",
            "Philadelphia Eagles", "Dallas Cowboys", "Baltimore Ravens",
            "Cincinnati Bengals", "Miami Dolphins"
        )
        val teamAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, teams)
        teamAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        teamSpinner.adapter = teamAdapter

        val players = arrayOf(
            "Patrick Mahomes", "Josh Allen", "Lamar Jackson",
            "Joe Burrow", "Christian McCaffrey", "Tyreek Hill", "Travis Kelce"
        )
        val playerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, players)
        playerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        playerSpinner.adapter = playerAdapter
    }

    private fun testConnection() {
        coroutineScope.launch {
            try {
                statusText.text = "Testing..."
                statusText.setTextColor(0xFFFFA500.toInt()) // Orange
                val response = apiService.testConnection()
                statusText.text = "✓ Connected"
                statusText.setTextColor(0xFF4CAF50.toInt()) // Green
                resultText.text = "API Connection Successful!\n\nServer: localhost:5000\nStatus: "
            } catch (e: Exception) {
                statusText.text = "✗ Not Connected"
                statusText.setTextColor(0xFFF44336.toInt()) // Red
                resultText.text = "Connection Failed!\n\nError: \n\nMake sure:\n• Flask API is running\n• USB reverse tethering is set"
            }
        }
    }

    private fun loadTeamStats() {
        val team = teamSpinner.selectedItem.toString()
        coroutineScope.launch {
            try {
                showLoading()
                val response = apiService.getTeamStats(team)
                hideLoading()
                resultText.text = formatTeamStats(team, response)
            } catch (e: Exception) {
                hideLoading()
                resultText.text = "Error loading team stats: "
            }
        }
    }

    private fun loadPlayerStats() {
        val player = playerSpinner.selectedItem.toString()
        coroutineScope.launch {
            try {
                showLoading()
                val response = apiService.getPlayerStats(player)
                hideLoading()
                resultText.text = formatPlayerStats(player, response)
            } catch (e: Exception) {
                hideLoading()
                resultText.text = "Error loading player stats: "
            }
        }
    }

    private fun loadWeather() {
        val team = teamSpinner.selectedItem.toString()
        coroutineScope.launch {
            try {
                showLoading()
                val response = apiService.getWeather(team)
                hideLoading()
                resultText.text = formatWeather(response)
            } catch (e: Exception) {
                hideLoading()
                resultText.text = "Error loading weather: "
            }
        }
    }

    private fun loadInjuries() {
        val team = teamSpinner.selectedItem.toString()
        coroutineScope.launch {
            try {
                showLoading()
                val response = apiService.getInjuries(team)
                hideLoading()
                resultText.text = formatInjuries(response)
            } catch (e: Exception) {
                hideLoading()
                resultText.text = "Error loading injuries: "
            }
        }
    }

    private fun loadNews() {
        val team = teamSpinner.selectedItem.toString()
        coroutineScope.launch {
            try {
                showLoading()
                val response = apiService.getNews(team)
                hideLoading()
                resultText.text = formatNews(response)
            } catch (e: Exception) {
                hideLoading()
                resultText.text = "Error loading news: "
            }
        }
    }

    private fun loadWinLoss() {
        val team = teamSpinner.selectedItem.toString()
        coroutineScope.launch {
            try {
                showLoading()
                val response = apiService.getWinLoss(team)
                hideLoading()
                resultText.text = formatWinLoss(response)
            } catch (e: Exception) {
                hideLoading()
                resultText.text = "Error loading record: "
            }
        }
    }

    private fun showUpdateForm(type: String) {
        formTitle.text = if (type == "team") "Update Team Stats" else "Update Player Stats"
        editField1.visibility = android.view.View.VISIBLE
        editField2.visibility = android.view.View.VISIBLE
        editField3.visibility = android.view.View.VISIBLE
        updateFormCard.visibility = android.view.View.VISIBLE
    }

    private fun hideUpdateForm() {
        updateFormCard.visibility = android.view.View.GONE
        editField1.text = ""
        editField2.text = ""
        editField3.text = ""
    }

    private fun submitUpdate() {
        // Simplified for now
        resultText.text = "Update feature coming soon!"
        hideUpdateForm()
    }

    private fun refreshAll() {
        testConnection()
    }

    private fun clearResults() {
        resultText.text = "Ready to fetch data..."
    }

    private fun showHelp() {
        resultText.text = """
            NFL BETTING PREDICTOR HELP
            
            • TEST: Check API connection
            • STATS: View team/player statistics
            • UPDATE: Modify stats (coming soon)
            • WEATHER: Real-time stadium weather
            • INJURIES: Current injury reports
            • NEWS: Latest NFL headlines
            • RECORD: Win/loss tracking
            
            Make sure Flask API is running on PC
            Use: adb reverse tcp:5000 tcp:5000
        """.trimIndent()
    }

    private fun formatTeamStats(team: String, response: Map<String, Any>): String {
        val sb = StringBuilder()
        sb.appendLine("📊 TEAM STATS: ")
        sb.appendLine("═══════════════════")
        sb.appendLine("Record: ")
        sb.appendLine("Points For: ")
        sb.appendLine("Points Against: ")
        sb.appendLine("Streak: ")
        
        if (response.containsKey("weather")) {
            @Suppress("UNCHECKED_CAST")
            val weather = response["weather"] as Map<String, Any>
            sb.appendLine("\n☁️ Weather:")
            sb.appendLine("  , °F")
            sb.appendLine("  Wind:  mph")
            sb.appendLine("  Precip: %")
        }
        return sb.toString()
    }

    private fun formatPlayerStats(player: String, response: Map<String, Any>): String {
        val sb = StringBuilder()
        sb.appendLine("👤 PLAYER STATS: ")
        sb.appendLine("═══════════════════")
        sb.appendLine("Position: ")
        sb.appendLine("Team: ")
        
        if (response.containsKey("passing_yards")) {
            sb.appendLine("Passing Yards: ")
            sb.appendLine("Touchdowns: ")
            sb.appendLine("Interceptions: ")
        } else if (response.containsKey("rushing_yards")) {
            sb.appendLine("Rushing Yards: ")
            sb.appendLine("Touchdowns: ")
        } else if (response.containsKey("receiving_yards")) {
            sb.appendLine("Receiving Yards: ")
            sb.appendLine("Touchdowns: ")
        }
        
        val injured = response["injured"] as? Boolean ?: false
        sb.appendLine("\nStatus: ")
        return sb.toString()
    }

    private fun formatWeather(response: Map<String, Any>): String {
        val sb = StringBuilder()
        sb.appendLine("☁️ STADIUM WEATHER")
        sb.appendLine("═══════════════════")
        sb.appendLine("Stadium: ")
        sb.appendLine("City: ")
        sb.appendLine("\n🌡️ Temperature: °F")
        sb.appendLine("☁️ Conditions: ")
        sb.appendLine("💨 Wind:  mph")
        sb.appendLine("🌧️ Precipitation: %")
        sb.appendLine("💧 Humidity: %")
        sb.appendLine("\n🎯 Impact: ")
        return sb.toString()
    }

    private fun formatInjuries(response: Map<String, Any>): String {
        val sb = StringBuilder()
        sb.appendLine("🏥 INJURY REPORT")
        sb.appendLine("═══════════════════")
        sb.appendLine("Team: \n")
        
        @Suppress("UNCHECKED_CAST")
        val injuries = response["injuries"] as List<Map<String, String>>
        if (injuries.isEmpty()) {
            sb.appendLine("No injuries reported")
        } else {
            injuries.forEach { injury ->
                val status = when (injury["status"]) {
                    "Out" -> "❌"
                    "Questionable" -> "⚠️"
                    else -> "✅"
                }
                sb.appendLine("  ()")
                sb.appendLine("   Injury: ")
                sb.appendLine("   Status: \n")
            }
        }
        return sb.toString()
    }

    private fun formatNews(response: Map<String, Any>): String {
        val sb = StringBuilder()
        sb.appendLine("📰 LATEST NEWS")
        sb.appendLine("═══════════════════")
        
        @Suppress("UNCHECKED_CAST")
        val news = response["news"] as List<Map<String, String>>
        news.take(5).forEach { item ->
            sb.appendLine("• ")
            sb.appendLine("   - \n")
        }
        return sb.toString()
    }

    private fun formatWinLoss(response: Map<String, Any>): String {
        val sb = StringBuilder()
        sb.appendLine("📊 WIN/LOSS RECORD")
        sb.appendLine("═══════════════════")
        sb.appendLine("Season: ")
        sb.appendLine("Win %: %")
        
        val streak = response["current_streak"] as String
        val streakEmoji = if (streak.startsWith("W")) "🔥" else "📉"
        sb.appendLine("Streak:  ")
        
        sb.appendLine("\nLast 5 games:")
        @Suppress("UNCHECKED_CAST")
        val last5 = response["last_5_games"] as List<String>
        last5.forEach { game ->
            when (game) {
                "W" -> sb.append("✅ ")
                "L" -> sb.append("❌ ")
                else -> sb.append("⬜ ")
            }
        }
        
        sb.appendLine("\n\nHome: ")
        sb.appendLine("Away: ")
        sb.appendLine("Division: ")
        return sb.toString()
    }

    private fun showLoading() {
        loadingIndicator.visibility = android.view.View.VISIBLE
        resultText.visibility = android.view.View.GONE
    }

    private fun hideLoading() {
        loadingIndicator.visibility = android.view.View.GONE
        resultText.visibility = android.view.View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}

interface ApiService {
    @GET("test")
    suspend fun testConnection(): TestResponse

    @GET("team_stats")
    suspend fun getTeamStats(@Query("team") team: String): Map<String, Any>

    @GET("player_stats")
    suspend fun getPlayerStats(@Query("name") name: String): Map<String, Any>

    @GET("weather")
    suspend fun getWeather(@Query("team") team: String): Map<String, Any>

    @GET("injuries")
    suspend fun getInjuries(@Query("team") team: String): Map<String, Any>

    @GET("news")
    suspend fun getNews(@Query("entity") entity: String): Map<String, Any>

    @GET("win_loss")
    suspend fun getWinLoss(@Query("team") team: String): Map<String, Any>
}

data class TestResponse(
    val status: String,
    val message: String
)
