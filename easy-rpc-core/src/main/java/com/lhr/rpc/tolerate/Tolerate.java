package com.lhr.rpc.tolerate;


import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.extern.SPI;

import java.util.concurrent.ExecutionException;

// com.lhr.rpc.tolerate.Tolerate
@SPI
public interface Tolerate {

    String DEFAULT_TYPE = "com.lhr.rpc.tolerate.DefaultType";
    String OUT_OF_TIME = "not_completed";

    boolean isOutOfTime(Object s);

    // 降级 or 熔断
    void enableCircuitBreaker(Invocation invocation, TolerateHandler handler) throws ExecutionException, InterruptedException;

    Object enableRateLimiting(Invocation invocation, int limitTime, int threads, TolerateHandler handler);

    // 强制打断 正在执行 send 函数的循环
    void interruptTryLoop();

    // 初始化 中断标志位
    void initialInterruptFlag();

}
