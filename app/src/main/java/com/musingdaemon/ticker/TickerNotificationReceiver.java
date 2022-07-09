package com.musingdaemon.ticker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.O)
public class TickerNotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.

        Intent serviceIntent = new Intent(context, TickerNotificationService.class);
        serviceIntent.putExtra("hour", serviceIntent.getIntExtra("hours", 16));
        serviceIntent.putExtra("minute", serviceIntent.getIntExtra("minutes", 0));

        context.startService(serviceIntent);
    }
}