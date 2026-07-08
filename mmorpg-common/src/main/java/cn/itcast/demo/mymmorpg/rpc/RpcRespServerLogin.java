/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcRespServerLogin.java
 * 类型：类
 * 职责：跨服 RPC 握手响应，中心服对 RpcReqServerLogin 的鉴权结果回复。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

/**
 * 跨服 RPC 握手响应：告知请求方签名校验是否通过，失败时携带原因说明。
 * <p>客户端收到 success=true 后可发布 {@link RpcConnectedEvent} 并开始正常 RPC 通信。
 */
public class RpcRespServerLogin {

    /** 握手鉴权是否成功（签名正确且 serverId 合法） */
    private boolean success;
    /** 握手结果说明：成功时为欢迎信息，失败时为拒绝原因 */
    private String message;

    /** 无参构造，供 JSON 反序列化使用 */
    public RpcRespServerLogin() {
    }

    /**
     * 构造握手响应。
     *
     * @param success 鉴权是否通过
     * @param message 结果描述信息
     */
    public RpcRespServerLogin(boolean success, String message) {
        this.success = success; // 标记握手成败，客户端据此决定是否继续 RPC 流程
        this.message = message; // 携带可读说明，便于日志排查
    }

    /** 判断 RPC 握手鉴权是否成功 */
    public boolean isSuccess() {
        return success;
    }

    /** 设置 RPC 握手鉴权结果 */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /** 获取握手结果描述信息 */
    public String getMessage() {
        return message;
    }

    /** 设置握手结果描述信息 */
    public void setMessage(String message) {
        this.message = message;
    }
}
