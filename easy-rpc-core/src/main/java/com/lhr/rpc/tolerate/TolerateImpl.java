package com.lhr.rpc.tolerate;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.extern.JdkSPI;
import com.lhr.rpc.network.impl.NettyClientImpl;
import com.lhr.rpc.registry.RegistryCenter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-14 23:19
 **/
@Slf4j
public class TolerateImpl implements Tolerate {

    private RegistryCenter registryCenter;
    ScheduledExecutorService faildServiceExecutor = Executors.newSingleThreadScheduledExecutor();  // 核心线程1一个 最大线程数 Int最大值
    public TolerateImpl() {
        registryCenter = JdkSPI.load(RegistryCenter.class);
    }

    private boolean isInterrupt = false;

    @Override
    public boolean isOutOfTime(Object s) {
        return s instanceof String && OUT_OF_TIME.equals(s);
    }

    // FIXME: 中断条件不对劲  应该都整合在  中断函数中， 而且下面的逻辑太乱了 应该很简单的 用 get 来判断 很愚蠢（可能是因为递归，很乱）
    public void enableCircuitBreaker(Invocation invocation, TolerateHandler handler) throws ExecutionException, InterruptedException {
        // 根本没绑定 fallback 接口 直接返回
        if (DEFAULT_TYPE.equals(invocation.getFallbackImplName())) return;

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
//            System.out.println("当前请求次数：" + invocation.getCurrentTimes() + " 总次数：" + invocation.getRetryTimes() + " 结果为：" + retFuture.get().getRet() +
//                    " 是否兜底：" + invocation.isFallback() + " ");
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
                        System.err.println(invocation);

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

    @Override
    public Object enableRateLimiting(Invocation invocation, TolerateHandler handler) {
        // 5秒内  请求量 大于 x  则准备备选队列 任务队列长度保持x个 其余应放到备选队列

        return handler.handle(invocation);
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
