package com.lhr.rpc.network.impl;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.extern.RpcConfig;
import com.lhr.rpc.network.Server;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-11 01:09
 **/
@Slf4j
public class SocketServerImpl implements Server {

    private final static boolean RUNNING = true;
    private Socket acceptSocket;

    public SocketServerImpl() {
        int serverPort = RpcConfig.getInt("server.port");
        try {
            ServerSocket serverSocket = new ServerSocket(serverPort);
            log.info("创建 Socket 完成 等待接受 Socket 请求");
            while (RUNNING) {
                acceptSocket = serverSocket.accept();
                receive();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receive() {
        try {
            InputStream in = acceptSocket.getInputStream();
            OutputStream out = acceptSocket.getOutputStream();

            ObjectInputStream ois = new ObjectInputStream(in);
            ObjectOutputStream oos = new ObjectOutputStream(out);
            // 获取方法信息
            Invocation invocation = (Invocation) ois.readObject();

            // 从服务器注册表找到具体的类
            Class<?> clazz = Class.forName(invocation.getInterfaceImplName());

            // 调用服务
            Method method = clazz.getMethod(invocation.getMethodName(), invocation.getMethodParamTypes());
            Object o = method.invoke(clazz.newInstance(), invocation.getMethodParams());

            // 直接返回一个对象
            oos.writeObject(o);
            oos.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void heartReceive() {

    }
}
