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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var teamSpinner: Spinner
    private lateinit var playerSpinner: Spinner
    private lateinit var resultText: TextView
    private lateinit var scoreBanner: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var gamesContainer: LinearLayout
    private lateinit var gamesScroll: ScrollView
    private lateinit var gamesList: LinearLayout
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
    private val favoriteTeams = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefs = getSharedPreferences("NFL_Betting_Prefs", Context.MODE_PRIVATE)
        loadManualOverrides()
        favoriteTeams.addAll(sharedPrefs.getStringSet("favorite_teams", emptySet()) ?: emptySet())
        serverIp = sharedPrefs.getString("server_ip", "10.0.0.60") ?: "10.0.0.60"

        teamSpinner = findViewById(R.id.teamSpinner)
        playerSpinner = findViewById(R.id.playerSpinner)
        resultText = findViewById(R.id.resultText)
        scoreBanner = findViewById(R.id.scoreBanner)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        gamesContainer = findViewById(R.id.gamesContainer)
        gamesScroll = findViewById(R.id.gamesScroll)
        gamesList = findViewById(R.id.gamesList)

        resultText.movementMethod = ScrollingMovementMethod()
        scoreBanner.isSelected = true
        setupRetrofit()
        playerSpinner.visibility = View.GONE

        swipeRefresh.setOnRefreshListener {
            when (currentMode) {
                "STATS" -> getTeamStats()
                "PLAYER" -> getPlayerStats()
                "PREDICT" -> fetchPrediction(currentTeam, opponentTeam)
            }
            fetchLiveScores()
        }

        findViewById<MaterialButton>(R.id.backFromGamesButton).setOnClickListener {
            gamesContainer.visibility = View.GONE
            resultText.visibility = View.VISIBLE
            setStatsMode()
        }

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
        Handler(Looper.getMainLooper()).postDelayed({ getTeamStats() }, 1000)
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
        val options = mutableListOf("Change Server IP", "Toggle Favorite Team", "View Prediction Accuracy", "Live Game Predictions", "Betting Odds", "Player Props")
        if (currentMode == "STATS") options.add("Manual Override Stats")
        AlertDialog.Builder(this)
            .setTitle("Options")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Change Server IP" -> showServerIpDialog()
                    "Toggle Favorite Team" -> toggleFavorite()
                    "View Prediction Accuracy" -> showAccuracy()
                    "Live Game Predictions" -> showLivePredictions()
                    "Betting Odds" -> showOdds()
                    "Player Props" -> showPlayerProps()
                    "Manual Override Stats" -> showManualOverrideDialog()
                }
            }.show()
    }

    private fun toggleFavorite() {
        if (currentTeam.isEmpty()) return
        if (favoriteTeams.contains(currentTeam)) {
            favoriteTeams.remove(currentTeam)
            Toast.makeText(this, "$currentTeam removed", Toast.LENGTH_SHORT).show()
        } else {
            favoriteTeams.add(currentTeam)
            Toast.makeText(this, "$currentTeam added", Toast.LENGTH_SHORT).show()
        }
        sharedPrefs.edit().putStringSet("favorite_teams", favoriteTeams).apply()
    }

    private fun showAccuracy() {
        apiService.getAccuracy().enqueue(object : Callback<AccuracyResponse> {
            override fun onResponse(call: Call<AccuracyResponse>, response: Response<AccuracyResponse>) {
                if (response.isSuccessful) {
                    val a = response.body()
                    resultText.text = "PREDICTION ACCURACY\n==============================\n\nTotal: ${a?.total_predictions ?: 0}\nCorrect: ${a?.correct ?: 0}\nAccuracy: ${a?.accuracy ?: 0.0}%"
                } else {
                    resultText.text = "No predictions recorded yet"
                }
            }
            override fun onFailure(call: Call<AccuracyResponse>, t: Throwable) {
                resultText.text = "Could not load accuracy"
            }
        })
    }

    private fun showLivePredictions() {
        resultText.text = "Loading live predictions..."
        apiService.getLivePredictions().enqueue(object : Callback<LivePredictionResponse> {
            override fun onResponse(call: Call<LivePredictionResponse>, response: Response<LivePredictionResponse>) {
                val games = response.body()?.live ?: emptyList()
                if (games.isEmpty()) {
                    resultText.text = "No live games right now"
                    return
                }
                val sb = StringBuilder()
                sb.append("LIVE GAME PREDICTIONS\n==============================\n\n")
                for (g in games) {
                    sb.append("${g.away} ${g.away_score} - ${g.home_score} ${g.home}\n")
                    sb.append("  ${g.clock}\n")
                    sb.append("  Live win prob: ${g.home} ${g.home_win_probability}% / ${g.away} ${g.away_win_probability}%\n\n")
                }
                resultText.text = sb.toString()
            }
            override fun onFailure(call: Call<LivePredictionResponse>, t: Throwable) {
                resultText.text = "Failed to load live predictions"
            }
        })
    }

    private fun showOdds() {
        resultText.text = "Loading odds..."
        apiService.getOdds().enqueue(object : Callback<OddsResponse> {
            override fun onResponse(call: Call<OddsResponse>, response: Response<OddsResponse>) {
                val odds = response.body()?.odds ?: emptyList()
                if (odds.isEmpty()) {
                    resultText.text = "No odds available"
                    return
                }
                val sb = StringBuilder()
                sb.append("BETTING ODDS\n==============================\n\n")
                for (o in odds) {
                    sb.append("${o.game}\n")
                    sb.append("  Spread: ${o.spread}\n")
                    sb.append("  O/U: ${o.over_under}\n")
                    sb.append("  Home ML: ${o.home_ml} | Away ML: ${o.away_ml}\n")
                    sb.append("  Source: ${o.provider}\n\n")
                }
                resultText.text = sb.toString()
            }
            override fun onFailure(call: Call<OddsResponse>, t: Throwable) {
                resultText.text = "Failed to load odds"
            }
        })
    }

    private fun showPlayerProps() {
        if (currentPlayer.isEmpty() || currentTeam.isEmpty()) {
            resultText.text = "Select a player first (PLAYER mode)"
            return
        }
        resultText.text = "Loading props for $currentPlayer..."
        apiService.getPlayerProps(currentPlayer, currentTeam).enqueue(object : Callback<PlayerPropsResponse> {
            override fun onResponse(call: Call<PlayerPropsResponse>, response: Response<PlayerPropsResponse>) {
                val p = response.body()
                val sb = StringBuilder()
                sb.append("PLAYER PROPS: ${p?.player ?: currentPlayer}\n==============================\n\n")
                if (p?.props.isNullOrEmpty()) {
                    sb.append("No prop data available")
                } else {
                    for (prop in (p?.props ?: emptyList())) {
                        sb.append("${prop.stat}: ${prop.line}\n")
                        sb.append("  Recommendation: ${prop.recommendation}\n\n")
                    }
                }
                resultText.text = sb.toString()
            }
            override fun onFailure(call: Call<PlayerPropsResponse>, t: Throwable) {
                resultText.text = "Failed to load props"
            }
        })
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
        gamesContainer.visibility = View.GONE
        resultText.visibility = View.VISIBLE
        teamSpinner.visibility = View.VISIBLE
        playerSpinner.visibility = View.GONE
        val numberedTeams = allNFLTeams.mapIndexed { index, team -> "${index + 1}. $team" }
        val teamAdapter = ArrayAdapter(this, R.layout.spinner_item, numberedTeams)
        teamAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
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
        gamesContainer.visibility = View.GONE
        resultText.visibility = View.VISIBLE
        teamSpinner.visibility = View.VISIBLE
        playerSpinner.visibility = View.VISIBLE
        val numberedTeams = allNFLTeams.mapIndexed { index, team -> "${index + 1}. $team" }
        val teamAdapter = ArrayAdapter(this, R.layout.spinner_item, numberedTeams)
        teamAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
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
        teamSpinner.visibility = View.GONE
        playerSpinner.visibility = View.GONE
        swipeRefresh.visibility = View.GONE
        gamesContainer.visibility = View.VISIBLE
        gamesList.removeAllViews()
        val loadingText = TextView(this)
        loadingText.text = "Loading games..."
        loadingText.setTextColor(0xFFE0E0E0.toInt())
        loadingText.textSize = 14f
        loadingText.setPadding(16, 16, 16, 16)
        gamesList.addView(loadingText)

        apiService.getUpcomingGames().enqueue(object : Callback<UpcomingGamesResponse> {
            override fun onResponse(call: Call<UpcomingGamesResponse>, response: Response<UpcomingGamesResponse>) {
                val games = response.body()?.games ?: emptyList()
                gamesList.removeAllViews()
                if (games.isEmpty()) {
                    val tv = TextView(this@MainActivity)
                    tv.text = "No games this week"
                    tv.setTextColor(0xFFE0E0E0.toInt())
                    tv.setPadding(16, 16, 16, 16)
                    gamesList.addView(tv)
                    return
                }
                for (g in games) {
                    val row = TextView(this@MainActivity)
                    val tag = if (g.is_live) " [LIVE]" else ""
                    val score = if (g.is_live) "  ${g.away} ${g.away_score} - ${g.home_score} ${g.home}" else ""
                    row.text = "${g.away_full} @ ${g.home_full}$tag$score\n${g.detail}"
                    row.setTextColor(0xFFFFD700.toInt())
                    row.textSize = 15f
                    row.setPadding(16, 16, 16, 16)
                    val bg = android.graphics.drawable.GradientDrawable()
                    bg.setColor(0xFF0F3460.toInt())
                    bg.cornerRadius = 8f
                    row.background = bg
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.setMargins(0, 0, 0, 12)
                    row.layoutParams = lp
                    row.isClickable = true
                    row.setOnClickListener {
                        currentTeam = g.home_full
                        opponentTeam = g.away_full
                        gamesContainer.visibility = View.GONE
                        swipeRefresh.visibility = View.VISIBLE
                        resultText.visibility = View.VISIBLE
                        resultText.text = "Analyzing ${g.away_full} @ ${g.home_full}..."
                        fetchPrediction(g.home_full, g.away_full)
                    }
                    gamesList.addView(row)
                }
            }
            override fun onFailure(call: Call<UpcomingGamesResponse>, t: Throwable) {
                gamesList.removeAllViews()
                val tv = TextView(this@MainActivity)
                tv.text = "Failed: ${t.message}"
                tv.setTextColor(0xFFE0E0E0.toInt())
                gamesList.addView(tv)
            }
        })
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
                    val adapter = ArrayAdapter(this@MainActivity, R.layout.spinner_item, playerNames)
                    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
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
        if (injuries?.injuries != null && injuries.injuries.isNotEmpty()) {
            val items = injuries.injuries
            for (i in 0 until minOf(items.size, 5)) {
                val item = items[i]
                sb.append("- ${item.player ?: "Unknown"} (${item.position ?: "N/A"}): ${item.status ?: "N/A"}\n")
            }
        } else sb.append("None reported\n")
        sb.append("\n[ NEWS ]\n")
        if (news?.news != null && news.news.isNotEmpty()) {
            val items = news.news
            for (i in 0 until minOf(items.size, 3)) {
                sb.append("${i+1}. ${items[i].headline ?: ""}\n")
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
        sb.append("${p.name}\n")
        sb.append("Position: ${p.position ?: "N/A"} | #${p.jersey ?: "N/A"}\n")
        sb.append("Team: ${p.team ?: currentTeam}\n")
        sb.append("Status: ${if (p.injured == true) "INJURED" else "Active"}\n")
        val s = p.stats
        if (s == null) {
            sb.append("\nNo season stats available")
            resultText.text = sb.toString()
            return
        }
        if (s.passing_yards != null) {
            sb.append("\n[ PASSING ]\n")
            sb.append("Yards: ${s.passing_yards}\n")
            if (s.passing_tds != null) sb.append("TDs: ${s.passing_tds}\n")
            if (s.completions != null && s.passing_attempts != null) sb.append("Comp: ${s.completions}/${s.passing_attempts}\n")
            if (s.interceptions != null) sb.append("INTs: ${s.interceptions}\n")
        }
        if (s.rushing_yards != null) {
            sb.append("\n[ RUSHING ]\n")
            sb.append("Yards: ${s.rushing_yards}\n")
            if (s.rushing_tds != null) sb.append("TDs: ${s.rushing_tds}\n")
        }
        if (s.receiving_yards != null) {
            sb.append("\n[ RECEIVING ]\n")
            sb.append("Yards: ${s.receiving_yards}\n")
            if (s.receiving_tds != null) sb.append("TDs: ${s.receiving_tds}\n")
            if (s.receptions != null) sb.append("Rec: ${s.receptions}\n")
        }
        resultText.text = sb.toString()
    }

    private fun fetchPrediction(team1: String, team2: String) {
        if (team1.isEmpty() || team2.isEmpty()) return
        showLoading(true)
        apiService.getPredictionWithMarket(team1, team2).enqueue(object : Callback<PredictionResponse> {
            override fun onResponse(call: Call<PredictionResponse>, response: Response<PredictionResponse>) {
                showLoading(false)
                if (response.isSuccessful) {
                    val p = response.body()
                    val sb = StringBuilder()
                    sb.append("PREDICTION\n==============================\n\n")
                    sb.append("${p?.predicted_winner ?: "N/A"} WINS\n\n")
                    sb.append("Predicted Score: ${p?.predicted_score ?: "N/A"}\n")
                    sb.append("Predicted Spread: ${p?.predicted_spread ?: "N/A"}\n")
                    sb.append("Predicted Total: ${p?.predicted_total ?: "N/A"}\n\n")
                    sb.append("$team1: ${p?.team1_win_probability}%\n")
                    sb.append("$team2: ${p?.team2_win_probability}%\n\n")
                    if (p?.market_spread != null) {
                        sb.append("MARKET CONSENSUS\n")
                        sb.append("Spread: ${p.market_spread}\n")
                        sb.append("Total: ${p.market_total}\n")
                        sb.append("Home Win: ${p.market_home_prob}%\n")
                        sb.append("Edge: ${p.edge}%\n\n")
                    }
                    sb.append("Confidence: ${p?.confidence}% (${p?.confidence_level})\n\n")
                    sb.append("KEY FACTORS:\n")
                    p?.key_factors?.forEach { sb.append("- $it\n") }
                    resultText.text = sb.toString()
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
        resultText.text = "HELP - $serverIp:5000\n\nSTATS: Team info\nPLAYER: Player stats\nPREDICT: Tap a game to predict\n\nLong press HELP for:\n- Change Server IP\n- Toggle Favorites\n- View Accuracy\n- Live Predictions\n- Betting Odds\n- Player Props\n- Manual Override"
    }

    private fun showLoading(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) swipeRefresh.isRefreshing = false
    }
}
