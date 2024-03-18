## easy-rpc

封装功能如下：服务发现、服务注册、服务调用、负载均衡、容错机制

相关技术：JDK提供的SPI、JDK提供的动态代理、Spring、Netty、Zookeeper

### 包结构

| 包          | 接口                                        |                                                              |
| ----------- | ------------------------------------------- | ------------------------------------------------------------ |
| entity      |                                             | 封装Invocation类作为传递对象，共15个字段                     |
| exception   |                                             | 实现自定义异常处理，总体继承了`RunTimeException`             |
| extern      | `@SPI`                                      | 封装了SPI的工具类，封装对外部资源文件的读取                  |
| loadbalance | `LoadBalance`                               | 实现负载均衡，分别实现随机与一致哈希                         |
| network     | `Client`|`Server`                           | 完成客户端和服务端的封装，分别需要实现`send`、`receive`      |
| proxy       | `@EnableRpcService`|`RpcService`|`RpcWired` | 将实体类的注入、初始化问题交给IOC容器，并支持注解进行服务的注册与代理调用 |
| registry    | `RegistryCenter`                            | 注册中心，封装了Zk的具体实现，包含服务注册、服务更新、撤销所有服务、服务发现 |
| serialize   | `Serializer`                                | 用Kryo实现了序列化与反序列化，并实现Netty传输的解码器与编码器 |
| tolerate    | `Tolerate`|`TolerateHandler`                | 实现容错策略，包含请求重试、熔断降级、限流；`TolerateHandler`函数式接口，传递`send`函数用 |

### 要点说明

- 使用IOC容器实现对Bean对象的动态管理，在注入过程中实现服务的注册与动态代理

重写`ImportBeanDefinitionRegistrard`的`registerBeanDefinitions`方法自定义包扫描器，将自定义注解修饰的类添加到IOC容器中，实现`BeanPostProcessor`接口实现`postProcessBeforeInitialization`和`postProcessAfterInitialization`，完成服务的注册与动态代理。

```java
scanBeanPackages(am, br, RpcService.class, RPC_BEAN_BASE_PACKAGE);
```

```java
// postProcessBeforeInitialization
// 服务注册
RpcService rpcService = bean.getClass().getAnnotation(RpcService.class);
Invocation invocation = new Invocation();
// ...
registryCenter.registerService(invocation);
```

```java
// postProcessAfterInitialization
// 动态代理 与 服务发现
Object o = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, (Object proxy, Method method, Object[] args) -> {
    Invocation invocation = registryCenter.lookupService(invo, rpcWired.version());
	// ...
	return client.send(invocation);
});
```

对应的注解为：`EnableRpcService`、`RpcService`、`RpcWired`

- 使用Netty搭建基于NIO的基础网络服务，增强异步性能。

为了体现更直接的调用过程，本项目没有封装具体的rpc协议，仅实现一个总的实体类`Invocation`传递相应的参数。一般情况下Netty常见两个对象来调用`writeAndFlush`。通道`Channel`、通道上下文处理器`ChannelHandlerContext`。前者的使用过程中应注意其是否处于`active`状态。

```java
channel.writeAndFlush(invocation);
```

读取数据时我们需要继承`ChannelInboundHandlerAdapter`适配器类并重写其`channelRead`方法

```java
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception { ... }
```

在Netty中发送或接受数据，没有额外配置时仅允许使用Netty提供的`ByteBuf`一种数据类型。这里本项目使用`Kryo`对传递对象序列化，`Kryo`的综合性能还是很好的，相比于`Protobuf`不用配置额外的实体文件。将写好的序列化类分别应用到继承于`ByteToMessageDecoder`和`MessageToByteEncoder<>`的实现类中。以下是`encoder`的具体实现

```java
public class NettyKryoInvocationEncodeSerializer extends MessageToByteEncoder<Invocation> {
    Serializer serializer;
    public NettyKryoInvocationEncodeSerializer() {
        super();
        serializer = JdkSPI.load(Serializer.class);
    }
    @Override
    protected void encode(ChannelHandlerContext ctx, Invocation invocation, ByteBuf byteBuf) throws Exception {
        byte[] bytes = serializer.serialize(invocation);
        byteBuf.writeBytes(bytes);
    }
}
```

在此之中，使用到`JdkSPI.load`为下文所提及`SPI`。

- 使用`CompletableFuture`完成异步请求数据的处理

`CompletableFuture`是`Jdk8`中提出的一种异步任务的解决方案。其调用`complete`方法后，才能从`future`对象中`get`到指定数据。

```java
public static class NettySingletonPool {
	public static final Map<String, CompletableFuture<Invocation>> FUTURE_BACK = Collections.synchronizedMap(new LinkedHashMap<>());
}
// 调用前创建
NettySingletonPool.FUTURE_BACK.put(invocation.getKey(), retFuture);
// 写在Netty的客户端的readChannel中最终的return在等待complete的执行
NettySingletonPool.FUTURE_BACK.get(invocation.getKey()).complete(invocation);
return NettySingletonPool.FUTURE_BACK.get(invocation.getKey()).get().getRet();  // 阻塞 返回执行结果
```

