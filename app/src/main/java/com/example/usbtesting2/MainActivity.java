package com.example.usbtesting2;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION = "com.example.usbtesting2.USB_PERMISSION";
    private LinearLayout deviceContainer;

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

        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

        if (deviceList.isEmpty()) {
            Toast.makeText(this, "No USB devices found", Toast.LENGTH_SHORT).show();
            return;
        }

        for (UsbDevice device : deviceList.values()) {
            // Request permission (you would usually handle the result with a BroadcastReceiver)
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, permissionIntent);

            // Display device info
            addDeviceView(device);
        }
    }

    private void addDeviceView(UsbDevice device) {
        TextView deviceInfo = new TextView(this);
        deviceInfo.setText(
                "Device ID: " + device.getDeviceId() + "\n" +
                        "Device Name: " + device.getDeviceName() + "\n"
        );
        deviceInfo.setTextSize(18);
        deviceInfo.setPadding(20, 20, 20, 20);
        deviceContainer.addView(deviceInfo);
    }
}
