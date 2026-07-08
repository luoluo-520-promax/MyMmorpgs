/**
 * 进程内事件基接口：所有经 {@link EventBus} 派发的事件须实现本接口，
 * 通过 owner 字段支持「仅投递给特定玩家/会话」的细粒度订阅。
 */
package jforgame.commons.eventbus;

/**
 * 事件携带的 owner 用于 {@link OwnerScopedSubscriber} 过滤：
 * 例如 RpcConnectedEvent 的 owner 为 serverId，则只有关心该 serverId 的 Handler 会收到回调。
 */
public interface BaseEvent {

    /**
     * 返回事件归属对象（玩家 ID、serverId、Channel 会话等）。
     * 返回 null 表示不做 owner 过滤，所有匹配事件类型的订阅者都会收到。
     */
    Object getOwner();
}
