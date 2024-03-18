package com.lhr.rpc.extern;


import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-13 09:42
 **/
public class RpcConfig {
    private final static Properties properties;

    static {
        try (InputStream in = RpcConfig.class.getResourceAsStream("/rpc.properties")) {
            properties = new Properties();
            properties.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static boolean getBoolean(String str) {
        return Boolean.parseBoolean(properties.getProperty(str).trim());
    }

    public static String getString(String str) {
        return properties.getProperty(str).trim();
    }

    public static int getInt(String str) {
        return Integer.parseInt(properties.getProperty(str).trim());
    }

    public static String[] getStringArray(String str) {
        String array = properties.getProperty(str).trim();
        int left = array.indexOf("{");
        int right = array.lastIndexOf("}");
        if (left == -1) {
            left = array.indexOf("[");
            right = array.lastIndexOf("]");
        }
        String substring = array.substring(left + 1, right);
        return substring.replace(" ", "").replace("\"", "").split(",");
    }

    public static String[] getBeanPaths(String str) {
        String[] array = getStringArray(str);
        String[] ret = new String[array.length + 1];
        System.arraycopy(array, 0, ret, 0, array.length);
        ret[array.length] = "com.lhr.rpc";
        array = null;
        return ret;
    }
}