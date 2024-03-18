package com.test.server.service;

import com.lhr.rpc.proxy.RpcService;
import com.rpc.service.HelloService;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-16 21:45
 **/

@RpcService(version = 3, fallback = FallbackHelloServiceImpl4.class, retryTimes = 2, waitTime = 2, recover = 15)
public class HelloServiceImpl4 implements HelloService {
    @Override
    public String sayHello() {

        try {
            // 给我睡死
            Thread.sleep(Integer.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return "熔断测试 Hello 的正常输出";
    }

    @Override
    public String sayHello(String str) {
        return null;
    }
}
