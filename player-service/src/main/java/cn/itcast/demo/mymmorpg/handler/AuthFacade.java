/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/AuthFacade.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：门面类 AuthFacade，协调协议层与业务服务。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.handler.MessageRoute; // module=Modules.AUTH，msgId 101/103/105 等
import cn.itcast.demo.mymmorpg.handler.RequestHandler; // cmd 与 GamePackets 子类 @MessageMeta 对齐
import cn.itcast.demo.mymmorpg.handler.DispatchSession; // 登录/选角成功后写 accountId/playerId 到会话
import cn.itcast.demo.mymmorpg.protocol.GamePackets; // PayloadPacket 子类，包装 protobuf byte[]
import cn.itcast.demo.mymmorpg.protocol.Modules; // AUTH 模块编号
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一回包，ClientRequestTask 经 session.send 出站
import cn.itcast.demo.mymmorpg.protocol.RetCode; // OK 时才更新会话绑定
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import cn.itcast.demo.mymmorpg.service.AccountPlayerService; // 账号登录/选角/登出领域逻辑
import cn.itcast.demo.mymmorpg.service.CharacterService;
import org.springframework.stereotype.Component; // GameMessageFactory 启动扫描注册路由
@Component // Spring Bean，@MessageRoute 供 GameMessageFactory 发现 AUTH 模块
@MessageRoute(module = Modules.AUTH) // 全局 msgId = 1*100+cmd
public class AuthFacade { // 登录/选角/登出 protobuf 入口，成功后更新 Netty ChannelAttr 或 WsState
    /** 账号与玩家业务服务，处理 Redis/DB 层登录态 */
    private final AccountPlayerService accountPlayerService; // 校验 token、写 Redis 登录态、选角互斥
    private final CharacterService characterService;

    public AuthFacade(AccountPlayerService accountPlayerService, CharacterService characterService) {
        this.accountPlayerService = accountPlayerService;
        this.characterService = characterService;
    }
    @RequestHandler(cmd = 1) // msgId 101：AccountLoginCsReq
    public ProtocolMessage accountLogin(DispatchSession session, GamePackets.AccountLoginCsReq pkt) throws Exception {
        var req = AccountLoginCsReq.parseFrom(pkt.payload());
        ProtocolMessage out = accountPlayerService.handleAccountLogin(req);
        var rsp = AccountLoginScRsp.parseFrom(out.payload());
        if (rsp.getRetcode() == RetCode.OK) {
            session.accountId(rsp.getAccountId());
            session.playerId(null);
        }
        return out;
    }

    @RequestHandler(cmd = 3) // SELECT_ROLE：登录后选角进入游戏
    public ProtocolMessage selectPlayer(DispatchSession session, GamePackets.SelectPlayerCsReq pkt) throws Exception {
        long aid = session.accountId() != null ? session.accountId() : 0L;
        var req = SelectPlayerCsReq.parseFrom(pkt.payload());
        ProtocolMessage out = accountPlayerService.handleSelectPlayer(req, aid, session.marker());
        var parsed = SelectPlayerScRsp.parseFrom(out.payload());
        if (parsed.getRetcode() == RetCode.OK && parsed.hasPlayerInfo()) {
            session.playerId(parsed.getPlayerInfo().getPlayerId());
        }
        return out;
    }

    @RequestHandler(cmd = 5)
    public ProtocolMessage logout(DispatchSession session, GamePackets.PlayerLogoutCsReq pkt) throws Exception {
        long aid = session.accountId() != null ? session.accountId() : 0L;
        var req = PlayerLogoutCsReq.parseFrom(pkt.payload());
        ProtocolMessage out = accountPlayerService.handleLogout(req, aid, session.playerId());
        session.accountId(null);
        session.playerId(null);
        return out;
    }

    @RequestHandler(cmd = 16)
    public ProtocolMessage renewTicket(DispatchSession session, GamePackets.RenewTicketCsReq pkt) throws Exception {
        return accountPlayerService.handleRenewTicket(RenewTicketCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 7)
    public ProtocolMessage createPlayer(DispatchSession session, GamePackets.CreatePlayerCsReq pkt) throws Exception {
        long aid = session.accountId() != null ? session.accountId() : 0L;
        return characterService.handleCreatePlayer(CreatePlayerCsReq.parseFrom(pkt.payload()), aid);
    }

    @RequestHandler(cmd = 9)
    public ProtocolMessage characterInfo(DispatchSession session, GamePackets.GetCharacterInfoCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return characterService.handleGetCharacterInfo(pid, GetCharacterInfoCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 11)
    public ProtocolMessage allocateTalent(DispatchSession session, GamePackets.AllocateTalentCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return characterService.handleAllocateTalent(pid, AllocateTalentCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 13)
    public ProtocolMessage spendGold(DispatchSession session, GamePackets.SpendGoldCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return characterService.handleSpendGold(pid, SpendGoldCsReq.parseFrom(pkt.payload()));
    }
} // 编译单元结束
