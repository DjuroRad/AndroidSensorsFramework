package com.example.externalsensorframework.sensor_framework.client

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.example.externalsensorframework.getThreadHandler
import com.example.externalsensorframework.sensor_framework.sensors.SensorObserver
import com.example.externalsensorframework.sensor_framework.communication_protocol.request.Request
import com.example.externalsensorframework.sensor_framework.communication_protocol.request.RequestPackage
import com.example.externalsensorframework.sensor_framework.communication_protocol.response.Response
import com.example.externalsensorframework.sensor_framework.communication_protocol.response.ResponsePackage
import com.example.externalsensorframework.sensor_framework.sensors.SensorType
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.ByteBuffer

/**
 * Processing new requests:
 *
 * Case 1: reading not started.
 *      1.1 - sendRequest, wait for response, process the response
 *      1.2 - while request is being processed new request comes
 *              - What? It won't be executed until current request's response is received and processed.
 *              - How? Using Android's HandlerThread (handler.post{ new task }) puts new request in a queue of runnables waiting for execution
 *
 * Case 2: reading data started.
 *      2.1 request is stop_read / disconnect
 *          2.1.1 send request.
 *          2.1.2 wait for / join dataTransferThread
 *          2.1.3 dataTransferThread checks the response, if stop_read_y terminates itself.
 *
 *      2.2 request is something else
 *          -> discard and send Log.w("Invalid request, data is currently being read from the sensor");
 *          -> trigger onRequestFailed()
 *
 * */
class ClientCommunicationThread: HandlerThread {

    private val TAG = "ClientCommunicationThrd"
    private var dataTransferThread: DataTransferThread? = null;
    private var handler: Handler? = null;
    @Volatile private var isReadingData: Boolean = false;

    private var clientOutputStream: OutputStream? = null;
    private var serverInputStream: InputStream? = null;

    private val currentResponse: ResponsePackage = ResponsePackage();
    private var currentRequest: RequestPackage? = null;
    private var sensorObserver: SensorObserver? = null;

    private var extraDataAsByteArray: ByteArray? = null;
    private var connectedToDeviceDriver: Boolean = false;
    private var availableSensors: MutableList<SensorEntry> = mutableListOf()

    constructor (
        sensorObserver: SensorObserver,
        serverInputStream: InputStream,
        clientOutputStream: OutputStream
    ) : super("ClientCommunicationThread"){
        handler = this.getThreadHandler()
        dataTransferThread = DataTransferThread(serverInputStream = serverInputStream, sensorObserver,availableSensors);
        this.clientOutputStream = clientOutputStream
        this.serverInputStream = serverInputStream
        this.sensorObserver = sensorObserver
    }


    /**
     * sends CONNECT request
     * @see Request
     * */
    fun connect(generalSampleRate: Int) =
        sendMessage(RequestPackage(Request.CONNECT, generalSampleRate.toByteArray()))

    fun configureSensor(sensorID: Int, sampleRate: Int){
        val requestBodyByte: ByteArray = sensorID.toByteArray()//send sensor id
        val requestPackage: RequestPackage = RequestPackage(Request.CONFIGURE, requestBodyByte)//send request header
        extraDataAsByteArray = sampleRate.toByteArray()//send extra data

        sendMessage(requestPackage)
    }

    fun sendConnectSensorRequest(sensorID: Int) {
        val sensorIdBytes = sensorID.toByteArray()
        val requestPackage = RequestPackage(Request.CONNECT_SENSOR, sensorIdBytes)
        sendMessage(requestPackage)
    }

    fun sendDisconnectSensorRequest(sensorID: Int) {
        val sensorIdBytes = sensorID.toByteArray()
        val requestPackage = RequestPackage(Request.DISCONNECT_SENSOR, sensorIdBytes)
        sendMessage(requestPackage)
    }

    fun isConnected(sensorID: Int) = sendMessage( RequestPackage(Request.IS_CONNECTED, sensorID.toByteArray()) )

    /**
     * used for framework manager
     * */
    fun sendStartReadRequest() = sendMessage( RequestPackage(Request.START_READ) )

