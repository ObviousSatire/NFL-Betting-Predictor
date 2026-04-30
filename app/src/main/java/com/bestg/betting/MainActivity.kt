package com.bestg.betting

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import android.text.method.ScrollingMovementMethod
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.util.Log

class MainActivity : AppCompatActivity() {
    
    private lateinit var teamSpinner: Spinner
    private lateinit var playerSpinner: Spinner
    private lateinit var resultText: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var apiService: ApiService
    private lateinit var sharedPrefs: SharedPreferences
    
    private var currentMode = "STATS"
    private var currentTeam = ""
    private var currentPlayer = ""
    private var opponentTeam = ""
    
    private val allNFLTeams = listOf(
        "Buffalo Bills", "Miami Dolphins", "New England Patriots", "New York Jets",
        "Baltimore Ravens", "Cincinnati Bengals", "Cleveland Browns", "Pittsburgh Steelers",
        "Houston Texans", "Indianapolis Colts", "Jacksonville Jaguars", "Tennessee Titans",
        "Denver Broncos", "Kansas City Chiefs", "Las Vegas Raiders", "Los Angeles Chargers",
        "Dallas Cowboys", "New York Giants", "Philadelphia Eagles", "Washington Commanders",
        "Chicago Bears", "Detroit Lions", "Green Bay Packers", "Minnesota Vikings",
        "Atlanta Falcons", "Carolina Panthers", "New Orleans Saints", "Tampa Bay Buccaneers",
        "Arizona Cardinals", "Los Angeles Rams", "San Francisco 49ers", "Seattle Seahawks"
    )
    
    private val manualStats = mutableMapOf<String, MutableMap<String, String>>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        sharedPrefs = getSharedPreferences("NFL_Betting_Prefs", Context.MODE_PRIVATE)
        loadManualOverrides()
        
        teamSpinner = findViewById(R.id.teamSpinner)
        playerSpinner = findViewById(R.id.playerSpinner)
        resultText = findViewById(R.id.resultText)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        
        resultText.movementMethod = ScrollingMovementMethod()
        
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.0.248:5000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        apiService = retrofit.create(ApiService::class.java)
        
        playerSpinner.visibility = View.GONE
        
        findViewById<MaterialButton>(R.id.testButton).setOnClickListener { testConnection() }
        findViewById<MaterialButton>(R.id.loadStatsButton).setOnClickListener { setStatsMode() }
        findViewById<MaterialButton>(R.id.loadPlayerButton).setOnClickListener { setPlayerMode() }
        findViewById<MaterialButton>(R.id.predictButton).setOnClickListener { setPredictMode() }
        
        findViewById<MaterialButton>(R.id.refreshAllButton).setOnClickListener { 
            when (currentMode) {
                "STATS" -> getTeamStats()
                "PLAYER" -> getPlayerStats()
                "PREDICT" -> fetchPrediction(currentTeam, opponentTeam)
            }
        }
        
        findViewById<MaterialButton>(R.id.clearButton).setOnClickListener { resultText.text = "" }
        findViewById<MaterialButton>(R.id.helpButton).setOnClickListener { showHelp() }
        
        findViewById<MaterialButton>(R.id.helpButton).setOnLongClickListener {
            if (currentMode == "STATS") showManualOverrideDialog()
            else Toast.makeText(this, "Manual override only available in Stats Mode", Toast.LENGTH_SHORT).show()
            true
        }
        
