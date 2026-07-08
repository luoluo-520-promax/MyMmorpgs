/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcBaseFacade.java
 * 类型：类
 * 职责：RPC 定时任务——连接健康检查与战斗节点目录同步。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import cn.itcast.demo.mymmorpg.net.GameContext;
import cn.itcast.demo.mymmorpg.net.ServerType;
import jforgame.commons.eventbus.EventBus;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * 跨服 RPC 定时门面：维护与中心服/战斗服的长连接健康，并同步战斗节点目录。
 * <p>
 * 仅在存在 {@link CenterRpcClient} 时启用（游戏服、战斗服等需连中心的节点）。
 * </p>
 */
@Configuration
@ConditionalOnBean(CenterRpcClient.class)
public class RpcBaseFacade {

    /** 与中心服 RPC 通信的客户端，负责注册、心跳、拉取战斗服列表 */
    private final CenterRpcClient centerRpcClient;

    /** 封装向中心服发起 RPC 请求的工具（如拉取战斗节点目录） */
    private final CrossMessageUtil crossMessageUtil;

    /**
     * @param centerRpcClient  中心服 RPC 客户端 Bean
     * @param crossMessageUtil   跨服消息发送工具
     */
    public RpcBaseFacade(CenterRpcClient centerRpcClient, CrossMessageUtil crossMessageUtil) {
        this.centerRpcClient = centerRpcClient; // 健康检查：重连/补注册断开的跨服 RPC
        this.crossMessageUtil = crossMessageUtil; // 目录同步：向中心服查询可用战斗服节点
    }

    /**
     * 定时检测并恢复与中心服/集群的 RPC 连接（默认每 30 秒）。
     * 中心服自身无需向自己注册，故跳过。
     */
    @Scheduled(fixedDelayString = "${rpc.schedule.health-ms:30000}")
    public void healthCheck() {
        if (GameContext.serverType == ServerType.CENTRE) { // 中心服是 RPC 枢纽，不做向自身的健康注册
            return;
        }
        centerRpcClient.checkAndRegisterConnections(); // 游戏服/战斗服：探测断线并重连、重新握手注册
    }

    /**
     * 游戏服定时从中心服拉取战斗服节点目录（默认每 59 秒），供 {@link RpcClientRouter} 负载均衡选服。
     */
    @Scheduled(fixedDelayString = "${rpc.schedule.directory-sync-ms:59000}")
    public void directorySync() {
        if (GameContext.serverType != ServerType.GAME) { // 仅游戏服需要知道有哪些战斗服可转发客户端战斗包
            return;
        }
        crossMessageUtil.requestToCenter(new Rpc_G2C_FetchFightServerNodes()); // G→C：拉取战斗服列表，更新本地路由表
    }
}
