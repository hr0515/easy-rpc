package com.test.server;

import com.lhr.rpc.BootStrapRpcServer;
import com.lhr.rpc.proxy.RpcServiceEnable;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-12 09:09
 **/
@Component
@RpcServiceEnable
public class TestServer {

    public static void main(String[] args) {
        BootStrapRpcServer.start(TestServer.class);
    }

}
