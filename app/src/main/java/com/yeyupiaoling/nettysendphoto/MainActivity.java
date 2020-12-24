package com.yeyupiaoling.nettysendphoto;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.yeyupiaoling.nettysendphoto.constant.ConnectState;
import com.yeyupiaoling.nettysendphoto.constant.Const;
import com.yeyupiaoling.nettysendphoto.listener.MessageStateListener;
import com.yeyupiaoling.nettysendphoto.listener.NettyClientListener;
import com.yeyupiaoling.nettysendphoto.utils.NettyTcpClientUtil;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity implements NettyClientListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private NettyTcpClientUtil mNettyTcpClientUtil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mNettyTcpClientUtil = new NettyTcpClientUtil.Builder()
                .setHost(Const.HOST)    //设置服务端地址
                .setTcpPort(Const.TCP_PORT) //设置服务端端口号
                .setMaxReconnectTimes(5)    //设置最大重连次数
                .setReconnectIntervalTime(5)    //设置重连间隔时间。单位：秒
                .setSendHeartBeat(true) //设置是否发送心跳
                .setHeartBeatInterval(5)    //设置心跳间隔时间。单位：秒
                .setHeartBeatData("心跳数据") //设置心跳数据，可以是String类型，也可以是byte[]，以后设置的为准
                .setIndex(0)    //设置客户端标识.(因为可能存在多个tcp连接)
                .build();

        mNettyTcpClientUtil.setListener(MainActivity.this); //设置TCP监听
        mNettyTcpClientUtil.connect();//连接服务器


        findViewById(R.id.sendText).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 要加上分割符
                String msg = "我是客户端" + System.getProperty("line.separator") ;
                byte[] data = msg.getBytes(StandardCharsets.UTF_8);
                mNettyTcpClientUtil.sendDataToServer(data, new MessageStateListener() {
                    @Override
                    public void isSendSuccess(boolean isSuccess) {
                        if (isSuccess) {
                            Log.d(TAG, "发送成功");
                        } else {
                            Log.d(TAG, "发送失败");
                        }
                    }
                });
            }
        });

        findViewById(R.id.sendPhoto).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, 1);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Uri uri = data.getData();
            Log.e("uri", uri.toString());
            try {
                ContentResolver resolver = MainActivity.this.getContentResolver();
                InputStream reader = resolver.openInputStream(uri);
                byte[] bytes = new byte[reader.available()];
                reader.read(bytes);
                reader.close();

                mNettyTcpClientUtil.sendDataToServer(bytes, new MessageStateListener() {
                    @Override
                    public void isSendSuccess(boolean isSuccess) {
                        if (isSuccess) {
                            Log.d(TAG, "Write auth successful");
                        } else {
                            Log.d(TAG, "Write auth error");
                        }
                    }
                });
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onMessageResponseClient(byte[] data, int index) {
        String msg = new String(data, StandardCharsets.UTF_8);
        Log.e(TAG, "onMessageResponse:" + msg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "接收到消息：" + msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onClientStatusConnectChanged(final int statusCode, final int index) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (statusCode == ConnectState.STATUS_CONNECT_SUCCESS) {
                    Log.e(TAG, "STATUS_CONNECT_SUCCESS:" + index);
                } else {
                    Log.e(TAG, "onServiceStatusConnectChanged:" + statusCode);
                }
            }
        });
    }


    @Override
    protected void onDestroy() {
        mNettyTcpClientUtil.disconnect();
        super.onDestroy();
    }
}