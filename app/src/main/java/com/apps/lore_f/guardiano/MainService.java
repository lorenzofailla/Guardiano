package com.apps.lore_f.guardiano;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.lorenzofailla.utilities.Files;
import com.lorenzofailla.utilities.Messaging;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Lorenzo Failla on 28/dic/2016.
 */

public class MainService extends Service {

    public static final String PICTURES_TAKEN_CHILD = "pictures_taken";
    public static final String ONLINE_DEVICES_CHILD = "online_devices";
    public static final String STORAGE_BUCKET = "gs://guardiano-2c543.appspot.com/";
    public static final String REQUEST_UI_UPDATE = "CAMERACONTROL___REQUEST_UI_UPDATE";
    public static final String MOTION_LEVEL_CHANGED = "CAMERACONTROL___MOTION_LEVEL_CHANGED";
    public static final String SHOT_TAKEN = "CAMERACONTROL___SHOT_TAKEN";
    public static final String CAMERA_STARTED = "CAMERACONTROL___CAMERA_STARTED";
    public static Camera mainCamera = null;
    public static MediaRecorder mediaRecorder;

    // Firebase storage
    public static StorageReference storageReference;

    // Firebase messaging
    public static String deviceToken;

    // Device description
    public static String deviceDescription;

    // Service management
    public static boolean amIRunning = false;
    public static boolean isOpenCVLibraryLoaded = false;
    public static int previewRotation = 0;
    public static double motionLevel;
    public static double motionLevelThreshold = 0.7; // TODO: 09/02/2017 settare tramite SharedPreferences
    public static MotionDetection motionDetection;
    public static VideoLooper videoLooper;
    private static LocalBroadcastManager broadcastManager;
    private static String dataBaseOnlineDeviceRegistrationEntry = null;
    private static String TAG = "->MainService";
    private static int previewFrameWidth;
    private static int previewFrameHeight;
    private static RequestListener requestListener;
    private static VideoLooper.OnVideoLooperStatusChangedListener onVideoLooperStatusChangedListener = new VideoLooper.OnVideoLooperStatusChangedListener() {
        @Override
        public void onStatusChanged(int status) {

            if (requestListener != null) requestListener.newEvent(REQUEST_UI_UPDATE);

        }

        @Override
        public void onCreated() {

            if (requestListener != null) requestListener.newEvent(REQUEST_UI_UPDATE);

        }

        @Override
        public void loopKept(File resultFile) {

            StorageReference videoToBeUploaded = storageReference.child(FirebaseAuth.getInstance().getCurrentUser().getUid() + "/" + "pictures_taken/" + resultFile.getName());
            UploadTask uploadTask = videoToBeUploaded.putBytes(Files.getFileDataBytes(resultFile));

            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    // Handle successful uploads on complete
                    Uri downloadUrl = taskSnapshot.getMetadata().getDownloadUrl();

                    PictureTakenMessage pictureTakenMessage = new PictureTakenMessage(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), "New video sequence captured", downloadUrl.toString(), deviceDescription);
                    FirebaseDatabase.getInstance().getReference().child(FirebaseAuth.getInstance().getCurrentUser().getUid()).child(PICTURES_TAKEN_CHILD).push().setValue(pictureTakenMessage).addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {

                            Messaging.sendNotification(
                                    "" + deviceDescription + " detected a movement!",
                                    "Tap here to view the recorded clip",
                                    ""
                            );

                        }

                    });

                }

            });

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



            DatabaseReference onLineDeviceEntry = FirebaseDatabase.getInstance().getReference().child(getUserId()).child(ONLINE_DEVICES_CHILD).push();
            onLineDeviceEntry.addListenerForSingleValueEvent(deviceRegisteredValueEventListener);
            onLineDeviceEntry.setValue(onlineDeviceMessage);

        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }

    };

    private static String getUserId(){

        return FirebaseAuth.getInstance().getCurrentUser().getUid().toString();
    }

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

            StorageReference pictureToBeUploaded = storageReference.child(getUserId() + "/" + "pictures_taken/" + pictureFilename);
            UploadTask uploadTask = pictureToBeUploaded.putBytes(data);

            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    // Handle successful uploads on complete
                    Uri downloadUrl = taskSnapshot.getMetadata().getDownloadUrl();

                    PictureTakenMessage pictureTakenMessage = new PictureTakenMessage(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), "Required by user", downloadUrl.toString(), deviceDescription);
                    FirebaseDatabase.getInstance().getReference().child(getUserId()).child(PICTURES_TAKEN_CHILD).push().setValue(pictureTakenMessage).addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {

                            Messaging.sendNotification(
                                    "The picture you requested to " + deviceDescription + " has been uploaded!",
                                    "Tap here to view the recorded picture",
                                    ""
                            );
                        }
                    });

                }
            });

            camera.startPreview();
            requestListener.newEvent(SHOT_TAKEN);

        }

    };
    MotionDetection.MotionDetectionOnValueChangeListener motionDetectionOnValueChangeListener = new MotionDetection.MotionDetectionOnValueChangeListener() {

        @Override
        public void onMotionValueChanged(double motionValue) {

            // aggiorna il valore di motionLevel
            motionLevel = motionValue;
            requestListener.newEvent(MOTION_LEVEL_CHANGED);
            /* Log.d(TAG, "motion level: " + motionValue); */

        }

        @Override
        public void onThresholdExceeded() {

            videoLooper.delayLoopAndKeepVideo();
            Log.d(TAG, "motion detected");
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

            Log.d(TAG, "got frame");
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

    public static void setRequestListener(RequestListener listener) {

        requestListener = listener;

    }

    private static void registerDeviceInDatabase(String deviceToken, String deviceDescription) {

        // inizializzo il database di Firebase
        DatabaseReference onlineDevicesDatabaseReference = FirebaseDatabase.getInstance().getReference().child(FirebaseAuth.getInstance().getCurrentUser().getUid()).child(ONLINE_DEVICES_CHILD);
        Query onlineDevicesQuery = onlineDevicesDatabaseReference.orderByChild("deviceDescription").equalTo(deviceDescription);
        onlineDevicesQuery.addListenerForSingleValueEvent(onlineDevicesValueEventListener);

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

        // inizializzo lo starage di Firebase
        storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(STORAGE_BUCKET);

        if (safeCameraOpen()) {

            Log.i(TAG, "Successfully opened camera");

            configureCamera();

            videoLooper = new VideoLooper(mainCamera);
            videoLooper.setOnVideoLooperStatusChangedListener(onVideoLooperStatusChangedListener);
            motionDetection = new MotionDetection(previewFrameWidth, previewFrameHeight, motionLevelThreshold);
            motionDetection.setMotionDetectionOnValueChangeListener(motionDetectionOnValueChangeListener);
            mainCamera.setPreviewCallback(previewCallback);
            mainCamera.startPreview();

            if (mainCamera.getParameters().getMaxNumDetectedFaces() > 0) {

                mainCamera.setFaceDetectionListener(faceDetectionListener);
                mainCamera.startFaceDetection();

            } else {

                Log.d(TAG, "face detection not supported");

            }

            if(requestListener!=null) requestListener.newEvent(CAMERA_STARTED);

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

            FirebaseDatabase.getInstance().getReference().child(getUserId()).child(ONLINE_DEVICES_CHILD).child(dataBaseOnlineDeviceRegistrationEntry).removeValue();
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
            cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
            cameraParameters.setJpegQuality(100);

            /*mainCamera.enableShutterSound(false);*/

            previewFrameWidth = cameraParameters.getPreviewSize().width;
            previewFrameHeight = cameraParameters.getPreviewSize().height;

            cameraParameters.setPreviewSize(previewFrameWidth, previewFrameHeight);
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

    public interface RequestListener {

        void newEvent(String eventName);

    }


}
