package com.yeyupiaoling.server.handle;

import android.util.Log;

import com.yeyupiaoling.server.constant.Const;
import com.yeyupiaoling.server.listener.ServerListener;

import java.nio.charset.StandardCharsets;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;


@ChannelHandler.Sharable
public class ServerHandler extends SimpleChannelInboundHandler<byte[]> {

    private static final String TAG = ServerHandler.class.getSimpleName();
    private final ServerListener mListener;

    public ServerHandler(ServerListener listener) {
        this.mListener = listener;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    /**
     * 连接成功
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Log.e(TAG, "channelActive");
        mListener.onChannelConnect(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Log.e(TAG, "channelInactive");
        mListener.onChannelDisConnect(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] data) throws Exception {
        String msg = new String(data, StandardCharsets.UTF_8);
        // 客户端发送来的心跳数据
        if (msg.equals("Heartbeat") || msg.equals(Const.PACKET_SEPARATOR)) {
            return;
        }
        mListener.onMessageResponseServer(data, ctx.channel().id().asShortText());
    }
}
