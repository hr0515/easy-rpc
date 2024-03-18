package com.lhr.rpc.serialize.impl;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.serialize.Serializer;
import com.lhr.rpc.extern.JdkSPI;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-13 14:48
 **/
public class NettyKryoInvocationDecodeSerializer extends ByteToMessageDecoder {

    Serializer serializer;

    public NettyKryoInvocationDecodeSerializer() {
        super();
        serializer = JdkSPI.load(Serializer.class);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> list) throws Exception {
        int i = byteBuf.readableBytes();
        byte[] bytes = new byte[i];
        byteBuf.readBytes(bytes);
        Invocation deserialize = serializer.deserialize(Invocation.class, bytes);
        list.add(deserialize);
    }
}
