package com.bestg.betting

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

class TestActivity : AppCompatActivity() {
    private val TAG = "TestActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val tv = TextView(this)
        tv.text = "Testing connection..."
        setContentView(tv)
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to connect...")
                val url = URL("http://10.182.114.244:5000/test")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.connect()
                
                Log.d(TAG, "Connected successfully")
                val responseCode = conn.responseCode
                Log.d(TAG, "Response code: ")
                val responseMessage = conn.responseMessage
                Log.d(TAG, "Response message: ")
                
                withContext(Dispatchers.Main) {
                    tv.text = "Connected! Response: "
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed: ", e)
                withContext(Dispatchers.Main) {
                    tv.text = "Failed: "
                }
            }
        }
    }
}
