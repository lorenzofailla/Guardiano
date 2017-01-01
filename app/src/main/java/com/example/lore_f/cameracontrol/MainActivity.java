package com.example.lore_f.cameracontrol;

import android.Manifest;
import android.app.ActivityManager;
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
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    Intent mainService;

    private LocalBroadcastManager broadcastManager;

    private SurfaceView cameraPreview;
    private SurfaceHolder cameraPreviewHolder;

    private final static String TAG = "_MainActivity";
    private final static String MAIN_SERVICE_NAME = "com.example.lore_f.cameracontrol.MainService";

    private static final int PERMISSION_RECORD_AUDIO = 1;
    private static final int PERMISSION_CAMERA = 2;
    private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 3;

/*    private int videoFrameHeight;
    private int videoFrameWidth;*/

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {

            Log.i(TAG, "Received intent: "+intent.getAction());

            switch (intent.getAction()) {

                case "CAMERACONTROL___REQUEST_UI_UPDATE":

                    updateUI();

                    break;

                case "CAMERACONTROL___EVENT_CAMERA_STARTED":


                    assignCameraPreviewSurface();

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

        if (checkPermissions()) {

            // i permessi necessari sono stati ottenuti, è possibile avviare l'attività

            // avvio il servizio
            mainService = new Intent(this, MainService.class);
            if (!isMainServiceRunning()) {

                startService(mainService);

            } else {

                assignCameraPreviewSurface();

            }


            // inzializzo i filtri per l'ascolto degli intent
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("CAMERACONTROL___REQUEST_UI_UPDATE");
            intentFilter.addAction("CAMERACONTROL___EVENT_CAMERA_STARTED");

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

                           // il loop è avviato, manda la richiesta per fermare
                            MainService.setVideoLoopActivity(!MainService.isVideoLoopRunning);


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

        if(!(MainService.isVideoLoopRunning)){

            stopService(mainService);

        }

        broadcastManager.unregisterReceiver(broadcastReceiver);

    }

    private void assignCameraPreviewSurface() {

        if (MainService.mainCamera != null) {

            // ottiene l'handler alla SurfaceView
            cameraPreview = (SurfaceView) findViewById(R.id.SFV___MAIN___CAMERA_PREVIEW);

            // inizializzazione dell'holder
            cameraPreviewHolder = cameraPreview.getHolder();
            cameraPreviewHolder.addCallback(this);
            cameraPreviewHolder.setFormat(SurfaceHolder.SURFACE_TYPE_HARDWARE);

            // - ridimensiona l'holder per occupare non più di metà schermo
            // ottiene le dimensioni del display
            int width = getWindowManager().getDefaultDisplay().getWidth();
            // ottiene l'indice di rotazione del display
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            Log.i(TAG, String.format("Display rotation = %d", rotation));

            // calcola la rotazione in gradi del display
            int displayOrientationDegrees=0;
            switch(rotation){
                case Surface.ROTATION_0:
                    displayOrientationDegrees=90;
                    break;
                case Surface.ROTATION_90:
                    displayOrientationDegrees=0;
                    break;
                case Surface.ROTATION_180:
                    displayOrientationDegrees=270;
                    break;
                case Surface.ROTATION_270:
                    displayOrientationDegrees=180;
                    break;
            }

            // imposta l'orientamento del display della preview
            MainService.mainCamera.setDisplayOrientation(displayOrientationDegrees);

            // calcola il rapporto d'aspetto del fram del preview
            float cameraFrameRatio = (float) (MainService.mainCamera.getParameters().getPreviewSize().width) / (float) (MainService.mainCamera.getParameters().getPreviewSize().height);
            Log.i(TAG, String.format("frame ratio = %.4f", cameraFrameRatio));

            // ridimensiona l'holder per tenere il rapporto d'aspetto e occupare meno di metà schermo
            cameraPreviewHolder.setFixedSize(width / 2, (int) (width / 2.0 * cameraFrameRatio));

        }
    }

    /*@Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.BTN___MAIN___STARTVIDEOLOOP:
*//*

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



/*


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

        updateStatusTextView();

    }

    private void updateStatusTextView(){

        TextView statusTextView = (TextView) findViewById(R.id.TXV___MAIN___STATUS);
        String textViewMessage;

        if(MainService.isVideoLoopRunning){

            textViewMessage=getString(R.string.MainActivity_videoLoopRunning);

        } else {

            textViewMessage=getString(R.string.MainActivity_videoLoopPaused);

        }

        statusTextView.setText(textViewMessage);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        Log.i(TAG, "Running callback: surfaceCreated");

         if(MainService.mainCamera!=null) {

             try {

                 MainService.mainCamera.setPreviewDisplay(holder);

             } catch (IOException e) {

                 e.printStackTrace();

             }

         }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        Log.i(TAG, "Running callback: surfaceChanged");

        if(MainService.mainCamera!=null) {

            try {

                MainService.mainCamera.setPreviewDisplay(holder);

            } catch (IOException e) {

                e.printStackTrace();

            }

        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    private boolean isMainServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (MAIN_SERVICE_NAME.equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

}
