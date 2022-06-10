package com.example.externalsensorframework.sensor_framework.sensors

import com.example.externalsensorframework.sensor_framework.client.ClientCommunicationThread
import com.example.externalsensorframework.sensor_framework.communication_protocol.response.Response

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

    public class SensorData(
        val value: Number, val isDigital: Boolean,
        val isAnalog: Boolean,
        val rawData: ByteArray, val sensorType: SensorType?,
        val sensorID: Int
    ){}

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