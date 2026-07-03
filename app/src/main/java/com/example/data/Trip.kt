package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

data class TripPoint(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val timestamp: Long
)

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val distanceKm: Double,
    val pointsJson: String // Serialized JSONArray of points for local storage
)
