package com.lhr.rpc.network.impl;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.exception.ClientException;
import com.lhr.rpc.extern.RpcConfig;
import com.lhr.rpc.network.Client;
import com.lhr.rpc.serialize.impl.NettyKryoInvocationDecodeSerializer;
import com.lhr.rpc.serialize.impl.NettyKryoInvocationEncodeSerializer;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-13 00:36
 **/
@Slf4j
public class NettyClientImpl implements Client {
    private static final int serverPort = RpcConfig.getInt("server.port");
    private Bootstrap bootstrap;

    private static class NettySingletonPool {
        public static final ConcurrentHashMap<String, Channel> CHANNEL_CONNECTIONS = new ConcurrentHashMap<>();
        public static final ConcurrentHashMap<String, CompletableFuture<Invocation>> FUTURE_BACK = new ConcurrentHashMap<>();
    }

    public NettyClientImpl() {
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
                                }
                                String key = invocation.getHost() + invocation.getInterfaceImplName();
                                NettySingletonPool.FUTURE_BACK.remove(key).complete(invocation);
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
            // 连接服务端  从连接池中拿一个  要是一个主机那么 没必要关闭再重新发  建立一个持久的连接会比较好 不然容易报错
            Channel channel = NettySingletonPool.CHANNEL_CONNECTIONS.get(host);
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
                // 利用单例的 Future 处理返回来的异步数据 键是 host + ImplementName
                channel = fc.get();
                NettySingletonPool.CHANNEL_CONNECTIONS.put(host, channel);
            }

            String futureKey = host + invocation.getInterfaceImplName();
            NettySingletonPool.FUTURE_BACK.put(futureKey, retFuture);
            // 写入数据 等待上方 客户端 channelRead
            channel.writeAndFlush(invocation).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.info("[Netty] 客户端 发起远程调用 [{}.{}] 成功 到 [{}]", invocation.getInterfaceImplName(), invocation.getMethodName(), host);
                } else {
                    future.channel().close();
                    retFuture.completeExceptionally(future.cause());
                    log.error("[Netty] 客户端 发起远程调用 [{}.{}] 失败 到 [{}]", invocation.getInterfaceImplName(), invocation.getMethodName(), host);
                    throw new ClientException("[Netty] 客户端 发起远程调用", future.cause());
                }
            });
            return retFuture.get().getRet();
        } catch (ExecutionException | InterruptedException e) {
            Throwable cause = e.getCause();
            throw new ClientException("[Netty] 客户端 执行ExecutionException或中断InterruptedException异常", cause);
        }
    }

    @Override
    public Object heartSend(Invocation invocation) {
        return null;
    }
}
