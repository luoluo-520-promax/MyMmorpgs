/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/SkillFacade.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：门面类 SkillFacade，协调协议层与业务服务。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.handler.MessageRoute; // module=Modules.SKILL
import cn.itcast.demo.mymmorpg.handler.RequestHandler; // cmd 1/3/5 列表/学习/释放
import cn.itcast.demo.mymmorpg.handler.DispatchSession; // playerId 来自选角后会话绑定
import cn.itcast.demo.mymmorpg.protocol.GamePackets; // 技能相关 CsReq 包装
import cn.itcast.demo.mymmorpg.protocol.Modules; // SKILL 模块编号
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 回包经 ClientRequestTask.send 出站
import cn.itcast.demo.mymmorpg.protocol.protobuf.CastSkillCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetPlayerSkillsCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetPlayerSkillsScRsp; // loading 标志触发 SKILL 预加载
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort; // 预加载类型 SKILL
import cn.itcast.demo.mymmorpg.protocol.protobuf.LearnSkillCsReq;
import cn.itcast.demo.mymmorpg.port.PlayerDataPreloadPort;
import cn.itcast.demo.mymmorpg.service.SkillService; // 技能列表/学习/施法逻辑，Cast 可能标记 BattleMessage
import org.springframework.stereotype.Component; // GameMessageFactory 扫描注册
@Component // Spring 单例 Facade，SKILL 模块 msgId 路由入口
@MessageRoute(module = Modules.SKILL) // signedMsgId(SKILL,cmd) 注册到 GameMessageFactory.routes
public class SkillFacade { // SKILL 模块 handler，CastSkill 若标记 BattleMessage 可走 RPC 转发 FIGHT
    private final SkillService skillService; // 技能列表/学习/施法领域逻辑
    private final PlayerDataPreloadPort preloadService;
    public SkillFacade(SkillService skillService, PlayerDataPreloadPort preloadService) {
        this.skillService = skillService;
        this.preloadService = preloadService;
    } // 编译单元结束

    @RequestHandler(cmd = 1) // msgId：GetPlayerSkillsCsReq
    public ProtocolMessage list(DispatchSession session, GamePackets.GetPlayerSkillsCsReq pkt) throws Exception { // SkillFacade.list：DispatchSession session, GamePackets.GetPlayerSkillsCsReq pk
        long pid = session.playerId() != null ? session.playerId() : 0L; // 查询技能列表须已选角
        ProtocolMessage out = skillService.handleGetPlayerSkills(pid, // 组装 GetPlayerSkillsScRsp protobuf
                GetPlayerSkillsCsReq.parseFrom(pkt.payload())); // 解码 GetPlayerSkillsCsReq
        GetPlayerSkillsScRsp rsp = GetPlayerSkillsScRsp.parseFrom(out.payload()); // 从回包 payload 解析 loading 标志
        if (rsp.getLoading()) { // 技能 Redis/DB 缓存未就绪
            preloadService.trigger(pid, PlayerDataLoadPort.DataType.SKILL); // 后台异步加载 SKILL 数据
        } // list 方法体结束

        return out; // GetPlayerSkillsScRsp 经 session.send 编码为 Netty/WebSocket 二进制帧出站
    } // 编译单元结束

    @RequestHandler(cmd = 3) // msgId：LearnSkillCsReq
    public ProtocolMessage learn(DispatchSession session, GamePackets.LearnSkillCsReq pkt) throws Exception { // SkillFacade.learn：DispatchSession session, GamePackets.LearnSkillCsReq pkt
        long pid = session.playerId() != null ? session.playerId() : 0L; // 学习技能须绑定 playerId
        return skillService.handleLearnSkill(pid, // 校验等级/消耗，返回 LearnSkillScRsp
                LearnSkillCsReq.parseFrom(pkt.payload())); // 解码 skillId 等 LearnSkillCsReq 字段
    } // learn 方法体结束

    @RequestHandler(cmd = 5) // msgId：CastSkillCsReq；若实现 BattleMessage 则 GAME 服可能 RPC 转发 FIGHT
    public ProtocolMessage cast(DispatchSession session, GamePackets.CastSkillCsReq pkt) throws Exception { // SkillFacade.cast：DispatchSession session, GamePackets.CastSkillCsReq pkt
        long pid = session.playerId() != null ? session.playerId() : 0L; // 施法须已选角，playerId 写入 RpcForwardClientMessage
        return skillService.handleCastSkill(pid, // 本地演算或返回 CastSkillScRsp；BattleMessage 时 MessageDispatchPipeline 优先 RPC
                CastSkillCsReq.parseFrom(pkt.payload())); // 解码 targetId/skillId 等 CastSkillCsReq 字段
    } // cast 方法体结束
} // 编译单元结束
