package com.lhr.rpc;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.extern.JdkSPI;
import com.lhr.rpc.extern.RpcConfig;
import com.lhr.rpc.network.impl.NettyServerImpl;
import com.lhr.rpc.registry.RegistryCenter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.concurrent.*;

/**
 * @description: 框架能跑的前提是 触发了 Spring的 自定义包扫描器完成Bean的自定义注册 和 实现了Bean后Processor
 * @author: LHR
 * @date: 2024-03-09 22:47
 **/
public class BootStrapRpcServer {
    /**
     * 起项目之前一定要 new 一个 ApplicationContext 触发上述
     * @param clzzz 此类上要保证 Component 修饰 交给 Spring 管理 (确保自定义包扫描器可以扫到)
     *              也要被 RpcServiceEnable 修饰 确保配置的包扫描路径加入 自定义扫描器
     */
    public static void start(Class<?> clzzz) {
        if (RpcConfig.getBoolean("boot.clear")) {
            JdkSPI.load(RegistryCenter.class).unregisterAllService();
        }
        new AnnotationConfigApplicationContext(clzzz);
        new NettyServerImpl();
    }


    public static void main(String[] args) throws ExecutionException, InterruptedException {

        RegistryCenter registryCenter = JdkSPI.load(RegistryCenter.class);

        Invocation invocation = registryCenter.lookupService("com.rpc.service.HelloService", 1);




        CompletableFuture<Object> objectCompletableFuture = new CompletableFuture<>();
//        objectCompletableFuture.complete("abc");


        System.out.println(objectCompletableFuture.get());
        System.out.println(objectCompletableFuture.get());



    }
    public static void main1(String[] args) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<?> future = executor.submit(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    System.out.println("Working on task " + i);
                    Thread.sleep(1000); // 模拟任务执行时间
                }
            } catch (InterruptedException e) {
                System.out.println("Task interrupted");
            }
        });

        // 等待一段时间后取消任务
        try {
            Thread.sleep(3000); // 等待3秒
            future.cancel(true); // 取消任务
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        executor.shutdown();
    }
}
