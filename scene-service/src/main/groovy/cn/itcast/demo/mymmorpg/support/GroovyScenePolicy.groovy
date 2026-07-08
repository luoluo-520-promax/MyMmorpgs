/**
 * 文件维护说明
 * 1) 文件路径：scene-service/src/main/groovy/cn/itcast/demo/mymmorpg/support/GroovyScenePolicy.groovy
 * 2) 所属模块：scene-service / groovy
 * 3) 主要职责：Groovy 实现的 ScenePolicy，可热替换默认 Lambda 策略扩展进场景校验逻辑。
 * 4) 变更建议：修改后可不重启 JVM 热加载（若启用 Groovy 脚本刷新），需同步回归 handleEnterScene。
 * 5) 风险提示：allowEnterScene 返回 false 时客户端收到 INTERNAL_ERROR，扩展时建议改用专用 retcode。
 */
package cn.itcast.demo.mymmorpg.support // Groovy 策略类与 Java ScenePolicy 接口同包，便于 @Component 扫描

import groovy.transform.CompileStatic // 编译期静态类型检查，避免 Groovy 动态 dispatch 带来运行时开销
import org.springframework.stereotype.Component // 注册为 Spring Bean，优先级高于 ScenePolicyConfiguration 中的 @ConditionalOnMissingBean 默认实现

/**
 * Groovy 实现的场景进入校验（可按地图/玩家扩展）。
 * 当前逻辑：mapId>0 且 playerId>0 即放行，与 ScenePolicyConfiguration 默认 Lambda 等价。
 * 扩展示例：按 mapId 查副本等级门槛、活动开关、VIP 白名单等。
 */
@Component // Spring 扫描注册；因先于 @ConditionalOnMissingBean 默认 Bean 存在，默认 Lambda 不会生效
@CompileStatic // 生成与 Java 等价的字节码，allowEnterScene 调用无 MetaClass 开销
class GroovyScenePolicy implements ScenePolicy { // 实现 mmorpg-common 中 ScenePolicy 接口

    @Override // 实现 ScenePolicy.allowEnterScene，SceneActorService.handleEnterScene 第 130 行调用
    boolean allowEnterScene(int mapId, long playerId) { // mapId=map_config.id，playerId=当前选角 ID
        mapId > 0 && playerId > 0 // 地图 ID 与玩家 ID 均为正才允许进场景；false 时返回 RetCode.INTERNAL_ERROR
    }
}
