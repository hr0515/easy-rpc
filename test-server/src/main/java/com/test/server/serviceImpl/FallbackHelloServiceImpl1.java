package com.test.server.serviceImpl;

import com.rpc.service.HelloService;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-17 14:06
 **/
public class FallbackHelloServiceImpl1 implements HelloService {

    int i = 1;

    @Override
    public String sayHello() {
        return (i++) + "  hello easy rpc!11111111111111111111111 托底的...........";
    }

    @Override
    public String sayHello(String str) {
        return str + " hello easy rpc! 实现类1  托底的...........";
    }
}
