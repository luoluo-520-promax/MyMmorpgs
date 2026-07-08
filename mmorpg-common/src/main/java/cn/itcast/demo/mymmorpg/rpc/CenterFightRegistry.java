/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/CenterFightRegistry.java
 * 类型：类
 * 职责：中心服维护的已注册战斗服节点注册表，支持注册、注销与全量查询。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中心服维护的已注册战斗服节点表：战斗服 RPC 握手成功后写入，断线或注销时移除。
 */
@Component
public class CenterFightRegistry {

    /** 以 serverId 为键的战斗服节点映射，ConcurrentHashMap 保证多线程注册/查询安全 */
    private final Map<Integer, FightServerNode> fights = new ConcurrentHashMap<>();

    /**
     * 战斗服 RPC 登录成功后，将其节点信息注册到中心服目录。
     *
     * @param node 含 sid、type、ip、port 的战斗服节点描述
     */
    public void register(FightServerNode node) {
        if (node != null) { // 忽略空节点，防止污染注册表
            fights.put(node.getSid(), node); // 以 serverId 为键覆盖写入（重连时更新地址）
        }
    }

    /**
     * 战斗服断线或主动下线时，从中心服目录中移除对应节点。
     *
     * @param sid 待移除的战斗服 serverId
     */
    public void unregister(int sid) {
        fights.remove(sid); // 从注册表删除，后续 listAll 不再包含该节点
    }

    /**
     * 返回当前所有已注册战斗服节点的快照，供响应 Rpc_G2C_FetchFightServerNodes 请求。
     */
    public List<FightServerNode> listAll() {
        return new ArrayList<>(fights.values()); // 拷贝 values 避免外部直接修改内部 Map
    }
}
