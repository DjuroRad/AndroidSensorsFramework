package com.example.externalsensorframework.sensor_framework.communication_channel.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.*
import java.util.*

val SERVERNAME = "package.com.example.externalsensorframework.communication_channls.bluetooth.BluetoothChannelManagerServerSocket"
val UUID_CHANNEL_MANAGER = UUID.fromString("287a5365-78ac-44ff-bf92-769cac311a2e")

/**
 * this class manages communication with the remote bluetooth device
 * it established a communication channel and is used to both read to server and write to server
 * */
class BluetoothChannelManager{
    //bluetooth based
    private var pcDevice: BluetoothDevice? = null

    private val TAG = "BluetoothChannelManager"

    var connectionEstablished: Boolean = false
        private set

    //using a list of MAC addresses in order to be able to connect to multiple external sensors.
    //now only one will be used but this will be necessary later on
    private var mClientSocket: BluetoothSocket? = null
    var outputStreamClient: OutputStream? = null
        private set

    var inputStreamServer: InputStream? = null
        private set
    /**
     * * TODO reduce parameters to only bluetoothDevice, thorw an exception if that device is not connected or can't establish a connection to ensure single responsibility because this should be responsibility of bluetoothPermissionManager.
     * @param targetDeviceName name by which remote device is visible
     * @param bluetoothDevices list of paired bluetooth devices
     * */
    fun openBluetoothSocketClientByName(targetDeviceName: String, bluetoothDevices: MutableList<BluetoothDevice>) {
        if( !connectionEstablished ){
            for ( device in bluetoothDevices ){
                if( device.name == targetDeviceName ){
                    pcDevice = device
                    establishConnection()
                    //unregister receivers here
                    break;
                }
            }
        }
    }

    fun openBluetoothConnection(bluetoothDevice: BluetoothDevice){
        pcDevice = bluetoothDevice
        establishConnection()
    }

    fun establishConnection(){
        connectToServer()//blocks
        setUpInputStream()
        setUpOutputStream()
        connectionEstablished = true
    }
    /**
     * @return returns true if connection with remote device is established and false if it is not established
     * */
    fun isConnected(): Boolean = connectionEstablished

    private fun setUpInputStream(){
        try{
            inputStreamServer = mClientSocket?.inputStream
        }catch ( ioem: IOException ){
            Log.e(TAG, "openBluetoothSocketServerRealByName: ${ioem.message}")
        }
    }

    private fun setUpOutputStream(){
        try{
            outputStreamClient = mClientSocket?.outputStream
        }catch ( ioem: IOException ){
            Log.e(TAG, "openBluetoothSocketServerRealByName: ${ioem.message}")
        }
    }

    /**
     * @return Returns last reading from the sensor
     * */
    fun getLast(): String?{
        var msg: String? = null

        mClientSocket?.let { clientSocket ->
            val buffer = ByteArray(1)
            try{
                inputStreamServer?.read(buffer)
                msg = String(buffer)
            }catch (ioe: IOException){
                Log.e(TAG, "acceptBluetoothServerSocket: ${ioe.message}")
            }
        }

        return msg
    }

    /**
     * blocks and connects to server
     * opens Rfcomm channel
     * calls BluetoothDevice.createRfcommSocketToServiceRecord(String)
     * calls BluetoothSocket.connect()
     */
    private fun connectToServer(){


        val mUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        //this requires a permission check in sdk 31
        try {
            mClientSocket = pcDevice?.createRfcommSocketToServiceRecord(mUUID);
        }catch (ioe: IOException){
            Log.d(TAG, "openBluetoothSocketServer: IO EXCEPTIONS OCCURED WHEN TRYING TO MAKE A SERVER SOCKET!")
        }
        //cancel discovery here
//        BluetoothPermissionManager.unregisterReceiverExtra(this@MainActivity)
        //wait for connection to establish
        try{
            mClientSocket?.connect()
        }catch (ioe: IOException){
            try {
                mClientSocket?.close()
                Log.d(TAG, "EXCEPTION HERE DUE TO CONNECT ${ioe.message}")
            }catch (ioe: IOException){
                Log.d(TAG, "EXCEPTION HERE DUE TO CONNECT ${ioe.message}")
            }
        }
    }

