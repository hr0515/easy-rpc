package com.lhr.rpc.exception;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-14 01:34
 **/
public class ClientException extends RuntimeException {
    public ClientException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
