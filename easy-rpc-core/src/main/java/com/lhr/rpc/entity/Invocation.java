package com.lhr.rpc.entity;

import java.io.Serializable;
import java.util.Arrays;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-13 00:38
 **/

public class Invocation implements Serializable {
    private String interfaceName;           // 1  接口名
    private int versoin;                    // 2  版本
    private String methodName;              // 3  方法名
    private Class<?>[] methodParamTypes;    // 4  方法参数类型
    private Object[] methodParams;          // 5  方法参数
    private String host;                    // 6  主机地址:端口
    private String interfaceImplName;       // 7  接口实现类名
    private String fallbackImplName;        // 8  托底实现类
    private Object ret;                     // 9  返回对象
    private int retryTimes;                 // 10 重试次数
    private int currentTimes;               // 11 当前请求次数
    private int waitTime;                   // 12 最长等待时间 0 为不等待 快速失败
    private int recover;                    // 13 0 为不恢复 为降级  x秒后恢复状态
    private boolean isFallback;             // 14 是否执行 fallback
    private boolean singleton;              // 15 是否 单例调用
    private int limitTime;                  // 16 限流阈值
    private int threads;                    // 17 阈值 范围内 运行执行的最大线程数

    // Netty 序列化时  kryo 要求 必须有无参构造函数
    public Invocation() {
    }

    public String getKey() {
        return this.getHost() + this.getInterfaceImplName() + this.getCurrentTimes();
    }

    public void addCurrentTimes() {
        ++currentTimes;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public int getVersoin() {
        return versoin;
    }

    public void setVersoin(int versoin) {
        this.versoin = versoin;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getMethodParamTypes() {
        return methodParamTypes;
    }

    public void setMethodParamTypes(Class<?>[] methodParamTypes) {
        this.methodParamTypes = methodParamTypes;
    }

    public Object[] getMethodParams() {
        return methodParams;
    }

    public void setMethodParams(Object[] methodParams) {
        this.methodParams = methodParams;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getInterfaceImplName() {
        return interfaceImplName;
    }

    public void setInterfaceImplName(String interfaceImplName) {
        this.interfaceImplName = interfaceImplName;
    }

    public String getFallbackImplName() {
        return fallbackImplName;
    }

    public void setFallbackImplName(String fallbackImplName) {
        this.fallbackImplName = fallbackImplName;
    }

    public Object getRet() {
        return ret;
    }

    public void setRet(Object ret) {
        this.ret = ret;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    public int getCurrentTimes() {
        return currentTimes;
    }

    public void setCurrentTimes(int currentTimes) {
        this.currentTimes = currentTimes;
    }

    public int getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    public int getRecover() {
        return recover;
    }

    public void setRecover(int recover) {
        this.recover = recover;
    }

    public boolean isFallback() {
        return isFallback;
    }

    public void setFallback(boolean fallback) {
        isFallback = fallback;
    }


    public boolean isSingleton() {
        return singleton;
    }

    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }

    public int getLimitTime() {
        return limitTime;
    }

    public void setLimitTime(int limitTime) {
        this.limitTime = limitTime;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    @Override
    public String toString() {
        return "Invocation{" +
                "\n 接口服务名 interfaceName='" + interfaceName + '\'' +
                "\n 版本号 versoin=" + versoin +
                "\n 方法名 methodName='" + methodName + '\'' +
                "\n 方法参数类型 methodParamTypes=" + Arrays.toString(methodParamTypes) +
                "\n 方法参数 methodParams=" + Arrays.toString(methodParams) +
                "\n 主机 host='" + host + '\'' +
                "\n 接口实现类名 interfaceImplName='" + interfaceImplName + '\'' +
                "\n 托底接口实现类名 fallbackImplName='" + fallbackImplName + '\'' +
                "\n 返回值 ret=" + ret +
                "\n 尝试次数 retryTimes=" + retryTimes +
                "\n 当前次数 currentTimes=" + currentTimes +
                "\n 最长等待时间 waitTime=" + waitTime +
                "\n 熔断恢复时间 recover=" + recover +
                "\n 是否执行托底 isFallback=" + isFallback +
                "\n 是否单例加载 singleton=" + singleton +
                "\n}";
    }
}
