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

import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


/**
 * Created by Lorenzo Failla on 28/dic/2016.
 */

public class MainService extends Service {

    public static Camera mainCamera = null;
    public static MediaRecorder mediaRecorder;
    private static Handler taskHandler;
    private static LocalBroadcastManager broadcastManager;

    // Firebase
    public static FirebaseAuth firebaseAuth;
    public static FirebaseUser firebaseUser;

    // Firebase database
    public static DatabaseReference databaseReference;
    public static FirebaseDatabase firebaseDatabase;
    public static final String PICTURES_TAKEN_CHILD = "pictures_taken";
    public static final String ONLINE_DEVICES_CHILD = "online_devices";
    private static String dataBaseOnlineDeviceRegistrationEntry = null;

    // Firebase storage
    public static StorageReference storageReference;
    public static final String STORAGE_BUCKET = "gs://guardiano-2c543.appspot.com/";
    private static UploadTask uploadTask;

    // Firebase messaging
    public static String deviceToken;

    // Device description
    public static String deviceDescription;

    // Service management
    public static boolean amIRunning = false;

    private static String TAG = "->MainService";

    public static boolean isVideoLoopRunning = false;
    public static long standardLoopDuration = 50000;

    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int MEDIA_TYPE_VIDEO = 2;

    public static int previewRotation = 0;

    private static byte[] previousFrameData;
    private static byte[] currentFrameData;

    public static Bitmap motionBitmap;
    private static boolean isProcessingFrames=false;

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

        // inizializzo taskHandler
        taskHandler = new Handler();

        // inizializzo il database di Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference();

        // inizializzo lo starage di Firebase
        storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(STORAGE_BUCKET);

