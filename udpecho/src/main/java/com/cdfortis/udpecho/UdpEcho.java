package com.cdfortis.udpecho;

import android.nfc.Tag;
import android.util.Log;
import android.util.Pair;
import android.util.SparseLongArray;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;


/**
 * Created by Diuy on 2017/5/24.
 * UdpEcho
 */

public class UdpEcho {
    private String ip;
    private int port;
    private int speed;
    private int size;
    private int tag;
    private OutputStream os;
    private static final String TAG = "UdpEcho";

    private boolean sendRunFlag;
    private boolean recvRunFlag;
    private Thread sendThread;
    private Thread recvThread;

    private DatagramSocket so = null;

    private long allSendCount = 0;
    private long allSendSize = 0;
    private long allRecvCount = 0;
    private long allRecvSize = 0;
    private long startTime = 0;
    private long stopTime = 0;
    private Map<Integer, Long> sendTimes = new HashMap<>();
    private Map<Integer, Long> recvTimes = new HashMap<>();

    public UdpEcho(String ip, int port, int speed, int size, int tag, OutputStream os) {
        this.ip = ip;
        this.port = port;
        this.speed = speed;
        this.size = size;
        this.tag = tag;
        this.os = os;
    }


    public boolean start() {
        if (so != null)
            return true;

        try {
            so = new DatagramSocket();
            so.setSendBufferSize(Constant.BUFFER_SIZE);
            so.setReceiveBufferSize(Constant.BUFFER_SIZE);
            so.connect(InetAddress.getByName(ip), port);
        } catch (Exception e) {
            error("connect fail,ip:%s,port:%d,error:", ip, port, e.getLocalizedMessage());
            e.printStackTrace();
            if (so != null) {
                so.close();
            }
            return false;
        }
        sendRunFlag = recvRunFlag = true;
        sendThread = new Thread(sendDataRunnable);
        recvThread = new Thread(recvDataRunnable);
        startTime = Util.getNowTime();
        sendThread.start();
        recvThread.start();
        debug("started,android!");
        debug("ip:%s,port:%d,tag:%d", ip, port, tag);
        debug("speed:%d,size:%d,countPerSecond:%05.2f", speed, size, speed * 1.0 / size);
        return true;
    }

    public void stopSend() {
        if (!sendRunFlag)
            return;
        stopTime = Util.getNowTime();
        sendRunFlag = false;
        debug("send stopped!");
    }

    public void stop() {
        if (!sendRunFlag && !recvRunFlag)
            return;
        sendRunFlag = recvRunFlag = false;

        //shutdown(so, SD_BOTH);
        so.close();
        try {
            recvThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            sendThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        recvThread = null;
        sendThread = null;
        so = null;
        printResult();
        debug("stopped!\r\n");
    }


    private Runnable sendDataRunnable = new Runnable() {
        @Override
        public void run() {
            byte  []data = new byte[size*2];
            data[0] = (byte)0xf1;
            data[1] = (byte) 0xf2;
            Util.setInt(data,4,tag);
            int index = 0;
            int realSize = 0;
            int sendSize = 0;
            long startTime = Util.getNowTime();
            long nowTime;
            long timeSpan = 0;

            while (sendRunFlag) {
                nowTime = Util.getNowTime();
                timeSpan = (nowTime - startTime);

                if (timeSpan>0 && (allSendSize * 1000 / timeSpan) > speed) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                    continue;
                }

                Util.setInt(data,8,index);
                realSize = size+Util.rand(-size / 3, size / 3);//随机把包大小加减1/3
                if (realSize < Constant.MIN_SIZE) {
                    realSize = Constant.MIN_SIZE;
                } else if (realSize > Constant.MAX_SIZE) {
                    realSize = Constant.MAX_SIZE;
                }
                //修改三个字节的为随机值
                data[Util.rand(Constant.MIN_SIZE, realSize)] = (byte)Util.rand(1, 255);
                data[Util.rand(Constant.MIN_SIZE, realSize)] = (byte)Util.rand(1, 255);
                data[Util.rand(Constant.MIN_SIZE, realSize)] = (byte)Util.rand(1, 255);
                sendTimes.put(index,nowTime);
                DatagramPacket dp = new DatagramPacket(data,0,realSize);
                try {
                    so.send(dp);
                    sendSize = realSize;
                } catch (IOException e) {
                    if(sendRunFlag)
                        error("send fail,error:" +e.toString());
                    break;
                }
                allSendCount++;
                allSendSize += sendSize;
                index++;
            }
        }
    };

