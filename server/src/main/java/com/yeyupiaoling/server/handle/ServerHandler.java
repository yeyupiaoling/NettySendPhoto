package com.yeyupiaoling.server.handle;

import android.util.Base64;
import android.util.Log;

import com.yeyupiaoling.server.constant.Const;
import com.yeyupiaoling.server.listener.ServerListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;


@ChannelHandler.Sharable
public class ServerHandler extends SimpleChannelInboundHandler<byte[]> {

    private static final String TAG = ServerHandler.class.getSimpleName();
    private final ServerListener mListener;
    private boolean isPhoto = false;
    private int photoSize;
    private final StringBuffer stringBuffer = new StringBuffer();

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
        if (msg.equals(Const.HEART_BEAT_DATA)) {
            return;
        }

        // 匹配获取图片大小
        Pattern pattern = Pattern.compile(Const.PHOTO_SIZE_TEMPLATE);
        Matcher matcher = pattern.matcher(msg);
        // 判断是否能获取图片大小
        if (matcher.find()) {
            photoSize = Integer.parseInt(matcher.group(1));
            isPhoto = true;
            return;
        }

        // 判断本次数据是否为图片数据
        if (isPhoto) {
            // 记录图片数据
            stringBuffer.append(msg);
            // 当记录的图片书等于指定大小就结束接收图片数据
            if (stringBuffer.length() == photoSize) {
                byte[] photo =  Base64.decode(stringBuffer.toString(), Base64.DEFAULT);
                mListener.onPhotoMessage(photo, ctx.channel().id().asShortText());
                photoSize = 0;
                isPhoto = false;
                // 清空数据
                stringBuffer.delete(0, stringBuffer.length());
            }
        } else {
            mListener.onTextMessage(data, ctx.channel().id().asShortText());
        }
    }
}
