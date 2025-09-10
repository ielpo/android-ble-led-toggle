package com.example.focus

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import java.util.*
import android.widget.TextView
import android.widget.Switch
import android.widget.ArrayAdapter

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null

    private var led1Characteristic: BluetoothGattCharacteristic? = null
    private var led2Characteristic: BluetoothGattCharacteristic? = null
    private var led3Characteristic: BluetoothGattCharacteristic? = null
    private var led4Characteristic: BluetoothGattCharacteristic? = null

    private val LED_SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789ABCDEF0")
    private val LED1_UUID = UUID.fromString("12345678-1234-5678-1234-56789ABCDEF1")
    private val LED2_UUID = UUID.fromString("12345678-1234-5678-1234-56789ABCDEF2")
    private val LED3_UUID = UUID.fromString("12345678-1234-5678-1234-56789ABCDEF3")
    private val LED4_UUID = UUID.fromString("12345678-1234-5678-1234-56789ABCDEF4")

    private lateinit var lvDevices: android.widget.ListView
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private val deviceNames = mutableListOf<String>()
    private val foundDevices = mutableListOf<BluetoothDevice>()
    private var selectedDevicePosition: Int = -1

    private fun connectToDevice(device: BluetoothDevice) {
        val connectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_CONNECT
        else
            Manifest.permission.BLUETOOTH

        if (ActivityCompat.checkSelfPermission(this, connectPermission) == PackageManager.PERMISSION_GRANTED) {
            device.connectGatt(this, false, gattCallback)
        } else {
            Log.e("BLE", "Connect permission not granted")
        }
    }

    // Launcher to request multiple permissions at once
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                Log.d("BLE", "All permissions granted, starting scan")
                startScan()
            } else {
                Log.e("BLE", "Permissions denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter

        val led1: Switch = findViewById(R.id.led1)
        led1.setOnCheckedChangeListener { _, isChecked ->
            updateLed(led1Characteristic, isChecked)
        }
        val led2: Switch = findViewById(R.id.led2)
        led2.setOnCheckedChangeListener { _, isChecked ->
            updateLed(led2Characteristic, isChecked)
        }
        val led3: Switch = findViewById(R.id.led3)
        led3.setOnCheckedChangeListener { _, isChecked ->
            updateLed(led3Characteristic, isChecked)
        }

        val led4: Switch = findViewById(R.id.led4)
        led4.setOnCheckedChangeListener { _, isChecked ->
            updateLed(led4Characteristic, isChecked)
        }

        // ListView for found devices
        lvDevices = findViewById(R.id.lvDevices)
        lvDevices.choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE

        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_activated_1, deviceNames)
        lvDevices.adapter = deviceAdapter

        lvDevices.setOnItemClickListener { _, _, position, _ ->
            val device = foundDevices[position]
            selectedDevicePosition = position
            connectToDevice(device)
        }

        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        val requiredPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val notGranted = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            startScan()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun startScan() {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        //val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(LED_SERVICE_UUID)).build())
        val filters = emptyList<ScanFilter>()
        val settings = ScanSettings.Builder().build()

        val scanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_SCAN
        else
            Manifest.permission.ACCESS_FINE_LOCATION

        if (ActivityCompat.checkSelfPermission(this, scanPermission) == PackageManager.PERMISSION_GRANTED) {
            scanner.startScan(filters, settings, scanCallback)
            Log.d("BLE", "Started scanning for BLE devices")
        } else {
            Log.e("BLE", "Scan permission not granted")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!foundDevices.any { it.address == device.address }) {
                 foundDevices.add(device)
                val name: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val connectPermission = Manifest.permission.BLUETOOTH_CONNECT
                    if (ActivityCompat.checkSelfPermission(this@MainActivity, connectPermission) == PackageManager.PERMISSION_GRANTED) {
                        device.name ?: device.address
                    } else {
                        device.address
                    }
                } else {
                    device.name ?: device.address
                }
                deviceNames.add(name)
                runOnUiThread { deviceAdapter.notifyDataSetChanged() }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt = gatt
                Log.d("BLE", "Connected, discovering services...")

                runOnUiThread {
                    if (selectedDevicePosition != -1) {
                        lvDevices.setItemChecked(selectedDevicePosition, true)
                    }
                }

                val connectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    Manifest.permission.BLUETOOTH_CONNECT
                else
                    Manifest.permission.BLUETOOTH
                if (ActivityCompat.checkSelfPermission(this@MainActivity, connectPermission) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices()
                } else {
                    Log.e("BLE", "Connect permission not granted")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(LED_SERVICE_UUID)
            led1Characteristic = service?.getCharacteristic(LED1_UUID)
            led2Characteristic = service?.getCharacteristic(LED2_UUID)
            led3Characteristic = service?.getCharacteristic(LED3_UUID)
            led4Characteristic = service?.getCharacteristic(LED4_UUID)
            Log.d("BLE", "Services discovered, ready to send")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            
        }
    }

    private fun updateLed(characteristic: BluetoothGattCharacteristic?, state: Boolean) {
        characteristic?.let { characteristic ->
            // Runtime permission check before writeCharacteristic
            val connectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                Manifest.permission.BLUETOOTH_CONNECT
            else
                Manifest.permission.BLUETOOTH

            if (ActivityCompat.checkSelfPermission(this, connectPermission) == PackageManager.PERMISSION_GRANTED) {
                val value: Byte = if (state) 1 else 0
                characteristic.value = byteArrayOf(value)
                bluetoothGatt?.writeCharacteristic(characteristic)
            } else {
                Log.e("BLE", "Connect permission not granted")
            }
        } ?: Log.e("BLE", "TX Characteristic not ready")
    }
}
