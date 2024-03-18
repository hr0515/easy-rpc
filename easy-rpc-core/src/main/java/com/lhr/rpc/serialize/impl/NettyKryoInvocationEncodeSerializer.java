package com.lhr.rpc.serialize.impl;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.serialize.Serializer;
import com.lhr.rpc.extern.JdkSPI;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-13 14:45
 **/
public class NettyKryoInvocationEncodeSerializer extends MessageToByteEncoder<Invocation> {

    Serializer serializer;
    public NettyKryoInvocationEncodeSerializer() {
        super();
        serializer = JdkSPI.load(Serializer.class);
    }
    @Override
    protected void encode(ChannelHandlerContext ctx, Invocation invocation, ByteBuf byteBuf) throws Exception {
        byte[] bytes = serializer.serialize(invocation);
        byteBuf.writeBytes(bytes);
    }
}
