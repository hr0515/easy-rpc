package com.lhr.rpc.serialize.impl;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.serialize.Serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-13 14:32
 **/
public class KryoSerializer implements Serializer {

    private static final ThreadLocal<Kryo> kryoLocal = new ThreadLocal<Kryo>() {
        @Override
        protected Kryo initialValue() {
            Kryo kryo = new Kryo();
            kryo.setReferences(true);  //默认值为true   支持对象循环引用（否则会栈溢出）
            kryo.setRegistrationRequired(false);  //遇到未注册的类时发生异常 默认值为false 不出现异常
            kryo.register(Invocation.class);
            return kryo;
        }
    };

    @Override
    public <T> T deserialize(Class<T> clazz, byte[] bytes) {
        Kryo kryo = kryoLocal.get();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        Input input = new Input(byteArrayInputStream);
        input.close();
        return clazz.cast(kryo.readClassAndObject(input));
    }

    @Override
    public <T> byte[] serialize(T object) {
        Kryo kryo = kryoLocal.get();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Output output = new Output(byteArrayOutputStream);
        kryo.writeClassAndObject(output, object);
        output.close();
        return byteArrayOutputStream.toByteArray();
    }
}
