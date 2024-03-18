package com.lhr.rpc.tolerate;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.extern.JdkSPI;
import com.lhr.rpc.extern.RpcConfig;
import com.lhr.rpc.network.impl.NettyClientImpl;
import com.lhr.rpc.registry.RegistryCenter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-14 23:19
 **/
@Slf4j
public class TolerateImpl implements Tolerate {

    private RegistryCenter registryCenter;
    ScheduledExecutorService faildServiceExecutor = Executors.newSingleThreadScheduledExecutor();  // 核心线程1一个 最大线程数 Int最大值
    private ExecutorService invocationExecutor = Executors.newCachedThreadPool();
    private final int limitTime = RpcConfig.getInt("tolerate.rate_limiting.limit_time");
    private final int threads = RpcConfig.getInt("tolerate.rate_limiting.threads");

    public TolerateImpl() {
        registryCenter = JdkSPI.load(RegistryCenter.class);
    }

    private boolean isInterrupt = false;

    @Override
    public boolean isOutOfTime(Object s) {
        return s instanceof String && OUT_OF_TIME.equals(s);
    }

    // FIXME: 中断条件可以再优化  应该都整合在 中断函数中
    public void enableCircuitBreaker(Invocation invocation, TolerateHandler handler) throws ExecutionException, InterruptedException {
        // 根本没绑定 fallback 接口 直接返回
        if (DEFAULT_TYPE.equals(invocation.getFallbackImplName()) || limitTime != 0 || threads != 0) return;

        // 判断超时  invocation.getWaitTime()
        String futureKey = invocation.getHost() + invocation.getInterfaceImplName() + invocation.getCurrentTimes();
        CompletableFuture<Invocation> retFuture = NettyClientImpl.NettySingletonPool.FUTURE_BACK.get(futureKey);
        Invocation invocationOutOfTime = new Invocation();
        invocationOutOfTime.setRet(OUT_OF_TIME);

        faildServiceExecutor.schedule(() -> {
            if (retFuture != null && !retFuture.isDone()) {
                retFuture.complete(invocationOutOfTime);
                NettyClientImpl.NettySingletonPool.FUTURE_BACK.get(futureKey).complete(invocationOutOfTime);
            }
        }, invocation.getWaitTime(), TimeUnit.SECONDS);
        // future 是否 已完成
        if (retFuture != null && !retFuture.isDone()) {
            // 查看结果是否超时
            if (this.isOutOfTime(retFuture.get().getRet())) {
                if (invocation.getCurrentTimes() >= invocation.getRetryTimes() || invocation.isFallback()) {
                    // 如果执行到这儿 证明 send 重复次数结束 执行  熔断 或 降级的逻辑
                    // 判断降级 / 熔断
                    // 如果 现在状态是 不启动fallback 那么需要 启动fallback 并且写进 注册中心
                    invocation.addCurrentTimes(); // 调用之前增加调用次数
                    if (!invocation.isFallback()) {
                        invocation.setFallback(true); // 服务端对此字段进行判断 来执行 fallback
                        registryCenter.updateService(invocation);
                    }
                    if (invocation.getRecover() == 0) {
                        // 降级状态
                        log.info("[容错] [降级] 发送 重试第 [{}]/[{}] 次 [{}] [{}]", invocation.getCurrentTimes(), invocation.getRetryTimes(), invocation.getInterfaceImplName(), invocation.getFallbackImplName());
                        // 记录到注册中心 current 执行次数  防止再进入循环 以后就一直走降级
                    }else {
                        // 熔断状态
                        log.info("[容错] [触发熔断] 发送 重试第 [{}]/[{}] 次 恢复时间为 [{}] s", invocation.getCurrentTimes() + 1, invocation.getRetryTimes(), invocation.getRecover());
                        faildServiceExecutor.schedule(() -> {
                            log.info("[熔断] [恢复原状态]");
                            invocation.setFallback(false);
                            invocation.setCurrentTimes(0);
                            registryCenter.updateService(invocation);
                        }, invocation.getRecover(), TimeUnit.SECONDS);
                    }
                    handler.handle(invocation);
                    return;
                }
                // 重新发送
                while (invocation.getCurrentTimes() < invocation.getRetryTimes() && !invocation.isFallback()) {
                    if (isInterrupt) {
                        isInterrupt = false;
                        log.info("[重试发送] [恢复原状态] [触发中断] [中途完成] [{}]", invocation.getInterfaceImplName());
                        invocation.setCurrentTimes(0);
                        invocation.setFallback(false);
                        registryCenter.updateService(invocation);
                        return;
                    }
                    log.info("[容错] 发送 重试第 [{}]/[{}] 次", invocation.getCurrentTimes(), invocation.getRetryTimes());
                    invocation.addCurrentTimes();
                    handler.handle(invocation);// handle 就是 send
                }
            }
        }
    }


