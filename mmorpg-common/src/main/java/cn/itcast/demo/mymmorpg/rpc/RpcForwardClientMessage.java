/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcForwardClientMessage.java
 * 类型：类
 * 职责：游戏服转发客户端战斗消息到战斗服的 RPC 请求体。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

/**
 * 游戏服 → 战斗服 RPC 请求体：转发客户端原始战斗协议包。
 * <p>
 * 玩家在游戏服发起战斗操作时，游戏服不本地演算，而是将客户端 msgId + payload
 * 经 {@link RpcClient} 发往战斗服，由战斗服按相同协议处理并回 {@link RpcForwardClientResponse}。
 * </p>
 */
public class RpcForwardClientMessage {

    /** 跨服 RPC 请求唯一号，与 {@link RequestResponseFuture}、回包 requestId 配对 */
    private long requestId;

    /** 发起操作的玩家 ID，战斗服用于定位战斗实例与权限校验 */
    private long playerId;

    /** 客户端战斗协议消息号（与游戏内 Protobuf/自定义协议 msgId 一致） */
    private int msgId;

    /** 客户端战斗包的原始字节载荷，战斗服按 msgId 反序列化后执行业务 */
    private byte[] payload;

    /** Jackson/Protostuff 反序列化需要的无参构造 */
    public RpcForwardClientMessage() {
    }

    /**
     * 构造一次完整的客户端战斗转发 RPC 请求。
     *
     * @param requestId 由 {@link RpcClient#nextRequestId()} 分配
     * @param playerId  当前玩家 ID
     * @param msgId     客户端战斗消息类型
     * @param payload   客户端报文二进制体
     */
    public RpcForwardClientMessage(long requestId, long playerId, int msgId, byte[] payload) {
        this.requestId = requestId; // 供 CallBackService 匹配异步回包
        this.playerId = playerId; // 战斗服据此路由到对应玩家战斗上下文
        this.msgId = msgId; // 标识 payload 的协议类型
        this.payload = payload; // 透传客户端战斗指令，不在游戏服解析
    }

    /** @return 本 RPC 请求的 requestId */
    public long getRequestId() {
        return requestId;
    }

    /** @param requestId 设置请求 ID（JSON 反序列化或重试场景） */
    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    /** @return 玩家 ID */
    public long getPlayerId() {
        return playerId;
    }

    /** @param playerId 设置玩家 ID */
    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    /** @return 客户端战斗协议 msgId */
    public int getMsgId() {
        return msgId;
    }

    /** @param msgId 设置消息号 */
    public void setMsgId(int msgId) {
        this.msgId = msgId;
    }

    /** @return 客户端战斗包原始字节 */
    public byte[] getPayload() {
        return payload;
    }

    /** @param payload 设置战斗包载荷 */
    public void setPayload(byte[] payload) {
        this.payload = payload;
    }
}
