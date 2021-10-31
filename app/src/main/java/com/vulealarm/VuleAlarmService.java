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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class VuleAlarmService extends Service {
    Socket Socket = null;
    VuleAlarmService Instance;
    boolean Connected = false, Running = false;
    int Vrata = 0;
    BroadcastReceiver ConnectionStateRequestReceiver;
    NotificationManagerCompat notificationManager;

    final int ALERT_NOTIFICATION = 0,
              NO_CONNECTION_NOTIFICATION = 1,
              WRONG_BUFFER_NOTIFICATION = 2;
    public VuleAlarmService() {
        Instance = this;
        ConnectionStateRequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                SendConnectionStateBroadcast(Connected, Vrata);
            }
        };

    }
    @Override
    public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startCustomForeground();
        }else startForeground(12, new Notification());
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private void startCustomForeground(){
        String NOTIFICATION_CHANNEL_ID = "vulealarm.service";
        String channelName = "Vule alarm background service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("Vule alarm service")
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setWhen(System.currentTimeMillis())
                .build();
        startForeground(12, notification);
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
    void SendConnectionStateBroadcast(boolean state, int vrata)
    {
        Connected = state;
        Vrata = vrata;
        sendBroadcast(new Intent().putExtra("STATE", state)
                                  .putExtra("VRATA", vrata)
                                  .setAction("ConnectionState"));
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        try{
            Running = false;
            Socket.close();
        }catch (Exception ignored){}
        SendConnectionStateBroadcast(false, 0);
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
        String Address;
        int Port;
        //Getting server ip and port
        try{
            URL url = new URL("https://jsonblob.com/api/903885772177031168");
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();
            InputStream stream = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            StringBuffer buffer = new StringBuffer();
            String line = "";

            while ((line = reader.readLine()) != null)
                buffer.append(line+"\n");

            JSONObject obj = new JSONObject(buffer.toString());
            Port = obj.getInt("port");
            Address = obj.getString("address");
        }
        catch (Exception ignored){
            Log.i("Networking", "Failed to get json from api " + ignored);
            Address = "192.168.0.17";
            Port = 1300;
            try{
                Thread.sleep(2000);
            }catch (Exception _ignored){}
        }

        while(Running){
            try{
                Socket.connect(new InetSocketAddress(Address, Port));
                notificationManager.cancel(NO_CONNECTION_NOTIFICATION);
                //Get and send device name
                Log.i("Networking", "Connected sending device name and starting listening for data");
                Socket.getOutputStream().write(Settings.Global.getString(this.getContentResolver(), "device_name").getBytes(StandardCharsets.UTF_8));
                SendConnectionStateBroadcast(true, 0);
                inStream = Socket.getInputStream();
                long startTime = System.currentTimeMillis();
                int timeout = 2000;
                while(Connected){
                    try{
                        int available = inStream.available();
                        //Timeout
                        if(available == 0 && (System.currentTimeMillis() - startTime) >= timeout){
                            Log.i("Networking", "Server failed to send keep alive");
                            Notification("PREKINUTA VEZA", "NEMA VISE KONEKCIJE SA KUCOM", NO_CONNECTION_NOTIFICATION);
                            SendConnectionStateBroadcast(false, 0);
                        }
                        //Has data
                        else if(available > 0){
                            startTime = System.currentTimeMillis();
                            byte[] buffer = new byte[2];
                            int read = inStream.read(buffer);
                            if(buffer[0] == 0) {
                                SendConnectionStateBroadcast(true, 2);
                            }
                            else if(buffer[0] == 1){
                                Notification("A NIJE ISLJLUCIO TIMER", "NEKO JE OTOVRIO VRATA", ALERT_NOTIFICATION);
                                SendConnectionStateBroadcast(true, 1);
                            }
                            if(read > 1){
                                Notification("Kuca salje cudne podatke, moguci sum konekcije", "Reci ovo vuku", WRONG_BUFFER_NOTIFICATION);
                                Log.i("Networking", "Strange buffer");
                            }
                        }
                    }catch (Exception ex){
                        System.out.println("Some error " + ex.getMessage());
                    }
                }
                Log.i("Networking", "Server disconnected trying again");
            }catch (IOException ex){
                //Dont send everytime to avoid much toasts
                if(Connected)
                    SendConnectionStateBroadcast(false, 0);

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