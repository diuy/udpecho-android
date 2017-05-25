package com.cdfortis.udpecho;

import android.widget.EditText;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Created by Diuy on 2017/5/24.
 * Util
 */

public class Util {
    private static Random random;
    private static SimpleDateFormat nowTimeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static SimpleDateFormat nowDateFormatter = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

    static {
        random = new Random(System.currentTimeMillis());
    }

    public static int stringToInt(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    public static int getEditTextIntValue(EditText text, int defaultValue) {
        return stringToInt(text.getText().toString(), defaultValue);
    }

    public static int rand(int min, int max) {
        if (min > max)
            throw new IllegalArgumentException("min > max");
        if (min == max)
            return min;
        return random.nextInt(max - min) + min;
    }

    public static long getNowTime() {
        return System.currentTimeMillis();
    }

    public static String nowTimeStr() {
        return nowTimeFormatter.format(new Date());
    }

    public static String nowDateStr() {
        return nowDateFormatter.format(new Date());
    }

    public static void setInt(byte[] buff, int offset, int n) {
        buff[0 + offset] = (byte) (n & 0xff);
        buff[1 + offset] = (byte) (n >> 8 & 0xff);
        buff[2 + offset] = (byte) (n >> 16 & 0xff);
        buff[3 + offset] = (byte) (n >> 24 & 0xff);
    }

    public static int getInt(byte[] buff, int offset) {
        int ret = 0;
        ret = (int) (buff[offset + 0] & 0xff)
                | (int) (buff[offset + 1] & 0xff) << 8
                | (int) (buff[offset + 2] & 0xff) << 16
                | (int) (buff[offset + 3] & 0xff) << 24;
        return ret;
    }

    public static String readText(File filePath) {
        FileInputStream fs = null;
        try {
            fs = new FileInputStream(filePath);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] data = new byte[1024 * 10];
            int readSize;
            while ((readSize = fs.read(data)) > 0) {
                os.write(data, 0, readSize);
            }
            return new String(os.toByteArray(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        } finally {
            if (fs != null) {
                try {
                    fs.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
