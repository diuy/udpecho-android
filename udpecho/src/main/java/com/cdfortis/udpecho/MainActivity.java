package com.cdfortis.udpecho;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;


public class MainActivity extends Activity implements TextView.OnEditorActionListener {


    private static final String TAG = "MainActivity";
    private EditText textIp, textPort, textSpeed, textSize, textTag;
    private TextView textLogFilePath;
    private Button btnStart, btnStop;

    private String ip;
    private int port, speed, size, tag;
    private UdpEcho echo = null;
    private Handler handler = new Handler();
    private File logFilePath;
    private FileOutputStream logStream;

    private EditText findEditText(int id) {
        return (EditText) findViewById(id);
    }

    private Button findButton(int id) {
        return (Button) findViewById(id);
    }

    private void loadConfig() {
        int defaultTag = Util.rand(0, 1000000);
        SharedPreferences preferences = this.getSharedPreferences(this.getPackageName(), 0);
        tag = preferences.getInt("tag", defaultTag);
        ip = preferences.getString("ip", Constant.DEFAULT_IP);
        port = preferences.getInt("port", Constant.DEFAULT_PORT);
        speed = preferences.getInt("speed", Constant.DEFAULT_SPEED);
        size = preferences.getInt("size", Constant.DEFAULT_SIZE);
    }

    private void saveConfig() {
        SharedPreferences preferences = this.getSharedPreferences(this.getPackageName(), 0);
        SharedPreferences.Editor edit = preferences.edit();
        edit.putInt("tag", tag);
        edit.putString("ip", ip);
        edit.putInt("port", port);
        edit.putInt("speed", speed);
        edit.putInt("size", size);
        edit.apply();
    }

    private void showToast(String str) {
        Toast.makeText(this, str, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getTitle() + " " + BuildConfig.VERSION_NAME);
        setContentView(R.layout.activity_main);
        textIp = findEditText(R.id.textIp);
        textPort = findEditText(R.id.textPort);
        textSpeed = findEditText(R.id.textSpeed);
        textSize = findEditText(R.id.textSize);
        textTag = findEditText(R.id.textTag);
        textLogFilePath = (TextView) findViewById(R.id.textLogFilePath);
        btnStart = findButton(R.id.btnStart);
        btnStop = findButton(R.id.btnStop);
        textSize.setOnEditorActionListener(this);
        loadConfig();
        textTag.setText(String.valueOf(tag));
        textIp.setText(ip);
        textPort.setText(String.valueOf(port));
        textSpeed.setText(String.valueOf(speed));
        textSize.setText(String.valueOf(size));

        logFilePath = new File(Environment.getExternalStorageDirectory(), "udpecho");
        logFilePath.mkdir();
        logFilePath = new File(logFilePath, Util.nowDateStr() + ".txt");

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);


    }

    private String getNetInfo(){
        ConnectivityManager connectivity = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo activeNetInfo = connectivity.getActiveNetworkInfo();
            return activeNetInfo.toString();
        }
        return "";
    }

    private void openLog() {
        if (logStream == null){
            try {
                logStream = new FileOutputStream(logFilePath, true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void closeLog(){
        if (logStream != null) {
            try {
                logStream.close();
                logStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (echo != null) {
            echo.stop();
            echo = null;
        }
        closeLog();
        super.onDestroy();
    }

    private Runnable runnableStopSend = new Runnable() {
        @Override
        public void run() {
            onBtnStop(null);
        }
    };
    private Runnable runnableStop = new Runnable() {
        @Override
        public void run() {
            if (echo != null) {
                echo.stop();
                echo = null;
            }
            btnStart.setEnabled(true);
            btnStop.setEnabled(true);
            if(logStream!=null)
                textLogFilePath.setText(logFilePath.getAbsolutePath());
            showToast("已停止");
            closeLog();
        }
    };


    public void onBtnStart(View view) {
        if (echo != null) {
            return;
        }
        ip = textIp.getText().toString();
        port = Util.getEditTextIntValue(textPort, -1);
        speed = Util.getEditTextIntValue(textSpeed, -1);
        size = Util.getEditTextIntValue(textSize, -1);
        // tag = Util.getEditTextIntValue(textTag, -1);

        if (TextUtils.isEmpty(ip)) {
            showToast("IP地址不能为空");
            return;
        }
        if (port <= 0 || port >= 65535) {
            showToast("端口设置错误 :" + port + ",范围:(0,65535)");
            return;
        }
        if (size < Constant.MIN_SIZE || size > Constant.MAX_SIZE) {
            showToast("大小设置错误 :" + size + ",范围:[" + Constant.MIN_SIZE + "," + Constant.MAX_SIZE + "]");
            return;
        }
        if (speed < size) {
            showToast("(带宽)不能小于(大小)值 :" + speed);
            return;
        }
        if (speed > Constant.MAX_SPEED) {
            showToast("带宽设置错误 :" + speed + ",最大值:" + Constant.MAX_SPEED);
            return;
        }
        openLog();
        echo = new UdpEcho(ip, port, speed, size, tag, logStream);
        if (echo.start()) {
            saveConfig();
            btnStart.setEnabled(false);
            handler.postDelayed(runnableStopSend, 60 * 1000);
            showToast("已启动,请等待1分钟出结果");
            echo.debug("当前网络：%s",getNetInfo());
        } else {
            showToast("启动失败,请查看日志");
            echo = null;
        }
    }

    public void onBtnStop(View view) {
        if (echo == null)
            return;
        showToast("已停止发送");
        handler.removeCallbacks(runnableStopSend);
        echo.stopSend();
        btnStop.setEnabled(false);
        handler.postDelayed(runnableStop, 3000);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            onBtnStart(v);
        }
        return true;
    }

    public void onBtnShowLog(View view) {
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(android.content.Intent.ACTION_VIEW);
        Uri uri = Uri.fromFile(logFilePath);
        intent.setDataAndType(uri, "text/plain");
        startActivity(intent);
    }

    public void onBtnSendLog(View view) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);

        // 分享文本
        intent.setType("text/plain"); // text/html ...
        intent.putExtra(Intent.EXTRA_SUBJECT, "发送日志");
        intent.putExtra(Intent.EXTRA_TEXT, Util.readText(logFilePath));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(Intent.createChooser(intent, "分享列表"));
    }
}
