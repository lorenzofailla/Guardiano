package com.apps.lore_f.guardiano;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Created by Lorenzo Failla on 28/dic/2016.
 */

public class MainService extends Service {

    public static Camera mainCamera=null;
    public static MediaRecorder mediaRecorder;
    private static Handler taskHandler;
    private static LocalBroadcastManager broadcastManager;

    // Firebase
    public static FirebaseAuth firebaseAuth;
    public static FirebaseUser firebaseUser;

    // Firebase database
    public static DatabaseReference databaseReference;
    public static final String PICTURES_TAKEN_CHILD = "pictures_taken";
    public static final String ONLINE_DEVICES_CHILD = "online_devices";
    private static String dataBaseOnlineDeviceRegistrationEntry = null;

    // Firebase storage
    public static StorageReference storageReference;
    public static final String STORAGE_BUCKET="gs://guardiano-2c543.appspot.com/";
    private static UploadTask uploadTask;

    // Firebase messaging
    public static String deviceToken;

    // Device description
    public static String deviceDescription="Cucina";

    // Service management
    public static boolean amIRunning=false;

    private static String TAG = "_MainService";

    public static boolean isVideoLoopRunning = false;
    public static long standardLoopDuration = 50000;

    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int MEDIA_TYPE_VIDEO = 2;

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

        // inzializzo i filtri per l'ascolto degli intent
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("CAMERACONTROL___REMOTE_COMMAND_RECEIVED");

        // registro il ricevitore di intent sul BroadcastManager registrato
        broadcastManager.registerReceiver(broadcastReceiver, intentFilter);

        // inizializzo taskHandler
        taskHandler = new Handler();

        // inizializzo il database di Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference();

        // inizializzo lo starage di Firebase
        storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(STORAGE_BUCKET);

        if(safeCameraOpen()){

            Log.i(TAG, "Successfully opened camera");
            Toast.makeText(MainService.this, "Successfully opened camera.", Toast.LENGTH_SHORT).show();
            mainCamera.startPreview();

            broadcastManager.sendBroadcast(new Intent("CAMERACONTROL___EVENT_CAMERA_STARTED"));

        }

        // inizializza il listener per le modifiche al database
        databaseReference.child(firebaseUser.getUid()).child(ONLINE_DEVICES_CHILD).removeEventListener(childEventListener);
        databaseReference.child(firebaseUser.getUid()).child(ONLINE_DEVICES_CHILD).addChildEventListener(childEventListener);

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

        // ottiene il token del dispositivo
        deviceToken = FirebaseInstanceId.getInstance().getToken();

        // registra il dispositivo come online
        OnlineDeviceMessage onlineDeviceMessage = new OnlineDeviceMessage(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()),deviceToken,deviceDescription);
        databaseReference.child(firebaseUser.getUid()).child(ONLINE_DEVICES_CHILD).push().setValue(onlineDeviceMessage);

        // registra il flag
        amIRunning = true;

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

        // rilascia la camera
        releaseCamera();

        // deregistra il ricevitore di eventi del database
        databaseReference.child(firebaseUser.getUid()).child(ONLINE_DEVICES_CHILD).removeEventListener(childEventListener);

        // de-registra il dispositivo dal database
        databaseReference.child(firebaseUser.getUid()).child(ONLINE_DEVICES_CHILD).child(dataBaseOnlineDeviceRegistrationEntry).removeValue();

        // de-registra il ricevitore di broadcast
        broadcastManager.unregisterReceiver(broadcastReceiver);

        stopForeground(true);

        // registra il flag
        amIRunning = false;

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


    public static void takeShot(){
            /*
        cattura un fotogramma dalla camera
            */

        if(mainCamera!=null){

            mainCamera.takePicture(null, null, takeShotCallback);

        }

    }

    private static Camera.PictureCallback takeShotCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile;
            pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if (pictureFile == null) {
                Log.d(TAG, "Error creating media file, check storage permissions.");
                return;
            }

            try {

                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();

                Log.i(TAG, "Successfully wrote file " + pictureFile.getAbsolutePath());


            } catch (FileNotFoundException e) {

                Log.d(TAG, "File not found: " + e.getMessage());

            } catch (IOException e) {

                Log.d(TAG, "Error accessing file: " + e.getMessage());

            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String pictureFilename = "IMG_" + timeStamp + ".jpg";

            StorageReference pictureToBeUploaded=storageReference.child(firebaseUser.getUid()+"/"+"pictures_taken/" + pictureFilename);
            uploadTask = pictureToBeUploaded.putBytes(data);

            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    // Handle successful uploads on complete
                    Uri downloadUrl = taskSnapshot.getMetadata().getDownloadUrl();

                    PictureTakenMessage pictureTakenMessage = new PictureTakenMessage(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()),"Picture taken!", downloadUrl.toString());
                    databaseReference.child(firebaseUser.getUid()).child(PICTURES_TAKEN_CHILD).push().setValue(pictureTakenMessage);

                }
            });

            camera.startPreview();
            broadcastManager.sendBroadcast(new Intent("CAMERACONTROL___SHOT_TAKEN"));

        }

    };

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {

            Log.i(TAG, "Received intent: "+intent.getAction());

            switch (intent.getAction()) {

                case "CAMERACONTROL___REMOTE_COMMAND_RECEIVED":

                    if (intent.hasExtra("REMOTE_COMMAND_MESSAGE")){

                        String remoteCommand = intent.getStringExtra("REMOTE_COMMAND_MESSAGE");
                        Toast.makeText(MainService.this, remoteCommand, Toast.LENGTH_SHORT).show();

                    }

                    break;
            }

        }
    };

    private static ChildEventListener childEventListener = new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {

            Log.d(TAG, "onChildAdded :: " +s+ " || " + dataSnapshot.getKey() + " || " + dataSnapshot.getValue().toString());
            dataBaseOnlineDeviceRegistrationEntry = dataSnapshot.getKey();

        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String s) {

        }

        @Override
        public void onChildRemoved(DataSnapshot dataSnapshot) {

        }

        @Override
        public void onChildMoved(DataSnapshot dataSnapshot, String s) {

        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }

    };

}
