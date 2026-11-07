package com.chagok.chagok.sdk.pleos

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs

/**
 * F-05 속도 수집.
 * Pleos Vehicle SDK에는 속도 API가 없어서 안드로이드 차량 API(CarPropertyManager)의
 * PERF_VEHICLE_SPEED를 쓴다 (화면설계서 4-2와 같음).
 *
 * - 필요: build.gradle `useLibrary("android.car")`, 권한 android.car.permission.CAR_SPEED (런타임 요청)
 * - 값 단위는 m/s. km/h로 바꿔서 내보낸다. 후진은 음수로 오므로 절댓값.
 * - 연속 속성이라 주기적으로 들어온다. 그래야 속도 끊김 감시(SignalWatchdog)가 맞게 동작한다.
 */
class CarSpeedSource(context: Context) {
    private val appContext = context.applicationContext
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var registered = false

    private val _speedKmh = MutableSharedFlow<Float>(
        replay = 1, extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val speedKmh: Flow<Float> = _speedKmh.asSharedFlow()

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val mps = value.value as? Float ?: return
            _speedKmh.tryEmit(abs(mps) * 3.6f)
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "speed error propId=$propId zone=$zone")
        }
    }

    /** 구독 시작. 실패하면 false (권한 없음, 차량 서비스 없음 등) */
    fun start(): Boolean {
        if (registered) return true
        return try {
            val c = car ?: Car.createCar(appContext)?.also { car = it } ?: return false
            val m = manager ?: (c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager)
                ?.also { manager = it } ?: return false
            registered = m.registerCallback(
                callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_UI,
            )
            registered
        } catch (e: Exception) {   // SecurityException(권한 없음) 포함
            Log.w(TAG, "speed start failed", e)
            false
        }
    }

    fun stop() {
        try {
            if (registered) manager?.unregisterCallback(callback)
            car?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "speed stop failed", e)
        } finally {
            registered = false
            manager = null
            car = null
        }
    }

    private companion object { const val TAG = "CarSpeedSource" }
}
