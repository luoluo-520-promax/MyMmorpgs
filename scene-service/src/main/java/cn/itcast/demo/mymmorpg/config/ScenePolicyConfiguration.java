package cn.itcast.demo.mymmorpg.config; // scene-service 模块 Spring 配置包，集中注册场景相关 Bean

import cn.itcast.demo.mymmorpg.support.ScenePolicy; // 进场景前策略接口，SceneActorService.handleEnterScene 调用 allowEnterScene
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean; // 仅当容器内尚无 ScenePolicy 实现时才注册默认 Bean，避免覆盖 GroovyScenePolicy
import org.springframework.context.annotation.Bean; // 标记方法返回值注册为 Spring 单例 Bean
import org.springframework.context.annotation.Configuration; // 声明本类为 @Configuration 配置类，启动时被组件扫描加载
/**
 * 负责向 Spring 容器提供 ScenePolicy 默认实现
 */
@Configuration(proxyBeanMethods = false)// 禁用 CGLIB 代理：本类无 @Bean 间互相调用，可节省启动内存
public class ScenePolicyConfiguration {
    /**
     * 工厂方法：创建内存中的默认进场景校验策略
     * @return
     */
    @Bean // 将 scenePolicy() 返回值注册为 ScenePolicy 类型 Bean，供 SceneActorService 构造器注入
    @ConditionalOnMissingBean(ScenePolicy.class) // 若已存在 GroovyScenePolicy 等自定义实现，则跳过本默认 Bean
    ScenePolicy scenePolicy() {
        // Lambda 实现 ScenePolicy：mapId 与 playerId 均为正数才允许进入，否则 handleEnterScene 返回 INTERNAL_ERROR
        return (mapId, playerId) -> mapId > 0 && playerId > 0;
    }
}
