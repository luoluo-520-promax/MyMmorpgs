/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/ActivityFacade.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：活动模块协议门面，解析 WebSocket 包并路由至 ActivityCommandGateway，触发预加载。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // WebSocket 活动模块 Modules.ACTIVITY 协议门面

import cn.itcast.demo.mymmorpg.handler.DispatchSession; // WebSocket 会话，含 playerId 与 accountId
import cn.itcast.demo.mymmorpg.handler.MessageRoute; // 标注模块号 Modules.ACTIVITY，供消息分发器扫描
import cn.itcast.demo.mymmorpg.handler.RequestHandler; // 标注子命令 cmd，映射到具体处理方法
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.PlayerDataPreloadPort;
import cn.itcast.demo.mymmorpg.protocol.GamePackets; // 网关层 Protobuf 包装，payload 需二次 parseFrom
import cn.itcast.demo.mymmorpg.protocol.Modules; // 活动模块号，与协议定义一致
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一响应 msgId + byte[] payload
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import org.springframework.stereotype.Component; // 消息分发器自动注册 ACTIVITY 模块处理器

@Component // Spring 容器管理，供 GameServerHandler 反射调用
@MessageRoute(module = Modules.ACTIVITY) // WebSocket 路由 module=ACTIVITY，cmd 唯一定位 list/detail/claim
public class ActivityFacade { // 解析外层 GamePackets 并转发 ActivityCommandGateway

    /** 活动命令网关，本地/远程路由在此完成 */
    private final ActivityCommandGateway activityCommandGateway; // cmd=1/3/5 活动列表/详情/领奖

    private final PlayerDataPreloadPort preloadService;

    public ActivityFacade(ActivityCommandGateway activityCommandGateway, PlayerDataPreloadPort preloadService) {
        this.activityCommandGateway = activityCommandGateway;
        this.preloadService = preloadService;
    }

    /** 活动列表：module=ACTIVITY, cmd=1 */
    @RequestHandler(cmd = 1) // 映射 GetActivityListCsReq
    public ProtocolMessage list(DispatchSession session, GamePackets.GetActivityListCsReq pkt) throws Exception { // 文件维护说明
        long pid = session.playerId() != null ? session.playerId() : 0L; // 未选角时 playerId 为 0
        ProtocolMessage out = activityCommandGateway.handleGetActivityList(pid, // 文件维护说明
                GetActivityListCsReq.parseFrom(pkt.payload())); // 解析内层 Protobuf
        GetActivityListScRsp rsp = GetActivityListScRsp.parseFrom(out.payload()); // 解析 GET_ACTIVITY_LIST_SC_RSP 检查 loading
        if (rsp.getLoading()) { // 活动数据尚未预加载完成
            preloadService.trigger(pid, PlayerDataLoadPort.DataType.ACTIVITY); // 异步预加载 activity 表数据
        }
        return out; // 原样下发 GET_ACTIVITY_LIST_SC_RSP（含 loading 或完整列表）
    }

    /** 活动详情：module=ACTIVITY, cmd=3 */
    @RequestHandler(cmd = 3) // 映射 GetActivityDetailCsReq
    public ProtocolMessage detail(DispatchSession session, GamePackets.GetActivityDetailCsReq pkt) throws Exception { // 文件维护说明
        long pid = session.playerId() != null ? session.playerId() : 0L; // 当前会话绑定的角色 ID
        ProtocolMessage out = activityCommandGateway.handleGetActivityDetail(pid, // 文件维护说明
                GetActivityDetailCsReq.parseFrom(pkt.payload())); // 解析内层请求
        GetActivityDetailScRsp rsp = GetActivityDetailScRsp.parseFrom(out.payload()); // 检查 GET_ACTIVITY_DETAIL_SC_RSP loading
        if (rsp.getLoading()) { // 活动详情尚未就绪
            preloadService.trigger(pid, PlayerDataLoadPort.DataType.ACTIVITY); // 触发 activity 数据预载
        }
        return out; // 下发 GET_ACTIVITY_DETAIL_SC_RSP 或 loading 占位响应
    }

    /** 领取奖励：module=ACTIVITY, cmd=5 */
    @RequestHandler(cmd = 5) // 映射 ClaimActivityRewardCsReq
    public ProtocolMessage claim(DispatchSession session, GamePackets.ClaimActivityRewardCsReq pkt) throws Exception { // 文件维护说明
        long pid = session.playerId() != null ? session.playerId() : 0L; // 领奖必须关联当前角色
        return activityCommandGateway.handleClaimActivityReward(pid, // 文件维护说明
                ClaimActivityRewardCsReq.parseFrom(pkt.payload())); // 转发至 CLAIM_ACTIVITY_REWARD
    }
}
