package com.test.server.serviceImpl;

import com.rpc.service.HelloService;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-15 22:34
 **/
public class FallbackHelloServiceImpl3 implements HelloService {

    int i = 0;

    @Override
    public String sayHello() {
        return "托底托底托底：：：：：：我是Service3 的托底实现类, 触发我: " + (i++);
    }

    @Override
    public String sayHello(String str) {

        System.out.println(str + "    托底调用");
        System.out.println(str + "   托底托底托底：：：：：：我是Service3 的托底实现类, 触发我: " + (i));

        return str + "   托底托底托底：：：：：：我是Service3 的托底实现类, 触发我: " + (i++);
    }
}
