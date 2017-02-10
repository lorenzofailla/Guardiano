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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
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

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.lorenzofailla.utilities.Files;

import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


/**
 * Created by Lorenzo Failla on 28/dic/2016.
 */

public class MainService extends Service {

    public static final String PICTURES_TAKEN_CHILD = "pictures_taken";
    public static final String ONLINE_DEVICES_CHILD = "online_devices";
    public static final String STORAGE_BUCKET = "gs://guardiano-2c543.appspot.com/";

    public static Camera mainCamera = null;
    public static MediaRecorder mediaRecorder;

    // Firebase
    public static FirebaseAuth firebaseAuth;
    public static FirebaseUser firebaseUser;

    // Firebase database
    public static DatabaseReference databaseReference;
    public static FirebaseDatabase firebaseDatabase;

    // Firebase storage
    public static StorageReference storageReference;

    // Firebase messaging
    public static String deviceToken;

    // Device description
    public static String deviceDescription;

    // Service management
    public static boolean amIRunning = false;
    public static boolean isOpenCVLibraryLoaded = false;
    public static boolean isVideoLoopRunning = false;

    public static int previewRotation = 0;

    private static LocalBroadcastManager broadcastManager;
    private static String dataBaseOnlineDeviceRegistrationEntry = null;
    private static UploadTask uploadTask;
    private static String TAG = "->MainService";

    private static int previewFrameWidth;
    private static int previewFrameHeight;

    public static double motionLevel;
    public static double motionLevelThreshold = 2.0; // TODO: 09/02/2017 settare tramite SharedPreferences

    public static MotionDetection motionDetection;
    private static RequestListener requestListener;

    public static VideoLooper videoLooper;
    private static VideoLooper.OnVideoLooperStatusChangedListener onVideoLooperStatusChangedListener = new VideoLooper.OnVideoLooperStatusChangedListener() {
        @Override
        public void onStatusChanged(int status) {

            requestListener.newEvent("CAMERACONTROL___REQUEST_UI_UPDATE");

        }
    };

    public interface RequestListener{

        void newEvent (String eventName);

    }

    public static void setRequestListener(RequestListener listener){

        requestListener = listener;

    }

    MotionDetection.MotionDetectionOnValueChangeListener motionDetectionOnValueChangeListener = new MotionDetection.MotionDetectionOnValueChangeListener() {

        @Override
        public void onMotionValueChanged(double motionValue) {

            // aggiorna il valore di motionLevel
            motionLevel=motionValue;
            requestListener.newEvent("CAMERACONTROL___MOTION_LEVEL_CHANGED");
            Log.d(TAG, "motion level:" + motionValue);

        }

        @Override
        public void onThresholdExceeded() {

        }

    };

