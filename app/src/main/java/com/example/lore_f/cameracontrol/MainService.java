package com.example.lore_f.cameracontrol;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by 105053228 on 28/dic/2016.
 */

public class MainService extends Service {

    public static Camera mainCamera;

    private static final String TAG = "_MainService";

    private static LocalBroadcastManager broadcastManager;

    public static boolean isVideoLoopRunning = false;

    public static int getPreviewRotation() {
        return previewRotation;
    }

    public static void setPreviewRotation(int previewRotation) {
        MainService.previewRotation = previewRotation;

        if(mainCamera!=null) {

            mainCamera.setDisplayOrientation(previewRotation);
        }

    }

    public static int previewRotation=0;

    @Override
    public void onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        super.onCreate();

        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // inizializzo il BroadcastManager
        broadcastManager = LocalBroadcastManager.getInstance(this);
        //local_broadcastmanager.registerReceiver(broadcast_receiver, intent_filter);

        if(safeCameraOpen()){

            Log.i(TAG, "Successfully opened camera");
            Toast.makeText(MainService.this, "Successfully opened camera.", Toast.LENGTH_SHORT).show();
            mainCamera.startPreview();

            broadcastManager.sendBroadcast(new Intent("CAMERACONTROL___EVENT_CAMERA_STARTED"));

        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        super.onStartCommand(intent, flags, startId);

        // create the notification
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.eye)
                        .setContentTitle("CameraControl")
                        .setContentText("is running... tap to open!");

        PendingIntent content_intent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        notificationBuilder.setContentIntent(content_intent);

        Notification notification = notificationBuilder.build();

        // start the service in foreground
        startForeground(1, notification);

        // TODO: 29/dic/2016 apre la camera

        // If we get killed, after returning from here, restart
        return START_STICKY;


    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {

        releaseCamera();

        // TODO: 29/dic/2016 rilascia la camera
        stopForeground(true);

    }

    private void releaseCamera() {

        if (mainCamera!=null){

            mainCamera.stopPreview();
            mainCamera.release();
        }

    }
    private boolean safeCameraOpen() {

        boolean qOpened = false;

        try {

            mainCamera = Camera.open();
            qOpened = (mainCamera != null);
            Log.i(TAG, "connected to Camera");

        } catch (Exception e) {

            Log.e(TAG, "failed to open Camera");
            Log.e(TAG, e.getMessage());
            e.printStackTrace();

        }

        return qOpened;

    }

    public static void setVideoLoopActivity(boolean status) {

        isVideoLoopRunning = status;
        broadcastManager.sendBroadcast(new Intent("CAMERACONTROL___REQUEST_UI_UPDATE"));

    }

}
