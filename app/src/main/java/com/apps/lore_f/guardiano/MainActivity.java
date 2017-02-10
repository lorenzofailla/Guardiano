package com.apps.lore_f.guardiano;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.apps.lore_f.guardiano.MainService.firebaseAuth;
import static com.apps.lore_f.guardiano.MainService.firebaseUser;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, GoogleApiClient.OnConnectionFailedListener {

    Intent mainService;

    private SurfaceView cameraPreview;
    private SurfaceHolder cameraPreviewHolder;

    private final static String TAG = "_MainActivity";

    private GoogleApiClient googleApiClient;

    SharedPreferences sharedPreferences;

    MainService.RequestListener requestListener = new MainService.RequestListener() {
        @Override
        public void newEvent(String eventName) {

            switch (eventName) {

                case "CAMERACONTROL___REQUEST_UI_UPDATE":

                    updateUI();

                    break;

                case "CAMERACONTROL___EVENT_CAMERA_STARTED":

                    assignCameraPreviewSurface();

                    break;

                case "CAMERACONTROL___SHOT_TAKEN":

                    Button buttonTakeShot = (Button) findViewById(R.id.BTN___MAIN___TAKESHOT);
                    buttonTakeShot.setEnabled(true);

                    break;

                case "CAMERACONTROL___MOTION_LEVEL_CHANGED":

                    TextView motionValueTextView = (TextView) findViewById(R.id.TXV___MAIN___MOTIONLEVEL);
                    motionValueTextView.setText(String.format("%.3f", MainService.motionDetection.currentMotionValue));

                    if (MainService.motionLevel > MainService.motionLevelThreshold) {

                        motionValueTextView.setTextColor(Color.RED);

                    } else {

                        motionValueTextView.setTextColor(Color.BLACK);

                    }
                    break;

            }

        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.sign_out_menuEntry:

                stopService(mainService);

                // è stato selezionata l'opzione di sign out dal menu
                firebaseAuth.signOut();
                Auth.GoogleSignInApi.signOut(googleApiClient);

                //mUsername=ANONYMOUS;
                startActivity(new Intent(this, SignInActivity.class));
                return true;

            case R.id.quit_app_menuEntry:

                // è stato selezionata l'opzione di sign out dal menu
                stopService(mainService);
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*
        controlla che i permessi siano stati dati.
        in assenza dei permessi, lancia l'activity per richiedere i permessi
        altrimenti inizializza i controlli
         */

        // ottiene un'istanza per le SharedPreferences
        sharedPreferences = getSharedPreferences(getString(R.string.preferenceFileKey), Context.MODE_PRIVATE);

        // carica le impostazioni memorizzate nelle SharedPreferences
        loadPreferences();

        if (!SharedFunctions.checkPermissions(this) || (MainService.deviceDescription == "") || !MainService.isOpenCVLibraryLoaded) {

            //
            startActivity(new Intent(this, PermissionsRequestActivity.class));
            finish();
            return;

        }
        // inizializza il FirebaseAuth
        firebaseAuth = FirebaseAuth.getInstance();

        // ottiene l'user corrente
        firebaseUser = firebaseAuth.getCurrentUser();

        if (firebaseUser == null) {
            // autenticazione non effettuata

            // lancia la SignInActivity e termina l'attività corrente
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;

        }

        TextView userNameTextView = (TextView) findViewById(R.id.TXV___MAIN___USERNAME);
        TextView deviceNameTextView = (TextView) findViewById(R.id.TXV___MAIN___DEVICENAME);

        userNameTextView.setText(firebaseAuth.getCurrentUser().getEmail());
        deviceNameTextView.setText(MainService.deviceDescription);

        googleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .build();

        FirebaseInstanceId firebaseInstanceId = FirebaseInstanceId.getInstance();
        Log.d(TAG, firebaseInstanceId.getToken());

        // inizializzo handlers ai drawable
        Button takeShotButton = (Button) findViewById(R.id.BTN___MAIN___TAKESHOT);
        takeShotButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        // il loop è avviato, manda la richiesta per fermare
                        v.setEnabled(false);
                        MainService.takeShot();

                    }
                }

        );

        // inizializzo handlers ai drawable
        Button videoLoopButton = (Button) findViewById(R.id.BTN___MAIN___STARTVIDEOLOOP);

        videoLoopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (MainService.videoLooper.currentStatus == VideoLooper.STATUS_IDLE) {

                    MainService.videoLooper.start();

                } else {

                    MainService.videoLooper.stop();

                }

            }

        });

        MainService.setRequestListener(requestListener);

    }

    private void loadPreferences() {

        // ottiene le preferenze
        MainService.deviceDescription = sharedPreferences.getString(getString(R.string.preference___DeviceDescriptionKey), "");

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainactivity_menu, menu);

        return true;

    }

    @Override
    protected void onResume() {

        super.onResume();
        mainService = new Intent(this, MainService.class);

        // avvio il servizio
        if (!MainService.amIRunning) {

            startService(mainService);

        } else {

            assignCameraPreviewSurface();

        }

        // aggiorna l'interfaccia utente
        updateUI();

    }

    @Override
    protected void onPause() {

        super.onPause();

    }

    private void assignCameraPreviewSurface() {

        if (MainService.mainCamera != null) {

            // ottiene l'handler alla SurfaceView
            cameraPreview = (SurfaceView) findViewById(R.id.SFV___MAIN___CAMERA_PREVIEW);

            // inizializzazione dell'holder
            cameraPreviewHolder = cameraPreview.getHolder();
            cameraPreviewHolder.addCallback(this);
            cameraPreviewHolder.setFormat(SurfaceHolder.SURFACE_TYPE_HARDWARE);

            // - ridimensiona l'holder per occupare non più di metà schermo
            // ottiene le dimensioni del display
            int width = getWindowManager().getDefaultDisplay().getWidth();

            // ottiene l'indice di rotazione del display
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            Log.i(TAG, String.format("Display rotation = %d", rotation));

            // calcola la rotazione in gradi del display
            int displayOrientationDegrees = 0;

            switch (rotation) {

                case Surface.ROTATION_0:
                    displayOrientationDegrees = 90;
                    break;

                case Surface.ROTATION_90:
                    displayOrientationDegrees = 0;
                    break;

                case Surface.ROTATION_180:
                    displayOrientationDegrees = 270;
                    break;

                case Surface.ROTATION_270:
                    displayOrientationDegrees = 180;
                    break;

            }

            // imposta l'orientamento del display della preview
            MainService.mainCamera.setDisplayOrientation(displayOrientationDegrees);

            // calcola il rapporto d'aspetto del fram del preview
            float cameraFrameRatio = (float) (MainService.mainCamera.getParameters().getPreviewSize().width) / (float) (MainService.mainCamera.getParameters().getPreviewSize().height);
            Log.i(TAG, String.format("frame ratio = %.4f", cameraFrameRatio));

            // ridimensiona l'holder per tenere il rapporto d'aspetto e occupare meno di metà schermo
            cameraPreviewHolder.setFixedSize(width / 2, (int) (width / 2.0 * cameraFrameRatio));

        }

    }


    private void updateUI() {

        // ottiene l'handler al pulsante per avviare/fermare il loop del video
        Button videoLoopButton = (Button) findViewById(R.id.BTN___MAIN___STARTVIDEOLOOP);

        if (MainService.isVideoLoopRunning) {

            // video loop is running
            videoLoopButton.setText(R.string.MainActivity_buttonStopVideoLoop);

        } else {

            // video loop is not running
            videoLoopButton.setText(R.string.MainActivity_buttonStartVideoLoop);

        }

        updateStatusTextView();

    }

    private void updateStatusTextView() {

        TextView statusTextView = (TextView) findViewById(R.id.TXV___MAIN___STATUS);
        String textViewMessage;

        if (MainService.isVideoLoopRunning) {

            textViewMessage = getString(R.string.MainActivity_videoLoopRunning);

        } else {

            textViewMessage = getString(R.string.MainActivity_videoLoopPaused);

        }

        statusTextView.setText(textViewMessage);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        Log.i(TAG, "Running callback: surfaceCreated");

        if (MainService.mainCamera != null) {

            try {

                MainService.mainCamera.setPreviewDisplay(holder);

            } catch (IOException e) {

                e.printStackTrace();

            }

        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        Log.i(TAG, "Running callback: surfaceChanged");

        if (MainService.mainCamera != null) {

            try {

                MainService.mainCamera.setPreviewDisplay(holder);

            } catch (IOException e) {

                e.printStackTrace();

            }

        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }

}
