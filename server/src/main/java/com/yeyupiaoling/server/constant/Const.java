package com.yeyupiaoling.server.constant;

public class Const {
    // 监听的端口
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