    /**
     * stops reading data from the remote device
     * */
    fun sendStopReadRequest() = sendMessage( RequestPackage(Request.STOP_READ) )
    /**
     * used for framework manager
     * */
    fun disconnect(){
        sendMessage( RequestPackage(Request.DISCONNECT) )
    }
    /**
     * used to ask for some request from server,
     * wait for the response and
     * update the communication thread if necessary
     * */
    private fun sendMessage(requestPackage: RequestPackage){
        handler?.post{
            this.currentRequest = requestPackage
            sendRequestToServer(requestPackage)
            if( requestPackage.requestType == Request.DISCONNECT ) {
                this.quit()
                serverInputStream?.close()
                clientOutputStream?.close()
            }
        }
//        handler?.post(object : Runnable{
//            override fun run() {
//                this.currentRequest = requestPackage
//                sendRequestToServer(requestPackage)
//                if( requestPackage.requestType == Request.DISCONNECT )
//                    this.quit()
//            }
//        })
    }

    /**
     * for each request, this same method is executed
     * it implements cases described header of the class
     * @param requestPackage request sent to server
     * */
    private fun sendRequestToServer(requestPackage: RequestPackage){
        if( requestPackage.requestType == Request.DISCONNECT){
            requestPackage.sendRequest(clientOutputStream!!) //send the request
            dataTransferThread?.stopReading()//terminate the reading thread
            dataTransferThread = null
            return
        }


        if( !isReadingData ){//case 1, not reading sensor data

            requestPackage.sendRequest(clientOutputStream!!)//send the request

            extraDataAsByteArray?.let { //send extra data if present, move to requestPackage later on
                sendExtraData()
            }
            extraDataAsByteArray = null;//after extra data is sent, reset it to null

            if( requestPackage.requestType != Request.DISCONNECT ) {
                currentResponse.getResponsePackage(serverInputStream!!)//wait for response, input stream reads in here
                processResponse(currentRequest?.requestType!!)//process the response
             }
        } else if(requestPackage.requestType == Request.STOP_READ){
            requestPackage.sendRequest(clientOutputStream!!) //send the request
            dataTransferThread?.join() //wait for thread to finish execution, it will finish when driver sends STOP_READ_Y, always checked in data transfer thread
            dataTransferThread = null
            dataTransferThread = DataTransferThread(serverInputStream!!, sensorObserver!!, availableSensors);
            isReadingData = false
        }
        else
            Log.w(TAG, "sendRequestToServer: Data reading in progress, invalid request ${requestPackage.requestType?.name}\nDuring data reading only possible requests are: ${Request.STOP_READ?.name} and ${Request.DISCONNECT?.name}")

    }

    private fun sendExtraData(){
        try {
            extraDataAsByteArray?.let { clientOutputStream?.write(it) }
        }catch (ioe: IOException){
            Log.e(TAG, "sendExtraData: Error while trying to send extra data alongside with the request\n ${ioe.message}");
        }
    }

    private fun processResponse(requestType: Request) {
        when(requestType){
            Request.CONNECT -> connectResponse()
            Request.CONFIGURE -> configureResponse()
            Request.CONNECT_SENSOR -> connectSensor()
            Request.IS_CONNECTED -> isConnected()
            Request.START_READ -> startRead()
            Request.STOP_READ -> stopRead()
            Request.DISCONNECT_SENSOR -> disconnectSensor()
            Request.DISCONNECT -> {
                Log.e(TAG, "Trying to process response of a request that doesn't even have response. Internal error!", )
            }
            else -> invalidRequestError()
        }
    }

    private fun configureResponse() {
        when( currentResponse?.responseType){
            Response.CONFIGURE_Y -> {
                val sensorID: Int = currentResponse.bodyAsInt()
                sensorObserver?.onConfigured(sensorID, true)
                responseOK("CONFIGURE_Y - RESPONSE OK")
            }
            Response.CONFIGURE_N -> {
                val sensorID: Int = currentResponse.bodyAsInt()
                sensorObserver?.onConfigured(sensorID, false)
                sensorObserver?.onError(SensorObserver.SensorError("Connecting sensor with given Sensor Type did not succeed. It may not be present on your remotely connected device", Response.CONFIGURE_N))
                responseClientError("CONFIGURE_N error on sensor with id $sensorID, desired sample rate not set")
            }
            else -> syncError()
        }
    }

    private fun connectResponse() {
        when( currentResponse?.responseType){
            Response.CONNECT_Y -> {
                //reads now the the data sent also
                //data sent as n sensors and their entries
                setUpAvailableSensors();
                responseOK("CONNECT_Y - RESPONSE OK")
                sensorObserver?.onConnected(availableSensors)//trigger
            }
            Response.CONNECT_N -> {
                responseClientError("CONFIGURE_N error, sample rate not set, make sure it is within limits of the specified device driver. Otherwise this might happen due to synchronization issues")
                sensorObserver?.onError(SensorObserver.SensorError("Connecting sensor with given Sensor Type did not succeed. It may not be present on your remotely connected device", Response.CONFIGURE_N))
            }
            else -> syncError()
        }
    }

