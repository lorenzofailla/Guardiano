package com.apps.lore_f.guardiano;

import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.lorenzofailla.utilities.Files;

import java.io.File;
import java.io.IOException;

/**
 * Created by 105053228 on 10/feb/2017.
 */

public class VideoLooper {

    private static final String TAG = "VideoLooper";

    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_IDLE = 0;

    private int currentStatus;
    private OnStatusChangedListener onStatusChangedListener;

    private MediaRecorder mediaRecorder;

    private static long standardLoopDuration = 10000L;

    private Handler taskHandler;
    private File currentFileName;

    public interface OnStatusChangedListener{

        public void onStatusChanged(int status);

    }

    public void setOnStatusChangedListener(OnStatusChangedListener listener){

        onStatusChangedListener=listener;

    }

    private Camera camera;

    public VideoLooper(Camera videoCamera){

        camera = videoCamera;
        currentStatus=STATUS_IDLE;
        taskHandler=new Handler();

    }

    public void start(){

        currentFileName = Files.getOutputMediaFile(Files.MEDIA_TYPE_VIDEO);

        if(currentFileName!=null) {

            // - avvia il loop di registrazione video
            // inizializza il MediaRecorder
            mediaRecorder = new MediaRecorder();

            // sblocca la camera
            camera.unlock();

            // configura il MediaRecorder
            mediaRecorder.setCamera(camera);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setVideoEncodingBitRate(4356000);
            mediaRecorder.setAudioEncodingBitRate(128000);
        /*mediaRecorder.setVideoSize(videoFrameWidth, videoFrameHeight);*/
            mediaRecorder.setOutputFile(currentFileName.getAbsolutePath());
        /*mediaRecorder.setPreviewDisplay(cameraPreviewHolder.getSurface());*/

            try {

                mediaRecorder.prepare();
                mediaRecorder.start();

                Log.i(TAG, "Video loop started");

                changeCurrentStatus(STATUS_RUNNING);

            } catch (IOException e) {

                Log.e(TAG, "Error preparing the MediaRecorder");

            }

        /*
        // richiede il blocco video
        taskHandler.postAtTime(stopRecorder, SystemClock.uptimeMillis() + standardLoopDuration);
        */

        } else {

            Log.e(TAG, "Error initializing video output file.");

        }

    }

    private Runnable callLoop=new Runnable() {
        @Override
        public void run() {
            loop();
        }
    };

    public void loop(){

        // rinnova il file dove salvare la sequenza video
        currentFileName = Files.getOutputMediaFile(Files.MEDIA_TYPE_VIDEO);

        if (currentFileName!=null) {
            mediaRecorder.stop();
            mediaRecorder.setOutputFile(currentFileName.getAbsolutePath());

            try {

                mediaRecorder.prepare();
                mediaRecorder.start();

                Log.i(TAG, "Video loop sequenced");

            } catch (IOException e) {

                Log.e(TAG, "Error preparing the MediaRecorder");
                stop();
            }

        } else {

            Log.e(TAG, "Error initializing video output file.");
            stop();

        }

    }

    public void stop(){

        // ferma il mediarecorder e richiama il lock della camera
        mediaRecorder.stop();
        camera.lock();

        changeCurrentStatus(STATUS_IDLE);

    }

    private void changeCurrentStatus(int newStatus){

        if(currentStatus!=newStatus){

            currentStatus=newStatus;
            onStatusChangedListener.onStatusChanged(currentStatus);

        }

    }

}
