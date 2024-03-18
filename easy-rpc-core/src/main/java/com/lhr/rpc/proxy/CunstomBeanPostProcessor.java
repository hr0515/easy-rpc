package com.lhr.rpc.proxy;


import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.exception.ServiceRegistryException;
import com.lhr.rpc.extern.RpcConfig;
import com.lhr.rpc.network.Client;
import com.lhr.rpc.registry.RegistryCenter;
import com.lhr.rpc.extern.JdkSPI;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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

    public CunstomBeanPostProcessor() {
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
                registryCenter.registerService(interfaces[0].getName(), bean.getClass().getName(), rpcService.version(), host);
            }else {
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
                Invocation serviceInfo = registryCenter.lookupService(clazz.getName(), rpcWired.version());
                log.info("为 [{}] 增加动态代理 [{}] 为 [{}]", bean.getClass().getName(), clazz.getName(), serviceInfo.getInterfaceImplName());
                Object o = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, (Object proxy, Method method, Object[] args) -> {
                    Invocation invocation = new Invocation(clazz.getName(), rpcWired.version(), method.getName(), method.getParameterTypes(), args);
                    invocation.setHost(serviceInfo.getHost());
                    invocation.setInterfaceImplName(serviceInfo.getInterfaceImplName());
                    return client.send(invocation);
                });
                declaredField.setAccessible(true);
                declaredField.set(bean, o);
            }
        }
        return bean;
    }
}
