package com.example.lore_f.cameracontrol;

import android.content.Context;
import android.graphics.Canvas;
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
    //
    private SurfaceHolder cameraPreviewHolder;
    private Camera assignedCamera;

    private static final String TAG = "_CameraPreview";

    CameraPreview (Context context){

        super(context);

        initializeHolder();

    }

    CameraPreview (Context context, AttributeSet attrs){

        super(context, attrs);

        initializeHolder();

    }

    CameraPreview (Context context, AttributeSet attrs, int defStyleAttr){

        super(context, attrs, defStyleAttr );

        initializeHolder();

    }

    private void initializeHolder(){

        cameraPreviewHolder = this.getHolder();
        cameraPreviewHolder.addCallback(this);
        cameraPreviewHolder.setFormat(SurfaceHolder.SURFACE_TYPE_HARDWARE);

    }


    public void setAssignedCamera(Camera camera){

        if (camera != null){

            assignedCamera=camera;

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

                Log.e(TAG, "Error assigning surface holder to camera preview");

            }

        }

    }

    @Override
    public void onDraw(Canvas canvas){

        super.onDraw(canvas);

    }


    @Override
    protected void onMeasure(int w, int h){

        super.onMeasure(w, h);

        w=MeasureSpec.getSize(w);
        h=MeasureSpec.getSize(h);
        setMeasuredDimension(w, h);
    }

    public void stopPreview(){

        if (assignedCamera != null) {

            assignedCamera.stopPreview();
            assignedCamera.release();
            assignedCamera=null;

        }

    }

}
