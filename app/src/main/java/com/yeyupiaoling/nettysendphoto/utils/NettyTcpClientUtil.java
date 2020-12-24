package com.yeyupiaoling.nettysendphoto.utils;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.yeyupiaoling.nettysendphoto.handler.NettyClientHandler;
import com.yeyupiaoling.nettysendphoto.listener.MessageStateListener;
import com.yeyupiaoling.nettysendphoto.listener.NettyClientListener;
import com.yeyupiaoling.nettysendphoto.constant.ConnectState;

import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
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
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;


public class NettyTcpClientUtil {
    private static final String TAG = "NettyTcpClient";

    private EventLoopGroup group;
    private NettyClientListener listener;
    private Channel channel;
    private boolean isConnect = false;

    /**
     * 最大重连次数
     */
    private int MAX_CONNECT_TIMES = Integer.MAX_VALUE;

    private int reconnectNum = MAX_CONNECT_TIMES;

    private boolean isNeedReconnect = true;
    private boolean isConnecting = false;

    private long reconnectIntervalTime = 5000;

    private final String host;
    private final int tcpPort;
    private final int mIndex;
    /**
     * 心跳间隔时间, 单位秒
     */
    private long heartBeatInterval = 5;

    /**
     * 是否发送心跳
     */
    private boolean isSendHeartBeat = false;

    /**
     * 心跳数据，可以是String类型，也可以是byte[].
     */
    private String heartBeatData;

    private String packetSeparator;
    private int maxPacketLong = 1024;

    private NettyTcpClientUtil(String host, int tcpPort, int index) {
        this.host = host;
        this.tcpPort = tcpPort;
        this.mIndex = index;
    }

    public void connect() {
        if (isConnecting) {
            return;
        }
        Thread clientThread = new Thread("client-Netty") {
            @Override
            public void run() {
                super.run();
                isNeedReconnect = true;
                reconnectNum = MAX_CONNECT_TIMES;
                connectServer();
            }
        };
        clientThread.start();
    }


    private void connectServer() {
        synchronized (NettyTcpClientUtil.this) {
            ChannelFuture channelFuture = null;
            if (!isConnect) {
                isConnecting = true;
                group = new NioEventLoopGroup();
                Bootstrap bootstrap = new Bootstrap().group(group)
                        .option(ChannelOption.TCP_NODELAY, true)//屏蔽Nagle算法试图
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) throws Exception {
                                if (isSendHeartBeat) {
                                    // 5s未发送数据，回调userEventTriggered
                                    ch.pipeline().addLast("ping", new IdleStateHandler(0, heartBeatInterval, 0, TimeUnit.SECONDS));
                                }

                                //黏包处理,需要客户端、服务端配合
                                if (!TextUtils.isEmpty(packetSeparator)) {
                                    ByteBuf delimiter = Unpooled.buffer();
                                    delimiter.writeBytes(packetSeparator.getBytes());
                                    ch.pipeline().addLast(new DelimiterBasedFrameDecoder(maxPacketLong, delimiter));
                                } else {
                                    ch.pipeline().addLast(new LineBasedFrameDecoder(maxPacketLong));
                                }
                                ch.pipeline().addLast(new ByteArrayEncoder());
                                ch.pipeline().addLast(new ByteArrayDecoder());

                                ch.pipeline().addLast(new NettyClientHandler(listener, mIndex, isSendHeartBeat, heartBeatData, packetSeparator));
                            }
                        });

