package com.test.client;

import com.lhr.rpc.proxy.RpcWired;
import com.lhr.rpc.proxy.EnableRpcService;
import com.rpc.service.HelloService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-12 09:09
 **/

@Component
@EnableRpcService
public class TestClient {

    @RpcWired
    HelloService helloService0;  // @RpcService(version = 0, fallback = FallbackHelloServiceImpl.class, retryTimes = 2, waitTime = 2, recover = 0)

    @RpcWired(version = 1)
    HelloService helloService1;  // @RpcService(version = 1)
                                // 或 @RpcService(version = 1, fallback = FallbackHelloServiceImpl1.class)  // 测限流的时候打开

    @RpcWired(version = 2)
    HelloService helloService2;  // @RpcService(version = 2, fallback = FallbackHelloServiceImpl3.class, retryTimes = 1, waitTime = 2, recover = 0)

    @RpcWired(version = 3)
    HelloService helloService3;  // @RpcService(version = 3, fallback = FallbackHelloServiceImpl4.class, retryTimes = 2, waitTime = 2, recover = 15)


    public static void main(String[] args) throws ExecutionException, InterruptedException {

        AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext(TestClient.class);
        TestClient bean = annotationConfigApplicationContext.getBean(TestClient.class);
        System.out.println("Bean对象" + bean);

        // 正常调用
        // int version() default 0;
        // Class<?> fallback() default DefaultType.class; // 绑定 fallback 实现类 默认Default类型不会触发 容错策略
        // int retryTimes() default 0;  // 超时 发生时  重试的次数  0 出错 直接降级
        // int waitTime() default 0; // 0 为不等待  将会 返回失败  谨慎设置
        // int recover() default 0; // 0 为不恢复 为纯降级状态 任意整数x为熔断状态 x秒后恢复正常状态
        // boolean singleton() default true;  // 是否为单例加载
        System.out.println("正常调用1: " + bean.helloService1.sayHello());
        System.out.println("正常调用2: " + bean.helloService1.sayHello());
        System.out.println("正常调用3: " + bean.helloService1.sayHello());

        // 降级测试
        // @RpcService(version = 0, fallback = FallbackHelloServiceImpl.class, retryTimes = 2, waitTime = 2, recover = 0)
        // 重试次数两次  自己调用一次  重试第一次  重试第二次直接降级 （一共调用三次）
        // 等待时间2秒
        System.out.println("降级测试1: " + bean.helloService0.sayHello());
        System.out.println("降级测试2: " + bean.helloService0.sayHello());
        System.out.println("降级测试3: " + bean.helloService0.sayHello());

        // 重试测试   尝试 修改 HelloServiceImpl3 的 retryTimes 获得更多用例  0失败即降级 1重试一次 2重试两次
        // @RpcService(version = 2, fallback = FallbackHelloServiceImpl3.class, retryTimes = 0, waitTime = 2, recover = 0)
        // @RpcService(version = 2, fallback = FallbackHelloServiceImpl3.class, retryTimes = 1, waitTime = 2, recover = 0)
        // @RpcService(version = 2, fallback = FallbackHelloServiceImpl3.class, retryTimes = 2, waitTime = 2, recover = 0)
        // 但是函数里面 是一个开关设置   重试一次 即可调用成功  设置成2 他会触发中断 提前完成
        System.out.println("重试测试1: " + bean.helloService2.sayHello("第1次调用"));
        System.out.println("重试测试2: " + bean.helloService2.sayHello("第2次调用"));
        System.out.println("重试测试3: " + bean.helloService2.sayHello("第3次调用"));
        System.out.println("重试测试4: " + bean.helloService2.sayHello("第4次调用"));

        // 熔断测试
        // @RpcService(version = 3, fallback = FallbackHelloServiceImpl4.class, retryTimes = 2, waitTime = 2, recover = 15)
        // 等待两秒  重试两次  熔断介入 20秒后 恢复
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        System.out.println("第1次: " + bean.helloService3.sayHello());  // 重试到熔断 调用   开始计时20秒   2 * 2 约等于 5秒 还剩 10秒
        System.out.println("第2次: " + bean.helloService3.sayHello());  // 这个是降级的结果
        // 5 秒过后 再去执行  此时应该是降级  还剩5秒  这个结果应该还是降级的
        CompletableFuture<Object> future = new CompletableFuture<>();
        scheduledExecutorService.schedule(new Runnable() {
            @Override
            public void run() {
                System.out.println("第3次 5秒后: " + bean.helloService3.sayHello());
                future.complete(true);
            }
        }, 5, TimeUnit.SECONDS);
        future.get();
        CompletableFuture<Object> future1 = new CompletableFuture<>();
        // 再等10秒 执行一次 应该会执行到原方法  但是原方法是 睡死的 所以又会触发 熔断
        scheduledExecutorService.schedule(new Runnable() {
            @Override
            public void run() {
                System.out.println("第4次 10秒后: " + bean.helloService3.sayHello());
                future1.complete(true);
            }
        }, 10, TimeUnit.SECONDS);
        future1.get();
        System.out.println("第5次: " + bean.helloService3.sayHello()); // 共调用 5次

        System.out.println("程序没有阻塞成功执行完成");
    }

    public static void main1(String[] args) {
        // 限流测试 需要该 HelloServiceImpl1 的RpcService注解  目前仅支持多线程 runnable中 仅存在一个 Rpc调用
        // 对没有 限流措施的 普通函数进行测试
        // 循环十次  每次发送两个请求  理应收到20个请求 但是实际上服务端显示写回 数据很多  客户端输出只有两个
//        ExecutorService executorService = Executors.newFixedThreadPool(10);
//        for (int i = 0; i < 10; i++) {
//            int finalI = i;
//            executorService.execute(()-> {
//                System.out.println(bean.helloService1.sayHello(finalI + " : 1"));
//            });
//        }
    }
}
