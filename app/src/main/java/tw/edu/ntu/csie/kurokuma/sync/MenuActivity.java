package tw.edu.ntu.csie.kurokuma.sync;

import android.app.ActivityOptions;
import android.content.DialogInterface;
import android.content.Intent;
import android.opengl.Visibility;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import mehdi.sakout.fancybuttons.FancyButton;


/**
 * Created by Y.C.Lai on 2016/6/17.
 */
public class MenuActivity extends AppCompatActivity {
    public static Socket mSocket;

    private Button URL_button;
    String URL = null;

    //For player identification
    public static String uuid = UUID.randomUUID().toString().replaceAll("-", "");
    public static int player;
    private TextView tvPlayer;

    public static String magic = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        tvPlayer= (TextView) findViewById(R.id.player);
        URL_button = (Button) findViewById(R.id.URL_btn);
        Button num_btn = (Button) findViewById(R.id.num_btn);

        if( num_btn != null )   {
            num_btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    type_number();
                }
            });
        }
        URL = getPreferences(MODE_PRIVATE).getString("connection", "http://10.5.6.140:3000/");
        if( mSocket == null ) {
            try {
                mSocket = IO.socket(URL);
            }catch (URISyntaxException e)   {
                e.printStackTrace();
            }

            ConnectandWaitforConfirm();
        }

        if( URL_button != null )    {
            URL_button.setText(URL);
            URL_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MenuActivity.this);
                    builder.setTitle("enter your target URL");

                    // Set up the input
                    final EditText input = new EditText(MenuActivity.this);
                    input.setText(URL);
                    // Specify the type of input expected;
                    builder.setView(input);

                    // Set up the buttons
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            URL = input.getText().toString();
                            URL_button.setText(URL);
                            getPreferences(MODE_PRIVATE).edit().putString("connection", URL).apply();
                            try {
                                mSocket = IO.socket(URL);
                            }catch (URISyntaxException e)   {
                                e.printStackTrace();
                            }

                            if( mSocket.connected() )
                                mSocket.disconnect();
                            if( mSocket.hasListeners("connectOK") )
                                mSocket.off("connectOK", onConnectOK);

                            ConnectandWaitforConfirm();
                            dialog.dismiss();
                        }
                    });
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });

                    builder.show();
                }
            });
        }
    }

    public void goPlay(View v){
        tvPlayer.setText("Player");
        EditText username = (EditText) findViewById(R.id.username);

        switch (player) {
            case 1:
                mSocket.emit("username1" + magic, username.getText().toString());
                break;
            case 2:
                mSocket.emit("username2" + magic, username.getText().toString());
                break;
        }

        mSocket.emit("gameStart" + magic, "0");



        Intent single = new Intent(MenuActivity.this, MainActivity.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            View sharedView = v;
            String transitionName = getString(R.string.blue_transitionName);

            ActivityOptions transitionActivityOptions = null;
            transitionActivityOptions = ActivityOptions.makeSceneTransitionAnimation(MenuActivity.this, sharedView, transitionName);
            startActivity(single, transitionActivityOptions.toBundle());
        }else {
            startActivity(single);
        }
    }

    /*public void goMultiplePlayer(View v){
        Intent multiple = new Intent(MenuActivity.this, MultipleActivity.class);
        startActivity(multiple);
    }*/

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Utils.full_screen_mode(getWindow().getDecorView());
    }

    public void type_number()   {
        AlertDialog.Builder builder = new AlertDialog.Builder(MenuActivity.this);
        builder.setTitle("enter your magic hash");

        // Set up the input
        final EditText input = new EditText(MenuActivity.this);
        // Specify the type of input expected;
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                magic = input.getText().toString();
                try {
                    mSocket = IO.socket(URL);
                }catch (URISyntaxException e)   {
                    e.printStackTrace();
                }

                mSocket.disconnect();
                mSocket.off("connectOK", onConnectOK);
                mSocket.off(uuid, onRealConnect);
                if( magic.length() > 0 )    {
                    mSocket.off("connectOK"+magic, onRealConnect);
                    mSocket.on("connectOK"+magic, onRealConnect);
                }

                ConnectandWaitforConfirm();
                mSocket.emit("magic", magic);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }


    private Emitter.Listener onConnectOK = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MenuActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String message = (String)args[0];

                    if (message.equals("OK")) {
                        setRealConnectListener();
                        mSocket.emit("requestPlayer" + magic, uuid);
                    } else if( message.equals("Failed") )   {
                        Toast.makeText(MenuActivity.this, "connect failed", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    };

    public void ConnectandWaitforConfirm()    {
        mSocket.once("connectOK", onConnectOK);
        Log.e("CWC", "before connect");
        mSocket.connect();
        Log.e("CWC", "connectOK");
    }


    //TODO: change activity to mainActivity then start Thread
    public void setRealConnectListener()   {
        mSocket.on("connectOK"+magic, onRealConnect);
        mSocket.on(uuid, onRealConnect);
    }

    // unique listener
    private Emitter.Listener onRealConnect = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String message = (String) args[0];

                    if (message.equals("player1")) {
                        player = 1;
                        tvPlayer.setText("Player1");
                    } else if (message.equals("player2")) {
                        player = 2;
                        tvPlayer.setText("Player2");
                        FancyButton play_btn = (FancyButton) findViewById(R.id.playBtn);
                        play_btn.setVisibility(View.GONE);
                    } else if (message.equals("checkConnect")) {
                        mSocket.emit("stillConnect" + magic, uuid);
                    } else if (message.equals("full")) {
                        player = 0;
                        tvPlayer.setText("full");
                        mSocket.disconnect();
                        Toast.makeText(MenuActivity.this,"The Room Is Full! Socket Disconnected.",Toast.LENGTH_SHORT).show();
                    }  else if (message.equals("p2go")) {
                        if(player == 2){
                            tvPlayer.setText("Player");
                            EditText username = (EditText) findViewById(R.id.username);
                            mSocket.emit("username2" + magic, username.getText().toString());

                            Intent single = new Intent(MenuActivity.this, MainActivity.class);
                            startActivity(single);
                        }
                    }
                }
            });
        }
    };
}