/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcConnectedEvent.java
 * 类型：类
 * 职责：游戏服与中心服 RPC 握手成功后发布的事件，通知业务层可开始跨服通信。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import jforgame.commons.eventbus.BaseEvent;

/**
 * 与中心服 RPC 连接就绪后发布的事件，订阅方可据此拉取战斗服节点或发起跨服请求。
 */
public class RpcConnectedEvent implements BaseEvent {

    /** 已握手成功的中心服 RPC 会话，可用于后续向中心服发送消息 */
    private final IdSession centerSession;

    /**
     * @param centerSession 刚与中心服建立并完成 RpcReqServerLogin 握手的会话
     */
    public RpcConnectedEvent(IdSession centerSession) {
        this.centerSession = centerSession; // 保存中心服会话，供事件订阅者直接使用
    }

    /**
     * 获取可用于向中心服发送 RPC 消息的会话对象。
     */
    public IdSession getCenterSession() {
        return centerSession;
    }

    @Override
    public Object getOwner() {
        return null; // 全局 RPC 连接事件，不绑定特定玩家或业务实体
    }
}
