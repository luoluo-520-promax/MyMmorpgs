/**
 * 文件维护说明
 * 1) 文件路径：scene-service/src/main/java/cn/itcast/demo/mymmorpg/support/SceneServiceJmx.java
 * 2) 所属模块：scene-service / support
 * 3) 主要职责：JMX 暴露场景分线数与实体总数，监控 SceneActorService 内存态。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.support; // scene-service 运维与策略辅助类包

import cn.itcast.demo.mymmorpg.service.SceneActorService; // 场景运行时核心服务，维护 lineStates 与 playerScene 内存表
import org.springframework.jmx.export.annotation.ManagedAttribute; // 将 getter 暴露为 JMX 可读属性，供 JConsole/VisualVM 拉取
import org.springframework.jmx.export.annotation.ManagedResource; // 将本类注册为 JMX MBean，objectName 在 JConsole 树中可见
import org.springframework.stereotype.Component; // 注册为 Spring 单例，启动时自动注册 JMX MBean

/**
 * JMX 场景运维：观察当前活跃分线与实体规模，排查内存泄漏或刷怪异常。
 */
@Component // Spring 容器创建单例，JMX 导出器扫描 @ManagedResource 后注册 MBean
@ManagedResource( // 定义 MBean 元数据
        objectName = "cn.itcast.demo.mymmorpg:type=SceneService,name=Scene", // JConsole 路径：MBeans → cn.itcast.demo.mymmorpg → SceneService → Scene
        description = "场景 Actor 运行时" // MBean 描述，鼠标悬停时显示
)
public class SceneServiceJmx { // JMX 门面：只读暴露 SceneActorService 内存指标，不修改业务状态

    private final SceneActorService sceneActorService; // 委托目标：lineStates.size() 与全分线 entities 计数均来自此服务

    public SceneServiceJmx(SceneActorService sceneActorService) { // 构造器注入，Spring 自动匹配 SceneActorService Bean
        this.sceneActorService = sceneActorService; // 保存引用，JMX getter 调用时转发到运行时内存表
    }

    /** 当前已创建的场景线路 Actor 数量（mapId × line 组合） */
    @ManagedAttribute(description = "当前内存中的场景线路实例数") // JConsole Attributes 页显示 sceneLineCount，可定时刷新
    public int getSceneLineCount() { // JMX 属性 getter：对应 MBean 属性 SceneLineCount
        return sceneActorService.getSceneLineCount(); // 返回 lineStates ConcurrentHashMap 的键数量，每键格式 sceneId:lineId
    }

    /** 所有线路内玩家+怪物+NPC 实体总数 */
    @ManagedAttribute(description = "所有线路内实体总数（含玩家与怪物）") // JConsole Attributes 页显示 totalEntityCount
    public int getTotalEntityCount() { // JMX 属性 getter：对应 MBean 属性 TotalEntityCount
        return sceneActorService.getTotalEntityCount(); // 遍历 lineStates 各分线 entities 表累加 size
    }
}
