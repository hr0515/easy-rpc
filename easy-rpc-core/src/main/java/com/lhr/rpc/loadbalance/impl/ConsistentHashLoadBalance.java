package com.lhr.rpc.loadbalance.impl;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.loadbalance.LoadBalance;
import lombok.SneakyThrows;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @description: 参照 Dubbo 源码
 * @author: LHR
 * @date: 2024-03-18 19:03
 **/
public class ConsistentHashLoadBalance implements LoadBalance {

    private final ConcurrentMap<String, ConsistentHashSelector> selectors = new ConcurrentHashMap<>();

    @Override
    public String select(List<String> hostList, Invocation invocation, int version) {

        String key = invocation.getHashKey(hostList.get(0), version);  // host version method
        // using the hashcode of list to compute the hash only pay attention to the elements in the list
        int invokersHashCode = hostList.hashCode();
        ConsistentHashSelector selector = selectors.get(key);
        if (selector == null || selector.identityHashCode != invokersHashCode) {
            selectors.put(key, new ConsistentHashSelector(hostList, invokersHashCode));
            selector = selectors.get(key);
        }
        return selector.select(invocation.getMethodName());
    }

    private static final class ConsistentHashSelector {

        private final TreeMap<Long, String> virtualInvokers;

        private final int identityHashCode;

        ConsistentHashSelector(List<String> invokers, int identityHashCode) {
            this.virtualInvokers = new TreeMap<>();
            this.identityHashCode = identityHashCode;
            int replicaNumber = 160;
            for (String s : invokers) {
                for (int i = 0; i < replicaNumber / 4; i++) {
                    byte[] digest = getMD5(s);
                    for (int h = 0; h < 4; h++) {
                        long m = hash(digest, h);
                        virtualInvokers.put(m, s);
                    }
                }
            }
        }

        @SneakyThrows
        public byte[] getMD5(String invoke) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(invoke.getBytes());
            return md.digest();
        }

        public String select(String invoke) {
            return selectForKey(hash(getMD5(invoke), 0));
        }

        private String selectForKey(long hash) {
            Map.Entry<Long, String> entry = virtualInvokers.ceilingEntry(hash);
            if (entry == null) {
                entry = virtualInvokers.firstEntry();
            }
            return entry.getValue();
        }

        private long hash(byte[] digest, int number) {
            return (((long) (digest[3 + number * 4] & 0xFF) << 24)
                    | ((long) (digest[2 + number * 4] & 0xFF) << 16)
                    | ((long) (digest[1 + number * 4] & 0xFF) << 8)
                    | (digest[number * 4] & 0xFF))
                    & 0xFFFFFFFFL;
        }
    }

}
