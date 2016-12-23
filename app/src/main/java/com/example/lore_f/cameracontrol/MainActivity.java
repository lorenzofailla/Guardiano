package com.example.lore_f.cameracontrol;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
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

// TODO: 18/12/2016 forzare il layout in modalità orizzontale
// TODO: 18/12/2016 richiedere il preview della camera dopo il take shot

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnClickListener {

    Camera mCamera;
    SurfaceView cameraPreview;
    SurfaceHolder cameraPreviewHolder;
    MediaRecorder mediaRecorder;
    Handler taskHandler;

    final static String TAG = "CameraControl";

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            //ask for authorisation
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 50);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //ask for authorisation
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 50);
        }

        // inizializza il task handler
        taskHandler = new Handler();

        // inizializza handlers ai drawable
        final Button btnStartCapture = (Button) findViewById(R.id.BTN___MAIN___STARTCAPTURE);
        final Button btnTakeShot = (Button) findViewById(R.id.BTN___MAIN___TAKESHOT);

        // assegna i drawable ai listener
        btnStartCapture.setOnClickListener(this);
        btnTakeShot.setOnClickListener(this);

        //start your camera
        if (safeCameraOpen()) {

            assignCameraPreviewSurface();
            startCameraPreview();

        }

    }

    private void startCameraPreview() {

        // richiesta camera in modalità preview
        mCamera.startPreview();
    }

    @Override
    protected void onPause() {

        super.onPause();

        mCamera.stopPreview();

    }

    private boolean safeCameraOpen() {

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

    }

    private void assignCameraPreviewSurface() {

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

            Camera.Parameters cameraParameters = mCamera.getParameters();
            cameraPreviewHolder.setFixedSize(cameraParameters.getPreviewSize().width, cameraParameters.getPreviewSize().height);


        }

    }

    private void stopCameraPreview() {

        if (mCamera != null) {

            mCamera.stopPreview();

        }

    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

        assignCameraPreviewSurface();
        startCameraPreview();

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        assignCameraPreviewSurface();
        startCameraPreview();

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

        stopCameraPreview();

    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.BTN___MAIN___STARTCAPTURE:

                // inizializza il MediaRecorder
                mediaRecorder = new MediaRecorder();

                // sblocca la camera
                mCamera.unlock();

                // configura il MediaRecorder
                mediaRecorder.setCamera(mCamera);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP);
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
                taskHandler.postAtTime(stopRecorder, SystemClock.uptimeMillis() + 5000);

                break;

        }

        switch (v.getId()) {
            case R.id.BTN___MAIN___TAKESHOT:

                Log.i(TAG, "TAKE SHOT requested.");

                // get an image from the camera
                mCamera.takePicture(null, null, takeShotCallBack);
                break;

        }

    }

    private Camera.PictureCallback takeShotCallBack = new Camera.PictureCallback() {

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

            } catch (Exception e) {

                Log.d(TAG, "Exception raised trying to restart the camera: " + e.getMessage());

            }

        }
    };

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

    private Runnable stopRecorder = new Runnable() {

        @Override
        public void run() {

            mediaRecorder.stop();
            mCamera.lock();

            Log.i(TAG, "Stop");
            findViewById(R.id.BTN___MAIN___STARTCAPTURE).setEnabled(true);

        }
    };

}
