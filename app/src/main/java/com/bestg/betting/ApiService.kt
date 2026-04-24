package com.bestg.betting

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName

interface ApiService {
    @GET("test")
    fun testConnection(): Call<TestResponse>
    
    @GET("team_stats")
    fun getTeamStats(@Query("team") team: String): Call<TeamStatsResponse>
    
    @GET("player_stats")
    fun getPlayerStats(@Query("name") name: String, @Query("team") team: String): Call<PlayerStatsResponse>
    
    @GET("weather")
    fun getWeather(@Query("team") team: String): Call<WeatherResponse>
    
    @GET("injuries")
    fun getInjuries(@Query("team") team: String): Call<InjuriesResponse>
    
    @GET("news")
    fun getNews(@Query("entity") entity: String): Call<NewsResponse>
    
    @GET("win_loss")
    fun getWinLoss(@Query("team") team: String): Call<WinLossResponse>
    
    @GET("roster")
    fun getRoster(@Query("team") team: String): Call<RosterResponse>
    
    @GET("predict")
    fun getPrediction(@Query("team1") team1: String, @Query("team2") team2: String): Call<PredictionResponse>
}

// ============= BASIC RESPONSES =============
data class TestResponse(
    @SerializedName("status") val status: String?, 
    @SerializedName("message") val message: String?
)

data class TeamStatsResponse(
    @SerializedName("team") val team: String?, 
    @SerializedName("record") val record: String?, 
    @SerializedName("points_for") val points_for: Int?, 
    @SerializedName("points_against") val points_against: Int?, 
    @SerializedName("streak") val streak: String?
)

// ============= EXPANDED PLAYER STATS =============
data class PlayerStatsResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?, 
    @SerializedName("position") val position: String?, 
    @SerializedName("jersey") val jersey: String?, 
    @SerializedName("team") val team: String?, 
    @SerializedName("injured") val injured: Boolean?,
    @SerializedName("stats") val stats: PlayerSeasonStats?
)

data class PlayerSeasonStats(
    @SerializedName("passing_yards") val passing_yards: Int?,
    @SerializedName("passing_tds") val passing_tds: Int?,
    @SerializedName("completions") val completions: Int?,
    @SerializedName("passing_attempts") val passing_attempts: Int?,
    @SerializedName("interceptions") val interceptions: Int?,
    @SerializedName("qb_rating") val qb_rating: Double?,
    
    @SerializedName("rushing_yards") val rushing_yards: Int?,
    @SerializedName("rushing_tds") val rushing_tds: Int?,
    @SerializedName("rushing_attempts") val rushing_attempts: Int?,
    
    @SerializedName("receiving_yards") val receiving_yards: Int?,
    @SerializedName("receiving_tds") val receiving_tds: Int?,
    @SerializedName("receptions") val receptions: Int?,
    @SerializedName("targets") val targets: Int?,
    
    @SerializedName("tackles") val tackles: Int?,
    @SerializedName("sacks") val sacks: Double?,
    @SerializedName("def_interceptions") val def_interceptions: Int?
)

// ============= WEATHER - FIXED WITH SERIALIZED NAMES =============
data class WeatherResponse(
    @SerializedName("city") val city: String?,
    @SerializedName("stadium") val stadium: String?,
    @SerializedName("temperature") val temperature: Int?,
    @SerializedName("conditions") val conditions: String?,
    @SerializedName("wind_speed") val wind_speed: Int?,
    @SerializedName("precipitation") val precipitation: Int?,
    @SerializedName("humidity") val humidity: Int?,
    @SerializedName("impact") val impact: String?
)

// ============= INJURIES =============
data class InjuriesResponse(
    @SerializedName("team") val team: String?, 
    @SerializedName("injuries") val injuries: List<Injury>?
)

data class Injury(
    @SerializedName("player") val player: String?, 
    @SerializedName("position") val position: String?, 
    @SerializedName("injury") val injury: String?, 
    @SerializedName("status") val status: String?, 
    @SerializedName("status_emoji") val status_emoji: String?, 
    @SerializedName("date") val date: String?
)

// ============= NEWS =============
data class NewsResponse(
    @SerializedName("team") val team: String?, 
    @SerializedName("news") val news: List<NewsItem>?
)

data class NewsItem(
    @SerializedName("headline") val headline: String?, 
    @SerializedName("date") val date: String?, 
    @SerializedName("description") val description: String?, 
    @SerializedName("source") val source: String?
)

// ============= WIN/LOSS =============
data class WinLossResponse(
    @SerializedName("season_record") val season_record: String?,
    @SerializedName("regular_season_record") val regular_season_record: String?,
    @SerializedName("playoff_record") val playoff_record: String?,
    @SerializedName("win_percentage") val win_percentage: Double?,
    @SerializedName("current_streak") val current_streak: String?,
    @SerializedName("last_5_games") val last_5_games: List<String>?,
    @SerializedName("home_record") val home_record: String?,
    @SerializedName("away_record") val away_record: String?,
    @SerializedName("division_record") val division_record: String?,
    @SerializedName("has_playoffs") val has_playoffs: Boolean?
)

// ============= ROSTER =============
data class RosterResponse(
    @SerializedName("team") val team: String?, 
    @SerializedName("players") val players: List<PlayerInfo>?
)

data class PlayerInfo(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?, 
    @SerializedName("position") val position: String?,
    @SerializedName("jersey") val jersey: String?,
    @SerializedName("injured") val injured: Boolean?
)

// ============= PREDICTION =============
data class PredictionResponse(
    @SerializedName("team1") val team1: String?,
    @SerializedName("team2") val team2: String?,
    @SerializedName("team1_win_probability") val team1_win_probability: Double?,
    @SerializedName("team2_win_probability") val team2_win_probability: Double?,
    @SerializedName("predicted_winner") val predicted_winner: String?,
    @SerializedName("confidence") val confidence: Double?,
    @SerializedName("key_factors") val key_factors: List<String>?
)