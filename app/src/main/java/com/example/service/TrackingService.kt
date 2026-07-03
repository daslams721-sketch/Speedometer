package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.Trip
import com.example.data.TripPoint
import com.example.data.TripPointSerializer
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class TrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var durationTimer: Timer? = null

    private var startTimeMillis = 0L
    private var lastLocation: Location? = null

    companion object {
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Trip Tracker Service"
        const val NOTIFICATION_ID = 4821

        // Static reactive states for the Compose UI to collect directly
        val isTracking = MutableStateFlow(false)
        val tripPoints = MutableStateFlow<List<TripPoint>>(emptyList())
        val currentSpeedKmh = MutableStateFlow(0.0)
        val maxSpeedKmh = MutableStateFlow(0.0)
        val avgSpeedKmh = MutableStateFlow(0.0)
        val currentDistanceKm = MutableStateFlow(0.0)
        val durationSeconds = MutableStateFlow(0L)

        // Event for UI to show trip summary popup upon stopping
        val lastFinishedTripId = MutableStateFlow<Long?>(null)
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> startTrackingService()
            ACTION_STOP_TRACKING -> stopTrackingService()
        }
        return START_NOT_STICKY
    }

    private fun startTrackingService() {
        if (isTracking.value) return

        // Clear previous state
        tripPoints.value = emptyList()
        currentSpeedKmh.value = 0.0
        maxSpeedKmh.value = 0.0
        avgSpeedKmh.value = 0.0
        currentDistanceKm.value = 0.0
        durationSeconds.value = 0L
        lastFinishedTripId.value = null
        lastLocation = null
        startTimeMillis = System.currentTimeMillis()

        isTracking.value = true

        startForegroundNotification()
        startLocationUpdates()
        startTimer()
    }

    private fun stopTrackingService() {
        if (!isTracking.value) return

        // Stop location updates
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null

        // Stop timer
        durationTimer?.cancel()
        durationTimer = null

        // Save trip to Database
        saveCurrentTrip()

        // Reset tracking states
        isTracking.value = false
        stopForeground(true)
        stopSelf()
    }

    private fun saveCurrentTrip() {
        val points = tripPoints.value
        if (points.isEmpty()) {
            return
        }

        val totalPoints = points.size
        val avgSpeed = if (totalPoints > 0) avgSpeedKmh.value else 0.0
        val maxSpeed = maxSpeedKmh.value
        val distance = currentDistanceKm.value
        val endTimeMillis = System.currentTimeMillis()

        serviceScope.launch {
            try {
                val serializedPoints = TripPointSerializer.serialize(points)
                val trip = Trip(
                    startTime = startTimeMillis,
                    endTime = endTimeMillis,
                    avgSpeedKmh = avgSpeed,
                    maxSpeedKmh = maxSpeed,
                    distanceKm = distance,
                    pointsJson = serializedPoints
                )
                val db = AppDatabase.getDatabase(this@TrackingService)
                val insertedId = db.tripDao().insertTrip(trip)
                Log.d("TrackingService", "Saved trip to database. Inserted ID: $insertedId")
                
                // Notify UI about the saved trip ID for summary
                lastFinishedTripId.value = insertedId
            } catch (e: Exception) {
                Log.e("TrackingService", "Error saving trip to database", e)
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    processLocationUpdate(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (unlikely: SecurityException) {
            Log.e("TrackingService", "Lost location permissions. Could not request location updates.", unlikely)
            isTracking.value = false
        }
    }

    private fun processLocationUpdate(location: Location) {
        // location.speed is in m/s, convert to km/h
        val speedKmhValue = location.speed * 3.6
        currentSpeedKmh.value = speedKmhValue

        if (speedKmhValue > maxSpeedKmh.value) {
            maxSpeedKmh.value = speedKmhValue
        }

        val lastLoc = lastLocation
        if (lastLoc != null) {
            val distanceInMeters = lastLoc.distanceTo(location)
            // Add distance only if the movement is significant to reduce stationary GPS drift
            if (location.accuracy < 25 && distanceInMeters > 1.0) {
                currentDistanceKm.value += (distanceInMeters / 1000.0)
            }
        }
        lastLocation = location

        val newPoint = TripPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            speedKmh = speedKmhValue,
            timestamp = location.time
        )

        val updatedPoints = tripPoints.value + newPoint
        tripPoints.value = updatedPoints

        // Calculate cumulative average speed
        if (updatedPoints.isNotEmpty()) {
            val sumSpeed = updatedPoints.sumOf { it.speedKmh }
            avgSpeedKmh.value = sumSpeed / updatedPoints.size
        }

        updateNotification()
    }

    private fun startTimer() {
        durationTimer = Timer()
        durationTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                durationSeconds.value += 1
            }
        }, 1000, 1000)
    }

    private fun startForegroundNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val stopIntent = Intent(this, TrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val mainActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speedText = String.format("%.1f km/h", currentSpeedKmh.value)
        val distanceText = String.format("%.2f km", currentDistanceKm.value)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Speedometer & Route Tracker Active")
            .setContentText("Speed: $speedText | Distance: $distanceText")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(mainActivityPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks your current speedometer speed and route map in the background."
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
