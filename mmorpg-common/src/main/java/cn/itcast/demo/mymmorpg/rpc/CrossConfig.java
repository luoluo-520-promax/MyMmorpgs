/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/CrossConfig.java
 * 类型：类
 * 职责：跨服 RPC 配置访问门面，统一读取 signKey、中心服地址等连接参数。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import cn.itcast.demo.mymmorpg.net.CenterConfig;
import cn.itcast.demo.mymmorpg.net.RpcConfig;
import cn.itcast.demo.mymmorpg.net.ServerConfig;

import org.springframework.stereotype.Component;

/**
 * 跨服 RPC 配置访问门面，委托 {@link ServerConfig#getRpc()} 提供连接与鉴权参数。
 */
@Component
public class CrossConfig {

    /** 全局服务器配置，内含 rpc 节点（signKey、center 地址、balanceStrategy 等） */
    private final ServerConfig serverConfig;

    /**
     * 注入全局 ServerConfig，供 RPC 客户端/服务端读取跨服连接参数。
     */
    public CrossConfig(ServerConfig serverConfig) {
        this.serverConfig = serverConfig; // 保存配置根对象，各 getter 从中读取 rpc 子配置
    }

    /**
     * 获取完整 RPC 配置块（含 signKey、center、balanceStrategy 等）。
     */
    public RpcConfig getRpc() {
        return serverConfig.getRpc();
    }

    /**
     * 获取 RPC 握手签名密钥，用于计算 RpcReqServerLogin 中的 MD5 签名字段。
     */
    public String getSignKey() {
        return serverConfig.getRpc().getSignKey();
    }

    /**
     * 获取中心服连接配置（host、port），游戏服/战斗服据此建立到中心服的 RPC 连接。
     */
    public CenterConfig getCenter() {
        return serverConfig.getRpc().getCenter();
    }
}
