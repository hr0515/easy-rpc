package com.lhr.rpc.serialize;

import com.lhr.rpc.extern.SPI;

// com.lhr.rpc.serialize.Serializer
@SPI
public interface Serializer {

    // 反序列化方法
    <T> T deserialize(Class<T> clazz, byte[] bytes);

    // 序列化方法
    <T> byte[] serialize(T object);
}
