package com.example.lore_f.cameracontrol;

import android.Manifest;
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
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    Intent mainService;

    private LocalBroadcastManager broadcastManager;

    Camera mCamera;
    Camera.Parameters cameraParameters;

    SurfaceView cameraPreview;
    //SurfaceHolder cameraPreviewHolder;
    //MediaRecorder mediaRecorder;
    //Handler taskHandler;

    private final static String TAG = "CameraControl";

/*    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;*/

    private static final int PERMISSION_RECORD_AUDIO = 1;
    private static final int PERMISSION_CAMERA = 2;
    private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 3;

/*    private int videoFrameHeight;
    private int videoFrameWidth;*/

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {

            switch (intent.getAction()) {

                case "CAMERACONTROL___REQUEST_UI_UPDATE":

                    updateUI();

                    break;
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {

        super.onResume();

        // avvio il servizio
        mainService = new Intent(this, MainService.class);
        startService(mainService);

        if (checkPermissions()) {

            // i permessi necessari sono stati ottenuti, è possibile avviare l'attività

            // inzializzo i filtri per l'ascolto degli intent
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("CAMERACONTROL___REQUEST_UI_UPDATE");

            // inizializzo il BroadcastManager
            broadcastManager = LocalBroadcastManager.getInstance(this);

            // registro il ricevitore di intent sul BroadcastManager registrato
            broadcastManager.registerReceiver(broadcastReceiver, intentFilter);

            // aggiorna l'interfaccia utente
            updateUI();

            // inizializzo handlers ai drawable
            Button videoLoopButton = (Button) findViewById(R.id.BTN___MAIN___STARTVIDEOLOOP);

            videoLoopButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {

                            if (MainService.isVideoLoopRunning) {

                                // il loop è avviato, manda la richiesta per fermare
                                MainService.setVideoLoopActivity(false);

                            } else {

                                // il loop è fermo, manda la richiesta per avviare
                                MainService.setVideoLoopActivity(true);

                            }


                        }
                    }
            );


        } else {

            // TODO: 29/dic/2016 gestire callback per richiesta permessi
            // TODO: 29/dic/2016 gestire informazioni all'utente

        }

        /*
        // inizializza il task handler
        taskHandler = new Handler();
        */



        /*final Button btnTakeShot = (Button) findViewById(R.id.BTN___MAIN___TAKESHOT);
        final Button btnStartService = (Button) findViewById(R.id.BTN___MAIN___STARTSERVICE);
*/


/*        btnTakeShot.setOnClickListener(this);
        btnStartService.setOnClickListener(this);*/
/*

        //start your camera
        if (permissionFlag) {

            Log.i(TAG, "Permissions granted.");

            if (safeCameraOpen()) {

                // ritrova i parametri della camera
                cameraParameters = mCamera.getParameters();

                // ritrova le dimensioni preferenziali della dimensione del video
                try {

                    videoFrameHeight = cameraParameters.getPreferredPreviewSizeForVideo().height;
                    videoFrameWidth = cameraParameters.getPreferredPreviewSizeForVideo().width;

                } catch (NullPointerException e) {

                    // set 1280×720
                    videoFrameHeight = 720;
                    videoFrameWidth = 1280;

                }

                // messaggio di Log
                Log.i(TAG, String.format("Preferred video size: %dx%d", videoFrameWidth, videoFrameHeight));

                // imposta la rotazione
                mCamera.setDisplayOrientation(90);

                assignCameraPreviewSurface();
                startCameraPreview();

            }

        } else {

            Log.i(TAG, "Missing permissions.");

        }
*/

    }

    /*private void startCameraPreview() {

        // richiesta camera in modalità preview
        mCamera.startPreview();

    }*/

    @Override
    protected void onPause() {

        super.onPause();

        /*if (mCamera != null) {

            mCamera.stopPreview();

        }*/

        stopService(mainService);

    }

    /*private boolean safeCameraOpen() {

        boolean qOpened = false;

        try {

            mCamera = Camera.open();
            qOpened = (mCamera != null);
            Log.i(getString(R.string.app_name), "connected to Camera");

        } catch (Exception e) {

            Log.e(getString(R.string.app_name), "failed to open Camera");
            Log.e(getString(R.string.app_name), e.getMessage());

            e.printStackTrace();

        }

        return qOpened;

    }*/

    /*private void assignCameraPreviewSurface() {

        if (mCamera != null){

            // handler a
            cameraPreview = (SurfaceView) findViewById(R.id.camera_preview);

            // inizializzazione dell'holder
            cameraPreviewHolder = cameraPreview.getHolder();
            cameraPreviewHolder.addCallback(this);
            cameraPreviewHolder.setFormat(SurfaceHolder.SURFACE_TYPE_HARDWARE);

            // assegnazione holder come preview display della camera
            try {

                mCamera.setPreviewDisplay(cameraPreviewHolder);

            } catch (IOException e) {

                Log.e(getResources().getString(R.string.app_name), getResources().getString(R.string.ERR_surface_assignment_for_preview));

            }

            Display display = getWindowManager().getDefaultDisplay();

            int width = display.getWidth();
            int height = display.getHeight();

            float cameraFrameRatio = (float) (mCamera.getParameters().getPreviewSize().width) / (float) (mCamera.getParameters().getPreviewSize().height);

            printLogInfo(String.format("frame ratio = %.4f", cameraFrameRatio));

            int previewSurfaceWidth = width / 2;
            int previewSurfaceHeight = width / 2;

            cameraPreviewHolder.setFixedSize(width / 2, (int) (width / 2.0 * cameraFrameRatio));

        }

        *//*
        Camera.Parameters cameraParameters = mCamera.getParameters();
        cameraParameters.setPreviewSize(100,100);
        mCamera.setParameters(cameraParameters);
        *//*

    }*/

    /*private void stopCameraPreview() {

        if (mCamera != null) {

            mCamera.stopPreview();

        }

    }*/

    /*@Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

        assignCameraPreviewSurface();
        startCameraPreview();

    }*/

    /*@Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        printLogInfo(String.format("i=%d, i1=%d, i2=%d", i, i1, i2));
        assignCameraPreviewSurface();
        startCameraPreview();

    }*/

    /*@Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

        stopCameraPreview();

    }*/


    /*@Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.BTN___MAIN___STARTVIDEOLOOP:
*//*
                // inizializza il MediaRecorder
                mediaRecorder = new MediaRecorder();

                // sblocca la camera
                mCamera.unlock();

                // configura il MediaRecorder
                mediaRecorder.setCamera(mCamera);
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                mediaRecorder.setVideoEncodingBitRate(4356000);
                mediaRecorder.setAudioEncodingBitRate(128000);
                mediaRecorder.setVideoSize(videoFrameWidth, videoFrameHeight);

                mediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());
                mediaRecorder.setPreviewDisplay(cameraPreviewHolder.getSurface());
                try {

                    mediaRecorder.prepare();
                    mediaRecorder.start();

                } catch (IOException e) {

                    Log.e(TAG, "Error preparing the MediaRecorder");

                }

                Log.i(TAG, "Start");
                findViewById(R.id.BTN___MAIN___STARTCAPTURE).setEnabled(false);
                taskHandler.postAtTime(stopRecorder, SystemClock.uptimeMillis() + 5000);*//*

                break;
*//*
            case R.id.BTN___MAIN___TAKESHOT:

                Log.i(TAG, "TAKE SHOT requested.");

                // disabilita il pulsante
                findViewById(R.id.BTN___MAIN___TAKESHOT).setEnabled(false);

                // get an image from the camera
                mCamera.takePicture(null, null, takeShotCallBack);
                break;

            case R.id.BTN___MAIN___STARTSERVICE:

                startService(mainService);
                break;*//*

        }

    }
*/
    /*private Camera.PictureCallback takeShotCallBack = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
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

            // se necessario, pone nuovamente la camera in modalità preview
            try {

                startCameraPreview();

                // disabilita il pulsante
                findViewById(R.id.BTN___MAIN___TAKESHOT).setEnabled(true);

            } catch (Exception e) {

                Log.d(TAG, "Exception raised trying to restart the camera: " + e.getMessage());

            }

        }
    };*/

    /**
     * Create a file Uri for saving an image or video
     *//*
    private static Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    */

    /**
     * Create a File for saving an image or video
     *//*
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
    }*/

