/**
 * 文件说明
 * 模块：mmorpg-common / 网络
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/net/GameContext.java
 * 类型：类
 * 职责：定义 GameContext，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.net;


import org.springframework.beans.BeansException;

import org.springframework.context.ApplicationContext;

import org.springframework.context.ApplicationContextAware;

import org.springframework.lang.NonNull;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Component;


import jakarta.annotation.PostConstruct;


import java.lang.annotation.Annotation;

import java.util.Map;

@Component("gameContext")
public class GameContext implements ApplicationContextAware {

    /** application上下文（类型：ApplicationContext） */
    private static ApplicationContext applicationContext;
    /** 服务器配置holder（类型：volatile ServerConfig） */
    private static volatile ServerConfig serverConfigHolder;
    /** 服务器类型（类型：volatile ServerType） */
    public static volatile ServerType serverType;
    /** 服务器配置（类型：ServerConfig） */
    private final ServerConfig serverConfig;
    @Value("${server.type:1}")
    /** 类型码（类型：int） */
    private int typeCode;/**
     * 构造 GameContext 实例
     */
    public GameContext(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;  // 访问或赋值当前实例字段
    }

    @Override
    /**
     * 设置application上下文属性值
     */
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        GameContext.applicationContext = applicationContext;  // 执行语句
    }

    @PostConstruct
    /**
     * init；无参数
     */
    public void init() {
        GameContext.serverType = ServerType.fromCode(typeCode);
        GameContext.serverConfigHolder = serverConfig;  // 执行语句
    }

    /**
     * 获取服务器配置属性值
     */
    public static ServerConfig getServerConfig() {
        return serverConfigHolder;
    }

    /**
     * 获取bean属性值
     */
    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }

    /**
     * 获取bean属性值
     */
    public static <T> T getBean(String name, Class<T> requiredType) {
        return applicationContext.getBean(name, requiredType);
    }

    /**
     * 获取beanswithannotation属性值
     */
    public static Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
        return applicationContext.getBeansWithAnnotation(annotationType);
    }
}
