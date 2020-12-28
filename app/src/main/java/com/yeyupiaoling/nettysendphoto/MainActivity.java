package com.yeyupiaoling.nettysendphoto;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.blankj.utilcode.util.PathUtils;
import com.bumptech.glide.Glide;
import com.yeyupiaoling.nettysendphoto.constant.Const;
import com.yeyupiaoling.nettysendphoto.listener.ClientListener;
import com.yeyupiaoling.nettysendphoto.utils.NettyClientUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity implements ClientListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private NettyClientUtil mNettyClientUtil;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!hasPermission()) {
            requestPermission();
        }

        imageView = findViewById(R.id.imageView);

        mNettyClientUtil = new NettyClientUtil.Builder()
                .setHost(Const.HOST)    //设置服务端地址
                .setTcpPort(Const.TCP_PORT) //设置服务端端口号
                .setMaxReconnectTimes(5)    //设置最大重连次数
                .setReconnectIntervalTime(5)    //设置重连间隔时间。单位：秒
                .setSendHeartBeat(true) //设置是否发送心跳
                .setHeartBeatInterval(5)    //设置心跳间隔时间。单位：秒
                .setHeartBeatData(Const.HEART_BEAT_DATA) //设置心跳数据
                .setPacketSeparator(Const.PACKET_SEPARATOR)  // 设置包分割符
                .setMaxPacketLong(Const.MAX_PACKET_LONG)  // 设置最大的包
                .setIndex(0)    //设置客户端标识.(因为可能存在多个tcp连接)
                .build();

        mNettyClientUtil.setListener(MainActivity.this); //设置TCP监听
        mNettyClientUtil.connect();//连接服务器

        // 点击按钮发送文本数据
        findViewById(R.id.sendText).setOnClickListener(view -> {
            if (mNettyClientUtil.getIsConnect()) {
                // 发送的数据
                String msg = "我是客户端";
                mNettyClientUtil.sendTextToServer(msg, isSuccess -> {
                    if (isSuccess) {
                        Log.d(TAG, "发送成功");
                    } else {
                        Log.d(TAG, "发送失败");
                    }
                });
            } else {
                Toast.makeText(MainActivity.this, "未连接服务端！", Toast.LENGTH_SHORT).show();
            }
        });

        // 打开相册
        findViewById(R.id.sendPhoto).setOnClickListener(v -> {
            if (mNettyClientUtil.getIsConnect()) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, 1);
            } else {
                Toast.makeText(MainActivity.this, "未连接服务端！", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Uri uri = data.getData();
            Log.e("uri", uri.toString());
            try {
                // 获取相册图像
                ContentResolver resolver = MainActivity.this.getContentResolver();
                InputStream reader = resolver.openInputStream(uri);
                byte[] bytes = new byte[reader.available()];
                reader.read(bytes);
                reader.close();

                // 判断发送的图片是否过大
                if (bytes.length > Const.MAX_PACKET_LONG) {
                    Toast.makeText(MainActivity.this, "发送的图片过大", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 执行发送
                mNettyClientUtil.sendPhotoToServer(bytes, isSuccess -> {
                    if (isSuccess) {
                        Log.d(TAG, "发送成功");
                    } else {
                        Log.d(TAG, "发送失败");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onTextMessage(byte[] data, int index) {
        // 显示文本数据
        String msg = new String(data, StandardCharsets.UTF_8);
        Log.e(TAG, "onMessageResponse:" + msg);
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "接收到消息：" + msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onPhotoMessage(byte[] data, int index) {
        try {
            // 将图片写入到本地
            String path = PathUtils.getExternalAppPicturesPath() + "/" + System.currentTimeMillis() + ".jpg";
            File file = new File(path);
            OutputStream outputStream = new FileOutputStream(file);
            outputStream.write(data);
            outputStream.flush();
            outputStream.close();

            // 显示图像
            runOnUiThread(() -> Glide.with(MainActivity.this).load(path).into(imageView));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClientStatusConnectChanged(final int statusCode, final int index) {
        runOnUiThread(() -> {
            if (statusCode == Const.STATUS_CONNECT_SUCCESS) {
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


    // check had permission
    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    // request permission
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

}