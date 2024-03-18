package com.lhr.rpc.proxy;

import com.lhr.rpc.extern.RpcConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;

/**
 * scan and filter specified annotations
 * 扫描和筛选指定的注释
 *
 * @author shuang.kou
 * @createTime 2020年08月10日 22:12:00
 * @modified LHR - 2024年3月14日 13点21分
 */
@Slf4j
public class CustomScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {
    private static final String[] SPRING_BEAN_BASE_PACKAGE = RpcConfig.getBeanPaths("spring.scan.paths");
    private static final String[] RPC_BEAN_BASE_PACKAGE = RpcConfig.getBeanPaths("rpc.scan.paths");
    private ResourceLoader resourceLoader;

    private static class CustomScanner extends ClassPathBeanDefinitionScanner {
        public CustomScanner(BeanDefinitionRegistry registry, Class<? extends Annotation> annoType) {
            super(registry);
            super.addIncludeFilter(new AnnotationTypeFilter(annoType));
        }
        @Override
        public int scan(String... basePackages) {
            return super.scan(basePackages);
        }
    }

    private int scanBeanPackages(AnnotationMetadata am, BeanDefinitionRegistry br, Class<? extends Annotation> relateClazz, String... packagesPaths) {
        int count = 0;
        if (am.hasAnnotation(RpcServiceEnable.class.getName())) {
            CustomScanner rpcServiceScanner = new CustomScanner(br, relateClazz);
            if (resourceLoader != null) {
                rpcServiceScanner.setResourceLoader(resourceLoader);
            }
            count = rpcServiceScanner.scan(packagesPaths);
        }
        return count;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata am, BeanDefinitionRegistry br) {

        // TODO: DEBUG可以下这儿下断 查看 包扫描范围内的注解类的个数是否一致
        int springBeanAmount = scanBeanPackages(am, br, Component.class, SPRING_BEAN_BASE_PACKAGE);
        int rpcServiceCount = scanBeanPackages(am, br, RpcService.class, RPC_BEAN_BASE_PACKAGE);

    }

}
