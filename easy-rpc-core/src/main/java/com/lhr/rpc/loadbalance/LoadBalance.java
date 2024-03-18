package com.lhr.rpc.loadbalance;


import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.extern.SPI;

import java.util.List;

@SPI
public interface LoadBalance {
    String select(List<String> hostList, Invocation invocation, int version);
}
