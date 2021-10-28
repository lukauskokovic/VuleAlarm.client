package com.vulealarm;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    VuleAlarmService ServiceInstance;
    Intent ServiceIntent;
    TextView ConnectionStateLabel;
    BroadcastReceiver ConnectionStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.hasExtra("STATE")){
                System.out.println("Got state");
                boolean value = intent.getBooleanExtra("STATE", false);
                ChangeConnectionLabel(value);
                //noinspection SpellCheckingInspection
                if(!value)
                    Toast.makeText(getApplicationContext(),"Nije se moglo povezati sa kucom.", Toast.LENGTH_LONG).show();
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ConnectionStateLabel = (TextView)findViewById(R.id.saKucomLabel);
        ChangeConnectionLabel(false);
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

    void ChangeConnectionLabel(boolean value){
        ConnectionStateLabel.setText(value? "POVEZAN" : "NEPOVEZAN");
        ConnectionStateLabel.setTextColor(value? Color.GREEN : Color.RED);
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