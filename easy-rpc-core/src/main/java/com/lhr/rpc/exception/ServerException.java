package com.lhr.rpc.exception;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-14 01:39
 **/
public class ServerException extends RuntimeException {
    public ServerException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
