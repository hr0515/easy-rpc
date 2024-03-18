package com.lhr.rpc;

import com.lhr.rpc.extern.JdkSPI;
import com.lhr.rpc.extern.RpcConfig;
import com.lhr.rpc.network.impl.NettyServerImpl;
import com.lhr.rpc.registry.RegistryCenter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

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
}
