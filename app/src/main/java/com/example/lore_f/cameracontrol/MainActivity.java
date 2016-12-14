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

public class MainActivity extends AppCompatActivity {

    Camera mCamera;
    CameraPreview cameraPreview;


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

            // imposta la SurfaceView 'camera_preview' alle stesse dimensioni della camera
            Camera.Size cameraPreviewSize = cameraParameters.getPreviewSize();
            Log.i(getString(R.string.app_name), String.format("Preview size: %d x %d", cameraPreviewSize.width, cameraPreviewSize.height));


            startCameraPreview();

        }

    }

    @Override
    protected void onPause() {

        super.onPause();

        cameraPreview.stopPreview();

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

            // definisce una nuova istanza della classe CameraPreview
            cameraPreview= new CameraPreview(this.getApplicationContext());
            cameraPreview.setAssignedCamera(mCamera);
            cameraPreview.startPreview();

        }

    }

}