    private static class Pool{
        private static final Map<String, Invocation> INVOCATION_CACHE = new ConcurrentHashMap<>();
        private static final AtomicInteger currentInvocationTimes = new AtomicInteger(0);
        private static final AtomicInteger invocationId = new AtomicInteger(0);
    }


    /**
     * 令牌桶算法：在一个固定容量的桶内存储一定数量的请求令牌，每个请求需要获取一个令牌才能执行，请求完成则释放令牌以供其他请求使用。
     * 漏桶算法：在一个固定容量的桶中不断加入请求，请求会从桶底部以常量速率流出，当桶满时即拒绝请求。(我使用的)
     * 计数器算法：设置请求速率、同时请求数、并发请求数等参数，并实现监控和统计服务。
     * FIXME: 功能不完整  有调用失败的可能
     * @param invocation
     * @param handler
     * @return
     */
    @Override
    public Object enableRateLimiting(Invocation invocation, int limitTime, int threads, TolerateHandler handler) {
        try {
            // 累计 定时 invocation
            CompletableFuture<Boolean> futureLimitTime = new CompletableFuture<>();
            faildServiceExecutor.schedule(() -> {
                if (Pool.INVOCATION_CACHE.size() > threads) {
                    futureLimitTime.complete(true);
                }else {
                    futureLimitTime.complete(false);
                }
            }, limitTime, TimeUnit.SECONDS);
            invocationExecutor.execute(()->{
                Pool.INVOCATION_CACHE.put(invocation.getKey() + Pool.invocationId.getAndIncrement(), invocation);
            });

            // 加锁 send 对 限流的 请求 进行区分
            synchronized (Pool.INVOCATION_CACHE) {
                if (futureLimitTime.get()) {
                    // i++ < limitTimes
                    if (Pool.currentInvocationTimes.get() < limitTime) {
                        // 限流状态下 的 正常请求
                        return handler.handle(Pool.INVOCATION_CACHE.remove(invocation.getKey() + Pool.currentInvocationTimes.getAndIncrement()));
                    }else {
                        // 限流状态下 的 废弃请求
                        Invocation invocation1 = Pool.INVOCATION_CACHE.remove(invocation.getKey() + Pool.currentInvocationTimes.getAndIncrement());
                        invocation1.setFallback(true);
                        return handler.handle(invocation1);
                    }
                }else {
                    // 配置了限流条件 但请求数量未达到限流要求
                    return handler.handle(Pool.INVOCATION_CACHE.remove(invocation.getKey() + Pool.currentInvocationTimes.getAndIncrement()));
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 它的触发点在 每次 Netty返回数据的时候 这样的话 会有上次的操作 影响到它的 中断标志 所以有下面的函数 每次send之前 初始化成false
    @Override
    public void interruptTryLoop() {
        synchronized (TolerateImpl.class) {
            isInterrupt = true;
        }
    }
    //初始化为false 每次send之前调用  因为send要进行一个递归 所以这些对数据的初始化 和 清除等操作只能放在send之前
    @Override
    public void initialInterruptFlag() {
        isInterrupt = false;
    }
}
