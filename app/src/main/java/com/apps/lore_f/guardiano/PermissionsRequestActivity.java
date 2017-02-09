package com.apps.lore_f.guardiano;

import android.*;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

public class PermissionsRequestActivity extends AppCompatActivity {

    private static final int PERMISSION_RECORD_AUDIO = 10;
    private static final int PERMISSION_CAMERA = 20;
    private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 30;
    private static final int PERMISSION_INTERNET = 40;

    private static final String TAG = "->PermissionsRequest";

    EditText deviceNameEditText;
    ImageButton confirmDeviceNameChangeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions_request);

        // inizializza la libreria OpenCV
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_11, this, openCVLoaderCallback);

        deviceNameEditText = (EditText) findViewById(R.id.ETX___PERMISSIONSREQUEST___DEVICE_NAME);

        confirmDeviceNameChangeButton = (ImageButton) findViewById(R.id.IBN___PERMISSIONSREQUEST___DEVICE_NAME_CONFIRM);
        confirmDeviceNameChangeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (deviceNameEditText.getText().toString() != "") {

                    MainService.deviceDescription = deviceNameEditText.getText().toString();
                    updateSharedPreferences();
                    updateUI();

                }

            }
        });

        updateUI();

    }

    private void updateSharedPreferences() {

        SharedPreferences sharedPreferences = getSharedPreferences(getString(R.string.preferenceFileKey), Context.MODE_PRIVATE);
        SharedPreferences.Editor sharedPreferencesEditor = sharedPreferences.edit()
                .putString(getString(R.string.preference___DeviceDescriptionKey), MainService.deviceDescription);
        sharedPreferencesEditor.commit();


    }

    private BaseLoaderCallback openCVLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:

                    Log.i(TAG, "OpenCV loaded successfully");
                    MainService.isOpenCVLibraryLoaded = true;
                    updateUI();
                    break;

                default:

                    super.onManagerConnected(status);
                    break;
            }

        }

    };

    private void updateUI() {

        if (SharedFunctions.checkPermissions(this) && MainService.deviceDescription != "" && MainService.isOpenCVLibraryLoaded) {

            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;

        } else {

            askForMissingPermissions();

            if (MainService.deviceDescription == "") {

                deviceNameEditText.setText("device description");
                deviceNameEditText.selectAll();
                deviceNameEditText.setEnabled(true);
                confirmDeviceNameChangeButton.setEnabled(true);

            } else {

                deviceNameEditText.setText(MainService.deviceDescription);
                deviceNameEditText.setEnabled(false);
                confirmDeviceNameChangeButton.setEnabled(false);

            }

            TextView openCVLibraryStatus = (TextView) findViewById(R.id.TXV___PERMISSIONSREQUEST___LIBRARY_OPENCV_STATUS);
            if (MainService.isOpenCVLibraryLoaded) {

                openCVLibraryStatus.setText(R.string.PermissionsRequestActivity_libraryLoaded);

            } else {

                openCVLibraryStatus.setText(R.string.PermissionsRequestActivity_libraryNotLoaded);

            }

        }


    }

    private void askForMissingPermissions() {

        TextView txvPermissionCameraStatus = (TextView) findViewById(R.id.TXV___PERMISSIONSREQUEST___PERMISSION_CAMERA_STATUS);
        TextView txvPermissionRecordAudioStatus = (TextView) findViewById(R.id.TXV___PERMISSIONSREQUEST___PERMISSION_RECORD_AUDIO_STATUS);
        TextView txvPermissionWriteExternalStorageStatus = (TextView) findViewById(R.id.TXV___PERMISSIONSREQUEST___PERMISSION_WRITE_EXTERNAL_STORAGE_STATUS);
        TextView txvPermissionInternetStatus = (TextView) findViewById(R.id.TXV___PERMISSIONSREQUEST___PERMISSION_INTERNET_STATUS);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, PERMISSION_CAMERA);
            txvPermissionCameraStatus.setText(R.string.PermissionsRequestActivity_permissionDenied);

        } else {

            txvPermissionCameraStatus.setText(R.string.PermissionsRequestActivity_permissionGranted);

        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_WRITE_EXTERNAL_STORAGE);
            txvPermissionInternetStatus.setText(R.string.PermissionsRequestActivity_permissionGranted);

        } else {

            txvPermissionWriteExternalStorageStatus.setText(R.string.PermissionsRequestActivity_permissionDenied);

        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, PERMISSION_RECORD_AUDIO);
            txvPermissionInternetStatus.setText(R.string.PermissionsRequestActivity_permissionDenied);

        } else {

            txvPermissionRecordAudioStatus.setText(R.string.PermissionsRequestActivity_permissionGranted);

        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.INTERNET}, PERMISSION_INTERNET);
            txvPermissionInternetStatus.setText(R.string.PermissionsRequestActivity_permissionDenied);

        } else {

            txvPermissionInternetStatus.setText(R.string.PermissionsRequestActivity_permissionGranted);

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {

        updateUI();

    }


}
