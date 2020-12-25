package com.yeyupiaoling.server.listener;


import io.netty.channel.Channel;


public interface ServerListener {
    /**
     * 接收到文本消息
     *
     * @param data
     * @param ChannelId unique id
     */
    void onTextMessage(byte[] data, String ChannelId);

    /**
     * 接收到图片消息
     *
     * @param data
     * @param ChannelId unique id
     */
    void onPhotoMessage(byte[] data, String ChannelId);


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