        setStatsMode()
        Handler(Looper.getMainLooper()).postDelayed({ getTeamStats() }, 500)
    }
    
    private fun setStatsMode() {
        currentMode = "STATS"
        teamSpinner.visibility = View.VISIBLE
        playerSpinner.visibility = View.GONE
        resultText.text = "STATS MODE\nSelect a team"
        
        val numberedTeams = allNFLTeams.mapIndexed { index, team -> "${index + 1}. $team" }
        val teamAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, numberedTeams)
        teamAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        teamSpinner.adapter = teamAdapter
        
        teamSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTeam = allNFLTeams[position]
                getTeamStats()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        if (currentTeam.isNotEmpty()) {
            val index = allNFLTeams.indexOf(currentTeam)
            if (index >= 0) teamSpinner.setSelection(index)
        }
    }
    
    private fun setPlayerMode() {
        currentMode = "PLAYER"
        teamSpinner.visibility = View.VISIBLE
        playerSpinner.visibility = View.VISIBLE
        resultText.text = "PLAYER MODE\nSelect a team, then pick a player"
        
        val numberedTeams = allNFLTeams.mapIndexed { index, team -> "${index + 1}. $team" }
        val teamAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, numberedTeams)
        teamAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        teamSpinner.adapter = teamAdapter
        
        teamSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTeam = allNFLTeams[position]
                loadRosterForTeam(currentTeam)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        if (currentTeam.isNotEmpty()) {
            val index = allNFLTeams.indexOf(currentTeam)
            if (index >= 0) {
                teamSpinner.setSelection(index)
                loadRosterForTeam(currentTeam)
            }
        }
    }
    
    private fun setPredictMode() {
        currentMode = "PREDICT"
        teamSpinner.visibility = View.VISIBLE
        playerSpinner.visibility = View.VISIBLE
        resultText.text = "PREDICT MODE\nSelect your team, then pick opponent"
        
        val numberedTeams = allNFLTeams.mapIndexed { index, team -> "${index + 1}. $team" }
        val teamAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, numberedTeams)
        teamAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        teamSpinner.adapter = teamAdapter
        
        teamSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTeam = allNFLTeams[position]
                updateOpponentSpinner()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        if (currentTeam.isEmpty()) currentTeam = allNFLTeams[0]
        val index = allNFLTeams.indexOf(currentTeam)
        if (index >= 0) teamSpinner.setSelection(index)
        
        updateOpponentSpinner()
    }
    
    private fun updateOpponentSpinner() {
        val opponents = allNFLTeams.filter { it != currentTeam }
        val numberedOpponents = opponents.mapIndexed { index, team -> "${index + 1}. $team" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, numberedOpponents)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        playerSpinner.adapter = adapter
        
        playerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0 && position < opponents.size) {
                    opponentTeam = opponents[position]
                    fetchPrediction(currentTeam, opponentTeam)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        if (opponents.isNotEmpty()) opponentTeam = opponents[0]
    }
    
    private fun loadRosterForTeam(teamName: String) {
        showLoading(true)
        resultText.text = "Loading roster for $teamName..."
        
        apiService.getRoster(teamName).enqueue(object : Callback<RosterResponse> {
            override fun onResponse(call: Call<RosterResponse>, response: Response<RosterResponse>) {
                showLoading(false)
                if (response.isSuccessful) {
                    val players = response.body()?.players ?: emptyList()
                    if (players.isEmpty()) {
                        resultText.text = "No players found for $teamName"
                        return
                    }
                    
                    val playerNames = players.mapIndexed { index, player -> 
                        val inj = if (player.injured == true) " [INJ]" else ""
                        "${index + 1}. ${player.name} (${player.position})$inj"
                    }
                    
                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, playerNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    playerSpinner.adapter = adapter
                    
                    playerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            if (position >= 0 && position < players.size) {
                                currentPlayer = players[position].name ?: ""
                                getPlayerStats()
                            }
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                    
                    resultText.text = "${players.size} players loaded\nSelect a player"
                } else {
                    resultText.text = "Failed to load roster"
                }
            }
            override fun onFailure(call: Call<RosterResponse>, t: Throwable) {
                showLoading(false)
                resultText.text = "Error: ${t.message}"
            }
        })
    }
    
    private fun getTeamStats() {
        val team = currentTeam
        if (team.isEmpty()) return
        
        showLoading(true)
        resultText.text = "Loading $team data..."
        
        val manual = manualStats[team]
        if (manual != null && manual.isNotEmpty()) {
            showLoading(false)
            displayManualStats(team, manual)
            return
        }
        
        // Fetch ALL data - stats, weather, injuries, news - and display together
        apiService.getTeamStats(team).enqueue(object : Callback<TeamStatsResponse> {
            override fun onResponse(call: Call<TeamStatsResponse>, response: Response<TeamStatsResponse>) {
                val stats = response.body()
                apiService.getWinLoss(team).enqueue(object : Callback<WinLossResponse> {
                    override fun onResponse(call: Call<WinLossResponse>, wlResponse: Response<WinLossResponse>) {
                        val wl = wlResponse.body()
                        apiService.getWeather(team).enqueue(object : Callback<WeatherResponse> {
                            override fun onResponse(call: Call<WeatherResponse>, weatherResponse: Response<WeatherResponse>) {
                                val weather = weatherResponse.body()
                                apiService.getInjuries(team).enqueue(object : Callback<InjuriesResponse> {
                                    override fun onResponse(call: Call<InjuriesResponse>, injResponse: Response<InjuriesResponse>) {
                                        val injuries = injResponse.body()
                                        apiService.getNews(team).enqueue(object : Callback<NewsResponse> {
                                            override fun onResponse(call: Call<NewsResponse>, newsResponse: Response<NewsResponse>) {
                                                showLoading(false)
                                                val news = newsResponse.body()
                                                Log.d("NEWS", "News received: ${news?.news?.size} articles")
                                                displayFullStats(team, stats, wl, weather, injuries, news)
                                            }
                                            override fun onFailure(call: Call<NewsResponse>, t: Throwable) {
                                                showLoading(false)
                                                Log.e("NEWS", "News failed: ${t.message}")
                                                displayFullStats(team, stats, wl, weather, injuries, null)
                                            }
                                        })
                                    }
                                    override fun onFailure(call: Call<InjuriesResponse>, t: Throwable) {
                                        showLoading(false)
                                        displayFullStats(team, stats, wl, weather, null, null)
                                    }
                                })
                            }
                            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                                showLoading(false)
                                displayFullStats(team, stats, wl, null, null, null)
                            }
                        })
                    }
                    override fun onFailure(call: Call<WinLossResponse>, t: Throwable) {
                        showLoading(false)
                        resultText.text = "Error loading win/loss data"
                    }
                })
            }
            override fun onFailure(call: Call<TeamStatsResponse>, t: Throwable) {
                showLoading(false)
                resultText.text = "Error loading team stats"
            }
        })
    }
    
    private fun displayFullStats(team: String, stats: TeamStatsResponse?, wl: WinLossResponse?, 
                                  weather: WeatherResponse?, injuries: InjuriesResponse?, news: NewsResponse?) {
        val sb = StringBuilder()
        
        sb.append("$team\n")
        sb.append("==============================\n\n")
        
        // Team Stats
        sb.append("[ TEAM STATS ]\n")
        if (stats != null && wl != null) {
            val winPercent = (wl.win_percentage ?: 0.0) * 100
            val pointDiff = (stats.points_for ?: 0) - (stats.points_against ?: 0)
            val last5 = wl.last_5_games?.joinToString(" ") ?: "N/A"
            
            sb.append("Record: ${wl.regular_season_record ?: "N/A"}\n")
            sb.append("Win: ${String.format("%.1f", winPercent)}%\n")
            sb.append("PF: ${stats.points_for ?: 0}  PA: ${stats.points_against ?: 0}\n")
            sb.append("Diff: ${if(pointDiff >= 0) "+" else ""}$pointDiff\n")
            sb.append("Streak: ${stats.streak ?: "N/A"}\n")
            sb.append("Last 5: $last5\n")
            sb.append("Home: ${wl.home_record ?: "N/A"}\n")
            sb.append("Away: ${wl.away_record ?: "N/A"}\n")
            if (wl.has_playoffs == true && wl.playoff_record != "0-0") {
                sb.append("Playoffs: ${wl.playoff_record}\n")
            }
        } else {
            sb.append("Stats unavailable\n")
        }
        sb.append("\n")
        
        // Weather
        sb.append("[ WEATHER ]\n")
        if (weather != null) {
            sb.append("${weather.stadium ?: team}\n")
            sb.append("${weather.temperature ?: "--"}F, ${weather.conditions ?: "N/A"}\n")
            sb.append("Wind: ${weather.wind_speed ?: "--"} mph\n")
            sb.append("Rain: ${weather.precipitation ?: 0}%\n")
            sb.append("${weather.impact ?: "Minimal impact"}\n")
        } else {
            sb.append("Weather unavailable\n")
        }
        sb.append("\n")
        
        // Injuries
        sb.append("[ INJURIES ]\n")
        if (injuries != null && injuries.injuries != null && injuries.injuries!!.isNotEmpty()) {
            val items = injuries.injuries!!
            for (i in 0 until minOf(items.size, 4)) {
                val item = items[i]
                sb.append("- ${item.player ?: "Unknown"} (${item.position ?: "N/A"})\n")
                sb.append("  ${item.injury ?: "Unknown"} - ${item.status ?: "Unknown"}\n")
            }
        } else {
            sb.append("No injuries reported\n")
        }
        sb.append("\n")
        
        // News - FIXED
        sb.append("[ LATEST NEWS ]\n")
        if (news != null && news.news != null && news.news!!.isNotEmpty()) {
            val items = news.news!!
            for (i in 0 until minOf(items.size, 5)) {
                val item = items[i]
                sb.append("${i+1}. ${item.headline ?: "No headline"}\n")
                sb.append("   ${item.date ?: ""}\n\n")
            }
        } else {
            sb.append("No recent news\n")
        }
        
        resultText.text = sb.toString()
    }
    
    private fun displayManualStats(team: String, manual: MutableMap<String, String>) {
        val record = manual["record"] ?: "N/A"
        val winPercent = manual["win_percentage"]?.toDoubleOrNull() ?: 0.0
        val pf = manual["points_for"]?.toIntOrNull() ?: 0
        val pa = manual["points_against"]?.toIntOrNull() ?: 0
        val last5 = manual["last_5"]?.replace(",", " ") ?: "N/A"
        
        resultText.text = """
            $team (MANUAL)
            ==============================
            
            [ MANUAL STATS ]
            Record: $record
            Win: ${String.format("%.1f", winPercent)}%
            PF: $pf  PA: $pa
            Diff: ${pf - pa}
            Last 5: $last5
            
            Long press HELP to edit
        """.trimIndent()
    }
    
    private fun getPlayerStats() {
        if (currentPlayer.isEmpty() || currentTeam.isEmpty()) {
            resultText.text = "Please select a player first"
            return
        }
        
        showLoading(true)
        apiService.getPlayerStats(currentPlayer, currentTeam).enqueue(object : Callback<PlayerStatsResponse> {
            override fun onResponse(call: Call<PlayerStatsResponse>, response: Response<PlayerStatsResponse>) {
                showLoading(false)
                if (response.isSuccessful) {
                    displayPlayerStats(response.body())
                } else {
                    resultText.text = "Failed to get player stats"
                }
            }
            override fun onFailure(call: Call<PlayerStatsResponse>, t: Throwable) {
                showLoading(false)
                resultText.text = "Error: ${t.message}"
            }
        })
    }
    
    private fun displayPlayerStats(p: PlayerStatsResponse?) {
        if (p == null) {
            resultText.text = "No player data"
            return
        }
        
        val stats = p.stats
        val sb = StringBuilder()
        
        sb.append("${p.name}\n")
        sb.append("==============================\n\n")
        
        sb.append("[ INFO ]\n")
        sb.append("Position: ${p.position ?: "N/A"}\n")
        sb.append("Jersey: #${p.jersey ?: "N/A"}\n")
        sb.append("Team: ${p.team ?: currentTeam}\n")
        sb.append("Status: ${if (p.injured == true) "INJURED" else "Active"}\n")
        
        if (stats == null) {
            sb.append("\nNo season stats available")
            resultText.text = sb.toString()
            return
        }
        
        if (stats.passing_yards != null || stats.passing_tds != null) {
            sb.append("\n[ PASSING ]\n")
            stats.passing_yards?.let { sb.append("Yards: $it\n") }
            stats.passing_tds?.let { sb.append("TDs: $it\n") }
            val comp = stats.completions ?: 0
            val att = stats.passing_attempts ?: 0
            if (comp > 0 || att > 0) {
                val pct = if (att > 0) (comp * 100.0 / att) else 0.0
                sb.append("Comp: $comp/$att (${String.format("%.1f", pct)}%)\n")
            }
            stats.interceptions?.let { sb.append("INTs: $it\n") }
            stats.qb_rating?.let { sb.append("Rating: ${String.format("%.1f", it)}\n") }
        }
        
        if (stats.rushing_yards != null || stats.rushing_tds != null) {
            sb.append("\n[ RUSHING ]\n")
            stats.rushing_yards?.let { sb.append("Yards: $it\n") }
            stats.rushing_tds?.let { sb.append("TDs: $it\n") }
            stats.rushing_attempts?.let { sb.append("Att: $it\n") }
            val yards = stats.rushing_yards ?: 0
            val attempts = stats.rushing_attempts ?: 0
            if (yards > 0 && attempts > 0) {
                sb.append("Avg: ${String.format("%.1f", yards.toDouble() / attempts)}\n")
            }
        }
        
        if (stats.receiving_yards != null || stats.receiving_tds != null) {
            sb.append("\n[ RECEIVING ]\n")
            stats.receiving_yards?.let { sb.append("Yards: $it\n") }
            stats.receiving_tds?.let { sb.append("TDs: $it\n") }
            stats.receptions?.let { sb.append("Rec: $it\n") }
            stats.targets?.let { sb.append("Tgts: $it\n") }
            val rec = stats.receptions ?: 0
            val yards = stats.receiving_yards ?: 0
            if (rec > 0 && yards > 0) {
                sb.append("Avg: ${String.format("%.1f", yards.toDouble() / rec)}\n")
            }
        }
        
        if (stats.tackles != null || stats.sacks != null) {
            sb.append("\n[ DEFENSE ]\n")
            stats.tackles?.let { sb.append("Tackles: $it\n") }
            stats.sacks?.let { sb.append("Sacks: $it\n") }
            stats.def_interceptions?.let { sb.append("INTs: $it\n") }
        }
        
        resultText.text = sb.toString()
    }
    
    private fun fetchPrediction(team1: String, team2: String) {
        if (team1.isEmpty() || team2.isEmpty()) return
        
        showLoading(true)
        resultText.text = "Analyzing $team1 vs $team2..."
        
        apiService.getWinLoss(team1).enqueue(object : Callback<WinLossResponse> {
            override fun onResponse(call: Call<WinLossResponse>, wl1Response: Response<WinLossResponse>) {
                val wl1 = wl1Response.body()
                apiService.getWinLoss(team2).enqueue(object : Callback<WinLossResponse> {
                    override fun onResponse(call: Call<WinLossResponse>, wl2Response: Response<WinLossResponse>) {
                        val wl2 = wl2Response.body()
                        apiService.getPrediction(team1, team2).enqueue(object : Callback<PredictionResponse> {
                            override fun onResponse(call: Call<PredictionResponse>, response: Response<PredictionResponse>) {
                                showLoading(false)
                                if (response.isSuccessful) {
                                    displayPrediction(response.body(), wl1, wl2, team1, team2)
                                } else {
                                    resultText.text = "Prediction failed"
                                }
                            }
                            override fun onFailure(call: Call<PredictionResponse>, t: Throwable) {
                                showLoading(false)
                                resultText.text = "Error: ${t.message}"
                            }
                        })
                    }
                    override fun onFailure(call: Call<WinLossResponse>, t: Throwable) {
                        fetchBasicPrediction(team1, team2)
                    }
                })
            }
            override fun onFailure(call: Call<WinLossResponse>, t: Throwable) {
                fetchBasicPrediction(team1, team2)
            }
        })
    }
    
    private fun fetchBasicPrediction(team1: String, team2: String) {
        apiService.getPrediction(team1, team2).enqueue(object : Callback<PredictionResponse> {
            override fun onResponse(call: Call<PredictionResponse>, response: Response<PredictionResponse>) {
                showLoading(false)
                if (response.isSuccessful) {
                    displayPrediction(response.body(), null, null, team1, team2)
                }
            }
            override fun onFailure(call: Call<PredictionResponse>, t: Throwable) {
                showLoading(false)
                resultText.text = "Error: ${t.message}"
            }
        })
    }
    
    private fun displayPrediction(pred: PredictionResponse?, wl1: WinLossResponse?, 
                                   wl2: WinLossResponse?, team1: String, team2: String) {
        val sb = StringBuilder()
        
        sb.append("PREDICTION: $team1 vs $team2\n")
        sb.append("==============================\n\n")
        
        if (wl1 != null && wl2 != null) {
            sb.append("[ RECORDS ]\n")
            sb.append("$team1: ${wl1.regular_season_record ?: "N/A"} (${String.format("%.1f", (wl1.win_percentage ?: 0.0) * 100)}%)\n")
            sb.append("$team2: ${wl2.regular_season_record ?: "N/A"} (${String.format("%.1f", (wl2.win_percentage ?: 0.0) * 100)}%)\n\n")
        }
        
        sb.append("[ WIN PROBABILITY ]\n")
        val prob1 = pred?.team1_win_probability ?: 50.0
        val prob2 = pred?.team2_win_probability ?: 50.0
        sb.append("$team1: ${String.format("%.1f", prob1)}%\n")
        sb.append("$team2: ${String.format("%.1f", prob2)}%\n\n")
        
        sb.append("[ PREDICTION ]\n")
        sb.append("Winner: ${pred?.predicted_winner ?: "N/A"}\n")
        sb.append("Confidence: ${String.format("%.1f", pred?.confidence ?: 0.0)}%\n\n")
        
        sb.append("[ KEY FACTORS ]\n")
        pred?.key_factors?.forEach {
            sb.append("- $it\n")
        } ?: sb.append("Analysis unavailable\n")
        
        resultText.text = sb.toString()
    }
    
    private fun loadManualOverrides() {
        sharedPrefs.getStringSet("manual_overrides", emptySet())?.forEach { entry ->
            val parts = entry.split("|")
            if (parts.size == 3) {
                manualStats.getOrPut(parts[0]) { mutableMapOf() }[parts[1]] = parts[2]
            }
        }
    }
    
    private fun saveManualOverride(team: String, statName: String, value: String) {
        manualStats.getOrPut(team) { mutableMapOf() }[statName] = value
        val savedSet = manualStats.flatMap { (t, stats) -> stats.map { "$t|${it.key}|${it.value}" } }.toSet()
        sharedPrefs.edit().putStringSet("manual_overrides", savedSet).apply()
    }
    
    private fun showManualOverrideDialog() {
        val team = currentTeam
        AlertDialog.Builder(this)
            .setTitle("Manual Override - $team")
            .setItems(arrayOf("Record", "Win Percentage", "Points For", "Points Against", "Last 5 Games", "Reset Team Data")) { _, which ->
                when (which) {
                    0 -> showStatEditDialog(team, "record", "Enter Record (e.g., 14-3)")
                    1 -> showStatEditDialog(team, "win_percentage", "Enter Win % (e.g., 82.4)")
                    2 -> showStatEditDialog(team, "points_for", "Enter Points For")
                    3 -> showStatEditDialog(team, "points_against", "Enter Points Against")
                    4 -> showLast5EditDialog(team)
                    5 -> resetTeamData(team)
                }
            }.show()
    }
    
    private fun showStatEditDialog(team: String, statKey: String, prompt: String) {
        val input = EditText(this)
        input.hint = prompt
        input.setText(manualStats[team]?.get(statKey) ?: "")
        AlertDialog.Builder(this)
            .setTitle("Edit $statKey")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newValue = input.text.toString()
                if (newValue.isNotBlank()) {
                    saveManualOverride(team, statKey, newValue)
                    if (currentMode == "STATS") getTeamStats()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showLast5EditDialog(team: String) {
        val games = arrayOf("Game 1", "Game 2", "Game 3", "Game 4", "Game 5")
        val checked = booleanArrayOf(false, false, false, false, false)
        AlertDialog.Builder(this)
            .setTitle("Last 5 Games Results")
            .setMultiChoiceItems(games, checked) { _, _, _ -> }
            .setPositiveButton("Save") { _, _ ->
                val results = checked.map { if (it) "W" else "L" }.joinToString(",")
                saveManualOverride(team, "last_5", results)
                if (currentMode == "STATS") getTeamStats()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun resetTeamData(team: String) {
        AlertDialog.Builder(this)
            .setTitle("Reset $team Data")
            .setMessage("Remove all manual overrides for $team?")
            .setPositiveButton("Reset") { _, _ ->
                manualStats.remove(team)
                sharedPrefs.edit().putStringSet("manual_overrides", emptySet()).apply()
                if (currentMode == "STATS") getTeamStats()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun testConnection() {
        showLoading(true)
        apiService.testConnection().enqueue(object : Callback<TestResponse> {
            override fun onResponse(call: Call<TestResponse>, response: Response<TestResponse>) {
                showLoading(false)
                resultText.text = if (response.isSuccessful) "API Connected" else "Server error"
            }
            override fun onFailure(call: Call<TestResponse>, t: Throwable) {
                showLoading(false)
                resultText.text = "Cannot reach server"
            }
        })
    }
    
    private fun showHelp() {
        resultText.text = """
            HELP
            ==============================
            
            MODES:
            STATS - Team info, weather, injuries
            PLAYER - Full player stats
            PREDICT - AI win prediction
            
            Long press HELP in Stats Mode
            for manual override
        """.trimIndent()
    }
    
    private fun showLoading(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }
}
