package com.test.server.service;

import com.lhr.rpc.proxy.RpcService;
import com.rpc.service.HelloService;

/**
 * @description: 为 尝试次数的高级测试 通过开关来控制 其函数的
 * @author: LHR
 * @date: 2024-03-15 22:32
 **/
@RpcService(version = 2, fallback = FallbackHelloServiceImpl3.class, retryTimes = 1, waitTime = 2, recover = 0)
public class HelloServiceImpl3 implements HelloService {
    int i = 1;
    boolean b = false;
    @Override
    public String sayHello() {
        b = !b;  // 开关切换  第二次 访问的时候 才可以正常执行

        System.out.println("当前为" + b);
        if (b) {
            System.out.println("不能运行的" + i);
            try {
                // 给我睡死
                Thread.sleep(Integer.MAX_VALUE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }else{
            System.out.println("可以运行的" + i);
        }
        return "我是Service3, 触发我: " + (i++);
    }

    @Override
    public String sayHello(String str) {
        b = !b;  // 开关切换  第二次 访问的时候 才可以正常执行

        System.out.println("当前为" + b);
        if (b) {
            System.out.println("不能运行的" + i);
            try {
                // 给我睡死
                Thread.sleep(Integer.MAX_VALUE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }else{
            System.out.println("可以运行的" + i);
            System.out.println(str + "   我是Service3, 触发我: " + (i));
        }

        return str + "   我是Service3, 触发我: " + (i++);
    }
}
