package com.example.data

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

object TripPointSerializer {
    fun serialize(points: List<TripPoint>): String {
        val array = JSONArray()
        for (point in points) {
            val obj = JSONObject()
            obj.put("lat", point.latitude)
            obj.put("lng", point.longitude)
            obj.put("speed", point.speedKmh)
            obj.put("time", point.timestamp)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserialize(jsonStr: String): List<TripPoint> {
        val list = mutableListOf<TripPoint>()
        if (jsonStr.isEmpty()) return list
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TripPoint(
                        latitude = obj.optDouble("lat", 0.0),
                        longitude = obj.optDouble("lng", 0.0),
                        speedKmh = obj.optDouble("speed", 0.0),
                        timestamp = obj.optLong("time", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}

class TripRepository(private val tripDao: TripDao) {
    val allTrips: Flow<List<Trip>> = tripDao.getAllTrips()

    fun getTripById(id: Long): Flow<Trip?> = tripDao.getTripById(id)

    suspend fun insertTrip(trip: Trip): Long = tripDao.insertTrip(trip)

    suspend fun deleteTrip(id: Long) = tripDao.deleteTripById(id)
}
