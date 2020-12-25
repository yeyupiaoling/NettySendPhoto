package com.yeyupiaoling.nettysendphoto.handler;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.yeyupiaoling.nettysendphoto.constant.Const;
import com.yeyupiaoling.nettysendphoto.listener.ClientListener;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;


public class ClientHandler extends SimpleChannelInboundHandler<byte[]> {

    private static final String TAG = ClientHandler.class.getSimpleName();
    private final boolean isSendHeartBeat;
    private final ClientListener listener;
    private final int index;
    private final Object heartBeatData;
    private final String packetSeparator;
    private boolean isPhoto = false;
    private int photoSize;
    private final StringBuffer stringBuffer = new StringBuffer();

    public ClientHandler(ClientListener listener, int index, boolean isSendHeartBeat, Object heartBeatData) {
        this(listener, index, isSendHeartBeat, heartBeatData, null);
    }

    public ClientHandler(ClientListener listener, int index, boolean isSendHeartBeat, Object heartBeatData, String separator) {
        this.listener = listener;
        this.index = index;
        this.isSendHeartBeat = isSendHeartBeat;
        this.heartBeatData = heartBeatData;
        this.packetSeparator = TextUtils.isEmpty(separator) ? System.getProperty("line.separator") : separator;
    }


    /**
     * <p>设定IdleStateHandler心跳检测每x秒进行一次读检测，
     * 如果x秒内ChannelRead()方法未被调用则触发一次userEventTrigger()方法 </p>
     *
     * @param ctx ChannelHandlerContext
     * @param evt IdleStateEvent
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                // 发送心跳
                if (isSendHeartBeat) {
                    if (heartBeatData == null) {
                        ctx.channel().writeAndFlush(Const.HEART_BEAT_DATA + packetSeparator);
                    } else {
                        if (heartBeatData instanceof String) {
                            ctx.channel().writeAndFlush(heartBeatData + packetSeparator);
                        } else if (heartBeatData instanceof byte[]) {
                            ByteBuf buf = Unpooled.copiedBuffer((byte[]) heartBeatData);
                            ctx.channel().writeAndFlush(buf);
                        } else {
                            Log.e(TAG, "userEventTriggered: 心跳数据类型错误");
                        }
                    }
                } else {
                    Log.e(TAG, "不发送心跳");
                }
            }
        }
    }

    /**
     * <p>客户端上线</p>
     *
     * @param ctx ChannelHandlerContext
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        Log.e(TAG, "连接成功");
        listener.onClientStatusConnectChanged(Const.STATUS_CONNECT_SUCCESS, index);
    }

    /**
     * <p>客户端下线</p>
     *
     * @param ctx ChannelHandlerContext
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Log.e(TAG, "断开连接");
    }

    /**
     * 客户端收到消息
     *
     * @param channelHandlerContext ChannelHandlerContext
     * @param data                  消息
     */
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, byte[] data) {
        // 将数据转成字符串
        String msg = new String(data, StandardCharsets.UTF_8);

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
                byte[] photo = Base64.decode(stringBuffer.toString(), Base64.DEFAULT);
                listener.onPhotoMessage(photo, index);
                photoSize = 0;
                isPhoto = false;
                // 清空数据
                stringBuffer.delete(0, stringBuffer.length());
            }
        } else {
            listener.onTextMessage(data, index);
        }
    }

    /**
     * @param ctx   ChannelHandlerContext
     * @param cause 异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Log.e(TAG, "发生异常");
        listener.onClientStatusConnectChanged(Const.STATUS_CONNECT_ERROR, index);
        cause.printStackTrace();
        ctx.close();
    }
}
