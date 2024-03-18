package com.test.server.service;

import com.rpc.service.HelloService;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-15 09:36
 **/
public class FallbackHelloServiceImpl implements HelloService {
    @Override
    public String sayHello() {
        return "[托底函数]  哟 我来了!";
    }

    @Override
    public String sayHello(String str) {
        return null;
    }
}
