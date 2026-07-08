/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcWireEnvelope.java
 * 类型：类
 * 职责：RPC 线上统一消息信封，kind 标识消息类型，body 承载 JSON 反序列化后的业务对象。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

/**
 * RPC 线上统一信封：kind 为消息类型（Java 类 simpleName），body 为 JSON 反序列化后的对象。
 * <p>Netty 写出前由 {@link RpcJsonCodec#wrap(Object)} 封装，接收端按 kind 路由到对应 Handler。
 */
public class RpcWireEnvelope {

    /** 消息类型标识，通常为 RPC DTO 类的 simpleName（如 RpcReqServerLogin） */
    private String kind;
    /** JSON 反序列化后的 RPC 业务消息体对象 */
    private Object body;

    /** 无参构造，供 JSON 反序列化使用 */
    public RpcWireEnvelope() {
    }

    /**
     * 构造一条完整的 RPC 线上信封。
     *
     * @param kind 消息类型 simpleName，接收端据此选择消息处理器
     * @param body 已序列化为 JSON 对象树的业务 DTO
     */
    public RpcWireEnvelope(String kind, Object body) {
        this.kind = kind; // 设置路由键，RpcMessageDispatcher 按 kind 分发
        this.body = body; // 设置业务载荷，Handler 中强转为具体 DTO 类型
    }

    /** 获取消息类型标识（路由键） */
    public String getKind() {
        return kind;
    }

    /** 设置消息类型标识 */
    public void setKind(String kind) {
        this.kind = kind;
    }

    /** 获取 JSON 反序列化后的业务消息体 */
    public Object getBody() {
        return body;
    }

    /** 设置 JSON 反序列化后的业务消息体 */
    public void setBody(Object body) {
        this.body = body;
    }
}
