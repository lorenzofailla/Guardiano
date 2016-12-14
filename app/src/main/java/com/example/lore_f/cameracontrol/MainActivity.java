package com.example.lore_f.cameracontrol;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    Camera mCamera;
    SurfaceView cameraPreview;
    SurfaceHolder cameraPreviewHolder;

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

        //start your camera
        if(safeCameraOpen()){

            // ottiene i parametri della camera

            Camera.Parameters cameraParameters = mCamera.getParameters();
            List<Camera.Size> cameraSupportedPreviewSizes = cameraParameters.getSupportedPreviewSizes();

            Camera.Size cameraPreviewSize = cameraParameters.getPreviewSize();
            Log.i(getString(R.string.app_name), String.format("Preview size: %d x %d", cameraPreviewSize.width, cameraPreviewSize.height));

            String previewSizesMessage = String.format("Supported preview sizes\n");


            for (int i = 0; i < cameraSupportedPreviewSizes.size(); i++) {

                previewSizesMessage += String.format("%d. [%dx%d]\n", i + 1, cameraSupportedPreviewSizes.get(i).width, cameraSupportedPreviewSizes.get(i).height);

            }

            Log.i(getString(R.string.app_name), previewSizesMessage);

            cameraParameters.setPreviewSize(cameraSupportedPreviewSizes.get(23).width, cameraSupportedPreviewSizes.get(23).height);
            startCameraPreview();

        }

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

    private void startCameraPreview(){

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

            // richiesta camera in modalità preview
            mCamera.startPreview();
        }

    }

    private void stopCameraPreview() {

        if (mCamera != null) {

            mCamera.stopPreview();

        }

    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

        startCameraPreview();

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        startCameraPreview();

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

        stopCameraPreview();

    }

}
