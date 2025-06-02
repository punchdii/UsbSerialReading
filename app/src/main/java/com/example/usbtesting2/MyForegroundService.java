package com.example.usbtesting2;


import static androidx.fragment.app.FragmentManager.TAG;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import com.example.usbtesting2.MainActivity;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import androidx.core.app.NotificationCompat;

import java.util.concurrent.Executors;
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




public class MyForegroundService extends Service {

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "usb_monitor_channel";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);


        Log.d("MyUSBService", "Service promoted to foreground");
//        UsbSerialPort port = UsbSessionManager.usbSerialPort;
        // TODO: Add your USB reading logic here
        Toast.makeText(this, "USB reading logic should be here", Toast.LENGTH_SHORT).show();
        startIoManager();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "USB Monitor";
            String description = "Channel for USB service";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Toast.makeText(this, "Notification channel created", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("USB Service Running")
                .setContentText("Reading USB data...")
                .setSmallIcon(android.R.drawable.stat_notify_sync) // Replace with your app icon
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    private void startIoManager() {
        UsbSerialPort serialPort = UsbSessionManager.usbSerialPort;


        Toast.makeText(this, "Starting IO MANAGER", Toast.LENGTH_SHORT).show();
//
        if (serialPort == null) {
            Toast.makeText(this, "❌ Serial port not available", Toast.LENGTH_SHORT).show();
            return;
        }
//
        SerialInputOutputManager.Listener listener = new SerialInputOutputManager.Listener() {
            @Override
            public void onNewData(byte[] data) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    String receivedData = new String(data);
                    Toast.makeText(getApplicationContext(), "📨 Data received: " + receivedData.trim(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onRunError(Exception e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    Toast.makeText(getApplicationContext(), "❌ Serial error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        };
        SerialInputOutputManager serialIoManager = new SerialInputOutputManager(serialPort, listener);
        Executors.newSingleThreadExecutor().submit(serialIoManager);
    }


}
