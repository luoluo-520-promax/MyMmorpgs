/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcC2G_FightServerNodes.java
 * 类型：类
 * 职责：中心服向游戏服返回的战斗服节点目录 RPC 响应消息体。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import java.util.ArrayList;
import java.util.List;

/**
 * 中心服 → 游戏服：返回 {@link CenterFightRegistry} 中维护的全部战斗服节点信息。
 * <p>游戏服收到后据此建立到各战斗服的 RPC 连接。
 */
public class RpcC2G_FightServerNodes {

    /** 中心服当前已注册的战斗服节点列表（sid、type、ip、port） */
    private List<FightServerNode> nodes = new ArrayList<>();

    /** 无参构造，供 JSON 反序列化使用 */
    public RpcC2G_FightServerNodes() {
    }

    /**
     * 直接使用给定节点列表构造响应。
     *
     * @param nodes 中心服注册表中的战斗服节点快照，null 时初始化为空列表
     */
    public RpcC2G_FightServerNodes(List<FightServerNode> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>(); // 防御 null，保证 nodes 始终可迭代
    }

    /**
     * 获取战斗服节点列表，游戏服据此逐个发起 RPC 连接。
     */
    public List<FightServerNode> getNodes() {
        return nodes;
    }

    /**
     * 设置战斗服节点列表（中心服组装响应时调用）。
     */
    public void setNodes(List<FightServerNode> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>(); // 防御 null，避免下游 NPE
    }
}
