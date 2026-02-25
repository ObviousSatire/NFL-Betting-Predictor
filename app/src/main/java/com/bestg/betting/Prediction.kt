package com.bestg.betting

data class PredictionResponse(
    val status: String,
    val count: Int,
    val predictions: List<Prediction>
)

data class Prediction(
    val home_team: String,
    val away_team: String,
    val predicted_winner: String,
    val confidence: String,
    val recommended_bet: String,
    val key_factor: String
)
