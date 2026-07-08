/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/event/EventAuditHandlers.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/event
 * 3) 主要职责：示例 EventBus 审计订阅者，登录/功能解锁/RPC 连接事件写 INFO 日志，无业务副作用。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.event; // player-service 领域事件与 EventBus 注册

import cn.itcast.demo.mymmorpg.rpc.RpcConnectedEvent; // 领域事件 RpcConnectedEvent 发布/订阅
import jforgame.commons.eventbus.Subscribe; // Subscribe，EventAuditHandlers.java 编译依赖
import org.slf4j.Logger; // Logger，EventAuditHandlers.java 编译依赖
import org.slf4j.LoggerFactory; // LoggerFactory，EventAuditHandlers.java 编译依赖
import org.springframework.stereotype.Component; // Spring 组件 stereotype 注解
/**
 * 示例事件处理器：保持无状态，仅做快速日志通知，生产可替换为 MQ 投递或指标打点。
 */

@Component // Spring 单例组件

public class EventAuditHandlers { // EventAuditHandlers 类型定义
    private static final Logger log = LoggerFactory.getLogger(EventAuditHandlers.class); // 审计日志输出器
    /** 订阅 PlayerLoginEvent：AccountPlayerService 选角成功后发布 */

    @Subscribe // @Subscribe 注解
    public void onPlayerLogin(PlayerLoginEvent event) { // EventAuditHandlers.onPlayerLogin：PlayerLoginEvent event
        var p = event.getOwner(); // 取选角成功的 Player 实体
        log.info("EventBus PlayerLoginEvent playerId={} name={}", p.getId(), p.getName()); // ELK 关联 playerId 与登录链路
    } // onPlayerLogin 方法体结束
    /** 订阅 PlayerFuncOpenEvent：FunctionService.open 成功后发布 */

    @Subscribe // @Subscribe 注解
    public void onFuncOpen(PlayerFuncOpenEvent event) { // EventAuditHandlers.onFuncOpen：PlayerFuncOpenEvent event
        var p = event.getOwner(); // 取解锁功能的角色
        log.info("EventBus PlayerFuncOpenEvent playerId={} funcId={}", p.getId(), event.getFuncId()); // 运营核对功能解锁记录
    } // onFuncOpen 方法体结束
    /** 订阅 RpcConnectedEvent：Netty RPC 与 center 握手成功后发布 */

    @Subscribe // @Subscribe 注解
    public void onRpcConnected(RpcConnectedEvent event) { // EventAuditHandlers.onRpcConnected：RpcConnectedEvent event
        log.info("EventBus RpcConnectedEvent sessionReady={}", event.getCenterSession() != null); // 跨服消息通道就绪审计
    } // onRpcConnected 方法体结束
} // EventAuditHandlers 类体结束
