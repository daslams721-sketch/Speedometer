package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Trip
import com.example.data.TripRepository
import com.example.service.TrackingService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TripRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TripRepository(database.tripDao())
    }

    // Load list of trips reactively
    val allTrips: StateFlow<List<Trip>> = repository.allTrips
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Direct binding with active service streams
    val isTracking = TrackingService.isTracking
    val tripPoints = TrackingService.tripPoints
    val currentSpeedKmh = TrackingService.currentSpeedKmh
    val maxSpeedKmh = TrackingService.maxSpeedKmh
    val avgSpeedKmh = TrackingService.avgSpeedKmh
    val currentDistanceKm = TrackingService.currentDistanceKm
    val durationSeconds = TrackingService.durationSeconds
    val lastFinishedTripId = TrackingService.lastFinishedTripId

    fun startTracking(context: Context) {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_TRACKING
        }
        context.startService(intent)
    }

    fun stopTracking(context: Context) {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP_TRACKING
        }
        context.startService(intent)
    }

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            repository.deleteTrip(tripId)
        }
    }

    fun getTripById(tripId: Long) = repository.getTripById(tripId)
}
