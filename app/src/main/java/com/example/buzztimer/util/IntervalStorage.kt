package com.example.buzztimer.util

import android.content.Context
import android.content.SharedPreferences
import com.example.buzztimer.model.TimerInterval
import org.json.JSONArray
import org.json.JSONObject

class IntervalStorage(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "buzztimer_prefs"
        private const val KEY_INTERVALS = "saved_intervals"
        private const val KEY_CIRCULAR_SEQUENCE = "circular_sequence"
    }
    
    /**
     * Save intervals to SharedPreferences
     */
    fun saveIntervals(intervals: List<TimerInterval>) {
        val jsonArray = JSONArray()
        
        intervals.forEach { interval ->
            val jsonObject = JSONObject().apply {
                put("minutes", interval.minutes)
                put("seconds", interval.seconds)
                put("name", interval.name ?: "")
            }
            jsonArray.put(jsonObject)
        }
        
        sharedPreferences.edit()
            .putString(KEY_INTERVALS, jsonArray.toString())
            .apply()
    }
    
    /**
     * Load intervals from SharedPreferences
     */
    fun loadIntervals(): List<TimerInterval> {
        val jsonString = sharedPreferences.getString(KEY_INTERVALS, null)
            ?: return emptyList()
        
        val intervals = mutableListOf<TimerInterval>()
        
        try {
            val jsonArray = JSONArray(jsonString)
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val minutes = jsonObject.getInt("minutes")
                val seconds = jsonObject.getInt("seconds")
                val name = jsonObject.getString("name").takeIf { it.isNotBlank() }
                
                intervals.add(TimerInterval(minutes, seconds, name))
            }
        } catch (e: Exception) {
            // If there's an error parsing, return empty list and clear corrupted data
            clearIntervals()
            return emptyList()
        }
        
        return intervals
    }
    
    /**
     * Save circular sequence setting
     */
    fun saveCircularSequence(isCircular: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_CIRCULAR_SEQUENCE, isCircular)
            .apply()
    }
    
    /**
     * Load circular sequence setting
     */
    fun loadCircularSequence(): Boolean {
        return sharedPreferences.getBoolean(KEY_CIRCULAR_SEQUENCE, false)
    }
    
    /**
     * Clear all saved data
     */
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
    
    /**
     * Clear saved intervals only
     */
    fun clearIntervals() {
        sharedPreferences.edit()
            .remove(KEY_INTERVALS)
            .apply()
    }
}
