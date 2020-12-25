package com.yeyupiaoling.server.utils;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.yeyupiaoling.server.constant.Const;
import com.yeyupiaoling.server.handle.ServerHandler;
import com.yeyupiaoling.server.listener.MessageStateListener;
import com.yeyupiaoling.server.listener.ServerListener;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;


/**
 * TCP 服务端
 * 目前服务端支持连接多个客户端
 */
public class NettyServerUtil {
    private static final String TAG = NettyServerUtil.class.getSimpleName();
    private Channel channel;

    private static NettyServerUtil instance = null;
    private ServerListener listener;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private final String packetSeparator;


    public static NettyServerUtil getInstance(String packetSeparator) {
        if (instance == null) {
            synchronized (NettyServerUtil.class) {
                if (instance == null) {
                    instance = new NettyServerUtil(packetSeparator);
                }
            }
        }
        return instance;
    }

    private NettyServerUtil(String packetSeparator) {
        this.packetSeparator = packetSeparator;
    }

    public void start() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                bossGroup = new NioEventLoopGroup(1);
                workerGroup = new NioEventLoopGroup();
                try {
                    ServerBootstrap b = new ServerBootstrap();
                    b.group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .localAddress(new InetSocketAddress(Const.TCP_PORT))
                            .childOption(ChannelOption.SO_KEEPALIVE, true)
                            .childOption(ChannelOption.SO_REUSEADDR, true)
                            .childOption(ChannelOption.TCP_NODELAY, true)
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                public void initChannel(SocketChannel ch) throws Exception {
                                    if (!TextUtils.isEmpty(packetSeparator)) {
                                        ByteBuf delimiter = Unpooled.buffer();
                                        delimiter.writeBytes(packetSeparator.getBytes());
                                        ch.pipeline().addLast(new DelimiterBasedFrameDecoder(Const.MAX_PACKET_LONG, delimiter));
                                    } else {
                                        ch.pipeline().addLast(new LineBasedFrameDecoder(Const.MAX_PACKET_LONG));
                                    }
                                    ch.pipeline().addLast(new ByteArrayEncoder());
                                    ch.pipeline().addLast(new ByteArrayDecoder());

                                    ch.pipeline().addLast(new ServerHandler(listener));
                                }
                            });

                    // Bind and start to accept incoming connections.
                    ChannelFuture f = b.bind().sync();
                    Log.e(TAG, " started and listen on " + f.channel().localAddress());
                    listener.onStartServer();
                    f.channel().closeFuture().sync();
                } catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage());
                    e.printStackTrace();
                } finally {
                    listener.onStopServer();
                    workerGroup.shutdownGracefully();
                    bossGroup.shutdownGracefully();
                }
            }
        }.start();

    }

    public void disconnect() {
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
    }

    /**
     * 异步发送文本消息
     *
     * @param msg      要发送的数据
     * @param listener 发送结果回调
     */
    public void sendTextToClient(String msg, MessageStateListener listener) {
        boolean flag = channel != null && channel.isActive();
        // 要加上分割符
        msg = msg + Const.PACKET_SEPARATOR;
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);

        ByteBuf buf = Unpooled.copiedBuffer(data);
        if (flag) {
            channel.writeAndFlush(buf).addListener((ChannelFutureListener) channelFuture -> {
                listener.isSendSuccess(channelFuture.isSuccess());
            });
        }
    }


    /**
     * 异步发送图片数据
     *
     * @param data     要发送的数据
     * @param listener 发送结果回调
     */
    public void sendPhotoToClient(byte[] data, MessageStateListener listener) {
        boolean flag = channel != null && channel.isActive();
        if (flag) {
            String base64 = Base64.encodeToString(data, Base64.DEFAULT).replace("\n", "");
            String m = base64 + Const.PACKET_SEPARATOR;
            byte[] bb = m.getBytes(StandardCharsets.UTF_8);

            // 首先发送通知客户端接下来发生的是图片数据，并告诉大小
            String msg = "Photo_Size:" + base64.length() + Const.PACKET_SEPARATOR;
            byte[] d = msg.getBytes(StandardCharsets.UTF_8);
            ByteBuf b1 = Unpooled.copiedBuffer(d);
            channel.writeAndFlush(b1).awaitUninterruptibly();

            // 发送图片数据
            ByteBuf buf = Unpooled.copiedBuffer(bb);
            channel.writeAndFlush(buf).awaitUninterruptibly();

            listener.isSendSuccess(true);
            return;
        }
        listener.isSendSuccess(false);
    }

    /**
     * 切换通道
     * 设置服务端，与哪个客户端通信
     *
     * @param channel
     */
    public void selectorChannel(Channel channel) {
        this.channel = channel;
    }


    public void setListener(ServerListener listener) {
        this.listener = listener;
    }
}
