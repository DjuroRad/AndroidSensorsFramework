package com.example.externalsensorframework.sensor_framework.communication_protocol.request

import java.io.IOException
import java.io.OutputStream

class RequestPackage {
    private var requestBody = ByteArray(REQUEST_BODY_SIZE)
    var requestTypeByte //used to represent request header
            : Byte = 0
        private set
    var requestType: Request? = null
        private set

    constructor( requestType: Request ,requestBody: ByteArray){
        this.requestType = requestType
        this.requestTypeByte = requestType.value
        this.requestBody = requestBody
    }

    constructor(requestType: Request): this (requestType, ByteArray(REQUEST_BODY_SIZE))

    fun sendRequest(outputStream: OutputStream) {
        try {
            val requestInBytes = ByteArray(RequestPackage.REQUEST_BODY_SIZE + RequestPackage.REQUEST_HEADER_SIZE)
            requestInBytes[0] = requestTypeByte
            for (i in 0 until RequestPackage.REQUEST_BODY_SIZE) requestInBytes[i + 1] =
                requestBody[i]
            //now write the request
            outputStream.write(requestInBytes)
            outputStream.flush() //wait for request to reach its destination
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    companion object {
        const val REQUEST_BODY_SIZE = 4
        const val REQUEST_HEADER_SIZE = 1
    }
}
