package com.yeyupiaoling.server;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.blankj.utilcode.util.PathUtils;
import com.bumptech.glide.Glide;
import com.yeyupiaoling.server.constant.Const;
import com.yeyupiaoling.server.listener.MessageStateListener;
import com.yeyupiaoling.server.listener.ServerListener;
import com.yeyupiaoling.server.utils.NettyServerUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.netty.channel.Channel;

public class MainActivity extends AppCompatActivity implements ServerListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private final List<Channel> channelList = new ArrayList<>();
    private AlertDialog alertDialog1;
    private NettyServerUtil nettyServerUtil;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!hasPermission()) {
            requestPermission();
        }

        imageView = findViewById(R.id.imageView);

        nettyServerUtil = NettyServerUtil.getInstance(Const.PACKET_SEPARATOR);
        nettyServerUtil.setListener(MainActivity.this);
        nettyServerUtil.start();

        // 点击按钮发送文本数据
        findViewById(R.id.sendText).setOnClickListener(view -> {
            if (!channelList.isEmpty()) {
                // 要发送的数据
                String msg = "我是服务器端";
                NettyServerUtil.getInstance(Const.PACKET_SEPARATOR).sendTextToClient(msg, isSuccess -> {
                    if (isSuccess) {
                        Log.d(TAG, "发送成功");
                    } else {
                        Log.d(TAG, "发送失败");
                    }
                });
            } else {
                Toast.makeText(MainActivity.this, "没有客户端连接！", Toast.LENGTH_SHORT).show();
            }
        });

        // 打开相册
        findViewById(R.id.sendPhoto).setOnClickListener(v -> {
            if (!channelList.isEmpty()) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, 1);
            } else {
                Toast.makeText(MainActivity.this, "没有客户端连接！", Toast.LENGTH_SHORT).show();
            }
        });

        // 选择客户端发送和接收消息
        findViewById(R.id.selectClient).setOnClickListener(v -> {
            String[] items = new String[channelList.size()];
            for (int i = 0; i < channelList.size(); i++) {
                items[i] = channelList.get(i).remoteAddress().toString();
            }
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(MainActivity.this);
            alertBuilder.setTitle("选择客户端");
            alertBuilder.setItems(items, (dialogInterface, i) -> {
                nettyServerUtil.selectorChannel(channelList.get(i));
                alertDialog1.dismiss();
            });
            alertDialog1 = alertBuilder.create();
            alertDialog1.show();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Uri uri = data.getData();
            Log.d(TAG, "图像路径：" + uri.toString());
            try {
                // 获取相册图像
                ContentResolver resolver = MainActivity.this.getContentResolver();
                InputStream inputStream = resolver.openInputStream(uri);
                byte[] bytes = new byte[inputStream.available()];
                inputStream.read(bytes);
                inputStream.close();

                // 判断发送的图片是否过大
                if (bytes.length > Const.MAX_PACKET_LONG) {
                    Toast.makeText(MainActivity.this, "发送的图片过大", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 执行发送
                NettyServerUtil.getInstance(Const.PACKET_SEPARATOR).sendPhotoToClient(bytes, isSuccess -> {
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
    protected void onDestroy() {
        NettyServerUtil.getInstance(Const.PACKET_SEPARATOR).disconnect();
        super.onDestroy();
    }

    @Override
    public void onTextMessage(byte[] data, String ChannelId) {
        // 显示文本数据
        String msg = new String(data, StandardCharsets.UTF_8);
        Log.d(TAG, "接收到的消息：" + msg);
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "接收到的消息：" + msg, Toast.LENGTH_SHORT).show());
    }


    @Override
    public void onPhotoMessage(byte[] data, String ChannelId) {
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
    public void onStartServer() {
        Log.e(TAG, "服务器已启动");
    }

    @Override
    public void onStopServer() {
        Log.e(TAG, "服务器已停止");
    }

    @Override
    public void onChannelConnect(final Channel channel) {
        channelList.add(channel);
        nettyServerUtil.selectorChannel(channel);
        String socketStr = channel.remoteAddress().toString();
        runOnUiThread(() -> Toast.makeText(MainActivity.this, socketStr + " 建立连接", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onChannelDisConnect(Channel channel) {
        channelList.remove(channel);
        String socketStr = channel.remoteAddress().toString();
        runOnUiThread(() -> Toast.makeText(MainActivity.this, socketStr + " 断开连接", Toast.LENGTH_SHORT).show());
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