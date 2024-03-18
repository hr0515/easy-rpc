package com.lhr.rpc.registry.impl;

import com.lhr.rpc.entity.Invocation;
import com.lhr.rpc.exception.ServiceRegistryException;
import com.lhr.rpc.extern.RpcConfig;
import com.lhr.rpc.loadbalance.LoadBalance;
import com.lhr.rpc.registry.RegistryCenter;
import com.lhr.rpc.extern.JdkSPI;
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
    private CuratorFramework zk;
    public ZookeeperRegistryCenterImpl() {
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
            throw new ServiceRegistryException(e.toString(), e);
        }
    }

    @Override
    public void registerService(String serviceInterfaceName, String serviceImplName, int version, String host) {
        // easy-rpc/接口名/版本号/ip地址/实现类名
        String path = "/" + serviceInterfaceName + "/" + version + "/" + host + "/" + serviceImplName;
        try {
            if (zk.checkExists().forPath(path) == null) {
                String ret = zk.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).inBackground((CuratorFramework client, CuratorEvent event)->{
                    log.info("[服务注册] 创建持久节点 [{}]", event.getPath());
                }).forPath(path);

            }else {
                log.info("[服务注册] 已有服务 [{}] 在[{}]", serviceImplName, host);
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            e.printStackTrace();
        }
    }

    @Override
    public void unregisterAllService() {
        try {
            zk.delete().guaranteed().deletingChildrenIfNeeded().inBackground((CuratorFramework client, CuratorEvent event)->{
                log.info("[服务撤销] 删除所有持久结点 [{}]", event.getPath() + RPC_ROOT_PATH);
            }).forPath("/");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Invocation lookupService(String serviceInterfaceName, int version) {
        // easy-rpc/接口名/版本号/ip地址/实现类名
        String path = "/" + serviceInterfaceName + "/" + version;
        try {
            List<String> hostList = zk.getChildren().forPath(path);
            String host = loadBalance.select(hostList);
            String serviceImplName = zk.getChildren().forPath(path + "/" + host).get(0);
            log.info("[服务发现] [{}] 在 [{}]", serviceImplName, host);
            return new Invocation(host, serviceImplName);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }
}
