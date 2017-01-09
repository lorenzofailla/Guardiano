package com.apps.lore_f.guardiano;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Lorenzo Failla on 28/dic/2016.
 */

public class MainService extends Service {

    public static Camera mainCamera=null;
    public static MediaRecorder mediaRecorder;
    private static Handler taskHandler;
    private static LocalBroadcastManager broadcastManager;

    private static final String TAG = "_MainService";

    public static boolean isVideoLoopRunning = false;
    public static long standardLoopDuration = 50000;

    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int MEDIA_TYPE_VIDEO = 2;

    private ServerSocket serverSocket;

    static final int SERVER_SOCKET_PORT = 6000;

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

    Thread serverThread=null;

    class ServerThread implements Runnable {

        public void run() {

            Socket socket = null;

            try {

                serverSocket = new ServerSocket(SERVER_SOCKET_PORT);
                Log.i(TAG, "Server socket created ->" + serverSocket.getLocalSocketAddress().toString()+" ("+serverSocket.getInetAddress().toString()+")");

            } catch (IOException e) {

                e.printStackTrace();

            }

            while (!Thread.currentThread().isInterrupted()) {

                try {

                    socket = serverSocket.accept();
                    Log.i(TAG, "Server socket initialized to listening");
                    CommunicationThread commThread = new CommunicationThread(socket);
                    new Thread(commThread).start();

                } catch (IOException e) {

                    e.printStackTrace();

                }
            }
        }
    }

    class CommunicationThread implements Runnable {

        private Socket clientSocket;

        private BufferedReader input;

        public CommunicationThread(Socket clientSocket) {

            this.clientSocket = clientSocket;

            try {

                this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {

            while (!Thread.currentThread().isInterrupted()) {

                try {

                    String read = input.readLine();

                    

                } catch (IOException e) {

                    e.printStackTrace();

                }
            }
        }

    }


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

        // inizializzo taskHandler
        taskHandler = new Handler();

        if(safeCameraOpen()){

            Log.i(TAG, "Successfully opened camera");
            Toast.makeText(MainService.this, "Successfully opened camera.", Toast.LENGTH_SHORT).show();
            mainCamera.startPreview();

            broadcastManager.sendBroadcast(new Intent("CAMERACONTROL___EVENT_CAMERA_STARTED"));

        }

        serverThread = new Thread(new ServerThread());
        serverThread.start();

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

        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        stopForeground(true);

    }

    private void releaseCamera() {

        if (mainCamera!=null){

            mainCamera.stopPreview();
            mainCamera.release();
            mainCamera=null;

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

        if(status){


            // - avvia il loop di registrazione video
            // inizializza il MediaRecorder
            mediaRecorder = new MediaRecorder();

            // sblocca la camera
            mainCamera.unlock();

            // configura il MediaRecorder
            mediaRecorder.setCamera(mainCamera);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setVideoEncodingBitRate(4356000);
            mediaRecorder.setAudioEncodingBitRate(128000);
            /*mediaRecorder.setVideoSize(videoFrameWidth, videoFrameHeight);*/
            mediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());
            /*mediaRecorder.setPreviewDisplay(cameraPreviewHolder.getSurface());*/

            try {

                mediaRecorder.prepare();
                mediaRecorder.start();

                Log.i(TAG, "Video loop started");

                isVideoLoopRunning = true;

            } catch (IOException e) {

                Log.e(TAG, "Error preparing the MediaRecorder");

            }

            // imposta il valore del flag a true
            isVideoLoopRunning = true;

            // richiede il blocco video
            taskHandler.postAtTime(stopRecorder, SystemClock.uptimeMillis() + standardLoopDuration);

        } else {

            // ferma il loop di registrazione video
            isVideoLoopRunning = false;

        }

        broadcastManager.sendBroadcast(new Intent("CAMERACONTROL___REQUEST_UI_UPDATE"));

    }

    /**
     * Create a file Uri for saving an image or video
     */
    private static Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }



    /**
     * Create a File for saving an image or video
     */
    private static File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    private static Runnable stopRecorder = new Runnable() {

        @Override
        public void run() {

            // ferma il mediarecorder e richiama il lock della camera
            mediaRecorder.stop();
            mainCamera.lock();

            if(isVideoLoopRunning) {
                setVideoLoopActivity(false);
            }

        }

    };

}
