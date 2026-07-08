/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/BuffFacade.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：Buff 模块协议门面，解析 WebSocket 包并委托 BuffService 处理。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // WebSocket Buff 模块 Modules.BUFF 协议门面

import cn.itcast.demo.mymmorpg.handler.MessageRoute; // 标注 Buff 模块号 Modules.BUFF
import cn.itcast.demo.mymmorpg.handler.RequestHandler; // 标注子命令 cmd
import cn.itcast.demo.mymmorpg.handler.DispatchSession; // WebSocket 会话，含 playerId
import cn.itcast.demo.mymmorpg.protocol.GamePackets; // 外层 Protobuf 包装
import cn.itcast.demo.mymmorpg.protocol.Modules; // Buff 模块协议号
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一 Buff 响应 msgId + payload
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetEntityBuffsCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RemoveBuffCsReq;
import cn.itcast.demo.mymmorpg.service.BuffService; // Buff 查询/移除业务实现
import org.springframework.stereotype.Component; // 消息分发器扫描注册 BUFF 模块 Handler

@Component // Spring 管理 Buff 模块 WebSocket 处理器
@MessageRoute(module = Modules.BUFF) // WebSocket 路由 module=BUFF，cmd=1/6 查列表/驱散
public class BuffFacade { // 解析外层 GamePackets 并委托 BuffService

    /** Buff 业务服务，查实体 Buff 列表与主动移除 */
    private final BuffService buffService; // GET_ENTITY_BUFFS / REMOVE_BUFF 业务逻辑

    public BuffFacade(BuffService buffService) { // 文件维护说明
        this.buffService = buffService; // 查 buff_config 关联的实体 Buff 列表与主动驱散
    }

    /** 查询实体 Buff 列表：module=BUFF, cmd=1 */
    @RequestHandler(cmd = 1) // GetEntityBuffsCsReq
    public ProtocolMessage list(DispatchSession session, GamePackets.GetEntityBuffsCsReq pkt) throws Exception { // 文件维护说明
        long pid = session.playerId() != null ? session.playerId() : 0L; // 请求发起者角色 ID
        return buffService.handleGetEntityBuffs(pid, // 文件维护说明
                GetEntityBuffsCsReq.parseFrom(pkt.payload())); // 查玩家/怪物当前 Buff 列表
    }

    /** 移除 Buff：module=BUFF, cmd=6 */
    @RequestHandler(cmd = 6) // RemoveBuffCsReq
    public ProtocolMessage remove(DispatchSession session, GamePackets.RemoveBuffCsReq pkt) throws Exception { // 文件维护说明
        long pid = session.playerId() != null ? session.playerId() : 0L; // 操作者角色 ID
        return buffService.handleRemoveBuff(pid, // 文件维护说明
                RemoveBuffCsReq.parseFrom(pkt.payload())); // 主动驱散指定 buffId
    }
}
