package com.test.server.serviceImpl;

import com.rpc.service.HelloService;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-16 21:46
 **/
public class FallbackHelloServiceImpl4 implements HelloService {
    @Override
    public String sayHello() {
        return "熔断输出 Hello 降级托底";
    }

    @Override
    public String sayHello(String str) {
        return null;
    }
}
