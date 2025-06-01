package com.example.usbtesting2;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.stream.IntStream;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION = "com.example.usbtesting2.USB_PERMISSION";

    private LinearLayout deviceContainer;
    private UsbManager usbManager;

    private TextView deviceName;
    private TextView deviceName2;
    private TextView deviceName3;
    private TextView deviceName4;
    private TextView deviceName5;
    private TextView deviceName6;
    private TextView deviceInfo;

    // Add a status TextView to show broadcast receiver status
    private TextView statusLog;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            // Use runOnUiThread to safely update UI from broadcast receiver
            runOnUiThread(() -> {
                try {
                    statusLog.setText("on receive is triggered");

                    String action = intent.getAction();

                    // Update status to show receiver is working
                    if (statusLog != null) {
                        statusLog.setText("BroadcastReceiver triggered: " + action);
                    }

                    if (ACTION_USB_PERMISSION.equals(action)) {
                        synchronized (this) {
                            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                            boolean permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);

                            if (deviceName2 != null) {
                                deviceName2.setText("Permission result: " + permissionGranted);
                            }

                            if (permissionGranted) {
                                if (device != null) {
                                    if (deviceName3 != null) {
                                        deviceName3.setText("Setting up device: " + device.getDeviceName());
                                    }
                                    setupDevice(device);
                                } else {
                                    if (deviceName3 != null) {
                                        deviceName3.setText("Device is null despite permission granted");
                                    }
                                }
                            } else {
                                if (deviceName3 != null) {
                                    deviceName3.setText("Permission denied for USB device");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Catch any exceptions to prevent crashes
                    if (statusLog != null) {
                        statusLog.setText("Error in broadcast receiver: " + e.getMessage());
                    }
                }
            });
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

        setContentView(R.layout.activity_main2);
        deviceContainer = findViewById(R.id.device_container);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Initialize all TextViews first
        deviceInfo = findViewById(R.id.log_step1);
        deviceName = findViewById(R.id.log_step2);
        deviceName2 = findViewById(R.id.log_step3);
        deviceName3 = findViewById(R.id.log_step4);
        deviceName4 = findViewById(R.id.log_step5);
        deviceName5 = findViewById(R.id.log_step6);
        deviceName6 = findViewById(R.id.log_step7);

        // Add status log (assuming you have another TextView for this)
        // If not, you can use one of the existing ones
        statusLog = deviceName; // Using deviceName as status log

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }

        // Initial status
        if (statusLog != null) {
            statusLog.setText("App started, checking USB devices...");
        }

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            Toast.makeText(this, "No USB devices found", Toast.LENGTH_SHORT).show();
            if (statusLog != null) {
                statusLog.setText("No USB devices found");
            }
            return;
        }

        if (statusLog != null) {
            statusLog.setText("Found " + deviceList.size() + " USB devices, requesting permissions...");
        }

        for (UsbDevice device : deviceList.values()) {
            // Ask for permission
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, permissionIntent);
            Toast.makeText(this, "Requesting permission for " + device.getDeviceName(), Toast.LENGTH_SHORT).show();

            // Display the device info
            addDeviceView(device);
        }
    }

    private void setupDevice(UsbDevice device) {
        runOnUiThread(() -> {
            if (deviceName4 != null) {
                deviceName4.setText("Opening device connection...");
            }
        });

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            Log.e(TAG, "Failed to open USB device.");
            runOnUiThread(() -> {
                if (deviceName4 != null) {
                    deviceName4.setText("Failed to open USB device");
                }
            });
            return;
        }

        UsbInterface usbInterface = device.getInterface(0);
        if (!connection.claimInterface(usbInterface, true)) {
            Log.e(TAG, "Failed to claim interface.");
            runOnUiThread(() -> {
                if (deviceName4 != null) {
                    deviceName4.setText("Failed to claim interface");
                }
            });
            return;
        }

        runOnUiThread(() -> {
            if (deviceName4 != null) {
                deviceName4.setText("Interface claimed, endpoints: " + usbInterface.getEndpointCount());
            }
        });

        UsbEndpoint endpointIn = IntStream.range(0, usbInterface.getEndpointCount())
                .mapToObj(usbInterface::getEndpoint)
                .filter(ep -> ep.getDirection() == UsbConstants.USB_DIR_IN)
                .findFirst().orElse(null);

        if (endpointIn == null) {
            runOnUiThread(() -> {
                if (deviceName5 != null) {
                    deviceName5.setText("No input endpoint found");
                }
            });
            return;
        }

        UsbRequest request = new UsbRequest();
        request.initialize(connection, endpointIn);

        runOnUiThread(() -> {
            if (deviceName5 != null) {
                deviceName5.setText("Request initialized, starting read thread...");
            }
        });

        ByteBuffer buffer = ByteBuffer.allocate(endpointIn.getMaxPacketSize());
        request.queue(buffer, buffer.capacity());

        new Thread(() -> {
            while (true) {
                try {
                    UsbRequest completedRequest = connection.requestWait(); // Blocking call
                    if (completedRequest != null) {
                        runOnUiThread(() -> {
                            if (deviceName6 != null) {
                                deviceName6.setText("Request completed");
                            }
                        });

                        buffer.flip();
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);
                        String received = new String(data);

                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "USB Data: " + received, Toast.LENGTH_SHORT).show();
                            if (deviceName6 != null) {
                                deviceName6.setText("Received: " + received);
                            }
                        });

                        buffer.clear();
                        request.queue(buffer, buffer.capacity());
                    } else {
                        Log.e(TAG, "Request wait failed or connection closed.");
                        runOnUiThread(() -> {
                            if (deviceName6 != null) {
                                deviceName6.setText("Connection lost or request failed");
                            }
                        });
                        break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in USB read thread", e);
                    runOnUiThread(() -> {
                        if (deviceName6 != null) {
                            deviceName6.setText("Error: " + e.getMessage());
                        }
                    });
                    break;
                }
            }
        }).start();
    }

    private void addDeviceView(UsbDevice device) {
        if (deviceInfo != null) {
            deviceInfo.setText(
                    "Device ID: " + device.getDeviceId() + "\n" +
                            "Device Name: " + device.getDeviceName() + "\n" +
                            "Vendor ID: " + device.getVendorId() + "\n" +
                            "Product ID: " + device.getProductId()
            );
            deviceInfo.setTextSize(18);
            deviceInfo.setPadding(20, 20, 20, 20);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
    }
}