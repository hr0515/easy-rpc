package com.test.server.serviceImpl;

import com.lhr.rpc.proxy.RpcService;
import com.rpc.service.HelloService;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-12 09:15
 **/

@RpcService(version = 0, fallback = FallbackHelloServiceImpl.class, retryTimes = 2, waitTime = 2, recover = 0)
public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello() {
        try {
            // 给我睡死
            Thread.sleep(Integer.MAX_VALUE);
//            Thread.sleep(1500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "hello easy rpc!";
    }

    @Override
    public String sayHello(String str) {
        return null;
    }
}
