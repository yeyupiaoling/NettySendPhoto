package com.yeyupiaoling.server;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.yeyupiaoling.server.constant.Const;
import com.yeyupiaoling.server.listener.MessageStateListener;
import com.yeyupiaoling.server.listener.NettyServerListener;
import com.yeyupiaoling.server.utils.NettyTcpServer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

public class MainActivity extends AppCompatActivity implements NettyServerListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private final List<Channel> channelList = new ArrayList<>();
    private AlertDialog alertDialog1;
    private NettyTcpServer nettyTcpServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nettyTcpServer = NettyTcpServer.getInstance(Const.PACKET_SEPARATOR);
        nettyTcpServer.setListener(MainActivity.this);
        nettyTcpServer.start();

        findViewById(R.id.sendText).setOnClickListener(view -> {
            // 要加上分割符
            String msg = "我是服务器端" + System.getProperty("line.separator") ;
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            NettyTcpServer.getInstance(Const.PACKET_SEPARATOR).sendDataToClient(data, new MessageStateListener() {
                @Override
                public void isSendSuccess(boolean isSuccess) {
                    if (isSuccess) {
                        Log.d(TAG, "发送成功");
                    } else {
                        Log.d(TAG, "发送失败");
                    }
                }
            });
        });

        findViewById(R.id.sendPhoto).setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(intent, 1);
        });


        findViewById(R.id.selectClient).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] items = new String[channelList.size()];
                for (int i = 0; i < channelList.size(); i++) {
                    items[i] = channelList.get(i).remoteAddress().toString();
                }
                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(MainActivity.this);
                alertBuilder.setTitle("选择客户端");
                alertBuilder.setItems(items, (dialogInterface, i) -> {
                    nettyTcpServer.selectorChannel(channelList.get(i));
                    alertDialog1.dismiss();
                });
                alertDialog1 = alertBuilder.create();
                alertDialog1.show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        NettyTcpServer.getInstance(Const.PACKET_SEPARATOR).disconnect();
        super.onDestroy();
    }

    @Override
    public void onMessageResponseServer(byte[] data, String ChannelId) {
        String msg = new String(data, StandardCharsets.UTF_8);
        Log.d(TAG, "接收到的消息：" + msg);
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "接收到的消息：" + msg, Toast.LENGTH_SHORT).show());
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
        nettyTcpServer.selectorChannel(channel);
        String socketStr = channel.remoteAddress().toString();
        runOnUiThread(() -> Toast.makeText(MainActivity.this, socketStr + " 建立连接", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onChannelDisConnect(Channel channel) {
        channelList.remove(channel);
        String socketStr = channel.remoteAddress().toString();
        runOnUiThread(() -> Toast.makeText(MainActivity.this, socketStr + " 断开连接", Toast.LENGTH_SHORT).show());
    }

}