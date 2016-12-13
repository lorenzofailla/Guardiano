package com.example.lore_f.cameracontrol;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/**
 * Created by 105053228 on 13/dic/2016.
 */

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private SurfaceHolder cameraPreviewHolder;
    private Camera assignedCamera;

    CameraPreview (Context context, Camera camera){

        super(context);

        initializeHolder(camera);

    }

    CameraPreview (Context context, AttributeSet attrs, Camera camera){

        super(context, attrs);

        initializeHolder(camera);

    }

    CameraPreview (Context context, AttributeSet attrs, int defStyleAttr, Camera camera){

        super(context, attrs, defStyleAttr );

        initializeHolder(camera);

    }

    private void initializeHolder(Camera camera){

        cameraPreviewHolder = this.getHolder();
        cameraPreviewHolder.addCallback(this);
        cameraPreviewHolder.setFormat(SurfaceHolder.SURFACE_TYPE_HARDWARE);

        if (camera != null) {

            assignedCamera = camera;

        }

    }


    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

        startPreview();

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        startPreview();

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

        stopPreview();

    }

    public void startPreview(){

        if (assignedCamera != null) {

            try{

                assignedCamera.setPreviewDisplay(cameraPreviewHolder);

            } catch (IOException e) {

                Log.e(getResources().getString(R.string.app_name), getResources().getString(R.string.ERR_surface_assignment_for_preview));

            }

        }

    }

    public void stopPreview(){

        if (assignedCamera != null) {

            assignedCamera.stopPreview();
            assignedCamera.release();
            assignedCamera=null;

        }


    }

}
