package com.lhr.rpc.network.impl;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.exception.ClientException;
import com.lhr.rpc.extern.JdkSPI;
import com.lhr.rpc.extern.RpcConfig;
import com.lhr.rpc.network.Client;
import com.lhr.rpc.serialize.impl.NettyKryoInvocationDecodeSerializer;
import com.lhr.rpc.serialize.impl.NettyKryoInvocationEncodeSerializer;
import com.lhr.rpc.tolerate.Tolerate;
import io.netty.channel.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-13 00:36
 **/
@Slf4j
public class NettyClientImpl implements Client {
    private static final int serverPort = RpcConfig.getInt("server.port");
    private final Bootstrap bootstrap;
    public static Tolerate tolerate;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public static class NettySingletonPool {
        public static final Map<String, Channel> CHANNEL_CONNECTIONS_CAHCE = new ConcurrentHashMap<>();
        public static final Map<String, CompletableFuture<Invocation>> FUTURE_BACK = Collections.synchronizedMap(new LinkedHashMap<>());
        // 这里要用到有序键值对 要么用队列（没有key 达不到查找效率）  要么用LinkedHashMap 但是得手动上锁
        public static int removeUnavailableFuture() {
            ArrayList<String> deleteKeys = new ArrayList<>();
            FUTURE_BACK.forEach((key, value) -> {
                if (value.isDone()) {
                    try {
                        if (tolerate.isOutOfTime(value.get().getRet())) {
                            deleteKeys.add(key);
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }
            });
            for (String deleteKey : deleteKeys) {
                FUTURE_BACK.remove(deleteKey);
            }
            return FUTURE_BACK.size();
        }
        public static int safeClearFutures() {
            int retLen = FUTURE_BACK.size();
            for (String s : FUTURE_BACK.keySet()) {
                CompletableFuture<Invocation> future = FUTURE_BACK.get(s);
                if (!future.isDone()) {
                    future.cancel(true); // 强制中断
                    // log.info("[清除] [强制中断] future: " + future);
                }/*else{

                    try {
                        log.info("[清除] [清除已完成] result: " + future.get().getRet());
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }*/
            }
            FUTURE_BACK.clear();
            return retLen;
        }
        public static int safeClearConnections() {
            int retLen = CHANNEL_CONNECTIONS_CAHCE.size();
            for (String s : CHANNEL_CONNECTIONS_CAHCE.keySet()) {
                Channel channel = CHANNEL_CONNECTIONS_CAHCE.get(s);
                // log.info("[清除] [关闭连接] " + channel);
                channel.closeFuture();
                channel.close();
            }
            CHANNEL_CONNECTIONS_CAHCE.clear();
            return retLen;
        }
    }

    public NettyClientImpl() {
        tolerate = JdkSPI.load(Tolerate.class);
        int writeTimes = RpcConfig.getInt("nerry.client_max_write");
        NioEventLoopGroup group = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        // 如果 writeTimes s没有收到写请求(证明服务端没有返回数据 也证明我们没有调服务) 则向服务端发送心跳请求
                        pipeline.addLast(new IdleStateHandler(0, writeTimes, 0, TimeUnit.SECONDS));
                        pipeline.addLast(new NettyKryoInvocationEncodeSerializer());
                        pipeline.addLast(new NettyKryoInvocationDecodeSerializer());
                        pipeline.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                // 服务器端发来的数据 执行 complete   get由阻塞变为运行
                                Invocation invocation = (Invocation) msg;
                                if (invocation.getRet() instanceof Number) {
                                    log.info("[Netty] [心跳包] 客户端 [{}:{}] 接受", InetAddress.getLocalHost().getHostAddress(), serverPort);
                                    return;
                                } else {
//                                    System.out.println("触发中断 提前完成：" + invocation.getRet());
                                    tolerate.interruptTryLoop();
                                }

                                // 记录它的 返回结果 如果出问题 这里是一直都不会被触发的   如果出问题 那只能是托底数据在触发
                                NettySingletonPool.FUTURE_BACK.get(invocation.getKey()).complete(invocation);
                                log.info("[Netty] 客户端 读取 服务端写回数据 [{}]", ((Invocation) msg).getRet());
                            }
                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                ctx.close();
                                throw new ClientException("[Netty] 客户端 异常", cause);
                            }
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                if(evt instanceof IdleStateEvent) {
                                    IdleStateEvent event = (IdleStateEvent) evt;
                                    if(IdleState.WRITER_IDLE.equals(event.state())) {
                                        Invocation invocation = new Invocation();
                                        invocation.setRet(2);
                                        ctx.writeAndFlush(invocation).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                                        log.info("[Netty] [心跳包] 客户端 [{}:{}] 发送", InetAddress.getLocalHost().getHostAddress(), serverPort);
                                    }
                                }
                                super.userEventTriggered(ctx, evt);
                            }
                        });
                    }
                });
    }

    @Override
    public Object send(Invocation invocation) {
        try {
            CompletableFuture<Invocation> retFuture = new CompletableFuture<>();
            String host = invocation.getHost();
            // 连接服务端  从连接池中拿一个  因为下面要递归调用 要处理好 递归调用的上下文 所以 建立的连接 和 异步对象future都得存到集合里
            Channel channel = NettySingletonPool.CHANNEL_CONNECTIONS_CAHCE.get(invocation.getKey());

            if (channel == null || !channel.isOpen()) {
                CompletableFuture<Channel> fc = new CompletableFuture<>();
                String[] split = host.split(":");
                bootstrap.connect(split[0], Integer.parseInt(split[1])).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        log.info("[Netty] 客户端 成功建立连接到 [{}]", host);
                        fc.complete(future.channel());
                    } else {
                        throw new ClientException("[Netty] 客户端 连接失败到 " + host, future.cause());
                    }
                });
                // 利用单例的 Future 处理返回来的异步数据
                channel = fc.get();
                NettySingletonPool.CHANNEL_CONNECTIONS_CAHCE.put(invocation.getKey(), channel);
            }

            NettySingletonPool.FUTURE_BACK.put(invocation.getKey(), retFuture);
            // 写入数据 等待上方 客户端 channelRead
            channel.writeAndFlush(invocation).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.info("[Netty] 客户端 发起远程调用 [{}.{}] 成功 到 [{}]", invocation.getInterfaceImplName(), invocation.getMethodName(), host);
                } else {
                    future.channel().close();
                    retFuture.completeExceptionally(future.cause());
                    log.error("[Netty] 客户端 发起远程调用 [{}.{}] 失败 到 [{}]", invocation.getInterfaceImplName(), invocation.getMethodName(), host);
                    throw new ClientException("[Netty] 客户端 发起远程调用失败", future.cause());
                }
            });
            // 设置定时  利用schedule线程池 配合 completableFuture 判断是否超时
            //  (invocation1) -> {return send(invocation1)}
            // 这里是 递归调用  修改代码的话 看好上下文
            tolerate.enableCircuitBreaker(invocation, this::send);
            // 这句话 以下的操作 都不会更新到注册中心 因为只在容错函数中写了更新

            int length = NettySingletonPool.removeUnavailableFuture(); // 删除后的长度
            if (length != 0) {
                ArrayList<String> keys = new ArrayList<>(NettySingletonPool.FUTURE_BACK.keySet());// 因为是LinkedHashMap 它的键是一直保持插入顺序的
                CompletableFuture<Invocation> lastFuture = NettySingletonPool.FUTURE_BACK.get(keys.get(0)); // 排第一个的是最先返回的
                return lastFuture.get().getRet();
            }
            return null; //FIXME: 这里其实不应该出现 null的情况  但是这里还必须这样写（语法要求） 目前没测出来它输出过 null
        } catch (ExecutionException | InterruptedException e) {
            Throwable cause = e.getCause();
            throw new ClientException("[Netty] 客户端 执行ExecutionException或中断InterruptedException异常", cause);
        }
    }
}
