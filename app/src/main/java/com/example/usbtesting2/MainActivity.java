package com.example.usbtesting2;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import android.content.SharedPreferences;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION = "com.example.usbtesting2.USB_PERMISSION";
    private static final int REQUEST_CODE_ENABLE_ADMIN = 1;
    private static final String NOTIFICATION_CHANNEL_ID = "usb_boot_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int EXIT_TAP_COUNT = 5;
    private static final long EXIT_TAP_TIMEOUT = 3000; // 3 seconds
    private static final String PREFS_NAME = "KioskPrefs";
    private static final String KEY_KIOSK_MODE = "kiosk_mode_enabled";

    private TextView deviceInfoView;
    private TextView logOutputView;
    private UsbManager usbManager;
    private UsbSerialPort serialPort;
    private SerialInputOutputManager serialIoManager;
    private KioskManager kioskManager;
    private ComponentName deviceAdmin;
    private StringBuilder logBuilder = new StringBuilder();
    private List<Long> exitTapTimes = new ArrayList<>();
    private boolean isKioskModeActive = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "USB Boot Channel",
                NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for USB device boot flow");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void showUsbPermissionNotification() {
        Intent permissionIntent = new Intent(this, MainActivity.class);
        permissionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, permissionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("USB Permission Required")
            .setContentText("Tap to grant USB device permissions")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show notification: " + e.getMessage());
        }
    }

    private void appendToLog(String message) {
        if (logOutputView != null) {
            logBuilder.append(message).append("\n");
            runOnUiThread(() -> {
                logOutputView.setText(logBuilder.toString());
                // Auto-scroll to bottom
                final ScrollView scrollView = (ScrollView) logOutputView.getParent();
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            });
        }
    }

    private void updateDeviceInfo(String info) {
        if (deviceInfoView != null) {
            runOnUiThread(() -> deviceInfoView.setText(info));
        }
    }

    private void handleExitTap() {
        long currentTime = System.currentTimeMillis();
        exitTapTimes.add(currentTime);
        
        // Remove old taps
        while (!exitTapTimes.isEmpty() && currentTime - exitTapTimes.get(0) > EXIT_TAP_TIMEOUT) {
            exitTapTimes.remove(0);
        }

        // Check if we have enough recent taps
        if (exitTapTimes.size() >= EXIT_TAP_COUNT) {
            exitTapTimes.clear();
            exitKioskMode();
        }
    }

    private void saveKioskState(boolean enabled) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putBoolean(KEY_KIOSK_MODE, enabled);
        editor.apply();
    }

    private void enterKioskMode() {
        if (!isKioskModeActive && kioskManager.isDeviceOwner()) {
            appendToLog("bananasEntering kiosk mode...");
            // Set allowed system flags before entering kiosk mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                );
            }
            
            // Configure kiosk policies to allow system dialogs
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null) {
                ComponentName deviceAdmin = new ComponentName(this, MyDeviceAdminReceiver.class);
                dpm.setStatusBarDisabled(deviceAdmin, false); // Allow status bar for notifications
                dpm.setKeyguardDisabled(deviceAdmin, true);
                dpm.setLockTaskPackages(deviceAdmin, new String[]{getPackageName()});
            }

            kioskManager.enableKioskMode(this);
            startLockTask(); // Explicitly start lock task mode
            isKioskModeActive = true;
            saveKioskState(true);
        }
    }

    private void exitKioskMode() {
        if (isKioskModeActive) {
            appendToLog("bananasExiting kiosk mode...");
            stopLockTask();
            isKioskModeActive = false;
            kioskManager.disableKioskMode(this);
            saveKioskState(false);
            finish();
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            appendToLog("bananasUSB Event: " + action);
            Log.d(TAG, "USB Event received: " + action);

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    appendToLog("bananasUSB Device attached: " + device.getDeviceName());
                    startUsbCommunication(device);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                appendToLog("bananas🔌 USB device detached");
                updateDeviceInfo("No USB device connected");
                stopIoManager();
                if (serialPort != null) {
                    try {
                        serialPort.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing serial port", e);
                    }
                    serialPort = null;
                }
            } else if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            appendToLog("bananas✅ USB Permission granted for device: " + device.getDeviceName());
                            openDeviceAndCommunicate(device);
                            // Enter kiosk mode after successful USB connection
                            handler.postDelayed(() -> enterKioskMode(), 1000);
                        }
                    } else {
                        appendToLog("bananas❌ USB Permission denied for device: " + (device != null ? device.getDeviceName() : "unknown"));
                        showUsbPermissionNotification();
                    }
                }
            }
        }
    };

    private void startUsbCommunication(UsbDevice device) {
        Log.d(TAG, "=== Starting USB Communication ===");
        String deviceInfo = String.format("Device: %s\nVID: 0x%04X\nPID: 0x%04X",
                device.getDeviceName(),
                device.getVendorId(),
                device.getProductId());
        
        updateDeviceInfo(deviceInfo);
        appendToLog("bananasConnected to: " + device.getDeviceName());

        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            String error = "❌ No driver found for USB device";
            Log.e(TAG, error);
            appendToLog(error);
            return;
        }

        appendToLog("bananas✅ Driver found: " + driver.getClass().getSimpleName());

        List<UsbSerialPort> ports = driver.getPorts();
        if (ports.isEmpty()) {
            String error = "❌ No serial ports available on device";
            Log.e(TAG, error);
            appendToLog(error);
            return;
        }

        appendToLog("bananas✅ Found " + ports.size() + " serial port(s)");

        serialPort = ports.get(0);
        try {
            // Check if we already have permission
            if (!usbManager.hasPermission(device)) {
                appendToLog("bananasNo USB permission - requesting access");
                Log.d(TAG, "Requesting USB permission for device: " + device.getDeviceName());
                showUsbPermissionNotification();
                
                // Request permission with proper flags for Android compatibility
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags |= PendingIntent.FLAG_MUTABLE;
                }
                
                PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, 
                    new Intent(ACTION_USB_PERMISSION), flags);
                
                // Actually request the permission
                usbManager.requestPermission(device, permissionIntent);
                appendToLog("bananasUSB permission request sent...");
                Log.d(TAG, "USB permission request sent for device: " + device.getDeviceName());
            } else {
                Log.d(TAG, "Already have USB permission for device: " + device.getDeviceName());
                appendToLog("bananasAlready have USB permission");
                // We already have permission, proceed with opening the device
                openDeviceAndCommunicate(device);
                // Enter kiosk mode after successful USB connection
                handler.postDelayed(() -> enterKioskMode(), 1000);
            }
        } catch (Exception e) {
            String errorMsg = "Error in USB communication: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            appendToLog("bananas❌ " + errorMsg);
        }
    }

    private void openDeviceAndCommunicate(UsbDevice device) {
        try {
            Log.d(TAG, "Attempting to open USB device connection");
            UsbDeviceConnection connection = usbManager.openDevice(device);
            if (connection == null) {
                String error = "Could not open device connection - device might be busy";
                Log.e(TAG, error);
                appendToLog("bananas❌ " + error);
                return;
            }

            Log.d(TAG, "Opening serial port");
            serialPort.open(connection);
            serialPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            
            Log.d(TAG, "Serial port opened successfully");
            appendToLog("bananas✅ Connected! Waiting for data...");

            UsbSessionManager.usbSerialPort = serialPort;
            UsbSessionManager.connection = connection;

            startIoManager();
        } catch (IOException e) {
            String errorMsg = "Error opening serial port: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            appendToLog("bananas❌ " + errorMsg);
        }
    }

    private void startIoManager() {
        if (serialPort == null) {
            appendToLog("bananas❌ Cannot start IO Manager - serial port is null");
            return;
        }

        SerialInputOutputManager.Listener listener = new SerialInputOutputManager.Listener() {
            @Override
            public void onNewData(byte[] data) {
                String receivedData = new String(data);
                appendToLog("bananasbananas: " + receivedData.trim());
            }

            @Override
            public void onRunError(Exception e) {
                appendToLog("bananas❌ Error: " + e.getMessage());
            }
        };

        serialIoManager = new SerialInputOutputManager(serialPort, listener);
        Executors.newSingleThreadExecutor().submit(serialIoManager);
        appendToLog("bananas✅ IO Manager started");
    }

    private void stopIoManager() {
        Log.d(TAG, "Stopping IO Manager...");
        if (serialIoManager != null) {
            serialIoManager.stop();
            serialIoManager = null;
            Log.d(TAG, "✅ IO Manager stopped");
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // Check if touch is in top-left corner (assuming 100x100 dp area)
            float density = getResources().getDisplayMetrics().density;
            float touchArea = 100 * density; // Convert 100dp to pixels
            
            if (event.getX() <= touchArea && event.getY() <= touchArea) {
                handleExitTap();
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        // Initialize views
        deviceInfoView = findViewById(R.id.device_info);
        logOutputView = findViewById(R.id.log_output);

        // Create notification channel
        createNotificationChannel();

        // Initialize USB manager
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Initialize KioskManager
        kioskManager = new KioskManager(this);
        
        // Check if we have device admin permission
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdmin = new ComponentName(this, MyDeviceAdminReceiver.class);
        
        if (!dpm.isAdminActive(deviceAdmin)) {
            appendToLog("bananasRequesting device admin permission...");
            requestDeviceAdmin();
        } else if (kioskManager.isDeviceOwner()) {
            appendToLog("bananasDevice owner mode active");
            // Enter kiosk mode immediately if we're starting from boot
            if (getIntent().getBooleanExtra("START_KIOSK", false) || 
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_KIOSK_MODE, false)) {
                enterKioskMode();
            }
            kioskManager.setupSystemUI(this);
        } else {
            appendToLog("bananasWarning: App is not device owner - kiosk mode not available");
        }

        // Set up window flags to allow system overlays
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        // Register USB receiver for device events and permission
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }
        appendToLog("bananasUSB receiver registered");

        // Check for existing USB devices
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        appendToLog("bananasFound " + deviceList.size() + " USB device(s)");

        if (!deviceList.isEmpty()) {
            for (UsbDevice device : deviceList.values()) {
                appendToLog(String.format("Device found: %s (VID: 0x%04X, PID: 0x%04X)",
                    device.getDeviceName(),
                    device.getVendorId(),
                    device.getProductId()));
                startUsbCommunication(device);
            }
        } else {
            appendToLog("bananasWaiting for USB device to be connected...");
            showUsbPermissionNotification();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null) {
                appendToLog("bananasUSB Device attached via new intent");
                startUsbCommunication(device);
            }
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

        // Disable kiosk mode if it was enabled
        if (isKioskModeActive) {
            kioskManager.disableKioskMode(this);
        }
        
        Log.d(TAG, "=== onDestroy completed ===");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (kioskManager.isDeviceOwner() && !isKioskModeActive) {
            enterKioskMode();
        }
    }

    private void requestDeviceAdmin() {
        deviceAdmin = new ComponentName(this, MyDeviceAdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Device admin is required for kiosk mode");
        startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            if (resultCode == Activity.RESULT_OK) {
                // Device admin enabled, check if we're device owner and enable kiosk mode
                if (kioskManager.isDeviceOwner()) {
                    kioskManager.enableKioskMode(this);
                    kioskManager.setupSystemUI(this);
                }
            } else {
                Toast.makeText(this, "Device admin permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}