package com.lhr.rpc.proxy;


import java.lang.annotation.*;

/**
 * RPC reference annotation, autowire the service implementation class
 *
 * @author smile2coder
 * @createTime 2020年09月16日 21:42:00
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Inherited
public @interface RpcWired {

    /**
     * Service version, default value is empty string
     */
    int version() default 0;

}
