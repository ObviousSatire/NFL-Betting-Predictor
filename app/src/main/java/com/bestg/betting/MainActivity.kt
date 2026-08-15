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
    private lateinit var scoreBanner: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var apiService: ApiService
    private lateinit var sharedPrefs: SharedPreferences
    
    private var currentMode = "STATS"
    private var currentTeam = ""
    private var currentPlayer = ""
    private var opponentTeam = ""
    private var serverIp = "10.0.0.60"
    
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
    private val scoreHandler = Handler(Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        sharedPrefs = getSharedPreferences("NFL_Betting_Prefs", Context.MODE_PRIVATE)
        loadManualOverrides()
        serverIp = sharedPrefs.getString("server_ip", "10.0.0.60") ?: "10.0.0.60"
        
        teamSpinner = findViewById(R.id.teamSpinner)
        playerSpinner = findViewById(R.id.playerSpinner)
        resultText = findViewById(R.id.resultText)
        scoreBanner = findViewById(R.id.scoreBanner)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        
        resultText.movementMethod = ScrollingMovementMethod()
        scoreBanner.isSelected = true
        
        setupRetrofit()
        
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
            fetchLiveScores()
        }
        
        findViewById<MaterialButton>(R.id.clearButton).setOnClickListener { resultText.text = "" }
        findViewById<MaterialButton>(R.id.helpButton).setOnClickListener { showHelp() }
        
        findViewById<MaterialButton>(R.id.helpButton).setOnLongClickListener {
            showOptionsDialog()
            true
        }
        
        setStatsMode()
        Handler(Looper.getMainLooper()).postDelayed({ getTeamStats() }, 500)
        fetchLiveScores()
        startScoreUpdates()
    }
    
    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://$serverIp:5000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)
    }
    
    private fun startScoreUpdates() {
        scoreHandler.postDelayed(object : Runnable {
            override fun run() {
                fetchLiveScores()
                scoreHandler.postDelayed(this, 30000)
            }
        }, 30000)
    }
    
    private fun fetchLiveScores() {
        apiService.getLiveScores().enqueue(object : Callback<LiveScoresResponse> {
            override fun onResponse(call: Call<LiveScoresResponse>, response: Response<LiveScoresResponse>) {
                if (response.isSuccessful) {
                    val scores = response.body()?.scores ?: emptyList()
                    if (scores.isNotEmpty()) {
                        scoreBanner.text = scores.joinToString(" | ") { 
                            "${it.away} ${it.away_score}-${it.home_score} ${it.home} (${it.detail.ifEmpty { it.status }})" 
                        }
                    }
                }
            }
            override fun onFailure(call: Call<LiveScoresResponse>, t: Throwable) {}
        })
    }
    
    private fun showOptionsDialog() {
        val options = if (currentMode == "STATS") {
            arrayOf("Change Server IP", "Manual Override Stats")
        } else {
            arrayOf("Change Server IP")
        }
        AlertDialog.Builder(this)
            .setTitle("Options")
            .setItems(options) { _, which ->
                if (options[which] == "Change Server IP") showServerIpDialog()
                else showManualOverrideDialog()
            }.show()
    }
    
    private fun showServerIpDialog() {
        val input = EditText(this)
        input.setText(serverIp)
        AlertDialog.Builder(this)
            .setTitle("Set Server IP")
            .setMessage("Current: $serverIp:5000")
            .setView(input)
            .setPositiveButton("Save & Test") { _, _ ->
                val newIp = input.text.toString().trim()
                if (newIp.isNotBlank()) {
                    serverIp = newIp
                    sharedPrefs.edit().putString("server_ip", newIp).apply()
                    setupRetrofit()
                    testConnection()
                }
            }.setNegativeButton("Cancel", null).show()
    }
    
    private fun setStatsMode() {
        currentMode = "STATS"
        teamSpinner.visibility = View.VISIBLE
        playerSpinner.visibility = View.GONE
        val numberedTeams = allNFLTeams.mapIndexed { index, team -> "${index + 1}. $team" }
        val teamAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, numberedTeams)
        teamAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        teamSpinner.adapter = teamAdapter
        teamSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTeam = allNFLTeams[position]; getTeamStats()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setPlayerMode() {
        currentMode = "PLAYER"
        teamSpinner.visibility = View.VISIBLE
        playerSpinner.visibility = View.VISIBLE
        val numberedTeams = allNFLTeams.mapIndexed { index, team -> "${index + 1}. $team" }
        val teamAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, numberedTeams)
        teamAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        teamSpinner.adapter = teamAdapter
        teamSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTeam = allNFLTeams[position]; loadRosterForTeam(currentTeam)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setPredictMode() {
        currentMode = "PREDICT"
        teamSpinner.visibility = View.VISIBLE
        playerSpinner.visibility = View.VISIBLE
        val numberedTeams = allNFLTeams.mapIndexed { index, team -> "${index + 1}. $team" }
        val teamAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, numberedTeams)
        teamAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        teamSpinner.adapter = teamAdapter
        teamSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTeam = allNFLTeams[position]; updateOpponentSpinner()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        if (currentTeam.isEmpty()) currentTeam = allNFLTeams[0]
        updateOpponentSpinner()
    }
    
    private fun updateOpponentSpinner() {
        val opponents = allNFLTeams.filter { it != currentTeam }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, opponents.mapIndexed { i, t -> "${i+1}. $t" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        playerSpinner.adapter = adapter
        playerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0 && position < opponents.size) {
                    opponentTeam = opponents[position]; fetchPrediction(currentTeam, opponentTeam)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun loadRosterForTeam(teamName: String) {
        showLoading(true)
        apiService.getRoster(teamName).enqueue(object : Callback<RosterResponse> {
            override fun onResponse(call: Call<RosterResponse>, response: Response<RosterResponse>) {
                showLoading(false)
                if (response.isSuccessful) {
                    val players = response.body()?.players ?: emptyList()
                    if (players.isEmpty()) { resultText.text = "No players found"; return }
                    val playerNames = players.mapIndexed { index, player -> 
                        "${index + 1}. ${player.name} (${player.position})${if (player.injured == true) " [INJ]" else ""}"
                    }
                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, playerNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    playerSpinner.adapter = adapter
                    playerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            if (position >= 0 && position < players.size) {
                                currentPlayer = players[position].name ?: ""; getPlayerStats()
                            }
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                    resultText.text = "${players.size} players loaded\nSelect a player"
                }
            }
            override fun onFailure(call: Call<RosterResponse>, t: Throwable) { showLoading(false) }
        })
    }
    
    private fun getTeamStats() {
        val team = currentTeam; if (team.isEmpty()) return
        showLoading(true)
        val manual = manualStats[team]
        if (manual != null && manual.isNotEmpty()) { showLoading(false); displayManualStats(team, manual); return }
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
                                                displayFullStats(team, stats, wl, weather, injuries, newsResponse.body())
                                            }
                                            override fun onFailure(call: Call<NewsResponse>, t: Throwable) { showLoading(false) }
                                        })
                                    }
                                    override fun onFailure(call: Call<InjuriesResponse>, t: Throwable) { showLoading(false) }
                                })
                            }
                            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) { showLoading(false) }
                        })
                    }
                    override fun onFailure(call: Call<WinLossResponse>, t: Throwable) { showLoading(false) }
                })
            }
            override fun onFailure(call: Call<TeamStatsResponse>, t: Throwable) { showLoading(false) }
        })
    }
    
    private fun displayFullStats(team: String, stats: TeamStatsResponse?, wl: WinLossResponse?, 
                                  weather: WeatherResponse?, injuries: InjuriesResponse?, news: NewsResponse?) {
        val sb = StringBuilder()
        sb.append("$team\n==============================\n\n[ TEAM STATS ]\n")
        if (stats != null && wl != null) {
            sb.append("Record: ${wl.regular_season_record ?: "N/A"}\n")
            sb.append("Win: ${String.format("%.1f", (wl.win_percentage ?: 0.0) * 100)}%\n")
            sb.append("PF: ${stats.points_for ?: 0}  PA: ${stats.points_against ?: 0}\n")
            sb.append("Streak: ${stats.streak ?: "N/A"}\n")
        }
        sb.append("\n[ WEATHER ]\n")
        if (weather != null) sb.append("${weather.temperature ?: "--"}F, ${weather.conditions ?: "N/A"}\n${weather.impact ?: ""}\n")
        sb.append("\n[ INJURIES ]\n")
        if (injuries?.injuries != null && injuries.injuries!!.isNotEmpty()) {
            for (i in 0 until minOf(injuries.injuries!!.size, 3)) {
                val item = injuries.injuries!![i]
                sb.append("- ${item.player}: ${item.status}\n")
            }
        } else sb.append("None reported\n")
        sb.append("\n[ NEWS ]\n")
        if (news?.news != null && news.news!!.isNotEmpty()) {
            for (i in 0 until minOf(news.news!!.size, 3)) {
                sb.append("${i+1}. ${news.news!![i].headline}\n")
            }
        } else sb.append("No recent news\n")
        resultText.text = sb.toString()
    }
    
    private fun displayManualStats(team: String, manual: MutableMap<String, String>) {
        resultText.text = "$team (MANUAL)\nRecord: ${manual["record"] ?: "N/A"}\nPF: ${manual["points_for"] ?: 0}  PA: ${manual["points_against"] ?: 0}"
    }
    
    private fun getPlayerStats() {
        if (currentPlayer.isEmpty()) return
        showLoading(true)
        apiService.getPlayerStats(currentPlayer, currentTeam).enqueue(object : Callback<PlayerStatsResponse> {
            override fun onResponse(call: Call<PlayerStatsResponse>, response: Response<PlayerStatsResponse>) {
                showLoading(false)
                if (response.isSuccessful) displayPlayerStats(response.body())
            }
            override fun onFailure(call: Call<PlayerStatsResponse>, t: Throwable) { showLoading(false) }
        })
    }
    
    private fun displayPlayerStats(p: PlayerStatsResponse?) {
        if (p == null) return
        val sb = StringBuilder()
        sb.append("${p.name}\nPosition: ${p.position} | #${p.jersey}\nTeam: ${p.team}\n")
        if (p.stats == null) {
            // Don't fetch preseason here - let getPlayerStats handle it
            resultText.text = sb.toString()
            return
        }
        val s = p.stats
        if (s!!.passing_yards != null) sb.append("\n[PASSING] Yards: ${s.passing_yards} TDs: ${s.passing_tds} INTs: ${s.interceptions}")
        if (s.rushing_yards != null) sb.append("\n[RUSHING] Yards: ${s.rushing_yards} TDs: ${s.rushing_tds}")
        if (s.receiving_yards != null) sb.append("\n[RECEIVING] Yards: ${s.receiving_yards} TDs: ${s.receiving_tds} Rec: ${s.receptions}")
        resultText.text = sb.toString()
    }
    
    private fun fetchPrediction(team1: String, team2: String) {
        if (team1.isEmpty() || team2.isEmpty()) return
        showLoading(true)
        apiService.getPrediction(team1, team2).enqueue(object : Callback<PredictionResponse> {
            override fun onResponse(call: Call<PredictionResponse>, response: Response<PredictionResponse>) {
                showLoading(false)
                if (response.isSuccessful) {
                    val p = response.body()
                    resultText.text = "$team1 vs $team2\nWinner: ${p?.predicted_winner}\n${team1}: ${p?.team1_win_probability}%\n${team2}: ${p?.team2_win_probability}%"
                }
            }
            override fun onFailure(call: Call<PredictionResponse>, t: Throwable) { showLoading(false) }
        })
    }
    
    private fun loadManualOverrides() {
        sharedPrefs.getStringSet("manual_overrides", emptySet())?.forEach { entry ->
            val parts = entry.split("|")
            if (parts.size == 3) manualStats.getOrPut(parts[0]) { mutableMapOf() }[parts[1]] = parts[2]
        }
    }
    
    private fun saveManualOverride(team: String, statName: String, value: String) {
        manualStats.getOrPut(team) { mutableMapOf() }[statName] = value
        sharedPrefs.edit().putStringSet("manual_overrides", manualStats.flatMap { (t, s) -> s.map { "$t|${it.key}|${it.value}" } }.toSet()).apply()
    }
    
    private fun showManualOverrideDialog() {
        val team = currentTeam
        AlertDialog.Builder(this).setTitle("Manual Override - $team")
            .setItems(arrayOf("Record", "Points For", "Points Against", "Reset")) { _, which ->
                when (which) {
                    0 -> showStatEditDialog(team, "record")
                    1 -> showStatEditDialog(team, "points_for")
                    2 -> showStatEditDialog(team, "points_against")
                    3 -> { manualStats.remove(team); getTeamStats() }
                }
            }.show()
    }
    
    private fun showStatEditDialog(team: String, statKey: String) {
        val input = EditText(this)
        input.setText(manualStats[team]?.get(statKey) ?: "")
        AlertDialog.Builder(this).setTitle("Edit $statKey").setView(input)
            .setPositiveButton("Save") { _, _ -> saveManualOverride(team, statKey, input.text.toString()); getTeamStats() }
            .setNegativeButton("Cancel", null).show()
    }
    
    private fun fetchPreseasonStats(name: String, team: String) {
        apiService.getPreseasonStats(team, name).enqueue(object : Callback<PreseasonStatsResponse> {
            override fun onResponse(call: Call<PreseasonStatsResponse>, response: Response<PreseasonStatsResponse>) {
                if (response.isSuccessful && response.body()?.stats != null) {
                    val s = response.body()!!.stats!!
                    val sb = StringBuilder()
                    sb.append("$name\nPosition: N/A\nTeam: $team\n")
                    sb.append("\n[ PRESEASON STATS ]")
                    if (s.passing_yards != null) sb.append("\nPassing: ${s.passing_yards} yds, ${s.passing_tds ?: 0} TD")
                    if (s.rushing_yards != null) sb.append("\nRushing: ${s.rushing_yards} yds, ${s.rushing_tds ?: 0} TD")
                    if (s.receiving_yards != null) sb.append("\nReceiving: ${s.receiving_yards} yds, ${s.receiving_tds ?: 0} TD")
                    resultText.text = sb.toString()
                } else {
                    resultText.text = "No season stats available"
                }
            }
            override fun onFailure(call: Call<PreseasonStatsResponse>, t: Throwable) {
                resultText.text = "No season stats available"
            }
        })
    }

    private fun testConnection() {
        showLoading(true)
        apiService.testConnection().enqueue(object : Callback<TestResponse> {
            override fun onResponse(call: Call<TestResponse>, response: Response<TestResponse>) {
                showLoading(false)
                resultText.text = if (response.isSuccessful) "Server: $serverIp:5000\nConnected!" else "Server error"
            }
            override fun onFailure(call: Call<TestResponse>, t: Throwable) { showLoading(false); resultText.text = "Cannot reach $serverIp:5000" }
        })
    }
    
    private fun showHelp() {
        resultText.text = "HELP - $serverIp:5000\n\nSTATS: Team info\nPLAYER: Player stats\nPREDICT: Win prediction\nLong press HELP for settings"
    }
    
    private fun showLoading(show: Boolean) { loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE }
}
