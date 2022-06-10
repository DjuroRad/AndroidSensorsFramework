package com.example.externalsensorframework.sensor_framework.communication_channel.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData



//write isEnabled() - checks if bluetooth is enabled
//write enableBluetoothRequest() - also returns if it is enabled or not

/**
 * requires bluetooth permission to be enabled
 * @param BluetoothPermissionManager is a singleton variable that manages permission for bluetooth communication channel required in order to establish communication through bluetooth.
 * */
object BluetoothPermissionManager {
    private const val TAG = "BluetoothPermissionMana"

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothManager: BluetoothManager? = null
    //user observes this and sets the observer according to this value
    val enabledBluetooth: MutableLiveData<Boolean> = MutableLiveData(false)
    //DEVICE MAC ADDRESS maps to DEVICE NAME
    val nearbyDevices: MutableLiveData<HashMap<String,String>> = MutableLiveData(HashMap())
    val nearbyDevicesList: MutableLiveData<MutableList<BluetoothDevice>> = MutableLiveData(mutableListOf())
    //DEVICE MAC ADDRESS maps to DEVICE NAME
    //bounded devices represent devices already bounded to bluetooth in the past
    private val boundedDevices: HashMap<String, String> = HashMap()
    private val boundedDevicesList: MutableList<BluetoothDevice> = mutableListOf()

//    val boundedBluetoothDevices: MutableLiveData<HashMap<String,String>> = MutableLiveData(HashMap())
    fun initBluetoothPermissionMangager(context: Context, appCompatActivity: AppCompatActivity){
        manageBluetooth(context, appCompatActivity)
    }

