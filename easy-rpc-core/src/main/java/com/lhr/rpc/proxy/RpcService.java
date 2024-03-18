package com.lhr.rpc.proxy;


import com.lhr.rpc.tolerate.DefaultType;

import java.lang.annotation.*;

/**
 * RPC service annotation, marked on the service implementation class
 *
 * @author shuang.kou
 * @createTime 2020年07月21日 13:11:00
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
public @interface RpcService {

    int version() default 0;

    Class<?> fallback() default DefaultType.class; // 绑定 fallback 实现类 默认Default类型不会触发 容错策略

    int retryTimes() default 0;  // 超时 发生时  重试的次数  0 出错 直接降级

    int waitTime() default 0; // 0 为不等待  将会 返回失败  谨慎设置

    int recover() default 0; // 0 为不恢复 为纯降级状态 任意整数x为熔断状态 x秒后恢复正常状态

    boolean singleton() default true;  // 是否为单例加载

    int limitTime() default 0; // 限流时间阈值  limitTime 与 threads 任一为0 则关闭限流

    int threads() default 0; // 在 阈值 区间内的最大线程数

}