                try {
                    channelFuture = bootstrap.connect(host, tcpPort).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture channelFuture) throws Exception {
                            if (channelFuture.isSuccess()) {
                                Log.e(TAG, "连接成功");
                                reconnectNum = MAX_CONNECT_TIMES;
                                isConnect = true;
                                channel = channelFuture.channel();
                            } else {
                                Log.e(TAG, "连接失败");
                                isConnect = false;
                            }
                            isConnecting = false;
                        }
                    }).sync();

                    // Wait until the connection is closed.
                    channelFuture.channel().closeFuture().sync();
                    Log.e(TAG, " 断开连接");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    isConnect = false;
                    listener.onClientStatusConnectChanged(ConnectState.STATUS_CONNECT_CLOSED, mIndex);
                    if (null != channelFuture) {
                        if (channelFuture.channel() != null && channelFuture.channel().isOpen()) {
                            channelFuture.channel().close();
                        }
                    }
                    group.shutdownGracefully();
                    reconnect();
                }
            }
        }
    }


    public void disconnect() {
        Log.e(TAG, "断开连接");
        isNeedReconnect = false;
        group.shutdownGracefully();
    }

    public void reconnect() {
        Log.e(TAG, "重新连接");
        if (isNeedReconnect && reconnectNum > 0 && !isConnect) {
            reconnectNum--;
            SystemClock.sleep(reconnectIntervalTime);
            if (isNeedReconnect && reconnectNum > 0 && !isConnect) {
                connectServer();
            }
        }
    }

    /**
     * 异步发送
     *
     * @param data     要发送的数据
     * @param listener 发送结果回调
     */
    public void sendDataToServer(byte[] data, final MessageStateListener listener) {
        boolean flag = channel != null && isConnect;
        if (flag) {
            ByteBuf buf = Unpooled.copiedBuffer(data);
            channel.writeAndFlush(buf).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    listener.isSendSuccess(channelFuture.isSuccess());
                }
            });
        }
    }

    public void setListener(NettyClientListener listener) {
        this.listener = listener;
    }

    /**
     * 构建者，创建NettyTcpClient
     */
    public static class Builder {

        /**
         * 最大重连次数
         */
        private int MAX_CONNECT_TIMES = Integer.MAX_VALUE;

        /**
         * 重连间隔
         */
        private long reconnectIntervalTime = 5000;
        /**
         * 服务器地址
         */
        private String host;
        /**
         * 服务器端口
         */
        private int tcp_port;
        /**
         * 客户端标识，(因为可能存在多个连接)
         */
        private int mIndex;

        /**
         * 是否发送心跳
         */
        private boolean isSendHeartBeat;
        /**
         * 心跳时间间隔
         */
        private long heartBeatInterval = 5;

        /**
         * 心跳数据，可以是String类型，也可以是byte[].
         */
        private String heartBeatData;

        private String packetSeparator;
        private int maxPacketLong;

        public Builder() {
            this.maxPacketLong = 1024;
        }

        public Builder setPacketSeparator(String packetSeparator) {
            this.packetSeparator = packetSeparator;
            return this;
        }

        public Builder setMaxPacketLong(int maxPacketLong) {
            this.maxPacketLong = maxPacketLong;
            return this;
        }

        public Builder setMaxReconnectTimes(int reConnectTimes) {
            this.MAX_CONNECT_TIMES = reConnectTimes;
            return this;
        }


        public Builder setReconnectIntervalTime(long reconnectIntervalTime) {
            this.reconnectIntervalTime = reconnectIntervalTime;
            return this;
        }


        public Builder setHost(String host) {
            this.host = host;
            return this;
        }

        public Builder setTcpPort(int tcp_port) {
            this.tcp_port = tcp_port;
            return this;
        }

        public Builder setIndex(int mIndex) {
            this.mIndex = mIndex;
            return this;
        }

        public Builder setHeartBeatInterval(long intervalTime) {
            this.heartBeatInterval = intervalTime;
            return this;
        }

        public Builder setSendHeartBeat(boolean isSendheartBeat) {
            this.isSendHeartBeat = isSendheartBeat;
            return this;
        }

        public Builder setHeartBeatData(String heartBeatData) {
            this.heartBeatData = heartBeatData;
            return this;
        }

        public NettyTcpClientUtil build() {
            NettyTcpClientUtil nettyTcpClientUtil = new NettyTcpClientUtil(host, tcp_port, mIndex);
            nettyTcpClientUtil.MAX_CONNECT_TIMES = this.MAX_CONNECT_TIMES;
            nettyTcpClientUtil.reconnectIntervalTime = this.reconnectIntervalTime;
            nettyTcpClientUtil.heartBeatInterval = this.heartBeatInterval;
            nettyTcpClientUtil.isSendHeartBeat = this.isSendHeartBeat;
            nettyTcpClientUtil.heartBeatData = this.heartBeatData;
            nettyTcpClientUtil.packetSeparator = this.packetSeparator;
            nettyTcpClientUtil.maxPacketLong = this.maxPacketLong;
            return nettyTcpClientUtil;
        }
    }
}
