package com.example.usbtesting2;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION = "com.example.usbtesting2.USB_PERMISSION";

    private LinearLayout deviceContainer;
    private UsbManager usbManager;
    private UsbSerialPort serialPort;

    private PendingIntent createPermissionIntent() {
        Log.d(TAG, "Creating permission intent");
        return PendingIntent.getBroadcast(
                this,
                0,
                new Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
        );
    }

    private SerialInputOutputManager serialIoManager;
    private TextView deviceName;
    private TextView deviceName2;
    private TextView deviceName3;
    private TextView deviceName4;
    private TextView deviceName5;
    private TextView deviceName6;
    private TextView deviceInfo;

    private TextView deviceIncomingData;

    // Add a status TextView to show broadcast receiver status
    private TextView statusLog;

    private void startUsbCommunication(UsbDevice device) {



        Log.d(TAG, "=== Starting USB Communication ===");
        Log.d(TAG, "Device: " + device.getDeviceName() +
                " VID: 0x" + Integer.toHexString(device.getVendorId()) +
                " PID: 0x" + Integer.toHexString(device.getProductId()));

        //Toast.makeText(this, "Connecting to: " + device.getDeviceName(), Toast.LENGTH_SHORT).show();

        // Display device info
        addDeviceView(device);

        //get driver, get port, open port
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            Log.e(TAG, "❌ No driver found for USB device");
            Toast.makeText(this, "❌ No driver found for the USB device", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "✅ Driver found: " + driver.getClass().getSimpleName());
        //Toast.makeText(this, "✅ Driver found: " + driver.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();

        //LAST TIME GOT TO HERE
        List<UsbSerialPort> ports = driver.getPorts();
        if (ports.isEmpty()) {
            Log.e(TAG, "❌ No serial ports available on device");
            Toast.makeText(this, "❌ No serial port available on the device", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "✅ Found " + ports.size() + " serial port(s)");
        Toast.makeText(this, "✅ Found " + ports.size() + " serial port(s)", Toast.LENGTH_SHORT).show();

        serialPort = ports.get(0);
        try {
            UsbDeviceConnection connection = usbManager.openDevice(device);
            if (connection == null) {
                Toast.makeText(this, "❌ Failed to open device connection", Toast.LENGTH_LONG).show();
                return;
            }

            try {
                serialPort.open(connection);
            }
            catch (IOException e){
                throw e;
            }

            try {
                serialPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            }
            catch (IOException e){
                throw e;
            }
            Toast.makeText(this, "✅ Connected! Waiting for data...", Toast.LENGTH_SHORT).show();


            //start the foregroudn service
            Context context = getApplicationContext();
            Intent serviceIntent = new Intent(this, MyForegroundService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "haha");
                context.startForegroundService(serviceIntent);
            } else {
                Log.d(TAG,"nathanwong");
                startService(serviceIntent);
            }

            //Foreground service started
            //Call startio manager will happen in the service
            //

        } catch (IOException e) {
            Toast.makeText(this, "❌ Error opening serial port: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startIoManager() {

        Toast.makeText(this, "Starting IO MANAGER", Toast.LENGTH_SHORT).show();
//
        if (serialPort == null) {
            Log.e(TAG, "❌ Cannot start IO Manager - serial port is null");
            Toast.makeText(this, "❌ Serial port not available", Toast.LENGTH_SHORT).show();
            return;
        }
//        try {
            SerialInputOutputManager.Listener listener = new SerialInputOutputManager.Listener() {
                @Override
                public void onNewData(byte[] data) {
                    runOnUiThread(() -> {

                                            String receivedData = new String(data);

//                                            deviceIncomingData.append(receivedData); // Append received data to the terminal
                        Toast.makeText(MainActivity.this, "📨 Data received: " + receivedData.trim(), Toast.LENGTH_SHORT).show();

                        //TODO: uncomment above line
                        // Only show toast for first few messages to avoid spam
                        //                    if (deviceIncomingData.getText().length() < 100) {
                        //                    }
                    });
                }

                //
                @Override
                public void onRunError(Exception e) {
                    runOnUiThread(() -> {
                        //                    Log.e(TAG, "❌ Serial communication error: " + e.getMessage(), e);
                        //                    Toast.makeText(MainActivity.this, "❌ Serial error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            };
            serialIoManager = new SerialInputOutputManager(serialPort, listener);
            Executors.newSingleThreadExecutor().submit(serialIoManager);

//        } catch (Exception e) {
//            Toast.makeText(MainActivity.this, "❌ Error creating listener: " + e.getMessage(), Toast.LENGTH_LONG).show();
//        }
//
//        Log.d(TAG, "✅ IO Manager started successfully");
//        Toast.makeText(this, "✅ Listening for data...", Toast.LENGTH_SHORT).show();
    }

    private void stopIoManager() {
        Log.d(TAG, "Stopping IO Manager...");
        if (serialIoManager != null) {
            serialIoManager.stop();
            serialIoManager = null;
            Log.d(TAG, "✅ IO Manager stopped");
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "📡 Broadcast received: " + action);

            if (ACTION_USB_PERMISSION.equals(action)) {
                Log.d(TAG, "Processing USB permission response...");
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.d(TAG, "✅ USB permission GRANTED for device: " + (device != null ? device.getDeviceName() : "null"));
                        //Toast.makeText(MainActivity.this, "✅ Permission granted!", Toast.LENGTH_SHORT).show();
                        if (device != null) {
                            //startUsbCommunication(device);
                        }
                    } else {
                        Log.w(TAG, "❌ USB permission DENIED for device: " + (device != null ? device.getDeviceName() : "null"));
                        //Toast.makeText(MainActivity.this, "❌ Permission denied for device", Toast.LENGTH_LONG).show();
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.d(TAG, "🔌 USB device attached: " + (device != null ? device.getDeviceName() : "null"));
                //Toast.makeText(MainActivity.this, "🔌 USB device attached", Toast.LENGTH_SHORT).show();

                if (device != null) {
                    if (usbManager.hasPermission(device)) {
                        Log.d(TAG, "✅ Already have permission, starting communication");
                        startUsbCommunication(device);
                    } else {
                        Log.d(TAG, "🔐 Requesting permission for attached device");
                        //Toast.makeText(MainActivity.this, "🔐 Requesting permission...", Toast.LENGTH_SHORT).show();
                        usbManager.requestPermission(device, createPermissionIntent());
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "🔌 USB device detached");
                //Toast.makeText(MainActivity.this, "🔌 USB device detached", Toast.LENGTH_SHORT).show();
                stopIoManager();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }

        //Initializing Foreground Service
        Context context = getApplicationContext();
       Intent serviceIntent = new Intent(this, MyForegroundService.class);





        setContentView(R.layout.activity_main2);
        Log.d(TAG, "Layout set successfully");

        deviceContainer = findViewById(R.id.device_container);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        Log.d(TAG, "USB Manager initialized");

        // Initialize all TextViews first
        deviceInfo = findViewById(R.id.log_step1);
        deviceName = findViewById(R.id.log_step2);
        deviceName2 = findViewById(R.id.log_step3);
        deviceName3 = findViewById(R.id.log_step4);
        deviceName4 = findViewById(R.id.log_step5);
        deviceName5 = findViewById(R.id.log_step6);
        deviceName6 = findViewById(R.id.log_step7);
        deviceIncomingData = findViewById(R.id.log_received);
        statusLog = deviceName; // Using deviceName as status log

        Log.d(TAG, "TextViews initialized");

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            Log.d(TAG, "Broadcast receiver registered (NOT_EXPORTED)");
        } else {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
            Log.d(TAG, "Broadcast receiver registered (EXPORTED)");
        }

        // Check for existing USB devices
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Log.d(TAG, "Found " + deviceList.size() + " USB device(s)");

        if (deviceList.isEmpty()) {
            Log.w(TAG, "❌ No USB devices found");
            Toast.makeText(this, "❌ No USB devices found. Please connect a device.", Toast.LENGTH_LONG).show();
            return;
        }

        //Toast.makeText(this, "🔍 Found " + deviceList.size() + " USB device(s)", Toast.LENGTH_SHORT).show();

        for (UsbDevice device : usbManager.getDeviceList().values()) {
            Log.d(TAG, "Processing device: " + device.getDeviceName() +
                    " VID: 0x" + Integer.toHexString(device.getVendorId()) +
                    " PID: 0x" + Integer.toHexString(device.getProductId()));

            if (usbManager.hasPermission(device)) {
                Log.d(TAG, "✅ Already have permission for " + device.getDeviceName());
                //Toast.makeText(this, "✅ Permission already granted", Toast.LENGTH_SHORT).show();
                startUsbCommunication(device);
            } else {
                Log.d(TAG, "🔐 Requesting permission for " + device.getDeviceName());
                Toast.makeText(this, "🔐 Requesting permission for " + device.getDeviceName(), Toast.LENGTH_SHORT).show();
                usbManager.requestPermission(device, createPermissionIntent());
                //TODO: does this control window popping up
            }
        }

        Log.d(TAG, "=== onCreate completed ===");
    }

    private void addDeviceView(UsbDevice device) {
        Log.d(TAG, "Displaying device information");
        if (deviceInfo != null) {
            String deviceInfoText = "Device ID: " + device.getDeviceId() + "\n" +
                    "Device Name: " + device.getDeviceName() + "\n" +
                    "Vendor ID: 0x" + Integer.toHexString(device.getVendorId()) + "\n" +
                    "Product ID: 0x" + Integer.toHexString(device.getProductId());

            deviceInfo.setText(deviceInfoText);
            deviceInfo.setTextSize(18);
            deviceInfo.setPadding(20, 20, 20, 20);
            Log.d(TAG, "✅ Device info displayed: " + deviceInfoText.replace("\n", " | "));
        } else {
            Log.w(TAG, "❌ deviceInfo TextView is null");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "=== MainActivity onDestroy ===");

        stopIoManager();

        if (serialPort != null) {
            try {
                serialPort.close();
                Log.d(TAG, "✅ Serial port closed");
            } catch (IOException e) {
                Log.w(TAG, "Error closing serial port: " + e.getMessage());
            }
        }

        try {
            unregisterReceiver(usbReceiver);
            Log.d(TAG, "✅ Broadcast receiver unregistered");
        } catch (Exception e) {
            Log.w(TAG, "Error unregistering receiver: " + e.getMessage());
        }

        Log.d(TAG, "=== onDestroy completed ===");
    }
}