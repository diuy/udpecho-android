package com.cdfortis.udpecho;

/**
 * Created by Diuy on 2017/5/24.
 * Constant
 */

public class Constant {
    public static String DEFAULT_IP = "121.41.108.248";
    public static int DEFAULT_PORT =  45005;
    public static int DEFAULT_SPEED =2000;
    public static int DEFAULT_SIZE =30;

    public static int MAX_SPEED = 1024 * 50;//最大速度为50KB
    public static int BUFFER_SIZE = 1024 * 100;//收发缓存大小
    public static int HEAD_SIZE = 2 + 2 + 4 + 4;//命令包头大小
    public static int MAX_SIZE = 1472;//发送包的最大值
    public static int MIN_SIZE = HEAD_SIZE;//发送包最小值
}
