package com.example.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class QiblaSensorManager(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    fun hasCompassSensor(): Boolean {
        val sm = sensorManager ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
            (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
                sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null)
    }

    fun observeHeadingDegrees(): Flow<Float> = callbackFlow {
        val sm = sensorManager
        if (sm == null) {
            close()
            return@callbackFlow
        }

        val rotationSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasMag = false
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        var smoothedSin = 0.0
        var smoothedCos = 0.0
        var initialized = false
        val alpha = 0.18

        fun emitSmoothedAzimuth(rawRadians: Double) {
            val s = sin(rawRadians)
            val c = cos(rawRadians)
            if (!initialized) {
                smoothedSin = s
                smoothedCos = c
                initialized = true
            } else {
                smoothedSin += alpha * (s - smoothedSin)
                smoothedCos += alpha * (c - smoothedCos)
            }
            val smoothedDeg = ((Math.toDegrees(atan2(smoothedSin, smoothedCos)) + 360.0) % 360.0).toFloat()
            trySend(smoothedDeg)
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        emitSmoothedAzimuth(orientation[0].toDouble())
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasMag = true
                    }
                }
                if (rotationSensor == null && hasGravity && hasMag) {
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        emitSmoothedAzimuth(orientation[0].toDouble())
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotationSensor != null) {
            sm.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        } else if (accelSensor != null && magSensor != null) {
            sm.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            sm.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        awaitClose {
            sm.unregisterListener(listener)
        }
    }
}