    fun sendDataAsString(message: String){
        try {
            outputStreamClient?.write(message.toByteArray())
        }catch (ioe: IOException){
            Log.d(TAG, "WRITING TO BUFFER ERROR! EXCEPTION THROWN ${ioe.message}")
        }
    }

    fun closeConnection(){
        mClientSocket?.close()
    }


    /**
     *  writes data to sensor driver's socket, connection, reading data, etc.
     * */
    private class WriterReader: HandlerThread{
        private val TAG: String = "Writer|HandlerThread"

        //needed for communication purposes
        private var mClientSocket: BluetoothSocket? = null
        private var outputStreamClient: OutputStream? = null
        private var inputStreamServer: InputStream? = null
        private var connectionEstablished: Boolean = false
        private var mUUID: UUID? = null
        private val UUIDstr:String = "00001101-0000-1000-8000-00805F9B34FB"

        private var handler: Handler? = null
        /**
         * calls constructor of Handler thread and give it Background priority
         * */
        constructor():super("BluetoothChannelManager - Writer: HandlerThread", Process.THREAD_PRIORITY_BACKGROUND)
        init {
            handler = Handler(Looper.myLooper()!!)
        }

        fun connectToRemoteDevice( pcDevice: BluetoothDevice ){
            handler?.post {
                connectToServer(pcDevice)
                setUpInputStream()
                setUpOutputStream()
                connectionEstablished = true
            }
        }


        private fun connectToServer( pcDevice: BluetoothDevice? ){
            mUUID = UUID.fromString(UUIDstr);
            //wait for connection to establish
            try {
                mClientSocket = pcDevice?.createRfcommSocketToServiceRecord(mUUID);
                try{
                    mClientSocket?.connect()//this call blocks here
                }catch (ioe: IOException){
                    try {
                        mClientSocket?.close()
                        Log.e(TAG, "Client socket wasn't able to connect -> ${ioe.message}")
                    }catch (ioe: IOException){
                        Log.d(TAG, "Client socket wasn't able to connect + error closing client socket -> ${ioe.message}")
                    }
                }
            }catch (ioe: IOException){
                Log.d(TAG, "openBluetoothSocketServer: IO EXCEPTIONS OCCURED WHEN TRYING TO MAKE A SERVER SOCKET!")
            }
        }
        /**
         * runs the writer thraed and initilizes it with the input stream
         * */
        private fun setUpInputStream(){
            try{
                inputStreamServer = mClientSocket?.inputStream
            }catch ( ioem: IOException ){
                Log.e(TAG, "setUpInputStream in thread: ${ioem.message}")
            }
        }

        // sets the output stream for the bluetooth socket of the device it connected to
        private fun setUpOutputStream(){
            try{
                outputStreamClient = mClientSocket?.outputStream
            }catch ( ioem: IOException ){
                Log.e(TAG, "openBluetoothSocketServerRealByName: ${ioem.message}")
            }
        }

        /**
         * This call will block trying to connect to the server
         * */
        fun connectSensor( sensorID: String ){

        }
        /**
         * TODO
         * 1. send first that it will send sensor type command
         * 2. send the sensor type
         * 3. wait for response from the server to see if it worked
         * */
        fun setSensorType( sensorID: String ){
            //have to check it with the server!

            //write to server checkAvailableSensor()
            //wait for response
            //if response POSITIVE -> set new sensor type in here, otherwise throw an error

        }//if

        /**
         * Notifies server to start reading the data
         * */
        fun startSensorRead( sensorID: String ){
            //sends data to the socket to start reading
            //send "R" as enumeration command
            //get response to confirm reading maybe
        }
        /**
         * notifies server to stop reading the data
         * */
        fun stopSensorRead( sensorID: String ){

            //send "S" as enumeration through the socket
            //stop reading data at the server driver
        }


        /**
         * gets the last data read from the sensor
         * */
        fun getSensorDataLast():String{return ""}

        /**
         * returns more than 1 instance of the data
         * */
        fun getSensorDataAsList():List<String>{return listOf()}
        /**
         * handler thread manges it by preparing a looper and a handler, runnables into the runnable queue
         * */
        override fun run() {
            //blocking calls in here
            //read data indefinately
            //wait for startRead if not reading at the moment
        }
    }

}