    /**
     * called after CONNECT_Y response
     * Response contains additional data on number of sensors and with their types and ids
     * */
    private fun setUpAvailableSensors() {//gets number of sensors and sets up their initial sensor entry values
        val n_sensors = BigInteger(currentResponse.responseBody).toInt()
        for( i in 0 until n_sensors){
            val sensorEntry:SensorEntry = SensorEntry()
            serverInputStream?.let {
                sensorEntry.getSensorEntry(it)
                availableSensors.add(sensorEntry)
            }

        }
    }

    private fun startRead() {
        when( currentResponse?.responseType){
            Response.START_READ_Y -> {
                isReadingData = true;
                dataTransferThread?.start()
                responseOK("${currentResponse?.responseType} - RESPONSE OK")
            }
            Response.START_READ_N -> responseClientError( "If it returns this, you are basically screwed, though shouldn't as it just suspends the thread" )
            else -> syncError()
        }
    }

    /**
     * called only after START_READ_Y response which returns number of bytes that the sample will be sending
     * */
    private fun configureReadingLength() {
        var readLengthSize = currentResponse?.bodyAsInt()
//        dataTransferThread?.setByteDataLength(readLengthSize!!); //contained withing available sensors sent
    }

    private fun stopRead() {//when this happens, data transfer thread should be suspended
        when( currentResponse?.responseType){
            Response.STOP_READ_Y -> {
                //reading thread should suspend itself during this response, this part is handled separately since data transfer thread receives STOP_READ response
                responseOK("${currentResponse.responseType} - RESPONSE OK")
            }
            Response.STOP_READ_N -> responseClientError( "STOP_READ_N, can not stop reading if didn't previously start reading" )
            else -> syncError()
        }
    }

    private fun isConnected() {
        when( currentResponse.responseType ){
            Response.IS_CONNECTED_Y -> {
                val sensorID: Int = BigInteger(currentResponse.responseBody).toInt();
                sensorObserver?.isConnected( sensorID, true )
                responseOK("${currentResponse.responseType} sensor's id: $sensorID - RESPONSE OK")
            }
            Response.IS_CONNECTED_N -> {
                val sensorID: Int = BigInteger(currentResponse.responseBody).toInt();
                sensorObserver?.isConnected( sensorID, false )
                responseClientError("Desired sensor not connected. Sensor ID: $sensorID. Either not previously connected or not available")
            }
            else -> syncError()
        }
    }

    /**
     * if sensor connected we can
     * */
    private fun connectSensor() {

        val sensorID = BigInteger(currentResponse.responseBody).toInt();
        when(currentResponse.responseType){
            Response.CONNECT_SENSOR_Y -> {
//                configureOnConnect();//configures available sensors
                sensorObserver?.onSensorConnected(sensorID)
                responseOK("CONNECT_SENSOR_Y - RESPONSE OK.\nSensor $sensorID connected")
            }
            Response.CONNECT_SENSOR_N -> {
                val errorMsg = "Connecting sensor with SENSOR ID of ---> $sensorID <--- did not succeed. Make sure this sensor is present on remote device"
                responseClientError(errorMsg)
                sensorObserver?.onError(SensorObserver.SensorError(errorMsg, Response.CONNECT_SENSOR_N))
            }
            else -> syncError()
        }
    }

    private fun disconnectSensor() {

        val sensorID = BigInteger(currentResponse.responseBody).toInt();
        when(currentResponse.responseType){
            Response.DISCONNECT_SENSOR_Y -> {
                sensorObserver?.onSensorDisconnected(sensorID)
                responseOK("DISCONNECT_SENSOR_Y - RESPONSE OK.\nSensor $sensorID connected")
            }
            Response.DISCONNECT_SENSOR_N -> {
                val errorMsg = "Disconnecting sensor with SENSOR ID of ---> $sensorID <--- did not succeed. Make sure this sensor is present on remote device"
                responseClientError(errorMsg)
                sensorObserver?.onError(SensorObserver.SensorError(errorMsg, Response.DISCONNECT_SENSOR_N))
            }
            else -> syncError()
        }
    }



