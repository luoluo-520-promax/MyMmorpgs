/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/FightServerNode.java
 * 类型：类
 * 职责：描述中心服目录中一个战斗服节点的连接信息，供游戏服建立 RPC 连接。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

/**
 * 中心目录中的战斗服节点描述：包含 serverId、类型及 RPC 监听地址。
 */
public class FightServerNode {

    /** 战斗服在集群中的唯一 serverId，与 RpcReqServerLogin.serverId 对应 */
    private int sid;
    /** 服务器类型枚举值（区分普通战斗服、副本服等） */
    private int type;
    /** 战斗服 RPC 监听 IP，游戏服据此发起 TCP 连接 */
    private String ip;
    /** 战斗服 RPC 监听端口 */
    private int port;

    /** 无参构造，供 JSON 反序列化使用 */
    public FightServerNode() {
    }

    /**
     * 构造完整的战斗服节点描述，用于中心服注册表存储与目录下发。
     */
    public FightServerNode(int sid, int type, String ip, int port) {
        this.sid = sid; // 战斗服唯一标识
        this.type = type; // 服务器类型
        this.ip = ip; // RPC 连接目标 IP
        this.port = port; // RPC 连接目标端口
    }

    /** 获取战斗服 serverId */
    public int getSid() {
        return sid;
    }

    /** 设置战斗服 serverId（战斗服登录中心服注册时使用） */
    public void setSid(int sid) {
        this.sid = sid;
    }

    /** 获取服务器类型 */
    public int getType() {
        return type;
    }

    /** 设置服务器类型 */
    public void setType(int type) {
        this.type = type;
    }

    /** 获取 RPC 连接 IP */
    public String getIp() {
        return ip;
    }

    /** 设置 RPC 连接 IP（战斗服上报自身监听地址时使用） */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /** 获取 RPC 连接端口 */
    public int getPort() {
        return port;
    }

    /** 设置 RPC 连接端口 */
    public void setPort(int port) {
        this.port = port;
    }
}
