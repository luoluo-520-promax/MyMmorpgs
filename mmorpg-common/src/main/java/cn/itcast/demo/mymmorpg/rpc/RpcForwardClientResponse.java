/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcForwardClientResponse.java
 * 类型：类
 * 职责：战斗服处理转发请求后的 RPC 响应体。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

/**
 * 战斗服 → 游戏服 RPC 响应体：客户端战斗转发的处理结果。
 * <p>
 * 战斗服演算完成后，将需下发给客户端的 msgId + payload 经本对象回传游戏服，
 * 游戏服再通过玩家 Session 推送给客户端，完成跨服战斗闭环。
 * </p>
 */
public class RpcForwardClientResponse {

    /** 与  相同，供 {@link CallBackService#complete} 匹配 Future */
    private long requestId;

    /** 需下发给客户端的战斗协议消息号 */
    private int msgId;

    /** 战斗服生成的客户端报文二进制体（如伤害结果、技能 CD 等） */
    private byte[] payload;

    /** JSON 反序列化无参构造 */
    public RpcForwardClientResponse() {
    }

    /**
     * 构造战斗服回包。
     *
     * @param requestId 对应请求的 requestId
     * @param msgId     回给客户端的消息类型
     * @param payload   回给客户端的协议字节
     */
    public RpcForwardClientResponse(long requestId, int msgId, byte[] payload) {
        this.requestId = requestId; // 关联发起方 RpcClient.send 注册的 Future
        this.msgId = msgId; // 游戏服转发给客户端时使用同一 msgId
        this.payload = payload; // 战斗逻辑输出，游戏服原样或封装后下发
    }

    /** @return 关联的请求 requestId */
    public long getRequestId() {
        return requestId;
    }

    /** @param requestId 设置 requestId */
    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    /** @return 客户端协议 msgId */
    public int getMsgId() {
        return msgId;
    }

    /** @param msgId 设置 msgId */
    public void setMsgId(int msgId) {
        this.msgId = msgId;
    }

    /** @return 下发给客户端的战斗包字节 */
    public byte[] getPayload() {
        return payload;
    }

    /** @param payload 设置响应载荷 */
    public void setPayload(byte[] payload) {
        this.payload = payload;
    }
}
