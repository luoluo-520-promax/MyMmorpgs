/**
 * 可选的 owner 作用域订阅者：实现本接口的 Bean 在 {@link EventBus#publish} 时会与事件的 owner 比对，
 * 只有 scopeOwner 与 event.getOwner() 相等（或 getOwnerScope 返回 null）时才触发 @Subscribe 方法。
 */
package jforgame.commons.eventbus;

/**
 * 典型用法：游戏服只关心本 serverId 的 RpcConnectedEvent，
 * Handler 实现 getOwnerScope() 返回自身 serverId，避免收到其他游戏服节点的 RPC 连接事件。
 */
public interface OwnerScopedSubscriber {

    /**
     * 返回本订阅者关心的 owner 范围。
     * 返回 null 表示不做 owner 限定，与未实现本接口的订阅者行为相同（全局接收）。
     */
    Object getOwnerScope();
}
