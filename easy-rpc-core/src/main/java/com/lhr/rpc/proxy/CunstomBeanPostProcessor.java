package com.lhr.rpc.proxy;


import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.exception.ServiceRegistryException;
import com.lhr.rpc.extern.RpcConfig;
import com.lhr.rpc.network.Client;
import com.lhr.rpc.network.impl.NettyClientImpl;
import com.lhr.rpc.registry.RegistryCenter;
import com.lhr.rpc.extern.JdkSPI;
import com.lhr.rpc.tolerate.Tolerate;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;

/**
 * call this method before creating the bean to see if the class is annotated
 * 在创建bean之前调用此方法，查看类是否被注释
 *
 * @author shuang.kou
 * @createTime 2020年07月14日 16:42:00
 * @modified LHR - 2024年3月14日 13点21分
 */
@Slf4j
@Component
public class CunstomBeanPostProcessor implements BeanPostProcessor {

    private int springBeanAmount;
    private int rpcServiceCount;
    private Client client;
    private RegistryCenter registryCenter;
    private static final int serverPort = RpcConfig.getInt("server.port");
    private final Tolerate tolerate;
    private final int limitTime = RpcConfig.getInt("tolerate.rate_limiting.limit_time");
    private final int threads = RpcConfig.getInt("tolerate.rate_limiting.threads");


    public CunstomBeanPostProcessor() {
        tolerate = JdkSPI.load(Tolerate.class);
        registryCenter = JdkSPI.load(RegistryCenter.class);
        client = JdkSPI.load(Client.class);
    }

    @SneakyThrows
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean.getClass().isAnnotationPresent(Component.class)) {
            log.info("[Spring] [{}] [{}] 被注解 [{}] 修饰", ++springBeanAmount, bean.getClass().getName(), Component.class.getCanonicalName());
        }
        if (bean.getClass().isAnnotationPresent(RpcService.class)) {
            log.info("[Rpc] [{}] [{}] 被注解 [{}] 修饰", ++rpcServiceCount, bean.getClass().getName(), RpcService.class.getCanonicalName());
            Class<?>[] interfaces = bean.getClass().getInterfaces();
            if (interfaces.length == 1) {
                RpcService rpcService = bean.getClass().getAnnotation(RpcService.class);
                String host = InetAddress.getLocalHost().getHostAddress() + ":" + serverPort;

                Invocation invocation = new Invocation();
                invocation.setInterfaceName(interfaces[0].getName());
                invocation.setInterfaceImplName(bean.getClass().getName());
                invocation.setFallbackImplName(rpcService.fallback().getName());
                invocation.setVersoin(rpcService.version());
                invocation.setHost(host);
                invocation.setWaitTime(rpcService.waitTime());
                invocation.setRetryTimes(rpcService.retryTimes());
                invocation.setRecover(rpcService.recover());
                invocation.setFallback(false);
                invocation.setSingleton(rpcService.singleton()); // 10 / 15
                registryCenter.registerService(invocation);
            } else {
                throw new ServiceRegistryException("[服务注册] 注册失败 服务仅允许有一个父接口");
            }
        }
        return bean;
    }

    @SneakyThrows
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Field[] declaredFields = bean.getClass().getDeclaredFields();
        for (Field declaredField : declaredFields) {
            RpcWired rpcWired = declaredField.getAnnotation(RpcWired.class);  //
            if (rpcWired != null) {
                Class<?> clazz = declaredField.getType();
                // InterfaceName(interfaces[0].getName());
                // InterfaceImplName(bean.getClass().getName());
                // FallbackImplName(rpcService.fallback().getName());
                // Versoin(rpcService.version());
                // Host(host);
                // WaitTime(rpcService.waitTime());
                // RetryTimes(rpcService.retryTimes());
                // Recover(rpcService.recover());
                // Fallback(false);
                // Singleton(rpcService.singleton());  10 / 15

                Object o = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, (Object proxy, Method method, Object[] args) -> {
                    // 就剩这仨了
                    Invocation invo = new Invocation();
                    invo.setInterfaceName(clazz.getName());
                    invo.setMethodName(method.getName());
                    invo.setMethodParamTypes(method.getParameterTypes());
                    invo.setMethodParams(args);
                    invo.setCurrentTimes(0);  // 14 / 15 还剩一个 setRet 在服务端设置的
                    Invocation invocation = registryCenter.lookupService(invo, rpcWired.version());
                    log.info("为 [{}] 增加动态代理 [{}] 为 [{}]", bean.getClass().getName(), clazz.getName(), invocation.getInterfaceImplName());

                    int i = NettyClientImpl.NettySingletonPool.safeClearFutures();// 每次send之前 清空 请求
                    // log.info("[初始化] 清理上次调用异步Future数 [{}] 个", i);
                    int i1 = NettyClientImpl.NettySingletonPool.safeClearConnections();
                    // log.info("[初始化] 清理上次调用Netty连接数 [{}] 个", i1);
                    tolerate.initialInterruptFlag();

                    if (limitTime == 0 || threads == 0) {
                        return client.send(invocation);
                    }else {
                        // 权限接管给 限流容错
                        return tolerate.enableRateLimiting(invocation, limitTime, threads, invParam -> client.send(invParam));
                    }
                });
                declaredField.setAccessible(true);
                declaredField.set(bean, o);
            }
        }
        return bean;
    }
}
