package com.lhr.rpc.exception;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-12 13:28
 **/
public class ServiceRegistryException extends RuntimeException {

    public ServiceRegistryException(String msg) {
        super(msg);
    }
    public ServiceRegistryException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
