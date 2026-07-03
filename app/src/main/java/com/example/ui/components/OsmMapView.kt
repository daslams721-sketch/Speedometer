package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.TripPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun OsmMapView(
    points: List<TripPoint>,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    currentLocation: TripPoint? = null
) {
    val context = LocalContext.current

    // Set configuration parameters for OsmDroid caching and naming
    remember {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().osmdroidTileCache = context.cacheDir
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(isInteractive)
            controller.setZoom(16.5)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        }
    }

    // Synchronize map markers and polylines with incoming trip data
    DisposableEffect(points, currentLocation) {
        mapView.overlays.clear()

        val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }

        // Draw polyline representing route history
        if (geoPoints.size > 1) {
            val polyline = Polyline(mapView).apply {
                setPoints(geoPoints)
                outlinePaint.color = android.graphics.Color.parseColor("#3F51B5") // Modern indigo route line
                outlinePaint.strokeWidth = 8f
            }
            mapView.overlays.add(polyline)
        }

        // Start location pin
        if (geoPoints.isNotEmpty()) {
            val startMarker = Marker(mapView).apply {
                position = geoPoints.first()
                title = "Start Point"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(startMarker)
        }

        // Live location tracker pin OR trip end pin
        if (currentLocation != null) {
            val currentGeo = GeoPoint(currentLocation.latitude, currentLocation.longitude)
            val liveMarker = Marker(mapView).apply {
                position = currentGeo
                title = "Current Position\nSpeed: ${String.format("%.1f", currentLocation.speedKmh)} km/h"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(liveMarker)
            mapView.controller.animateTo(currentGeo)
        } else if (geoPoints.isNotEmpty()) {
            val endMarker = Marker(mapView).apply {
                position = geoPoints.last()
                title = "End Point"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(endMarker)
            
            // Adjust zoom to frame all points if possible, or center on last
            mapView.controller.setCenter(geoPoints.last())
        }

        mapView.invalidate()

        onDispose {
            // Free any map hooks if necessary
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}