        if (safeCameraOpen()) {

            Log.i(TAG, "Successfully opened camera");

            configureCamera();

            mainCamera.setPreviewCallback(previewCallback);
            mainCamera.startPreview();

            if(mainCamera.getParameters().getMaxNumDetectedFaces()>0){

                mainCamera.setFaceDetectionListener(faceDetectionListener);
                mainCamera.startFaceDetection();

            } else {

                Log.d(TAG, "face detection not supported");

            }

            broadcastManager.sendBroadcast(new Intent("CAMERACONTROL___EVENT_CAMERA_STARTED"));

        }

    }

    Camera.FaceDetectionListener faceDetectionListener = new Camera.FaceDetectionListener() {
        @Override
        public void onFaceDetection(Camera.Face[] faces, Camera camera) {

            Log.d(TAG, faces.length + " faces detected.");

        }

    };

    Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {

                        if (currentFrameData!=null){

                previousFrameData=currentFrameData;

            }

            currentFrameData = data;
            Log.d(TAG, "received " + data.length + " bytes of data");

            if(currentFrameData!=null && previousFrameData!=null && !isProcessingFrames) {

                new ProcessFrames().execute();

            }

        }

    };

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

    private static void registerDeviceInDatabase(String deviceToken, String deviceDescription) {

        DatabaseReference onlineDevicesDatabaseReference = databaseReference.child(firebaseUser.getUid()).child(ONLINE_DEVICES_CHILD);
        Query onlineDevicesQuery = onlineDevicesDatabaseReference.orderByChild("deviceDescription").equalTo(deviceDescription);
        onlineDevicesQuery.addListenerForSingleValueEvent(onlineDevicesValueEventListener);

    }

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
            dataBaseOnlineDeviceRegistrationEntry=null;

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

    private void configureCamera(){

        /* configura i parametri di funzionamento della camera */

        if (mainCamera!=null){

            // ottiene i parametri della camera
            Camera.Parameters cameraParameters = mainCamera.getParameters();

            // imposta i parametri
            cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
            cameraParameters.setJpegQuality(100);

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

    public static void setVideoLoopActivity(boolean status) {

        if (status) {

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

            if (isVideoLoopRunning) {
                setVideoLoopActivity(false);
            }

        }

    };


    public static void takeShot() {
            /*
        cattura un fotogramma dalla camera
            */

        if (mainCamera != null) {

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
            broadcastManager.sendBroadcast(new Intent("CAMERACONTROL___SHOT_TAKEN"));

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

    public static class ProcessFrames extends AsyncTask<Void, Void, Integer> {

        protected Integer doInBackground(Void... dummy) {

            isProcessingFrames=true;
            motionBitmap = subTractImages();
            return 0;
        }


        protected void onPostExecute(Integer motionValue) {

            Intent intent = new Intent("CAMERACONTROL___MOTION_PICTURE_READY");
            broadcastManager.sendBroadcast(intent);
            isProcessingFrames=false;

        }

    }

    public static void sendMessage(final String recipient, final String message, final String sender) {

        new AsyncTask<String, String, String>() {
            @Override
            protected String doInBackground(String... params) {
                try {

                    return getResponseFromMessagingServer(
                            "http://lorenzofailla.esy.es/Guardiano/Messaging/sendmessage.php?Action=M&t=title&m="+message+"&r="+recipient+"&sender="+sender);

                    // TODO: 29/01/2017 implementare, sia lato server che lato client, il time to live del messaggio

                } catch (Exception e) {

                    e.printStackTrace();
                    return null;

                }

            }

            @Override
            protected void onPostExecute(String result) {
                try {

                    if(result!=null) {

                        JSONObject resultJson = new JSONObject(result);
                        int success, failure;
                        success = resultJson.getInt("success");
                        failure = resultJson.getInt("failure");

                        Log.d(TAG, "got response from messaging server: " + success + " success, " + failure + " failure");

                    } else {

                        Log.d(TAG, "got NULL response from messaging server");

                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    static String getResponseFromMessagingServer(String requestUrl) throws IOException {

        OkHttpClient okHttpClient = new OkHttpClient();

        Request request = new Request.Builder()
                .url(requestUrl)
                .build();

        Response response = okHttpClient.newCall(request).execute();

        return response.body().string();

    }

    private static Bitmap subTractImages(){

        /*
        Parameters parameters = camera.getParameters();
        imageFormat = parameters.getPreviewFormat();
        if (imageFormat == ImageFormat.NV21)
        {

            Rect rect = new Rect(0, 0, PreviewSizeWidth, PreviewSizeHeight);
            YuvImage img = new YuvImage(data, ImageFormat.NV21, PreviewSizeWidth, PreviewSizeHeight, null);
            OutputStream outStream = null;
            File file = new File(NowPictureFileName);
            try
            {
                outStream = new FileOutputStream(file);
                img.compressToJpeg(rect, 100, outStream);
                outStream.flush();
                outStream.close();
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        */

        Bitmap previousFrameBitmap = BitmapFactory.decodeByteArray(previousFrameData,0,previousFrameData.length);
        Bitmap currentFrameBitmap = BitmapFactory.decodeByteArray(currentFrameData, 0, currentFrameData.length);

        int width = currentFrameBitmap.getWidth();
        int height = currentFrameBitmap.getHeight();

        Mat previousFrameMatrix = new Mat(height, width, CvType.CV_8UC4);
        Mat currentFrameMatrix = new Mat(height, width, CvType.CV_8UC4);
        Mat frameSubtractionMatrix = new Mat(height, width, CvType.CV_8UC4);

        Utils.bitmapToMat(previousFrameBitmap, previousFrameMatrix);
        Utils.bitmapToMat(currentFrameBitmap, currentFrameMatrix);

        Core.absdiff(currentFrameMatrix, previousFrameMatrix, frameSubtractionMatrix);
        Bitmap resultBitmap = Bitmap.createBitmap(width, height, currentFrameBitmap.getConfig());
        Utils.matToBitmap(frameSubtractionMatrix, resultBitmap);

        return resultBitmap;

    };



}
