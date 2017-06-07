package tw.edu.ntu.csie.kurokuma.sync;

import android.app.Service;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.github.nkzawa.emitter.Emitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class TankActivity extends AppCompatActivity implements SensorEventListener{

    private TextView tv;
    private SensorManager sManager;
    Sensor accelerometer;
    Sensor magnetometer;
    float[] mGravity;
    float[] mGeomagnetic;
    String[] ZYXvalue = new String[3];
    Timer timer;
    Vibrator myVibrator;
    Boolean menu_state = true;
    ImageView gameover;
    ViewGroup container;

    SoundPool soundPool;
    SparseIntArray soundPoolMap;

    // confirm related
    boolean magic_match = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tank);

        Utils.full_screen_mode(getWindow().getDecorView());

        MenuActivity.mSocket.on("connectOK" + MenuActivity.magic, onRealConnect);
        MenuActivity.mSocket.on(MenuActivity.uuid, onRealConnect);

        soundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 100);
        soundPoolMap = new SparseIntArray();
        soundPoolMap.put(1, soundPool.load(this, R.raw.shoot, 1));
        soundPoolMap.put(2, soundPool.load(this, R.raw.hurt, 1));
        soundPoolMap.put(3, soundPool.load(this, R.raw.die, 1));
        soundPoolMap.put(4, soundPool.load(this, R.raw.bullet_sound, 1));
        soundPoolMap.put(5, soundPool.load(this, R.raw.lightning_sound, 1));

        View mContentView = findViewById(R.id.fullscreen_content);
        if( mContentView != null )
            mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playSound(1);
                attemptSend(view);
            }
        });

        container = (ViewGroup) findViewById(R.id.container);
        gameover = (ImageView) findViewById(R.id.gameover);

        myVibrator = (Vibrator) getApplication().getSystemService(Service.VIBRATOR_SERVICE);

        tv = (TextView) findViewById(R.id.sensorValue);

        sManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        timer = new Timer(true);
        timer.schedule(new MyTimerTask(), 80, 80);
    }

    // ================================================

    @Override
    protected void onResume()
    {
        super.onResume();
        sManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        
        /*if( MenuActivity.mSocket != null ) {
            ConnectandWaitforConfirm();
            if( MenuActivity.magic.length() > 0 )    {
                setRealConnectListener();
            }
        }*/
    }

    protected void onPause() {
        super.onPause();
        sManager.unregisterListener(this);
        if( timer != null ) {
            timer.cancel();
        }
        MenuActivity.mSocket.disconnect();
        //MenuActivity.mSocket.off("connectOK", onConnectOK);
        if( MenuActivity.magic.length() > 0 )
            MenuActivity.mSocket.off("connectOK"+MenuActivity.magic, onRealConnect);

    }

    @Override
    protected void onStop()
    {
        sManager.unregisterListener(this);
        super.onStop();

        if( MenuActivity.mSocket != null )   {
            if( MenuActivity.mSocket.connected() )   {
                MenuActivity.mSocket.disconnect();
            }
            //MenuActivity.mSocket.off("connectOK", onConnectOK);
            if( MenuActivity.magic.length() > 0 )
                MenuActivity.mSocket.off("connectOK"+MenuActivity.magic, onRealConnect);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1)
    {
        //Do nothing.
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                ZYXvalue[0] = Float.toString((float) Math.toDegrees(orientation[0]));
                ZYXvalue[1] = Float.toString((float) Math.toDegrees(orientation[1]));
                ZYXvalue[2] = Float.toString((float) Math.toDegrees(orientation[2]));
            }
        }

        //tv.setText("Orientation X (Roll) :" + ZYXvalue[2] + "\n" +
        //        "Orientation Y (Pitch) :" + ZYXvalue[1] + "\n" +
        //        "Orientation Z (Yaw) :" + ZYXvalue[0]);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void attemptSend(View v) {
        String message = "fire";
        if(menu_state){
            message = "start";
            menu_state = false;
        }
        //mSocket.emit("message", message);
        switch (MenuActivity.player) {
            case 1:
                MenuActivity.mSocket.emit("message1"+MenuActivity.magic, message);
                break;
            case 2:
                MenuActivity.mSocket.emit("message2"+MenuActivity.magic, message);
                break;
        }
    }

    private Emitter.Listener onRealConnect = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String message = (String) args[0];

                    if (message.equals("checkConnect")) {
                        MenuActivity.mSocket.emit("stillConnect" + MenuActivity.magic, MenuActivity.uuid);
                    } else if (message.equals("hit")) {
                        myVibrator.vibrate(300);
                        playSound(2);
                    } else if (message.equals("die")) {
                        myVibrator.vibrate(1000);
                        playSound(3);
                        menu_state = true;

                        gameover.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                gameover.setOnClickListener(null);
                                gameover.setVisibility(View.GONE);
                            }
                        });
                        gameover.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
    };

    public void playSound(int num){
        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        float curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        float maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float leftVolume = curVolume/maxVolume;
        float rightVolume = curVolume/maxVolume;
        int priority = 1;
        int no_loop = 0;
        float normal_playback_rate = 1f;
        soundPool.play(num, leftVolume, rightVolume, priority, no_loop, normal_playback_rate);
    }



    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Utils.full_screen_mode(getWindow().getDecorView());
    }

    public class MyTimerTask extends TimerTask
    {
        public void run()
        {
            switch (MenuActivity.player) {
                case 1:
                    MenuActivity.mSocket.emit("X1"+MenuActivity.magic, ZYXvalue[2]);
                    MenuActivity.mSocket.emit("Y1"+MenuActivity.magic, ZYXvalue[1]);
                    break;
                case 2:
                    MenuActivity.mSocket.emit("X2"+MenuActivity.magic, ZYXvalue[2]);
                    MenuActivity.mSocket.emit("Y2"+MenuActivity.magic, ZYXvalue[1]);
                    break;
            }
            //mSocket.emit("X", ZYXvalue[2]);
            //mSocket.emit("Y", ZYXvalue[1]);
            //mSocket.emit("Z", ZYXvalue[0]);
        }
    }
}
