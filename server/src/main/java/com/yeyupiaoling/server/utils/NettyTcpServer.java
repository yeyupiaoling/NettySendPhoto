package com.yeyupiaoling.server.utils;

import android.text.TextUtils;
import android.util.Log;

import com.yeyupiaoling.server.constant.Const;
import com.yeyupiaoling.server.handle.EchoServerHandler;
import com.yeyupiaoling.server.listener.MessageStateListener;
import com.yeyupiaoling.server.listener.NettyServerListener;

import java.net.InetSocketAddress;

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
public class NettyTcpServer {
    private static final String TAG = NettyTcpServer.class.getSimpleName();
    private Channel channel;

    private static NettyTcpServer instance = null;
    private NettyServerListener listener;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private final String packetSeparator;
    private final int maxPacketLong = 1024;


    public static NettyTcpServer getInstance(String packetSeparator) {
        if (instance == null) {
            synchronized (NettyTcpServer.class) {
                if (instance == null) {
                    instance = new NettyTcpServer(packetSeparator);
                }
            }
        }
        return instance;
    }

    private NettyTcpServer(String packetSeparator) {
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
                                        ch.pipeline().addLast(new DelimiterBasedFrameDecoder(maxPacketLong, delimiter));
                                    } else {
                                        ch.pipeline().addLast(new LineBasedFrameDecoder(maxPacketLong));
                                    }

                                    ch.pipeline().addLast(new ByteArrayEncoder());
                                    ch.pipeline().addLast(new ByteArrayDecoder());

                                    ch.pipeline().addLast(new EchoServerHandler(listener));
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
     * 异步发送
     *
     * @param data     要发送的数据
     * @param listener 发送结果回调
     */
    public void sendDataToClient(byte[] data, MessageStateListener listener) {
        boolean flag = channel != null && channel.isActive();
        ByteBuf buf = Unpooled.copiedBuffer(data);
        if (flag) {
            channel.writeAndFlush(buf).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    listener.isSendSuccess(channelFuture.isSuccess());
                }
            });
        }
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


    public void setListener(NettyServerListener listener) {
        this.listener = listener;
    }
}
