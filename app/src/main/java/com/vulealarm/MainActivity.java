package com.vulealarm;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    VuleAlarmService ServiceInstance;
    Intent ServiceIntent;
    TextView ConnectionStateLabel, VrataStateLabel;
    BroadcastReceiver ConnectionStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.hasExtra("STATE"))
                ChangeConnectionLabel(intent.getBooleanExtra("STATE", false),
                                      intent.getIntExtra("VRATA", 0));
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ConnectionStateLabel = (TextView)findViewById(R.id.saKucomLabel);
        VrataStateLabel = (TextView)findViewById(R.id.vrataLabel);
        ChangeConnectionLabel(false, 0);
        ServiceInstance = new VuleAlarmService();
        ServiceIntent = new Intent(this, ServiceInstance.getClass());
        //
        if(!isMyServiceRunning(ServiceInstance.getClass()))
            startService(ServiceIntent);

        //Ask for current state
        Intent i = new Intent();
        i.setAction("GetState");
        sendBroadcast(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(ConnectionStateReceiver, new IntentFilter("ConnectionState"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(ConnectionStateReceiver);
    }

    @Override
    protected void onDestroy(){
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(this, ServiceRestarter.class);
        this.sendBroadcast(broadcastIntent);
        super.onDestroy();
    }

    void ChangeConnectionLabel(boolean value, int vrata){
        ConnectionStateLabel.setText(value? "POVEZAN" : "NEPOVEZAN");
        ConnectionStateLabel.setTextColor(value? Color.GREEN : Color.RED);
        int color = Color.rgb(34, 5, 255);
        String text = "NEPOZNATO";
        if(vrata == 0){
            color = Color.rgb(34, 5, 255);
            text = "NEPOZNATO";
        }else if(vrata == 1){
            color = Color.RED;
            text = "OTVORENA";
        }else if(vrata == 2){
            color = Color.GREEN;
            text = "ZATVORENA";
        }
        VrataStateLabel.setTextColor(color);
        VrataStateLabel.setText(text);
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}