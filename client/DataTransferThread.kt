package com.example.externalsensorframework.sensor_framework.client

import android.os.Handler
import android.util.Log
import com.example.externalsensorframework.sensor_framework.sensors.SensorObserver
import com.example.externalsensorframework.sensor_framework.communication_protocol.response.Response
import com.example.externalsensorframework.sensor_framework.communication_protocol.response.ResponsePackage
import com.example.externalsensorframework.sensor_framework.sensors.SensorType
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger


class DataTransferThread(val serverInputStream: InputStream, val sensorObserver: SensorObserver, val availableSensors: List<ClientCommunicationThread.SensorEntry>): Thread() {

    private val TAG = "DataTransferThread"
    private var readingRawData: Boolean = true;
    private var currentResponse: ResponsePackage? = ResponsePackage()//alongside with the data, each time a response is received from the server
    private var nBytes: Int = -1;//represents n bytes for current sensor, on each process response this value is updated to the current sensor's id
    private var currentSensorID: Int = -1;//when started it is supposed to continuously read data from the device driver, in nBytes chunks

    override fun run() {
        super.run()

        if( availableSensors.size <= 0){
            Log.w(TAG, "run: number of available sensors is 0 ( number of bytes to be read not specified by handshake proviedd through CONNECT requset ) RESPONSE", IOException())
            stopReading()//stop, don't read anything, length of the buffer not set properly
        }

        while( readingRawData ){
            processResponse()//1. reads the response header, 2. reads sensor id, 3. according to read ID, sets nBytes representing data to be read next

            if( readingRawData )
                forwardData()
        }
    }

    private fun forwardData() {

        var rawData: ByteArray = ByteArray(nBytes)
        var formattedData: String = ""
        var bytesRead: Int = serverInputStream.read(rawData, 0, nBytes)//read raw data

        if(currentResponse?.responseType == Response.READING_SENSOR_DATA_FORMATTED )//read formatted data if present
            formattedData = getFormattedData();

        if( bytesRead == -1 ){//do something, end of stream is reached which shouldn't happen
            stopReading()
            Log.w(TAG, "run: EOF reaches in server's input stream. Should quit by receiving STOP_READ_Y response instead", IOException())
        }else{
            var sensorData: SensorObserver.SensorData = SensorObserver.SensorData( BigInteger(rawData).toInt(), formattedData, rawData, sensorTypeFromID(currentSensorID), currentSensorID)
            sensorObserver.onSensorDataChanged(sensorData)
        }

        this.nBytes = -1;
    }

    private fun getFormattedData(): String {
        var lengthBytes: ByteArray = ByteArray(4)// 4 bytes long integer sent as length
        var formattedData: String = ""
        serverInputStream.read(lengthBytes, 0, lengthBytes.size)//read raw string length
        val lengthFormattedString = BigInteger(lengthBytes).toInt()//string length

        var formattedDataBytes: ByteArray = ByteArray(lengthFormattedString)//prepare formatted raw string
        serverInputStream.read(formattedDataBytes, 0, lengthFormattedString)//get formatted raw string
        return String(formattedDataBytes)//return formatted string
    }

    /*
     * all incoming data comes alongside with response packages
     * response is form of
     * READING_SENSOR_DATA | ID | DATA      - DATA is either raw or in some other form depending on device driver implementation
     * */
    private fun processResponse() {
        currentResponse?.getResponsePackage(serverInputStream)//get the response, read type of response and its body from the stream
        when(currentResponse?.responseType){
            Response.READING_SENSOR_DATA -> Log.i(TAG, "processResponse: Reading data in progress -> RESPONSE OK ${currentResponse?.responseType}")
            Response.READING_SENSOR_DATA_FORMATTED -> Log.i(TAG, "processResponse: Reading data in progress -> RESPONSE OK ${currentResponse?.responseType}")
            Response.STOP_READ_Y -> {
                readingRawData = false
                return;
            }
            else-> {
                Log.e(TAG, "processResponse: Invalid response received while reading data, suspending. ${currentResponse?.responseType}" )
                readingRawData = false// while reading the only data sent from the driver side can be that it's reading
                return;
            }
        }
        currentResponse?.let {//configure reading
            var sensorID = it.bodyAsInt()//get id from the reading response
            for( sensor in availableSensors ) {
                if (sensor.sensorID == sensorID) {
                    this.currentSensorID = sensorID
                    sensor.dataSampleByteLength?.let { this.nBytes = it }//configure to read N bytes of sensor pointed to by its ID
                    break;
                }
            }
            if( nBytes == -1 )
                Log.e(TAG, "processResponse: sensor's id is invalid $sensorID\n")
        }
    }

    /* null indicates that sensor type is invalid */
    private fun sensorTypeFromID(sensorID: Int): SensorType? {

        for( sensor in availableSensors ) {
            if (sensor.sensorID == sensorID)
                return sensor.sensorType
        }

        return null
    }

    fun stopReading(){this.readingRawData = false}
}
