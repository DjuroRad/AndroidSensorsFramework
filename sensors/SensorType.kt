package com.example.externalsensorframework.sensor_framework.sensors

enum class SensorType(val value: Byte) {
    //positive responses
    ACCELEROMETER(16.toByte()),
    LIGHT_SENSOR(32.toByte()),
    PRESSURE_SENSOR(48.toByte()),
    PROXIMITY_SENSOR(10.toByte()),
    TEMPERATURE_SENSOR(11.toByte()),
    RELATIVE_HUMIDITY_SENSOR(12.toByte()),
    ORIENTATION_SENSOR(13.toByte()),
    GYROSCOPE_TYPE(14.toByte()),
    PRESSURE_SENSOR_DIGITAL(49.toByte()),
    SENSOR_CUSTOM_ANALOG(255.toByte()),
    SENSOR_CUSTOM_DIGITAL(254.toByte());

    fun dataByteSize(): Int {
        return if (isSensorDataDigital(this)!!) 1 //digital data returns 0/1, 1 byte is enough for it (actually 1 bit but output stream can't take less than a byte
        else 4 //analog data returns an integer 0-1023
    }

    fun isDigital(): Boolean{
        return isSensorDataDigital(this) == true;
    }

    fun isAnalog(): Boolean{
        return !this.isDigital();
    }

    companion object {
        fun isSensorDataDigital(sensorType: SensorType?): Boolean? {
            when (sensorType) {
                LIGHT_SENSOR, PRESSURE_SENSOR, SENSOR_CUSTOM_ANALOG, ACCELEROMETER -> return false
                SENSOR_CUSTOM_DIGITAL, PRESSURE_SENSOR_DIGITAL -> return true
            }
            return null
        }

        fun isSensorDataAnalog(sensorType: SensorType?): Boolean? {
            val isDigital = isSensorDataDigital(sensorType)
            return if (isDigital == null) null else !isDigital
        }

        fun getSensorTypeFromByte(requestAsByte: Byte): SensorType? {
            if (requestAsByte == LIGHT_SENSOR.value) return LIGHT_SENSOR
            if (requestAsByte == PRESSURE_SENSOR.value) return PRESSURE_SENSOR
            if (requestAsByte == PRESSURE_SENSOR_DIGITAL.value) return PRESSURE_SENSOR_DIGITAL
            return if (requestAsByte == ACCELEROMETER.value) ACCELEROMETER else null
        }
    }
}
