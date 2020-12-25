package com.yeyupiaoling.nettysendphoto.constant;

public class Const {
    // 连接服务状态
    public final static int STATUS_CONNECT_SUCCESS = 1;
    public final static int STATUS_CONNECT_CLOSED = 0;
    public final static int STATUS_CONNECT_ERROR = -1;

    // 服务端IP和端口
    public static final String HOST = "192.168.43.1";
    public static final int TCP_PORT = 1088;

    // 分隔符
    public static final String PACKET_SEPARATOR = System.getProperty("line.separator");
    // 心跳数据
    public static final String HEART_BEAT_DATA = "心跳数据";
    // 匹配图片大小
    public static final String PHOTO_SIZE_TEMPLATE = "Photo_Size:(\\d+)";
    // 设置最大包大小
    public static final int MAX_PACKET_LONG = 1000000;
}
