package com.lhr.rpc.loadbalance.impl;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.loadbalance.LoadBalance;

import java.util.List;
import java.util.Random;

/**
 * @description:
 * @author: LHR
 * @date: 2024-03-12 20:28
 **/
public class RandomLoadBalance implements LoadBalance {
    @Override
    public String select(List<String> hostList, Invocation invocation, int version) {
        return hostList.get(new Random().nextInt(hostList.size()));
    }
}