    private Runnable recvDataRunnable = new Runnable() {
        @Override
        public void run() {
            byte[]  data = new byte[Constant.BUFFER_SIZE];
            int recvSize = 0;

            int t;
            int index;
            long nowTime;

            while (recvRunFlag) {
                DatagramPacket dp = new DatagramPacket(data,0,data.length);

                try {
                    so.receive(dp);
                    recvSize = dp.getLength();
                } catch (IOException e) {
                    if(recvRunFlag)
                        error("recv fail,error:"+e.toString());
                    break;
                }
                nowTime = Util.getNowTime();

                if (data[0] != (byte)0xf1 || data[1] != (byte)0xf2) {
                    error("recv sync error");
                    continue;
                }
                t = Util.getInt(data,4);
                index = Util.getInt(data,8);
                recvTimes.put(index,nowTime);
                if (t != tag) {
                    error("recv tag error,tag:" + tag);
                    continue;
                }
                allRecvCount++;
                allRecvSize += recvSize;
            }
        }
    };

    private void printResult() {
        long lastCount = allSendCount-allRecvCount  ;
        long lastSize = allSendSize-allRecvSize  ;
        double lastCountPercent = lastCount*100.00/ allSendCount;
        double lastSizePercent = lastSize*100.00/allSendSize;
        double sendCountPerSecond = allSendCount*1000.0 / (stopTime -startTime);
        double sendSizePerSecond = allSendSize*1000.0 / (stopTime - startTime);
        double sendSizePerPack = allSendSize *1.0/ allSendCount;
        double recvSizePerPack = allRecvSize *1.0 / allRecvCount;

        debug("发送时间(毫秒):" +(stopTime - startTime));

        if (lastCountPercent >= 100) {
            error("无法连接");
        } else if (lastCountPercent > 20) {
            error("丢包非常严重");
        } else if (lastCountPercent > 10) {
            debug("丢包比较严重");
        }
        if (lastCountPercent < 100) {

            debug("发送包数:%d,发送流量:%d,发送包平均大小:%.2f" ,allSendCount ,allSendSize ,sendSizePerPack);
            debug("接收包数:%d,接收流量:%d,接收包平均大小:%.2f" ,allRecvCount ,allRecvSize ,recvSizePerPack);
            debug("每秒发送包数:%.2f" ,sendCountPerSecond ,sendSizePerSecond);
            debug("丢包数量:%d,丢包流量:%d" ,lastCount,lastSize);
            debug("丢包数量百分比:%.2f%%,丢包流量百分比:%.2f%%" ,lastCountPercent , lastSizePercent);

            Map<Pair<Long,Long>,Integer> times = new TreeMap<>(new Comparator<Pair<Long,Long>>(){

                @Override
                public int compare(Pair<Long, Long> o1, Pair<Long, Long> o2) {
                    return (int)(o1.first-o2.first);
                }
            });
            times.put(new Pair<>(0L, 10L),0);
            times.put(new Pair<>(10L, 20L),0);
            times.put(new Pair<>(20L, 50L),0);
            times.put(new Pair<>(50L, 100L),0);
            times.put(new Pair<>(100L, 150L),0);
            times.put(new Pair<>(150L, 200L),0);
            times.put(new Pair<>(200L, 300L),0);
            times.put(new Pair<>(300L, 500L),0);
            times.put(new Pair<>(500L, 700L),0);
            times.put(new Pair<>(700L, 1000L),0);
            times.put(new Pair<>(1000L, 1500L),0);
            times.put(new Pair<>(1500L, 2000L),0);
            times.put(new Pair<>(2000L, 3000L),0);
            times.put(new Pair<>(3000L, 4000L),0);
            times.put(new Pair<>(4000L, 5000L),0);
            times.put(new Pair<>(5000L, 7000L),0);
            times.put(new Pair<>(7000L, 10000L),0);
            times.put(new Pair<>(10000L, 1000000000L),0);
            for ( Map.Entry<Integer, Long> item : recvTimes.entrySet()){
                long t = item.getValue()-sendTimes.get(item.getKey());
                for (Map.Entry<Pair<Long,Long>,Integer> item1 :times.entrySet() ){
                    if(t>= item1.getKey().first && t < item1.getKey().second){
                        item1.setValue(item1.getValue()+1);
                        break;
                    }
                }
            }

            debug("接收数据包详情");
            for (Map.Entry<Pair<Long,Long>,Integer>  item :times.entrySet()) {
                long d1 = item.getKey().first;
                long d2 = item.getKey().second;
                int count = item.getValue();
                if (count > 0) {
                    double p = count*100.0 / allRecvCount;
                    debug("时间(毫秒):[%04d,%04d),包数量:%05d,百分比:%05.2f%%" , d1 ,d2 , count, p);
                }
            }
        }
    }

    private void writeLog(int level, String format, Object... args) {
        String content = String.format(Locale.getDefault(), format, args);
        String levelStr;
        if (level == 0)
            levelStr = "DEBUG";
        else
            levelStr = "ERROR";
        String str = String.format(Locale.getDefault(), "%s: %s: %s\r\n", Util.nowTimeStr(), levelStr, content);
        if(os!=null){
            try {
                os.write(str.getBytes("UTF-8"));
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (level == 0) {
            Log.d(TAG, str);
        } else {
            Log.e(TAG, str);
        }
    }

    public void debug(String format, Object... args) {
        writeLog(0, format, args);
    }

    private void error(String format, Object... args) {
        writeLog(1, format, args);
    }
}
