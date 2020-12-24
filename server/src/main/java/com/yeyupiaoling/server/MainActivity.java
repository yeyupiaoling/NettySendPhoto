package com.yeyupiaoling.server;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
    private List<Channel> channelList = new ArrayList<>();
    private AlertDialog alertDialog1;
    private NettyTcpServer nettyTcpServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nettyTcpServer = NettyTcpServer.getInstance();
        nettyTcpServer.setListener(MainActivity.this);
        nettyTcpServer.start();

        findViewById(R.id.sendText).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = "我是服务器端";
                NettyTcpServer.getInstance().sendMsgToServer(msg, new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        if (channelFuture.isSuccess()) {
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


        findViewById(R.id.selectClient).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] items = new String[channelList.size()];
                for (int i = 0; i < channelList.size(); i++) {
                    items[i] = channelList.get(i).remoteAddress().toString();
                }
                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(MainActivity.this);
                alertBuilder.setTitle("选择客户端");
                alertBuilder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        nettyTcpServer.selectorChannel(channelList.get(i));
                        alertDialog1.dismiss();
                    }
                });
                alertDialog1 = alertBuilder.create();
                alertDialog1.show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        NettyTcpServer.getInstance().disconnect();
        super.onDestroy();
    }

    @Override
    public void onMessageResponseServer(byte[] data, String ChannelId) {
        String msg = new String(data, StandardCharsets.UTF_8);
        Log.d(TAG, "接收到的消息：" + msg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "接收到的消息：" + msg, Toast.LENGTH_SHORT).show();
            }
        });
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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, socketStr + " 建立连接", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onChannelDisConnect(Channel channel) {
        channelList.remove(channel);
        String socketStr = channel.remoteAddress().toString();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, socketStr + " 断开连接", Toast.LENGTH_LONG).show();
            }
        });
    }

}