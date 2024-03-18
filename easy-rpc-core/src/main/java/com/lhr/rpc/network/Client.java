package com.lhr.rpc.network;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.extern.SPI;

@SPI
public interface Client {
    Object send(Invocation invocation);
}
