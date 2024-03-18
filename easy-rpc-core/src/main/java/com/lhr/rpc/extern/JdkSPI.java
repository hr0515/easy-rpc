package com.lhr.rpc.extern;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @description:
 * @author: LHR
 * @date: 2024-03-11 21:40
 **/
@Slf4j
public class JdkSPI {

    // 静态内部类(保证CACHE本身单例) + 双锁较验(在CACHE是单例的前提上保证Loader类的单例)
    private static class SPISingletonPool {
        public static final ConcurrentHashMap<Class<?>, Object> CACHE = new ConcurrentHashMap<>();
    }

    public static <S> S load(Class<S> clazz) {
        S s = null;
        if (clazz.isAnnotationPresent(SPI.class) && clazz.isInterface()) {
            // SPI 注解修饰 + 接口 条件下
            s = clazz.cast(SPISingletonPool.CACHE.get(clazz));
            if (s != null) {
                log.info("发现SPI [单例池] [{}] 实现类为 [{}]", clazz.getName(), s.getClass().getName());
                return s;
            }else {
                synchronized (SPISingletonPool.CACHE) {
                    s = clazz.cast(SPISingletonPool.CACHE.get(clazz));
                    if (s == null) {
                        ServiceLoader<S> serviceLoader = ServiceLoader.load(clazz);
                        Iterator<S> iterator = serviceLoader.iterator();
                        if (iterator.hasNext()) {
                            s = serviceLoader.iterator().next();
                            SPISingletonPool.CACHE.put(clazz, s);
                            log.info("发现SPI [{}] 实现类为 [{}]", clazz.getName(), s.getClass().getName());
                        }else {
                            log.info("发现SPI [未处理] [{}] 实现类为 [null]", clazz.getName());
                        }
                    }
                }
            }
        }
        return s;
    }




}
