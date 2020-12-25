package com.yeyupiaoling.nettysendphoto.listener;


// Netty状态变化监听
public interface ClientListener {

    /**
     * 接收到文本消息
     * @param msg 消息
     * @param index tcp 客户端的标识，因为一个应用程序可能有很多个长链接
     */
    void onTextMessage(byte[] msg, int index);

    /**
     * 接收到图片消息
     * @param msg 消息
     * @param index tcp 客户端的标识，因为一个应用程序可能有很多个长链接
     */
    void onPhotoMessage(byte[] msg, int index);


    /**
     * 当服务状态发生变化时触发
     * @param statusCode 状态变化
     * @param index tcp 客户端的标识，因为一个应用程序可能有很多个长链接
     */
    public void onClientStatusConnectChanged(int statusCode, int index);
}
