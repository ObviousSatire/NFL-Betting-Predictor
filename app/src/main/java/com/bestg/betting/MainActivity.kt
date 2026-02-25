package com.bestg.betting

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    
    private lateinit var resultText: TextView
    private lateinit var testButton: Button
    private lateinit var predictButton: Button
    
    // FIXED: Correct IP address with proper dots
    private val BASE_URL = "http://10.195.205.94:5000"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        resultText = findViewById(R.id.resultText)
        testButton = findViewById(R.id.testButton)
        predictButton = findViewById(R.id.predictButton)
        
        testButton.setOnClickListener { testConnection() }
        predictButton.setOnClickListener { getPredictions() }
    }
    
    private fun testConnection() {
        runOnUiThread { resultText.text = "Testing connection to $BASE_URL..." }
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    try {
                        val url = URL("$BASE_URL/test")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.requestMethod = "GET"
                        
                        val responseCode = connection.responseCode
                        if (responseCode == 200) {
                            connection.inputStream.bufferedReader().use { it.readText() }
                        } else {
                            "HTTP Error: $responseCode"
                        }
                    } catch (e: Exception) {
                        "Connection failed: ${e.message}"
                    }
                }
                
                resultText.text = "? Response:\n$response"
                Toast.makeText(this@MainActivity, "Test complete", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                resultText.text = "? Error: ${e.message}"
            }
        }
    }
    
    private fun getPredictions() {
        runOnUiThread { resultText.text = "Loading predictions..." }
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    try {
                        val url = URL("$BASE_URL/predict")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.requestMethod = "GET"
                        
                        val responseCode = connection.responseCode
                        if (responseCode == 200) {
                            connection.inputStream.bufferedReader().use { it.readText() }
                        } else {
                            "HTTP Error: $responseCode"
                        }
                    } catch (e: Exception) {
                        "Connection failed: ${e.message}"
                    }
                }
                
                try {
                    val json = JSONObject(response)
                    val predictions = json.getJSONArray("predictions")
                    
                    val displayText = StringBuilder("?? NFL PREDICTIONS\n\n")
                    for (i in 0 until predictions.length()) {
                        val game = predictions.getJSONObject(i)
                        displayText.append("${i+1}. ${game.getString("game")}\n")
                        displayText.append("   ? ${game.getString("prediction")}\n")
                        displayText.append("   ${(game.getDouble("confidence")*100).toInt()}% confident\n\n")
                    }
                    resultText.text = displayText.toString()
                } catch (e: Exception) {
                    resultText.text = "Raw response:\n$response"
                }
            } catch (e: Exception) {
                resultText.text = "? Error: ${e.message}"
            }
        }
    }
}
