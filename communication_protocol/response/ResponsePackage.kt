package com.example.externalsensorframework.sensor_framework.communication_protocol.response

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger

class ResponsePackage {
    var responseBody = ByteArray(RESPONSE_BODY_SIZE)
        private set

    private var responseTypeByte: Byte = 0//used to represent response header
    var responseType: Response? = null
        private set

    constructor( responseType: Response? ){
        this.responseBody = ByteArray( RESPONSE_BODY_SIZE)
        this.responseType = responseType
    }

    constructor(): this(null)



    fun bodyAsInt(): Int{
        return BigInteger(responseBody).toInt()
    }

    fun getResponsePackage(inputStream: InputStream) {
        try {
            val responseTypeInt: Int = inputStream.read()
            if (responseTypeInt == -1)
                throw IOException("ResponseType reading: Data read from input stream is -1")
            else {
                this.responseTypeByte = responseTypeInt.toByte()
                this.responseType = Response.getResponseFromByte(responseTypeByte)
                val responseBodyLength: Int = inputStream.read(responseBody, 0, RESPONSE_BODY_SIZE)
                if (responseBodyLength != 4) throw IOException("ResponseType reading: Data read from input stream is -1")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun setResponseBody(responseBody: ByteArray) {
        this.responseBody = responseBody
    }

    private fun setResponseTypeByte(responseTypeByte: Byte) {
        this.responseTypeByte = responseTypeByte
    }

    fun setResponseType(responseType: Response?) {
        this.responseType = responseType
        setResponseTypeByte(this.responseType!!.value)
    }

    companion object {
        const val RESPONSE_BODY_SIZE = 4
        const val RESPONSE_HEADER_SIZE = 1
    }

    init {
        this.responseBody = responseBody
        responseType?.let { it -> setResponseType(it) }
    }


    //    fun sendResponse(outputStream: OutputStream) {
//        try {
//            val responseInBytes = ByteArray(RESPONSE_BODY_SIZE + RESPONSE_HEADER_SIZE)
//            responseInBytes[0] = responseTypeByte
//            for (i in 0 until RESPONSE_BODY_SIZE) responseInBytes[i + 1] =
//                responseBody[i]
//            //now write the response
//            outputStream.write(responseInBytes)
//            outputStream.flush() //wait for response to reach its destination
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//    }
}
