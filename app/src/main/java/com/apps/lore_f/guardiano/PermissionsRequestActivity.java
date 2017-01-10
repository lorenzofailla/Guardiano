package com.apps.lore_f.guardiano;

import android.*;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class PermissionsRequestActivity extends AppCompatActivity {

    private static final int PERMISSION_RECORD_AUDIO = 10;
    private static final int PERMISSION_CAMERA = 20;
    private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 30;
    private static final int PERMISSION_INTERNET=40;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions_request);

        updateUI();

    }

    private void updateUI(){

        if(SharedFunctions.checkPermissions(this)){

            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;

        } else {

            askForMissingPermissions();

        }

    }

    private void askForMissingPermissions(){

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

        /*
        switch (requestCode) {
            case PERMISSION_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            case PERMISSION_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            case PERMISSION_INTERNET: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            case PERMISSION_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
        */
    }


}