/*
    private Runnable stopRecorder = new Runnable() {

        @Override
        public void run() {

            mediaRecorder.stop();
            mCamera.lock();

            Log.i(TAG, "Stop");
            findViewById(R.id.BTN___MAIN___STARTCAPTURE).setEnabled(true);

        }
    };

    private void printLogInfo(String message) {

        Log.i(TAG, message);

    }*/
    private boolean checkPermissions() {

        boolean permissionFlag = true;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            // set the value of flag
            permissionFlag = permissionFlag && false;

            //ask for authorisation
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            // set the value of flag
            permissionFlag = permissionFlag && false;

            //ask for authorisation
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_WRITE_EXTERNAL_STORAGE);

        }


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            // set the value of flag
            permissionFlag = permissionFlag && false;

            //ask for authorisation
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_RECORD_AUDIO);

        }

        return permissionFlag;

    }

    private void updateUI() {

        // ottiene l'handler al pulsante per avviare/fermare il loop del video
        Button videoLoopButton = (Button) findViewById(R.id.BTN___MAIN___STARTVIDEOLOOP);

        if (MainService.isVideoLoopRunning) {

            // video loop is running
            videoLoopButton.setText(R.string.MainActivity_buttonStopVideoLoop);

        } else {

            // video loop is not running
            videoLoopButton.setText(R.string.MainActivity_buttonStartVideoLoop);
        }

    }

}
