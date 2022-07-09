package com.musingdaemon.ticker;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.O)
public class TickerInitializeService extends Service {
    private static final LocalTime MORNING_TIME = LocalTime.of(8, 35);
    private static final LocalTime NOON_TIME = LocalTime.of(12, 5);
    private static final LocalTime EVENING_TIME = LocalTime.of(15, 5);

    public TickerInitializeService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Context context = getApplicationContext();

        List<LocalTime> notificationTimes = new ArrayList<>();
        notificationTimes.add(MORNING_TIME);
        notificationTimes.add(NOON_TIME);
        notificationTimes.add(EVENING_TIME);

        for (LocalTime notificationTime : notificationTimes) {
            Intent serviceIntent = new Intent(context, TickerSchedulingService.class);
            serviceIntent.putExtra("hour", notificationTime.getHour());
            serviceIntent.putExtra("minute", notificationTime.getMinute());
            context.startService(serviceIntent);
        }

        return super.onStartCommand(intent, flags, startId);
    }
}