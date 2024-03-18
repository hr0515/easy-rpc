package com.lhr.rpc.registry;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.extern.SPI;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-11 21:16
 **/
@SPI
public interface RegistryCenter {


    void registerService(Invocation invocation);

    void unregisterService(String serviceInterfaceName, String serviceImplName);

    void updateService(Invocation invocation);

    void unregisterAllService();

    Invocation lookupService(Invocation invocation, int version);

}
