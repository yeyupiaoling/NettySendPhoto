package com.yeyupiaoling.nettysendphoto;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.yeyupiaoling.nettysendphoto.constant.ConnectState;
import com.yeyupiaoling.nettysendphoto.constant.Const;
import com.yeyupiaoling.nettysendphoto.listener.ClientListener;
import com.yeyupiaoling.nettysendphoto.utils.NettyClientUtil;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity implements ClientListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private NettyClientUtil mNettyClientUtil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mNettyClientUtil = new NettyClientUtil.Builder()
                .setHost(Const.HOST)    //设置服务端地址
                .setTcpPort(Const.TCP_PORT) //设置服务端端口号
                .setMaxReconnectTimes(5)    //设置最大重连次数
                .setReconnectIntervalTime(5)    //设置重连间隔时间。单位：秒
                .setSendHeartBeat(true) //设置是否发送心跳
                .setHeartBeatInterval(5)    //设置心跳间隔时间。单位：秒
                .setHeartBeatData(Const.HEART_BEAT_DATA) //设置心跳数据
                .setPacketSeparator(Const.PACKET_SEPARATOR)  // 设置包分割符
                .setIndex(0)    //设置客户端标识.(因为可能存在多个tcp连接)
                .build();

        mNettyClientUtil.setListener(MainActivity.this); //设置TCP监听
        mNettyClientUtil.connect();//连接服务器


        findViewById(R.id.sendText).setOnClickListener(view -> {
            // 要加上分割符
            String msg = "我是客户端" + System.getProperty("line.separator") ;
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            mNettyClientUtil.sendDataToServer(data, isSuccess -> {
                if (isSuccess) {
                    Log.d(TAG, "发送成功");
                } else {
                    Log.d(TAG, "发送失败");
                }
            });
        });

        findViewById(R.id.sendPhoto).setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(intent, 1);
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

                mNettyClientUtil.sendDataToServer(bytes, isSuccess -> {
                    if (isSuccess) {
                        Log.d(TAG, "发送成功");
                    } else {
                        Log.d(TAG, "发送失败");
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
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "接收到消息：" + msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onClientStatusConnectChanged(final int statusCode, final int index) {
        runOnUiThread(() -> {
            if (statusCode == ConnectState.STATUS_CONNECT_SUCCESS) {
                Log.e(TAG, "STATUS_CONNECT_SUCCESS:" + index);
            } else {
                Log.e(TAG, "onServiceStatusConnectChanged:" + statusCode);
            }
        });
    }


    @Override
    protected void onDestroy() {
        mNettyClientUtil.disconnect();
        super.onDestroy();
    }
}