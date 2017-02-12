package com.apps.lore_f.guardiano;

import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.lorenzofailla.utilities.Files;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by 105053228 on 10/feb/2017.
 */

class VideoLooper {

    private static final String TAG = "VideoLooper";

    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_IDLE = 0;

    int currentStatus;
    private OnVideoLooperStatusChangedListener onVideoLooperStatusChangedListener;

    private MediaRecorder mediaRecorder;

    long standardLoopDuration = 10000L;
    long standardKeepDuration = 30000L;
    long standardCleanInterval = 5*60000L;

    private Handler taskHandler;
    private File currentFileName;

    private boolean keepCurrentLoop;
    private List<File> cleaningList;

    interface OnVideoLooperStatusChangedListener {

        void onStatusChanged(int status);
        void onCreated();
        void loopKept(File resultFile);

    }

    void setOnVideoLooperStatusChangedListener(OnVideoLooperStatusChangedListener listener) {

        onVideoLooperStatusChangedListener = listener;
        onVideoLooperStatusChangedListener.onCreated();

    }

    private Camera camera;

    VideoLooper(Camera videoCamera) {

        camera = videoCamera;
        currentStatus = STATUS_IDLE;
        taskHandler = new Handler();
        cleaningList=new ArrayList<File>();

    }

    private boolean startRecorder(){

        currentFileName = Files.getOutputMediaFile(Files.MEDIA_TYPE_VIDEO);

        if (currentFileName != null) {

            // - avvia il loop di registrazione video
            // inizializza il MediaRecorder
            mediaRecorder = new MediaRecorder();

            // sblocca la camera
            camera.unlock();

            // configura il MediaRecorder
            configureMediaRecorder();

            mediaRecorder.setOutputFile(currentFileName.getAbsolutePath());

            try {

                keepCurrentLoop=false;
                mediaRecorder.prepare();
                mediaRecorder.start();

                Log.i(TAG, "Video loop started");
                taskHandler.postAtTime(callLoop, SystemClock.uptimeMillis() + standardLoopDuration);
                return true;

            } catch (IOException e) {

                Log.e(TAG, "Error preparing the MediaRecorder");
                return false;

            }

        } else {

            Log.e(TAG, "Error initializing video output file.");
            return false;

        }

    }

    void start() {

        if (startRecorder()) {

            taskHandler.postAtTime(removeUnusedFiles, SystemClock.uptimeMillis() + standardCleanInterval);
            changeCurrentStatus(STATUS_RUNNING);

        }

    }

    private void configureMediaRecorder() {

        mediaRecorder.setCamera(camera);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoEncodingBitRate(4356000);
        mediaRecorder.setAudioEncodingBitRate(128000);

    }

    private Runnable callLoop = new Runnable() {
        @Override
        public void run() {
            loop();
        }
    };


    private Runnable removeUnusedFiles = new Runnable() {
        @Override
        public void run() {

            new RemoveFiles().execute(cleaningList.toArray(new File[cleaningList.size()]));
            taskHandler.postAtTime(removeUnusedFiles, SystemClock.uptimeMillis() + standardCleanInterval);

        }

    };

    private void loop() {

        /* ferma il recorder */
        stopRecorder();

        /* se il loop non è da conservare, lo aggiunge alla lista dei file da rimuovere */
        if(!keepCurrentLoop){

            cleaningList.add(currentFileName);

        } else {

            onVideoLooperStatusChangedListener.loopKept(currentFileName);

        }

        /* riavvia il recorder */
        startRecorder();

    }

    void stop() {

        taskHandler.removeCallbacks(callLoop);
        taskHandler.removeCallbacks(removeUnusedFiles);

        stopRecorder();

        changeCurrentStatus(STATUS_IDLE);
        cleaningList.add(currentFileName);
        new RemoveFiles().execute(cleaningList.toArray(new File[cleaningList.size()]));

    }

    private void stopRecorder(){

        // ferma il mediarecorder e richiama il lock della camera
        mediaRecorder.stop();
        mediaRecorder.reset();
        mediaRecorder.release();

        camera.lock();

    }

    private void changeCurrentStatus(int newStatus) {

        if (currentStatus != newStatus) {

            currentStatus = newStatus;
            onVideoLooperStatusChangedListener.onStatusChanged(currentStatus);

        }

    }

    public void delayLoopAndKeepVideo(){

        keepCurrentLoop=true;
        taskHandler.removeCallbacks(callLoop);
        taskHandler.postAtTime(callLoop, SystemClock.uptimeMillis() + standardKeepDuration);

    }

    private class RemoveFiles extends AsyncTask <File, Void, Void>{

        @Override
        protected Void doInBackground(File... filesToBeDeleted) {

            for (File fileToBeDeleted : filesToBeDeleted) {

                fileToBeDeleted.delete();

            }

            return null;
        }

        @Override
        protected void onPostExecute(Void dummy){

        }

    }

}
