package com.lhr.rpc.network.impl;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.exception.ServerException;
import com.lhr.rpc.extern.RpcConfig;
import com.lhr.rpc.network.Server;
import com.lhr.rpc.serialize.impl.NettyKryoInvocationDecodeSerializer;
import com.lhr.rpc.serialize.impl.NettyKryoInvocationEncodeSerializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.concurrent.*;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-13 00:36
 **/
@Slf4j
public class NettyServerImpl implements Server {

    private static final int serverPort = RpcConfig.getInt("server.port");
    public ChannelHandlerContext ctx;
    public Invocation invocation;

    private static class Pool {
        public static final ConcurrentHashMap<String, Object> CACHE_SERVICE = new ConcurrentHashMap<>();
    }

    public NettyServerImpl() {
        int readTimes = RpcConfig.getInt("netty.server_max_read");
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);  // 处理客户端连接的主线程池
        EventLoopGroup workerGroup = new NioEventLoopGroup();  // 传0就是 CPU个数*2    8*2 16   用于处理I/O的从线程池
        try {
            // 构建 NettyServerHandler
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.DEBUG))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline = socketChannel.pipeline();
                            // 如果 readTimes s没有读请求(channelRead不触发 证明客户端没有调用服务 正常他要每 4(默认) 秒 发送心跳包的)则向客户端发送心跳
                            pipeline.addLast(new IdleStateHandler(readTimes, 0, 0, TimeUnit.SECONDS));
                            pipeline.addLast(new NettyKryoInvocationEncodeSerializer());
                            pipeline.addLast(new NettyKryoInvocationDecodeSerializer());
                            pipeline.addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext channelHandlerContext, Object message) throws Exception {
                                    invocation = (Invocation) message;
                                    if (invocation.getRet() instanceof Number) {
                                        log.info("[Netty] [心跳包] 服务端 [{}:{}] 接受", InetAddress.getLocalHost().getHostAddress(), serverPort);
                                        return;
                                    }
                                    ctx = channelHandlerContext;
                                    log.info("[Netty] 服务端 通道读取 接受来自 [{}] 的 [{}]", invocation.getHost(), invocation.getInterfaceImplName());
                                    receive();
                                }
                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                    ctx.close();
                                    throw new ServerException("[Netty] 服务端 异常", cause);
                                }

                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                    if (evt instanceof IdleStateEvent) {
                                        IdleStateEvent event = (IdleStateEvent) evt;
                                        if (IdleState.READER_IDLE.equals((event.state()))) {
                                            //  log.info("[Netty] [心跳包] 服务端 [{}:{}] 发送", InetAddress.getLocalHost().getHostAddress(), serverPort);
                                            // 超过5秒没 触发 通道读 说明 他没调用  但是没调用他也应该发心跳包 没发代表它G了 关闭连接
                                            log.info("[Netty] [心跳包] 服务端 [{}:{}] 下线通知", InetAddress.getLocalHost().getHostAddress(), serverPort);
                                            // Invocation invocation = new Invocation();
                                            // invocation.setRet(1);
                                            // ctx.writeAndFlush(invocation).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                                            ctx.close(); // 关闭心跳  因为这种状况下 已经判定 客户端 下机了
                                        }
                                    }
                                    super.userEventTriggered(ctx, evt);
                                }
                            }); //业务处理器
                        }
                    });
            log.info("[Netty] 服务端 Netty服务建立");
            ChannelFuture channelFuture = serverBootstrap.bind(serverPort).sync();

            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    @Override
    public void receive() {
        try {
            String DEFAULT_TYPE = "com.lhr.rpc.tolerate.DefaultType";
            Class<?> clazz;
            if (invocation.isFallback()) {
                clazz = Class.forName(invocation.getFallbackImplName());
            }else {
                clazz = Class.forName(invocation.getInterfaceImplName());
            }

            Object o;
            if (invocation.isSingleton()) {
                o = Pool.CACHE_SERVICE.get(clazz.getName());
                if (o == null) {
                    o = clazz.newInstance();
                    Pool.CACHE_SERVICE.put(clazz.getName(), o);
                }
            }else {
                o = clazz.newInstance();
            }

            try {
                if (!clazz.getName().equals(DEFAULT_TYPE)) {
                    Method method = clazz.getMethod(invocation.getMethodName(), invocation.getMethodParamTypes());
                    invocation.setRet(method.invoke(o, invocation.getMethodParams()));
                }else {
                    invocation.setRet(null); // 默认托底类 返回
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            ctx.writeAndFlush(invocation).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.info("[Netty] 服务端 写回数据成功 [{}]", invocation.getRet());
                } else {
                    future.channel().close();
                    log.error("[Netty] 服务端 写回数据失败", future.cause());
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
