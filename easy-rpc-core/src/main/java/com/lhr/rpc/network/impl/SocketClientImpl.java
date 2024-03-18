package com.lhr.rpc.network.impl;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.network.Client;
import lombok.extern.slf4j.Slf4j;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-11 01:09
 **/
@Slf4j
public class SocketClientImpl implements Client {

    @Override
    public Object send(Invocation invocation) {
        Object o = null;
        try {
            String[] ipPort = invocation.getHost().split(":");
            Socket socket = new Socket(ipPort[0], Integer.parseInt(ipPort[1]));
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            oos.writeObject(invocation);
            oos.flush();

            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
            o = ois.readObject();
            oos.close();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return o;
    }

    @Override
    public Object heartSend(Invocation invocation) {
        return null;
    }
}
