package com.lhr.rpc.tolerate;

import com.lhr.rpc.entity.Invocation;

@FunctionalInterface
public interface TolerateHandler {

    Object handle(Invocation invocation);

}
