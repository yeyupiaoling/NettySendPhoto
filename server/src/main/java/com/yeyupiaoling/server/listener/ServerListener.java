package com.yeyupiaoling.server.listener;


import io.netty.channel.Channel;


public interface ServerListener {
    /**
     *
     * @param data
     * @param ChannelId unique id
     */
    void onMessageResponseServer(byte[] data, String ChannelId);

    /**
     * server开启成功
     */
    void onStartServer();

    /**
     * server关闭
     */
    void onStopServer();

    /**
     * 与客户端建立连接
     *
     * @param channel
     */
    void onChannelConnect(Channel channel);

    /**
     * 与客户端断开连接
     * @param
     */
    void onChannelDisConnect(Channel channel);

}
