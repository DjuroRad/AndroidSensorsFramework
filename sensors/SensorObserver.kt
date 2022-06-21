package com.example.externalsensorframework.sensor_framework.sensors

import com.example.externalsensorframework.sensor_framework.client.ClientCommunicationThread
import com.example.externalsensorframework.sensor_framework.communication_protocol.response.Response
import java.nio.ByteBuffer

/**
 * This interface must be implemented in order to get results from SensorFrameworkManager.
 * */
interface SensorObserver {

    /**
     * Used for initial handshake connection.
     *
     * used for returning value of [sensor_framework.SensorFrameworkManager.connect]
     *
     * @param availableSensors list of available sensors
     * @see ClientCommunicationThread.SensorEntry
     * */
    fun onConnected( availableSensors: List<ClientCommunicationThread.SensorEntry> );

    /**
     * Activates when a specific sensor is connected
     *
     * used for returning value of [sensor_framework.SensorFrameworkManager.connectSensor]
     *
     * @param sensorID id of sensor that connected
     * @return sensor id that connected, this is return value of [sensor_framework.SensorFrameworkManager.connectSensor]
     * */
    fun onSensorConnected(sensorID: Int);

    /**
     * Activates when a specific sensor is disconnected
     *
     * used for returning value of [sensor_framework.SensorFrameworkManager.disconnectSensor]
     *
     * @param sensorID id of sensor that disconnected
     * @return sensor id that disconnected, this is return value of [sensor_framework.SensorFrameworkManager.disconnectSensor]
     * */
    fun onSensorDisconnected(sensorID: Int);

    /**
     * Activates when data from a sensor arrives
     *
     * @param sensorData data read from a sensor
     * @see SensorData
     * */
    fun onSensorDataChanged(sensorData: SensorData);

    /**
     * Checks if a sensor is connected.
     *
     * This is a return value of [sensor_framework.SensorFrameworkManager.isConnected]
     *
     * @param sensorId id of sensor on which check is performed
     * @param connected true if connected, false if not connected
     * */
    fun isConnected(sensorId: Int, connected: Boolean);
    fun onError(sensorError: SensorError);

    /**
     * Checks if a sensor is connected.
     *
     * This is a return value of [sensor_framework.SensorFrameworkManager.configure]
     *
     * @param sensorId id of targeted sensor
     * @param configured true if sensor exists and is connected, false otherwise
     * */
    fun onConfigured(sensorID: Int, configured: Boolean)

    /**
     * Data class used to provide get data from device driver when reading
     * @param formattedData data sent by driver developer as JSONString
     * @param rawData raw data of the sensor
     * @param sensorID id of the sensor
     * @param sensorType sensor's type
     * @see SensorType
     * */
    public class SensorData(
        val value: Number,//sometimes sent, delete maybe - formatted is enough, its work depends on sensor so yes probably just remove it
        val formattedData: String,
        val rawData: ByteArray,
        val sensorType: SensorType?,
        val sensorID: Int
    )

    /**
     * @param sampleRate used for setting sample rate of the given sensor
     * @param formatted set to true in order to get data formatted as JSONString
     * @param sensorPrecision use in order to set the precision of the sensor. This allows optimization and getting values only on sample intervals that indicate specified change
     * @see [SensorPrecision]
     * */
    data class SensorConfiguration(
        val sampleRate: Int,
        val sensorPrecision: SensorPrecision,
        val formatted: Boolean){

        fun toByteArray(): ByteArray{

            val sampleRateBytes = ByteBuffer.allocate(4).putInt(sampleRate).array()
            val sensorPrecisionBytes = sensorPrecision.toByteArray()
            val formattedByte = if( formatted ) 1.toByte() else 0.toByte()

            val n_bytes = sampleRateBytes.size + sensorPrecisionBytes.size + 1
            val byteArray: ByteArray = ByteArray(n_bytes);

            for( i in 0 until sampleRateBytes.size )
                byteArray[i] = sampleRateBytes[i]

            for( i in sampleRateBytes.size until (sampleRateBytes.size + sensorPrecisionBytes.size) )
                byteArray[i] = sensorPrecisionBytes[i - sampleRateBytes.size]

            byteArray[sampleRateBytes.size + sensorPrecisionBytes.size] = formattedByte

            return byteArray
        }
    }

    /**
     * class is used in order to specify
     *
     * @param precision specifies precision of the sensor allowing for optimization
     * @param difference specifies how precise reading is supposed to be. Example: last data reading was 200, difference of 30 means that next value can't be between 170 and 230 (200+-30)
     * @see PRECISION
     * */
    data class SensorPrecision(
        val precision: PRECISION = PRECISION.PRECISE,
        val difference: Double = 0.0,
    ){
        fun toByteArray(): ByteArray{
            val differenceBytes: ByteArray = ByteBuffer.allocate(8).putDouble(difference).array()
            val precisionByte = PRECISION.toByte(precision)

            val sensorPrecisionBytes: ByteArray = ByteArray(differenceBytes.size + 1);

            val n_bytes = differenceBytes.size + 1;
            sensorPrecisionBytes[0] = precisionByte;
            for( i in 1 until n_bytes )
                sensorPrecisionBytes[i] = differenceBytes[i-1];

            return sensorPrecisionBytes
        }
    }

    /**
     * @param PRECISE each data sample will be taken into consideration
     * @param IMPRECISE_OPTIMIZED only some data samples taken into consideration
     * */
    enum class PRECISION{
        PRECISE,
        IMPRECISE_OPTIMIZED;

        companion object {
            fun toByte(precision: PRECISION): Byte = if(precision==PRECISE) 0.toByte() else 1.toByte();
        }
    }

    class SensorError(val errorMessage: String, private val responseType: Response){

        val errorType: SensorErrorType
        init{
            errorType = when( responseType ){
                Response.START_READ_N -> SensorErrorType.START_READ
                Response.CONNECT_SENSOR_N -> SensorErrorType.SET_SENSOR_TYPE
                Response.CONNECT_N -> SensorErrorType.CONNECT
                Response.CONFIGURE_N -> SensorErrorType.CONFIGURE
                else -> throw Exception("Attempt of initializing SensorError class instance without an appropriate responseType")
            }
        }

        enum class SensorErrorType{
            SET_SENSOR_TYPE,
            START_READ,
            CONNECT,
            CONFIGURE;
        }
    }
}