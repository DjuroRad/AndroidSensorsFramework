package com.example.externalsensorframework.sensor_framework.communication_protocol.request


/**
 * Request form
 * | Request | BODY |
 * 1       4
 * each request contains of 5 bytes
 * 1^st byte represents request type
 * following 4 bytes represent additional request data. Some requests will use this space, some will leave it empty.
 * For example:
 * Request type: CONNECT_SENSOR
 * [CONNECT_SENSOR.byteValue, SENSOR_TYPE.byteValue, -, -, - ]
 * Request type: CONFIGURE
 * [CONFIGURE.byteValue, 0, 0, 0, 0b0000_1111] - informs that maximum amount of data it can get is 15 bytes
 * Request type: IS_CONNECTED
 * [IS_CONNECTED.byteValue, -, -, -, -]
 * etc.
 *
 *
 */
enum class Request(val value: Byte) {

    CONNECT(200.toByte()),
    CONNECT_SENSOR(0.toByte()),
    IS_CONNECTED(1.toByte()),
    START_READ(2.toByte()),
    STOP_READ(3.toByte()),
    DISCONNECT(4.toByte()),
    CONFIGURE(5.toByte()),
    DISCONNECT_SENSOR(6.toByte());

    companion object {
        fun getRequestFromByte(requestAsByte: Byte): Request? {
            if (requestAsByte == CONNECT.value) return CONNECT
            if (requestAsByte == CONNECT_SENSOR.value) return CONNECT_SENSOR
            if (requestAsByte == IS_CONNECTED.value) return IS_CONNECTED
            if (requestAsByte == START_READ.value) return START_READ
            if (requestAsByte == STOP_READ.value) return STOP_READ
            if (requestAsByte == DISCONNECT.value) return DISCONNECT
            if (requestAsByte == CONFIGURE.value) return CONFIGURE
            if (requestAsByte == DISCONNECT_SENSOR.value) return DISCONNECT_SENSOR
            return null
        }
        const val REQUEST_SIZE = 5
    }
}


