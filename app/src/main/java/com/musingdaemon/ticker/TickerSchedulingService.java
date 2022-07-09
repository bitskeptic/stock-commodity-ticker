package com.musingdaemon.ticker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@RequiresApi(api = Build.VERSION_CODES.O)
public class TickerSchedulingService extends Service {
    public TickerSchedulingService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Context context = getApplicationContext();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, startId, new Intent(context, TickerNotificationReceiver.class), PendingIntent.FLAG_IMMUTABLE);

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime nextDateTime = LocalDateTime.of(now.getYear(),
                now.getMonth(),
                now.getDayOfMonth(),
                intent.getIntExtra("hour", 16),
                intent.getIntExtra("minute", 0)
        );
        if (nextDateTime.isBefore(now)) {
            nextDateTime = nextDateTime.plus(1, ChronoUnit.DAYS);
        }

        alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), alarmIntent);

        return super.onStartCommand(intent, flags, startId);
    }
}