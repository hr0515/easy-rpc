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


    void registerService(String serviceInterfaceName, String serviceImplName, int version, String host);

    void unregisterService(String serviceInterfaceName, String serviceImplName);

    void unregisterAllService();

    Invocation lookupService(String serviceInterfaceName, int version);

}
