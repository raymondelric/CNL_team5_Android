package tw.edu.ntu.csie.kurokuma.sync;

import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
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
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity implements SensorEventListener, ViewSwitcher.ViewFactory, DrawerLayout.DrawerListener{


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

    // about shining Bomb button
    ImageSwitcher imageSwitcher;
    int[] images = new int[]{ R.mipmap.bomb_botton, R.mipmap.bomb_botton_end, R.mipmap.bomb_botton_end, R.mipmap.bomb_botton };
    int interval = 250, index = 0;
    boolean isRunning = false;
    Handler handler = new Handler();
    Runnable shining_task = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                index++;
                index = index % images.length;
                //Log.d("Intro Screen", "Change Image " + index);
                imageSwitcher.setImageResource(images[index]);
                handler.postDelayed(this, interval);
            }
        }
    };

    // weapon list related
    ListView weapon_list;
    List<String> weapon_array = new ArrayList<>();
    String[] WeaponNameList = new String[] {"Bullet", "Ray", "Lightning"};
    public static DrawerLayout drawer_layout;
    FloatingActionButton fab;
    public static int CurrentWeapon = 0;
    public int[] weapon_soundPool = new int[]{
            4,
            1,
            5
    };

    // confirm related
    boolean magic_match = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

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
                playSound(weapon_soundPool[CurrentWeapon]);
                attemptSend(view);
            }
        });

        setBombButton();

        container = (ViewGroup) findViewById(R.id.container);
        gameover = (ImageView) findViewById(R.id.gameover);
        weapon_list = (ListView) findViewById(R.id.weapon_list);
        drawer_layout = (DrawerLayout) findViewById(R.id.drawer_layout);
        fab = (FloatingActionButton) findViewById(R.id.fab);

        drawer_layout.addDrawerListener(this);

        if( fab != null )   {
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    fab.hide();
                    drawer_layout.openDrawer(GravityCompat.END);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if( !drawer_layout.isDrawerOpen(GravityCompat.END) ) {
                                fab.show();
                            }
                        }
                    }, 500);
                }
            });
        }

        for(int i = 0; i < WeaponNameList.length ; i ++ )
            weapon_array.add(WeaponNameList[i]);
        weapon_list.setAdapter(new CircularArrayAdapter(MainActivity.this, WeaponNameList, drawer_layout));
        weapon_list.setDivider(ContextCompat.getDrawable(MainActivity.this, R.drawable.list_divider));
        weapon_list.setDividerHeight(10);
        weapon_list.setSelectionFromTop(CircularArrayAdapter.HALF_MAX_VALUE, 0);

        myVibrator = (Vibrator) getApplication().getSystemService(Service.VIBRATOR_SERVICE);

        tv = (TextView) findViewById(R.id.sensorValue);

        sManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        timer = new Timer(true);
        timer.schedule(new MyTimerTask(), 80, 80);
    }

    private void setBombButton()    {
        imageSwitcher = (ImageSwitcher) findViewById(R.id.bomb_btn);
        if( imageSwitcher != null ) {
            Animation aniIn = AnimationUtils.loadAnimation(this,
                    android.R.anim.fade_in);
            aniIn.setDuration(interval);
            Animation aniOut = AnimationUtils.loadAnimation(this,
                    android.R.anim.fade_out);
            aniOut.setDuration(interval);

            imageSwitcher.setInAnimation(aniIn);
            imageSwitcher.setOutAnimation(aniOut);
            imageSwitcher.setFactory(MainActivity.this);
            imageSwitcher.setImageResource(images[0]);
        }
    }

    // ============== bomb button ==================

    private void startAnimatedBackground() {

        isRunning = true;

        imageSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ultraAttack();
                stopAnimation();
            }
        });
        imageSwitcher.setImageResource(images[index]);
        handler.postDelayed(shining_task, interval);
    }

    public void stopAnimation() {
        imageSwitcher.setOnClickListener(null);
        isRunning = false;
        handler.removeCallbacks(shining_task);
        imageSwitcher.setImageResource(images[0]);
        index = 0;
    }

    @Override
    public View makeView() {
        ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setLayoutParams(new ImageSwitcher.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        return imageView;
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

        if( isRunning )
            handler.postDelayed(shining_task, interval);
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

        if( isRunning )
            handler.removeCallbacks(shining_task);
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

    public void ultraAttack()   {
        String message = "ultra";
        if(menu_state){
            message = "start";
            menu_state = false;
        }
        MenuActivity.mSocket.emit("ultra"+MenuActivity.magic, message);
    }

    public static void Switch_weapon(int Weapon_No)  {
        //MenuActivity.mSocket.emit("switch_weapon"+MenuActivity.magic, Weapon_No);

        switch (MenuActivity.player) {
            case 1:
                MenuActivity.mSocket.emit("switch_weapon1"+MenuActivity.magic, Weapon_No);
                break;
            case 2:
                MenuActivity.mSocket.emit("switch_weapon2"+MenuActivity.magic, Weapon_No);
                break;
        }

        CurrentWeapon = Weapon_No;
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

    /**
     *  ================= NavigationDrawer Listener ==================
     */
    @Override
    public void onDrawerSlide(View drawerView, float slideOffset) {

    }

    @Override
    public void onDrawerOpened(View drawerView) {
        fab.hide();
    }

    @Override
    public void onDrawerClosed(View drawerView) {
        fab.show();
    }

    @Override
    public void onDrawerStateChanged(int newState) {

    }

    // ===============================================================



    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Utils.full_screen_mode(getWindow().getDecorView());
    }

    @Override
    public void onBackPressed() {
        if (drawer_layout != null && drawer_layout.isDrawerOpen(GravityCompat.END)) {
            drawer_layout.closeDrawer(GravityCompat.END);
        } else {
            super.onBackPressed();
        }
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
