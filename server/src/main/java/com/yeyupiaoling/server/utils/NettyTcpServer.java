package com.yeyupiaoling.server.utils;

import android.text.TextUtils;
import android.util.Log;

import com.yeyupiaoling.server.handle.EchoServerHandler;
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
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;


/**
 * TCP 服务端
 * 目前服务端支持连接多个客户端
 */
public class NettyTcpServer {
    private static final String TAG = NettyTcpServer.class.getSimpleName();
    private final int port = 1088;
    private Channel channel;

    private static NettyTcpServer instance = null;
    private NettyServerListener listener;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private String packetSeparator;
    private final int maxPacketLong = 1024;


    public static NettyTcpServer getInstance() {
        if (instance == null) {
            synchronized (NettyTcpServer.class) {
                if (instance == null) {
                    instance = new NettyTcpServer();
                }
            }
        }
        return instance;
    }

    private NettyTcpServer() {
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
                            .localAddress(new InetSocketAddress(port))
                            .childOption(ChannelOption.SO_KEEPALIVE, true)
                            .childOption(ChannelOption.SO_REUSEADDR, true)
                            .childOption(ChannelOption.TCP_NODELAY, true)
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                public void initChannel(SocketChannel ch) throws Exception {
                                    if (!TextUtils.isEmpty(packetSeparator)) {
                                        ByteBuf delimiter= Unpooled.buffer();
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

    // 异步发送消息
    public boolean sendMsgToServer(String data, ChannelFutureListener listener) {
        boolean flag = channel != null && channel.isActive();
        String separator = TextUtils.isEmpty(packetSeparator) ? System.getProperty("line.separator") : packetSeparator;
        if (flag) {
            channel.writeAndFlush(data + separator).addListener(listener);
        }
        return flag;
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