这里我使用了一个静态内部类来维护一个`LinkedHashMap`（后面讲为什么）的`CompletableFuture`对象。存储并缓存各个`future`对象

- 封装Java SPI使用双锁校验保持单例加载，并使用静态内部类与`ConcurrentHashMap`实现单例的缓存

核心实现如下

```java
private static class SPISingletonPool {
    public static final ConcurrentHashMap<Class<?>, Object> CACHE = new ConcurrentHashMap<>();
}
public static <S> S load(Class<S> clazz) {
    S s = null;
    s = clazz.cast(SPISingletonPool.CACHE.get(clazz));
    if (s != null) {
        return s;
    }else {
        synchronized (SPISingletonPool.CACHE) {
            s = clazz.cast(SPISingletonPool.CACHE.get(clazz));
            if (s == null) {
                ServiceLoader<S> serviceLoader = ServiceLoader.load(clazz);
                Iterator<S> iterator = serviceLoader.iterator();
                s = serviceLoader.iterator().next();
                SPISingletonPool.CACHE.put(clazz, s);
            }
        }
    }
    return s;
}
```

这里是收到某团某项目的启发，创建所谓的单例池来解决`CACHE`的单例问题（这里本人也是不太确定不写静态内部类仅靠`final`是否可以保持单例，但是写了准没错，还能保证一个懒加载）

- 使用一致哈希法实现负载均衡，使请求均匀分布在各个节点

一致哈希法是一个比较古老的算法了，这里使用它的目的是为了保证在添加或者删除节点的时候影响范围最小，请求尽量均匀分布在各个节点上。在一个环上对每个节点虚拟出多个虚拟节点，请求过来时求得hash值后在环上进行顺时针匹配命中节点（或者虚拟节点对应的节点）。构造哈希环，哈希结果打到哈希环上，没打到节点就索引比它大的点。最终的实现参考`Dubbo`

- 使用Scheduled线程池与CompletableFuture实现超时请求的再响应，完成请求重试

在请求重试与熔断中，会有一个计时的需求。超过多少秒重试；以及超过多少秒恢复从降级状态恢复为原状态。

```java
faildServiceExecutor.schedule(() -> {
    if (retFuture != null && !retFuture.isDone()) {
        retFuture.complete(invocationOutOfTime);
        NettyClientImpl.NettySingletonPool.FUTURE_BACK.get(futureKey).complete(invocationOutOfTime);
    }
}, invocation.getWaitTime(), TimeUnit.SECONDS);
```

在请求重试中，使用了两个`CompletableFuture`来判断其是否需要重新发送。两者是在传递这个中间结果。

```java
faildServiceExecutor.schedule(() -> {
    invocation.setFallback(false);
    registryCenter.updateService(invocation);
}, invocation.getRecover(), TimeUnit.SECONDS);
```

在熔断恢复时，达到规定恢复时间需要更新注册中心的具体服务，使其不再执行`fallback`函数

- 采用递归调用发送方法的方式，完成常见容错策略的实现，包含降级、熔断、限流，并在注解上规定相应字段

为了不改变原有架构的基础上增加这些功能，本项目使用到函数式接口，通过接口的匿名实现来让"函数以一个参数的形式传递"，这里刚好用`Lambda`表达式更加清爽的呈现。

```java
public static class NettySingletonPool {
	public static final Map<String, CompletableFuture<Invocation>> FUTURE_BACK = Collections.synchronizedMap(new LinkedHashMap<>());
}
@Override
public Object send(Invocation invocation) {
    channel.writeAndFlush(invocation);
    // ...
    tolerate.enableCircuitBreaker(invocation, this::send);
    // ...
    return lastFuture.get().getRet();
}
```

在`enableCircuitBreaker`开启请求重试、熔断降级的一些列检测。由于我们是递归执行，所以需要保存每次的Future对象，所以需要`FUTURE_BACK`，且要满足有序的键值对。

回答上面的问题：为什么要用`LinkedHashMap`。在`Client`接受写回数据时，因为`Netty`是异步的写回，以防写回时的乱序，所以要使用到键值找到准确的`future`，并执行其`complete`方法，使得`return`语句不再阻塞；此外，因为是递归调用，会有很多的`CompletableFuture`的积攒，要保证最后一次递归返回的是第一次的`future`结果，这样的话，递归的返回结果与我们想要的结果正好相反，于是要在出`send`函数（下半部分）时对`CompletableFuture`进行逆向的处理，这里要遵循一个有序性，所以选用`LinkedHashMap`，但是它没有线程安全的版本。在这个过程中如果可以保证`Netty`的写回是呈顺序性的，也可以用队列来做，自带线程安全处理。















> 

我读了很多的手写Rpc框架的源码，认识到自己的不足。为了能让自己更好的理解rpc的调用流程，以及更直观的执行各个流程。所以有了easy-rpc。

以下是我看过或我借鉴过的开源仓库，大家可以通过以下扩展自己，同时感谢以下大佬的贡献：