    private static ValueEventListener deviceRegisteredValueEventListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {

            // dispositivo registrato
            Log.d(TAG, "Device registered: " + dataSnapshot.getKey());
            dataBaseOnlineDeviceRegistrationEntry = dataSnapshot.getKey();

        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

            dataBaseOnlineDeviceRegistrationEntry = null;

        }

    };
    private static ValueEventListener onlineDevicesValueEventListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {

            // rimuove i children con la stessa descrizione
            Log.d(TAG, String.format("%d devices found with the same description.", dataSnapshot.getChildrenCount()));
            dataSnapshot.getRef().removeValue();

            // inserisce un nuovo child con un nuovo OnlineDeviceMessage
            OnlineDeviceMessage onlineDeviceMessage = new OnlineDeviceMessage(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), deviceToken, deviceDescription);

            DatabaseReference onLineDeviceEntry = databaseReference.child(firebaseUser.getUid()).child(ONLINE_DEVICES_CHILD).push();
            onLineDeviceEntry.addListenerForSingleValueEvent(deviceRegisteredValueEventListener);

            onLineDeviceEntry.setValue(onlineDeviceMessage);

        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }

    };



    private static Camera.PictureCallback takeShotCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = Files.getOutputMediaFile(Files.MEDIA_TYPE_IMAGE);

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
            String pictureFilename = "IMG_" + deviceDescription + "_" + timeStamp + ".jpg";

            StorageReference pictureToBeUploaded = storageReference.child(firebaseUser.getUid() + "/" + "pictures_taken/" + pictureFilename);
            uploadTask = pictureToBeUploaded.putBytes(data);

            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    // Handle successful uploads on complete
                    Uri downloadUrl = taskSnapshot.getMetadata().getDownloadUrl();

                    PictureTakenMessage pictureTakenMessage = new PictureTakenMessage(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), "Required by user", downloadUrl.toString(), deviceDescription);
                    databaseReference.child(firebaseUser.getUid()).child(PICTURES_TAKEN_CHILD).push().setValue(pictureTakenMessage);

                }
            });

            camera.startPreview();
            requestListener.newEvent("CAMERACONTROL___SHOT_TAKEN");

        }

    };

    Camera.FaceDetectionListener faceDetectionListener = new Camera.FaceDetectionListener() {
        @Override
        public void onFaceDetection(Camera.Face[] faces, Camera camera) {

            Log.d(TAG, faces.length + " faces detected.");

        }

    };

    Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {

            motionDetection.enterFrame(data);

        }

    };

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {

            Log.i(TAG, "Received intent: " + intent.getAction());

            switch (intent.getAction()) {

                case "CAMERACONTROL___REMOTE_COMMAND_RECEIVED":

                    if (intent.hasExtra("REMOTE_COMMAND_MESSAGE")) {

                        String remoteCommand = intent.getStringExtra("REMOTE_COMMAND_MESSAGE");
                        Toast.makeText(MainService.this, remoteCommand, Toast.LENGTH_SHORT).show();

                    }

                    break;
            }

        }
    };

    private static void registerDeviceInDatabase(String deviceToken, String deviceDescription) {

        DatabaseReference onlineDevicesDatabaseReference = databaseReference.child(firebaseUser.getUid()).child(ONLINE_DEVICES_CHILD);
        Query onlineDevicesQuery = onlineDevicesDatabaseReference.orderByChild("deviceDescription").equalTo(deviceDescription);
        onlineDevicesQuery.addListenerForSingleValueEvent(onlineDevicesValueEventListener);

    }

    public static void setVideoLoopActivity(boolean status) {

        if (status) {



        } else {

            // ferma il loop di registrazione video
            isVideoLoopRunning = false;

        }

        requestListener.newEvent("CAMERACONTROL___REQUEST_UI_UPDATE");

    }

    public static void takeShot() {
            /*
            cattura un fotogramma dalla camera
            */

        if (mainCamera != null) {

            mainCamera.takePicture(null, null, takeShotCallback);

        }

    }



    @Override
    public void onCreate() {

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

        // inizializzo il database di Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference();

        // inizializzo lo starage di Firebase
        storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(STORAGE_BUCKET);

        if (safeCameraOpen()) {

            Log.i(TAG, "Successfully opened camera");

            configureCamera();

            videoLooper = new VideoLooper(mainCamera);

            motionDetection=new MotionDetection(previewFrameWidth,previewFrameHeight,motionLevelThreshold);
            motionDetection.setMotionDetectionOnValueChangeListener(motionDetectionOnValueChangeListener);
            mainCamera.setPreviewCallback(previewCallback);
            mainCamera.startPreview();

            if (mainCamera.getParameters().getMaxNumDetectedFaces() > 0) {

                mainCamera.setFaceDetectionListener(faceDetectionListener);
                mainCamera.startFaceDetection();

            } else {

                Log.d(TAG, "face detection not supported");

            }

            requestListener.newEvent("CAMERACONTROL___EVENT_CAMERA_STARTED");

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

        // ottiene il token del dispositivo
        deviceToken = FirebaseInstanceId.getInstance().getToken();

        // regirstra il dispositivo nel database con il token ottenuto
        registerDeviceInDatabase(deviceToken, deviceDescription);

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

        /* de-registra il dispositivo dal database */
        if (dataBaseOnlineDeviceRegistrationEntry != null) {

            databaseReference.child(firebaseUser.getUid()).child(ONLINE_DEVICES_CHILD).child(dataBaseOnlineDeviceRegistrationEntry).removeValue();
            dataBaseOnlineDeviceRegistrationEntry = null;

        }


        // de-registra il ricevitore di broadcast
        broadcastManager.unregisterReceiver(broadcastReceiver);

        stopForeground(true);

        // registra il flag
        amIRunning = false;

    }

    private void releaseCamera() {

        if (mainCamera != null) {

            mainCamera.stopPreview();
            mainCamera.release();
            mainCamera = null;

        }

    }

    private void configureCamera() {

        /* configura i parametri di funzionamento della camera */

        if (mainCamera != null) {

            // ottiene i parametri della camera
            Camera.Parameters cameraParameters = mainCamera.getParameters();

            // imposta i parametri
            cameraParameters.setPreviewFormat(ImageFormat.NV21);
            cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
            cameraParameters.setJpegQuality(100);

            previewFrameWidth = cameraParameters.getPreviewSize().width;
            previewFrameHeight = cameraParameters.getPreviewSize().height;

            cameraParameters.setPreviewSize(previewFrameWidth,previewFrameHeight);
            mainCamera.setParameters(cameraParameters);

        }

    }

    private boolean safeCameraOpen() {

        boolean isCameraOpened = false;

        try {

            mainCamera = Camera.open();
            isCameraOpened = (mainCamera != null);
            Log.i(TAG, "connected to Camera");

        } catch (Exception e) {

            Log.e(TAG, "failed to open Camera");
            Log.e(TAG, e.getMessage());
            e.printStackTrace();

        }

        return isCameraOpened;

    }



}
