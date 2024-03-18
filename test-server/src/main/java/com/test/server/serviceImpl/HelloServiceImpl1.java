package com.test.server.serviceImpl;

import com.lhr.rpc.proxy.RpcService;
import com.rpc.service.HelloService;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-12 19:54
 **/
//@RpcService(version = 1, fallback = FallbackHelloServiceImpl1.class)  // 测限流的时候打开
@RpcService(version = 1)
public class HelloServiceImpl1 implements HelloService {

    int i = 1;

    @Override
    public String sayHello() {
        return (i++) + "  hello easy rpc!11111111111111111111111";
    }

    @Override
    public String sayHello(String str) {
        return str + " hello easy rpc! 实现类1";
    }
}
