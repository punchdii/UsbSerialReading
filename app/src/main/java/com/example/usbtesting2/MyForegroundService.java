package com.example.usbtesting2;


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

import androidx.core.app.NotificationCompat;

public class MyForegroundService extends Service {

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "usb_monitor_channel";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);


        Log.d("MyUSBService", "Service promoted to foreground");

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
}
