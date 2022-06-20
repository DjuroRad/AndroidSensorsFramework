package com.example.externalsensorframework.sensor_framework

import android.os.Handler
import com.example.externalsensorframework.sensor_framework.client.ClientCommunicationThread
import com.example.externalsensorframework.sensor_framework.sensors.SensorObserver
import java.io.InputStream
import java.io.OutputStream

/**
 * Class instance provides methods for communication with remote device.
 *
 * @param sensorObserver used to get return for each of the methods [SensorFrameworkManager] provides
 * @param serverInputStream remove device's (sensor driver) input stream
 * @param clientOutputStream remove device's (sensor driver) output stream
 *
 * @see SensorObserver
 * @see InputStream
 * @see OutputStream
 * */
class SensorFrameworkManager(sensorObserver: SensorObserver,
                             serverInputStream: InputStream,
                             clientOutputStream: OutputStream,
) {
    private var clientCommunicationThread:ClientCommunicationThread? = null;

    init {
        //initialize necessary response/request threads
        clientCommunicationThread = ClientCommunicationThread(sensorObserver, serverInputStream, clientOutputStream);
        clientCommunicationThread?.start();
        clientCommunicationThread?.let {
            val handler = Handler(it.looper)
            it.handler = handler
        }
    }

    /**
     * Performs initial handshake/connection to remote device.
     *
     * Make sure this initial connection is done in order to be able to execute other methods provided by this class.
     *
     * @param generalSampleRate sets sample rate in milliseconds for each sensor on the remote device to given value, if this value is not passed, default value of 1000 milliseconds is assigned
     * @return value returned through [SensorObserver.onConnected]
     * @see SensorObserver.onConnected upon initial handshake, data of available sensors can be accessed by implementing this interface
     */
    fun connect(generalSampleRate: Int= 2000) = clientCommunicationThread?.connect(generalSampleRate)

    /**
     * Configure sample rate of specific sensor
     * @param sensorID id of sensor whose sample rate is to be changed, if id is -1, all the sensors will be se
     * @param sensorConfiguration use it to set sample rate, precision and formatting desired
     * @return value returned through [SensorObserver.onConfigured]
     * @see [SensorObserver.SensorConfiguration]
     */
    fun configure(sensorID: Int, sensorConfiguration: SensorObserver.SensorConfiguration) = clientCommunicationThread?.configureSensor(sensorID, sensorConfiguration)

    /**
     * Checks if sensor is connected
     *
     * Connecting sensor means that its value will be one of the values provided in [SensorObserver.onSensorDataChanged] during reading.
     *
     * @param sensorID id of sensor whose connection will be checked
     * @return value returned through [SensorObserver.isConnected]
     * @see connectSensor
     * @see disconnectSensor
     */
    fun isConnected(sensorID: Int) = clientCommunicationThread?.isConnected(sensorID)

    /**
     * Connects sensor ensures its data is sent during reading
     *
     * Connecting sensor means that its value will be one of the values provided in [SensorObserver.onSensorDataChanged] during reading.
     *
     * @param sensorID id of sensor to connect
     * @return value returned through [SensorObserver.onSensorConnected]
     * @see isConnected
     * @see disconnectSensor
     */
    fun connectSensor(sensorID: Int) = clientCommunicationThread?.sendConnectSensorRequest(sensorID)

    /**
     * Disconnecting sensor ensures its data is not sent during reading
     *
     * Disconnecting sensor means that its value will won't be provided in [SensorObserver.onSensorDataChanged] during reading.
     *
     * @param sensorID id of sensor to connect
     * @return value returned through [SensorObserver.onSensorDisconnected]
     * @see isConnected
     * @see connectSensor
     */
    fun disconnectSensor(sensorID: Int) = clientCommunicationThread?.sendDisconnectSensorRequest(sensorID)

    /**
     * Starts reading data of sensors previously connected through [connectSensor]. Data is sent in time intervals configured during [connect], [configure] or [configureGeneralSampleRate]
     *
     * @return value returned through [SensorObserver.onSensorDataChanged]
     * @see connectSensor
     * @see disconnectSensor
     * @see stopRead
     */
    fun startRead() = clientCommunicationThread?.sendStartReadRequest();

    /**
     * Stops reading data from the sensor. Will not work if [startRead] hasn't been successfully invoked beforehand.
     *
     * Reconfiguration and [startRead] possible afterwards
     *
     * @return doesn't return any value.
     * @see stopRead
     */
    fun stopRead() = clientCommunicationThread?.sendStopReadRequest();

    /**
     * Disconnects from the remote device.
     *
     * Reconfiguration and [startRead] not possible afterwards
     *
     * @return doesn't return any value.
     */
    fun disconnect() {
        clientCommunicationThread?.disconnect();
        clientCommunicationThread = null;
    }

}