package com.anderza.fmnet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Держит приложение живым, пока играет радио: уведомление «Сейчас в эфире»,
 * плюс блокировки процессора и Wi-Fi, чтобы поток не обрывался при выключенном экране.
 */
public class PlaybackService extends Service {

    static final String ACTION_UPDATE = "com.anderza.fmnet.UPDATE";
    static final String ACTION_TOGGLE = "com.anderza.fmnet.TOGGLE";
    static final String ACTION_NEXT = "com.anderza.fmnet.NEXT";
    private static final String CHANNEL = "playback";
    private static final int NOTIFICATION_ID = 1;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RadioNet:playback");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        }
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            @SuppressWarnings("deprecation")
            int mode = WifiManager.WIFI_MODE_FULL_HIGH_PERF;
            wifiLock = wm.createWifiLock(mode, "RadioNet:stream");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_TOGGLE.equals(action)) {
            MainActivity.sendMediaToCurrent("pause");
            return START_NOT_STICKY;
        }
        if (ACTION_NEXT.equals(action)) {
            MainActivity.sendMediaToCurrent("next");
            return START_NOT_STICKY;
        }
        String title = intent != null ? intent.getStringExtra("title") : null;
        String text = intent != null ? intent.getStringExtra("text") : null;
        Notification n = build(title == null || title.isEmpty() ? getString(R.string.app_name) : title,
                text == null || text.isEmpty() ? getString(R.string.now_playing) : text);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private Notification build(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this).setPriority(Notification.PRIORITY_LOW);
        }
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, piFlags);

        PendingIntent pausePi = PendingIntent.getService(this, 1,
                new Intent(this, PlaybackService.class).setAction(ACTION_TOGGLE), piFlags);
        PendingIntent nextPi = PendingIntent.getService(this, 2,
                new Intent(this, PlaybackService.class).setAction(ACTION_NEXT), piFlags);

        b.setSmallIcon(R.drawable.ic_stat_radio)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_pause, getString(R.string.action_pause), pausePi)
                .addAction(android.R.drawable.ic_media_next, getString(R.string.action_next), nextPi);
        if (Build.VERSION.SDK_INT >= 21) {
            b.setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1));
        }
        return b.build();
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
