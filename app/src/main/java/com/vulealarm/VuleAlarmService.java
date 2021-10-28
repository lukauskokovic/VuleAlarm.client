package com.vulealarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class VuleAlarmService extends Service {
    Socket Socket = null;
    VuleAlarmService Instance;
    boolean Connected = false, Running = false;
    BroadcastReceiver ConnectionStateRequestReceiver;
    NotificationManagerCompat notificationManager;
    public VuleAlarmService() {
        Instance = this;
        ConnectionStateRequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                SendConnectionStateBroadcast(Connected);
            }
        };

    }
    @Override
    public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startCustomForeground();
        }else startForeground(2, new Notification());
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private void startCustomForeground(){
        String NOTIFICATION_CHANNEL_ID = "vulealarm.service";
        String channelName = "Vule alarm background service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("Vule alarm service")
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }
    @Override
    public int onStartCommand(Intent i, int flags, int startId){
        super.onStartCommand(i, flags, startId);
        //If api is > than oreo i need to make notification channel?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            System.out.println("Registering notification channel");
            NotificationChannel channel = new NotificationChannel("ALERT", "NotificationChannel", NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Log.i("Running", "Registring receiver");
        registerReceiver(ConnectionStateRequestReceiver, new IntentFilter("GetState"));
        if(!Running)
            new Thread(this::ListenThread).start();
        return START_STICKY;
    }
    void SendConnectionStateBroadcast(boolean state)
    {
        Connected = state;
        Intent i = new Intent();
        i.putExtra("STATE", state);
        i.setAction("ConnectionState");
        sendBroadcast(i);
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        try{
            Running = false;
            Socket.close();
        }catch (Exception ignored){}
        SendConnectionStateBroadcast(false);
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(this, ServiceRestarter.class);
        this.sendBroadcast(broadcastIntent);
    }
    void ListenThread(){
        Log.i("Running", "Starting thread");
        notificationManager = NotificationManagerCompat.from(Instance);
        Running = true;
        Socket = new Socket();
        InputStream inStream;
        int sleepTimer = 3000;
        while(Running){
            try{
                Socket.connect(new InetSocketAddress("192.168.0.17", 1421));
                notificationManager.cancel(0);
                //Get and send device name
                Log.i("Networking", "Connected sending device name and starting listening for data");
                Socket.getOutputStream().write(Settings.Global.getString(this.getContentResolver(), "device_name").getBytes(StandardCharsets.UTF_8));
                SendConnectionStateBroadcast(true);
                inStream = Socket.getInputStream();
                long startTime = System.currentTimeMillis();
                int timeout = 2000;
                while(Connected){
                    try{
                        int available = inStream.available();
                        if(available == 0 && (System.currentTimeMillis() - startTime) >= timeout){
                            Log.i("Networking", "Server failed to send keep alive");
                            Notification("PREKINUTA VEZA", "NEMA VISE KONEKCIJE SA KUCOM", 0);
                            SendConnectionStateBroadcast(false);
                        }
                        else if(available > 0){
                            Log.i("Test", "read");
                            startTime = System.currentTimeMillis();
                            byte[] buffer = new byte[2];
                            int read = inStream.read(buffer);
                            if(buffer[0] == 0)continue;
                            else if(buffer[0] == 1){
                                Notification("A NIJE ISLJLUCIO TIMER", "NEKO JE OTOVRIO VRATA", 1);
                            }
                            if(read > 1){
                                Notification("Kuca salje cudne podatke, moguci sum konekcije", "Reci ovo vuku", 2);
                                Log.i("Networking", "Strange buffer");
                            }
                        }
                    }catch (Exception ex){
                        System.out.println("Some error " + ex.getMessage());
                    }
                }
                Log.i("Networking", "Server disconnected trying again");
            }catch (IOException ex){
                if(Connected)
                    SendConnectionStateBroadcast(false);

                Log.i("Networking", "Could not connect to server trying again in " + (sleepTimer/1000) + " seconds");
                Socket = new Socket();
                try {

                    Thread.sleep(sleepTimer);
                } catch (Exception ignored) { }
            }
        }
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    void Notification(String text, String title, int id){
        Intent intent = new Intent(this, VuleAlarmService.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "ALERT")
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify(id, builder.build());
    }
}