    //reads sensor entities one by one
//    private fun configureOnConnect() {
//        val sizeListBytes: ByteArray = currentResponse.responseBody
//        val sizeList: Int = BigInteger(sizeListBytes).toInt()
//
//        for( i in 0 until sizeList ){
//            serverInputStream?.let {
//                val newSensorEntry: SensorEntry  = getSensorEntry(it)
//                availableSensors.add(newSensorEntry);
//            }
//        }
//    }

    /**
     * function assumes next `SensorEntry.SENSOR_ENTRY_BYTE_LENGTH` belongs to sensor entry
     * */
//    private fun getSensorEntry(serverInputStream: InputStream): ClientCommunicationThread.SensorEntry {
//        try{
//            val sensorTypeByte: Int = serverInputStream.read();
//
//            var sensorIdBytes = ByteArray(ID_LENGTH_BYTES)
//            var sensorDataLengthBytes = ByteArray(SAMPLE_LENGTH_BYTES)
//
//            serverInputStream.read(sensorIdBytes, 0, ID_LENGTH_BYTES);
//            serverInputStream.read(sensorDataLengthBytes, 0, SAMPLE_LENGTH_BYTES);
//
//            val sensorType:SensorType? = SensorType.getSensorTypeFromByte(sensorTypeByte.toByte())
//            val sensorID:Int = BigInteger(sensorIdBytes).toInt()
//            val sensorDataLength:Int = BigInteger(sensorDataLengthBytes).toInt()
//
//            if( sensorTypeByte == -1 )
//                throw IOException("Sensor type returned as -1 => error reading sensory type during initial CONNECT handshake request\n");
//
//            return SensorEntry(sensorType, sensorID, sensorDataLength)
//
//        }catch (ioe: IOException) {
//            ioe.printStackTrace()
//        }
//        throw IOException("@:Error reading sensory type during initial CONNECT handshake request\n")
//    }


    private fun responseClientError(information: String) {
        Log.e(TAG, "Response    ERROR:   $information", )
    }

    private fun responseOK(information: String) {
        Log.i(TAG, information)
    }

    private fun invalidRequestError(){
        Log.e(TAG, "invalidRequestError: Request not recognized: ${currentRequest?.requestType?.name}")
    }
    private fun syncError(){
        Log.e(TAG, "syncError: Response doesn't corresponse to sent request -> ${currentRequest?.requestType?.name} | ${currentResponse?.responseType?.name}" )
    }

    /**
     * class used in order to store received sensor id alongside with its sensor id
     * @param sensorID sensor's id
     * @param sensorType sensor's type
     * */
    class SensorEntry(var sensorType: SensorType? = null, var sensorID: Int? = null, var dataSampleByteLength: Int? = null) {

        companion object{
            const val SENSOR_ENTRY_BYTE_LENGTH = 9 // 1 + 4 + 4  -  type + id + sample_byte_length
            const val SAMPLE_LENGTH_BYTES = 4
            const val ID_LENGTH_BYTES = 4
        }


        private val TAG = "SensorEntry"
        fun getSensorEntry(inputStream: InputStream){

            val sensorEntryAsBytesArray = ByteArray(SENSOR_ENTRY_BYTE_LENGTH);//5 bytes are sent for sensor entry on handshake, 1 byte is for the sensor type, the other 4 bytes are for the id
            try {
                inputStream.read(sensorEntryAsBytesArray, 0, SENSOR_ENTRY_BYTE_LENGTH)// put 5 bytes from input stream into byte array
            }catch (ioe: IOException){
                Log.e(TAG, "getSensorEntry: In ClientCommunicationThread when reading extra data for sensor exception occured\n${ioe.message}")
            }

            SensorType.getSensorTypeFromByte( sensorEntryAsBytesArray[0] )?.let { this.sensorType = it }
            val sensorIdAsByteArray = sensorEntryAsBytesArray.copyOfRange(1, 5);//1 inclusive, 5 exclusive
            val dataSampleByteLengthInBytes = sensorEntryAsBytesArray.copyOfRange(5, SENSOR_ENTRY_BYTE_LENGTH);
            sensorIdAsByteArray.let { this.sensorID = BigInteger(it).toInt() }
            dataSampleByteLengthInBytes.let{ this.dataSampleByteLength = BigInteger(it).toInt() }
        }
    }

    fun Int.toByteArray(): ByteArray =
        ByteBuffer.allocate(RequestPackage.REQUEST_BODY_SIZE).putInt(this).array()
}