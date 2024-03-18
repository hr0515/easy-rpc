package com.test.server.service;

import com.lhr.rpc.proxy.RpcService;
import com.rpc.service.HelloService;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-12 19:54
 **/
@RpcService(version = 1)
public class HelloServiceImpl1 implements HelloService {
    @Override
    public String sayHello() {
        return "hello easy rpc!11111111111111111111111";
    }
}
