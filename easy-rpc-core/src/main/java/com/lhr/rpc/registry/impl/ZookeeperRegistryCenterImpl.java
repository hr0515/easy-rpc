package com.lhr.rpc.registry.impl;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.exception.ServiceRegistryException;
import com.lhr.rpc.extern.RpcConfig;
import com.lhr.rpc.loadbalance.LoadBalance;
import com.lhr.rpc.registry.RegistryCenter;
import com.lhr.rpc.extern.JdkSPI;
import com.lhr.rpc.serialize.Serializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.util.List;


/**
 * @description:
 * @author: LHR
 * @date: 2024-03-11 21:19
 **/
@Slf4j
public class ZookeeperRegistryCenterImpl implements RegistryCenter {

    private final static String ZK_HOST = RpcConfig.getString("zookeeper.host");
    private final static int MAX_RETRY = RpcConfig.getInt("zookeeper.max_try");  // 最大重试次数
    private final static int WAIT_RETRY_MSEC = RpcConfig.getInt("zookeeper.wait_time") * 1000;  // 重试等待时间
    private final static int SESSION_TIME_OUT = RpcConfig.getInt("zookeeper.session_timeout") * 1000;  // Session过期时间
    private final static int CONNECTION_TIME_OUT = RpcConfig.getInt("zookeeper.connect_timeout") * 1000;  // 连接过期时间
    private final static String RPC_ROOT_PATH = RpcConfig.getString("zookeeper.root_path");

    private LoadBalance loadBalance;
    private Serializer serializer;
    private CuratorFramework zk;
    public ZookeeperRegistryCenterImpl() {
        serializer = JdkSPI.load(Serializer.class);
        loadBalance = JdkSPI.load(LoadBalance.class);
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(WAIT_RETRY_MSEC, MAX_RETRY);
        zk = CuratorFrameworkFactory.builder()
                .connectString(ZK_HOST)
                .sessionTimeoutMs(SESSION_TIME_OUT)   // 会话超时时间 单位ms
                .connectionTimeoutMs(CONNECTION_TIME_OUT)  // 连接超时时间 单位ms
                .retryPolicy(retryPolicy)
                // namespace会添加一个命名空间，创建了一个同名的节点。
                // 之后通过该链接的所有操作，默认是在该节点下操作的 create("/app") 相当于 /itheima/app
                .namespace(RPC_ROOT_PATH)
                .build();
        //开启连接
        zk.start();
        log.info("Zk连接成功!");
        try {
            if (zk.checkExists().forPath("/" + RPC_ROOT_PATH) == null) {
                String ret = zk.create().withMode(CreateMode.PERSISTENT).forPath("/" + RPC_ROOT_PATH);
                log.info("[注册中心初始化] 根节点创建为 [{}]", ret);
            }
        } catch (Exception e) {
            throw new ServiceRegistryException("尝试连接zookeeper失败", e);
        }
    }

    @Override
    public void registerService(Invocation invocation) {
        // easy-rpc/接口名/版本号/ip地址/实现类名
        // InterfaceName(interfaces[0].getName());
        // InterfaceImplName(bean.getClass().getName());
        // FallbackImplName(rpcService.fallback().getName());
        // Versoin(rpcService.version());
        // Host(host);
        // WaitTime(rpcService.waitTime());
        // RetryTimes(rpcService.retryTimes());
        // Recover(rpcService.recover());
        // Fallback(false);
        // Singleton(rpcService.singleton());
        // LimitTime(rpcService.limitTime());
        // Threads(rpcService.threads());  12 / 17
        String path = "/" + invocation.getInterfaceName() +
                "/" + invocation.getVersoin() +
                "/" + invocation.getHost() +
                "/" + invocation.getInterfaceImplName();
        try {
            if (zk.checkExists().forPath(path) == null) {
                zk.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).inBackground((CuratorFramework client, CuratorEvent event)->{
                    log.info("[服务注册] 创建持久节点 [{}]", event.getPath());
                    zk.setData().forPath(path, serializer.serialize(invocation));
                }).forPath(path);
            }else {
                log.info("[服务注册] 已有服务 [{}] 在[{}]", invocation.getInterfaceImplName(), invocation.getHost());
            }
        } catch (Exception e) {
            throw new ServiceRegistryException("创建/注册 持久服务结点失败", e);
        }
    }

    @Override
    public void unregisterService(String serviceInterfaceName, String serviceImplName) {
        // 删除 / 禁用 结点
        String path = "/" + serviceInterfaceName + "/" + serviceImplName;
        try {
            zk.delete().guaranteed().deletingChildrenIfNeeded().inBackground((CuratorFramework client, CuratorEvent event)->{
                log.info("[服务撤销] 删除持久结点 [{}]", event.getPath());
            }).forPath(path);
        } catch (Exception e) {
            throw new ServiceRegistryException("删除服务结点失败", e);
        }
    }

    @Override
    public void updateService(Invocation invocation) {
        String path = "/" + invocation.getInterfaceName() +
                "/" + invocation.getVersoin() +
                "/" + invocation.getHost() +
                "/" + invocation.getInterfaceImplName();
        try {
            if (zk.checkExists().forPath(path) == null) {
                log.info("[服务更新] 更新失败 无此持久节点 [{}]", path);
            }else {
                zk.setData().forPath(path, serializer.serialize(invocation));
                log.info("[服务更新] 更新成功 [{}] 在[{}]", invocation.getInterfaceImplName(), invocation.getHost());
            }
        } catch (Exception e) {
            throw new ServiceRegistryException("创建/注册 持久服务结点失败", e);
        }
    }

    @Override
    public void unregisterAllService() {
        try {
            zk.delete().guaranteed().deletingChildrenIfNeeded().inBackground((CuratorFramework client, CuratorEvent event)->{
                log.info("[服务撤销] 删除所有持久结点 [{}]", event.getPath() + RPC_ROOT_PATH);
            }).forPath("/");
        } catch (Exception e) {
            throw new ServiceRegistryException("删除所有结点失败", e);
        }
    }

    @Override
    public Invocation lookupService(Invocation invocation, int version) {
        // easy-rpc/接口名/版本号/ip地址/实现类名
        String path = "/" + invocation.getInterfaceName() + "/" + version;
        try {
            List<String> hostList = zk.getChildren().forPath(path);
            if (hostList.isEmpty()) log.info("[服务发现] 找不到版本为 [{}] 的服务 [{}]", version, invocation.getInterfaceImplName());
            String host = loadBalance.select(hostList, invocation, version);

            String serviceImplName = zk.getChildren().forPath(path + "/" + host).get(0);

            Invocation invo = serializer.deserialize(Invocation.class, zk.getData().forPath(path + "/" + host + "/" + serviceImplName));
            invo.setMethodName(invocation.getMethodName());
            invo.setMethodParamTypes(invocation.getMethodParamTypes());
            invo.setMethodParams(invocation.getMethodParams());
            invo.setCurrentTimes(invocation.getCurrentTimes());  // 14 / 15 还剩一个 setRet 在服务端设置的
            log.info("[服务发现] [{}] 在 [{}]", serviceImplName, host);
            return invo;
        } catch (Exception e) {
            throw new ServiceRegistryException("服务发现失败", e);
        }
    }
}