    fun isLocationEnabled(context: Context): Boolean? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // This is a new method provided in API 28
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isLocationEnabled
        } else {
            // This was deprecated in API 28
            val mode: Int = Settings.Secure.getInt(
                context.contentResolver, Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    fun enableLocation(context: Context){
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }
    /**
     * @param context - context application is bound to
     * */
    fun deviceSupportsBluetooth(context: Context): Boolean{
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        return bluetoothAdapter.deviceSupportsBluetooth()
    }

    /**
     * @return if bluetooth is enabled returns a boolean value accordingly, if device doesn't support bluetooth it will return false.
     * @param context - context application is bound to
     * */
    fun isBluetoothEnabled(context: Context): Boolean{
        val bluetoothAdapter = getBluetoothAdapter(context)
        if( bluetoothAdapter.deviceSupportsBluetooth() ){
            if( bluetoothAdapter == null ) return false
            bluetoothAdapter.let {
                return it.isEnabled
            }
        }else
            return false
    }

    private var getResultBluetooth: ActivityResultLauncher<Intent>? = null;
    private var getResultLocation: ActivityResultLauncher<Intent>?= null;


    fun registerToEnableBluetoothAndLocation(activity: AppCompatActivity, systemServiceActionBluetooth: SystemServiceAction, systemServiceActionLocation: SystemServiceAction){


        getResultBluetooth =
            activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()) {

                if(it.resultCode == Activity.RESULT_OK) {
//                    if( isLocationEnabled(activity) == false)
//                        enableLocation()
                    systemServiceActionBluetooth.onEnabled()
                }
                else
                    systemServiceActionBluetooth.onDisabled()
            }

        getResultLocation =
            activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()) {
                if( isLocationEnabled(activity) == true )
                    systemServiceActionLocation.onEnabled()
                else
                    systemServiceActionLocation.onDisabled()
            }

    }
    fun enableLocation(){
        val enableLocationIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        getResultLocation?.launch(enableLocationIntent)
    }


    fun enableBluetoothAndLocation(){
        val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        getResultBluetooth?.launch(enableBluetoothIntent)
    }

    fun checkEnableLocation(activity: AppCompatActivity, systemServiceAction: SystemServiceAction ){
        if( isLocationEnabled(activity) != true){
            val enableBtIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            val getResult =
                activity.registerForActivityResult(
                    ActivityResultContracts.StartActivityForResult()) {
                        if( isLocationEnabled(activity) == true ) {
                            systemServiceAction.onEnabled()
                            Log.w(TAG, "checkEnableLocation: ENABLED LOCATION")
                        }
                        else {
                            systemServiceAction.onDisabled()
                            Log.w(TAG, "checkEnableLocation: DIDN'T ENABLE LOCATION")
                        }
                }
            //launch getResult Action here
            //this action asks user to enable bluetooth
            getResult.launch(enableBtIntent)

        }else {
            systemServiceAction.onEnabled()
        }
    }
    /**
     * makes a request to enable bluetooth within the application
     * @param context - context application is bound to
     * @param activity - AppCompatActivity application is bound to
     * @param bluetoothAction - SystemServiceAction acts as a Callback, user passes its instance as an argument
     * */
    fun enableBluetoothRequest(activity: AppCompatActivity, bluetoothAction: SystemServiceAction){
        if( !isBluetoothEnabled(activity) ){
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            val getResult =
                activity.registerForActivityResult(
                    ActivityResultContracts.StartActivityForResult()) {

                    if(it.resultCode == Activity.RESULT_OK){
                        //bluetooth granted here
//                        Log.d(TAG, "registerForActivityResult -> bluetooth enabled")
                        bluetoothAction.onEnabled()
                    }else{
//                        Log.w(TAG, "manageBluetooth: Bluetooth not enabled", )
                        bluetoothAction.onDisabled()
                    }
                }
            //launch getResult Action here
            //this action asks user to enable bluetooth
            getResult.launch(enableBtIntent)
        }else
            bluetoothAction.onEnabled()
    }



    /**
     * used to imitate callback for get result
     * user implements it before and performs actions according to the bluetooth's state
     * */
    interface SystemServiceAction{
        fun onEnabled()
        fun onDisabled()
    }
    /**
     * Manages connection needed to establish bluetooth channel communication.
     * It checks for bluetooth, permissions needed for bluetooth
     * It asks for permissions if they aren't available.
     *
     *
     * @param context - Context required for checking permissions, AppCompatActivity instance used for requesting bluetooth activation if it OFF
     * */


    /**
     * just returns the bluetooth adapter according to the context it is in
     * */
    private fun getBluetoothAdapter(context: Context): BluetoothAdapter?{
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager?.adapter
    }


    private fun manageBluetooth(context: Context, appCompatActivity: AppCompatActivity) {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        //first check if the device supports bluetooth
        if ( !bluetoothAdapter.deviceSupportsBluetooth() ) {
            enabledBluetooth.postValue(false)
        }else{
            //After making sure device supports bluetooth
            //check now if bluetooth is enabled and if it isn't request enabling it using reference to AppCompatActivity we provided
            if ( ! bluetoothAdapter!!.isEnabled ) {
//                if( !context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT) ) {
    // doesn't exist in sdk 30
                if( !context.hasPermission(Manifest.permission.BLUETOOTH) ) {

                    val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    if( !mBluetoothAdapter.isEnabled) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

                        val getResult =
                            appCompatActivity.registerForActivityResult(
                                ActivityResultContracts.StartActivityForResult()) {
                                if(it.resultCode == RESULT_OK){
                                    //bluetooth granted here
                                    enabledBluetooth.postValue(true)
                                    Log.d(TAG, "registerForActivityResult -> bluetooth enabled")
                                }else{
                                    Log.w(TAG, "manageBluetooth: Bluetooth not enabled")
                                    enabledBluetooth.postValue(false)
                                }
                            }
                        getResult.launch(enableBtIntent)
                    }
                }
                else Log.w(TAG, "manageBluetooth: DOESN'T HAVE BLUETOOTH_CONNECT PERMISSION" )
            }else {
                enabledBluetooth.postValue(true)
            }
        }
    }
    /**
     * Registers the receiver, starts discover and checks the permissions
     * Call after  method is called since it solved permissions
     *
     * @param activity need reference to activity which will register to broadcast receiver
     * */
    fun registerReceiverExtra(activity: AppCompatActivity){
        try {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            activity.registerReceiver(nearbyBluetoothDeviceReceiver,filter)
            bluetoothAdapter = getBluetoothAdapter(activity)
            bluetoothAdapter?.startDiscoveryAndCheckPermission(activity)
            Log.i(TAG, "Registering the nearbyBluetoothDeviceReceiver")
        }catch (faultyArgument: IllegalArgumentException){
            Log.w(TAG, "registerReceiver exception, receiver has probably already been registered")
        }
    }

    fun registerForPairingIntent(activity: AppCompatActivity, broadcastReceiver: BroadcastReceiver){
        try {
            val filterPairing = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            activity.registerReceiver(broadcastReceiver,filterPairing)
        }catch (faultyArgument: IllegalArgumentException){
            Log.w(TAG, "registerReceiver exception, receiver has probably already been registered")
        }
    }
    @SuppressLint("MissingPermission")
    private fun BluetoothAdapter.startDiscoveryAndCheckPermission(activtity: AppCompatActivity): Boolean{
        val disoveryRes = this.startDiscovery()
        Log.d(TAG, "startDiscoveryAndCheckPermissoin: RESULT - $disoveryRes")
        return true
    }

    /**
     * unregisters a receiver, Logs the exception if already unregistered.
     *
     * @param activity required to call unregisterReceiver from activity
     * */
    @SuppressLint("MissingPermission")
    fun unregisterReceiverExtra(broadcastReceiverPairing: BroadcastReceiver, activity: Activity){
        try {
            activity.unregisterReceiver(nearbyBluetoothDeviceReceiver)
            activity.unregisterReceiver(broadcastReceiverPairing)

            bluetoothAdapter?.cancelDiscovery()
        }catch (faultyArgument: IllegalArgumentException){
            Log.w(TAG, "unregisterReceiver exception, receiver has probably already been unregistered")
        }
    }

    /**
     *  @param nearbyBluetoothDeviceReceiver listens for nearby bluetooth devices, sends the device when it is found
     * */
    private val nearbyBluetoothDeviceReceiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action

            when(action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.apply {
                        val newHashMap = nearbyDevices.value
                        val deviceList: MutableList<BluetoothDevice>? = nearbyDevicesList.value
                        newHashMap?.put(address, name)
                        deviceList?.add(this)
                        nearbyDevices.postValue(newHashMap)
                        nearbyDevicesList.postValue(deviceList)
                    }
                }
            }
        }
    }


    fun getBoundedDevices(context: Context): HashMap<String,String>{
        updateBoundedDevices(context)
        return boundedDevices
    }

    private fun updateBoundedDevices(context: Context){
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        Log.d(TAG, "updateBoundedDevices:  PIRJE RAFO TOGA TAMO")
        pairedDevices?.forEach { device ->
            boundedDevices[device.address] = device.name
            Log.d(TAG, "updateBoundedDevices: RAFO NEKA ODJE JE")
        }
    }

    private fun BluetoothAdapter?.deviceSupportsBluetooth():Boolean = (this != null)
    fun Context.hasPermission(permission: String):Boolean =
        ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
//functions for permissions

}