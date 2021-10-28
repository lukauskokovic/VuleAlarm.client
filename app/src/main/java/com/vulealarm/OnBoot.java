package com.vulealarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class OnBoot extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent arg1) {
        Intent intent = new Intent(context, VuleAlarmService.class);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(intent);
        else
            context.startService(intent);
    }
}