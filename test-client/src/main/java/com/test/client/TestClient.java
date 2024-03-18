package com.test.client;

import com.lhr.rpc.proxy.RpcWired;
import com.lhr.rpc.proxy.RpcServiceEnable;
import com.rpc.service.HelloService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-12 09:09
 **/

@Component
@RpcServiceEnable
public class TestClient {

    @RpcWired
    HelloService helloService0;

    @RpcWired(version = 1)
    HelloService helloService1;


    public static void main(String[] args) {

        AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext(TestClient.class);
        TestClient bean = annotationConfigApplicationContext.getBean(TestClient.class);
        System.out.println("Bean对象" + bean);
        System.out.println(bean.helloService0.sayHello());
//        System.out.println(bean.helloService0.sayHello());
        System.out.println(bean.helloService1.sayHello());


        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

        scheduledExecutorService.schedule(new Runnable() {
            @Override
            public void run() {
                System.out.println(bean.helloService1.sayHello());
            }
        }, 10, TimeUnit.SECONDS);

        scheduledExecutorService.schedule(new Runnable() {
            @Override
            public void run() {
                System.out.println(bean.helloService1.sayHello());
            }
        }, 25, TimeUnit.SECONDS);


    }


}
