package com.apps.lore_f.guardiano;

import android.os.AsyncTask;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

/**
 * Created by 105053228 on 08/feb/2017.
 */

public class MotionDetection  {

    private static final String TAG = "MotionDetection";

    public boolean isBusy;

    private int previewFrameHeight;
    private int previewFrameWidth;
    private double motionLevelBase;
    private byte[] previousFrame;
    private byte[] currentFrame;

    private MotionValueListener motionValueListener;

    public interface MotionValueListener {

        void onMotionValueChanged(double motionValue);

    }

    public void setMotionValueChanged(MotionValueListener listener) {

        motionValueListener = listener;

    }

    public void enterFrame(byte[] frameData){

        if (currentFrame != null) {

            previousFrame = currentFrame;

        }

        currentFrame = frameData;

        if (currentFrame != null && previousFrame != null) {

            if (!isBusy) {

                new FrameMotionDetector().execute();

            } else {

                Log.d(TAG, "motion computing skipped due to workload");

            }

        }

    }

    public MotionDetection(int frameWidth, int frameHeight){

        previewFrameWidth=frameWidth;
        previewFrameHeight=frameHeight;
        motionLevelBase=1.0*frameWidth*frameHeight;
        isBusy=false;

    }

    private class FrameMotionDetector extends AsyncTask<Void, Void, Double> {

        protected Double doInBackground(Void... dummy) {

            isBusy = true;
            return computeMotionLevel(currentFrame, previousFrame);


        }

        protected void onPostExecute(Double motionValue) {

            isBusy = false;
            motionValueListener.onMotionValueChanged(motionValue);

        }

    }

    private double computeMotionLevel(byte[] frame1, byte[] frame2) {

        Mat previousFrameMatrix = convertToMat(frame1);
        Mat currentFrameMatrix = convertToMat(frame2);

        Mat frameSubtractionMatrix = new Mat(previewFrameHeight, previewFrameWidth, CvType.CV_8UC4);

        Core.absdiff(previousFrameMatrix, currentFrameMatrix, frameSubtractionMatrix);

        return (1.0 * Core.sumElems(frameSubtractionMatrix).val[0]/motionLevelBase);

    }

    private Mat convertToMat(byte[] yuvData) {

        Mat supportMat = new Mat(previewFrameWidth, previewFrameHeight, CvType.CV_8UC4);
        Mat grayMat = new Mat(previewFrameWidth, previewFrameHeight, CvType.CV_8UC4);
        supportMat.put(0,0, yuvData);
        Imgproc.cvtColor(supportMat, grayMat, Imgproc.COLOR_BGR2GRAY);

        return grayMat;

    }

}
