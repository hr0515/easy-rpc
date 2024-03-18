package com.lhr.rpc.entity;

import java.io.Serializable;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-13 00:38
 **/

public class Invocation implements Serializable {
    private String interfaceName;           // 接口名
    private int versoin;                    // 版本
    private String methodName;              // 方法名
    private Class<?>[] methodParamTypes;    // 方法参数类型
    private Object[] methodParams;          // 方法参数
    private String host;                    // 主机地址:端口
    private String interfaceImplName;       // 接口实现类名
    private Object ret;


    // Netty 序列化时  kryo 要求 必须有无参构造函数
    public Invocation() {
    }

    public Invocation(String interfaceName, int versoin, String methodName, Class<?>[] methodParamTypes, Object[] methodParams, String host, String interfaceImplName) {
        this.interfaceName = interfaceName;
        this.versoin = versoin;
        this.methodName = methodName;
        this.methodParamTypes = methodParamTypes;
        this.methodParams = methodParams;
        this.host = host;
        this.interfaceImplName = interfaceImplName;
    }

    public Invocation(String interfaceName, int versoin, String methodName, Class<?>[] methodParamTypes, Object[] methodParams) {
        this.interfaceName = interfaceName;
        this.versoin = versoin;
        this.methodName = methodName;
        this.methodParamTypes = methodParamTypes;
        this.methodParams = methodParams;
    }

    public Invocation(String host, String interfaceImplName) {
        this.host = host;
        this.interfaceImplName = interfaceImplName;
    }

    public Object getRet() {
        return ret;
    }

    public void setRet(Object ret) {
        this.ret = ret;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setInterfaceImplName(String interfaceImplName) {
        this.interfaceImplName = interfaceImplName;
    }

    public String getMethodName() {
        return methodName;
    }

    public Class<?>[] getMethodParamTypes() {
        return methodParamTypes;
    }

    public Object[] getMethodParams() {
        return methodParams;
    }

    public String getHost() {
        return host;
    }

    public String getInterfaceImplName() {
        return interfaceImplName;
    }
}
