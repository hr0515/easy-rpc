package com.test.server.service;

import com.lhr.rpc.proxy.RpcService;
import com.rpc.service.HelloService;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-12 09:15
 **/

@RpcService
public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello() {
        return "hello easy rpc!";
    }
}
