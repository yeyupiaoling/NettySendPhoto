package com.yeyupiaoling.nettysendphoto.utils;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.yeyupiaoling.nettysendphoto.constant.Const;
import com.yeyupiaoling.nettysendphoto.handler.ClientHandler;
import com.yeyupiaoling.nettysendphoto.listener.ClientListener;
import com.yeyupiaoling.nettysendphoto.listener.MessageStateListener;

import java.nio.charset.StandardCharsets;
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
import io.netty.handler.timeout.IdleStateHandler;


public class NettyClientUtil {
    private static final String TAG = "NettyTcpClient";

    private EventLoopGroup group;
    private ClientListener listener;
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

    private NettyClientUtil(String host, int tcpPort, int index) {
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
        synchronized (NettyClientUtil.this) {
            ChannelFuture channelFuture = null;
            if (!isConnect) {
                isConnecting = true;
                group = new NioEventLoopGroup();
                Bootstrap bootstrap = new Bootstrap().group(group)
                        .option(ChannelOption.TCP_NODELAY, true)  //屏蔽Nagle算法试图
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

                                ch.pipeline().addLast(new ClientHandler(listener, mIndex, isSendHeartBeat, heartBeatData, packetSeparator));
                            }
                        });

                try {
                    channelFuture = bootstrap.connect(host, tcpPort).addListener((ChannelFutureListener) channelFuture1 -> {
                        if (channelFuture1.isSuccess()) {
                            Log.e(TAG, "连接成功");
                            reconnectNum = MAX_CONNECT_TIMES;
                            isConnect = true;
                            channel = channelFuture1.channel();
                        } else {
                            Log.e(TAG, "连接失败");
                            isConnect = false;
                        }
                        isConnecting = false;
                    }).sync();

                    // Wait until the connection is closed.
                    channelFuture.channel().closeFuture().sync();
                    Log.e(TAG, " 断开连接");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    isConnect = false;
                    listener.onClientStatusConnectChanged(Const.STATUS_CONNECT_CLOSED, mIndex);
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

    public boolean getIsConnect() {
        return isConnect;
    }

    /**
     * 异步发送文本消息
     *
     * @param msg      要发送的数据
     * @param listener 发送结果回调
     */
    public void sendTextToServer(String msg, MessageStateListener listener) {
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
     * @param base64     要发送的数据
     * @param listener   发送结果回调
     */
    public void sendPhotoToServer(String base64, MessageStateListener listener) {
        boolean flag = channel != null && channel.isActive();
        if (flag) {
            // 将图像打包成Base64的字符串
            base64 = base64.replace("\n", "");
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


    public void setListener(ClientListener listener) {
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

        public NettyClientUtil build() {
            NettyClientUtil nettyClientUtil = new NettyClientUtil(host, tcp_port, mIndex);
            nettyClientUtil.MAX_CONNECT_TIMES = this.MAX_CONNECT_TIMES;
            nettyClientUtil.reconnectIntervalTime = this.reconnectIntervalTime;
            nettyClientUtil.heartBeatInterval = this.heartBeatInterval;
            nettyClientUtil.isSendHeartBeat = this.isSendHeartBeat;
            nettyClientUtil.heartBeatData = this.heartBeatData;
            nettyClientUtil.packetSeparator = this.packetSeparator;
            nettyClientUtil.maxPacketLong = this.maxPacketLong;
            return nettyClientUtil;
        }
    }
}
