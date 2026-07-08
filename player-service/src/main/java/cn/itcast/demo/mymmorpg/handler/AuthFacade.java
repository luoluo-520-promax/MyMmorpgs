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
import org.springframework.stereotype.Component; // GameMessageFactory 启动扫描注册路由
@Component // Spring Bean，@MessageRoute 供 GameMessageFactory 发现 AUTH 模块
@MessageRoute(module = Modules.AUTH) // 全局 msgId = 1*100+cmd
public class AuthFacade { // 登录/选角/登出 protobuf 入口，成功后更新 Netty ChannelAttr 或 WsState
    /** 账号与玩家业务服务，处理 Redis/DB 层登录态 */
    private final AccountPlayerService accountPlayerService; // 校验 token、写 Redis 登录态、选角互斥
    public AuthFacade(AccountPlayerService accountPlayerService) { // MethodHandle 预绑定 Facade 实例
        this.accountPlayerService = accountPlayerService; // 持有 AccountPlayerService 供 login/select/logout 委托
    } // 编译单元结束

    @RequestHandler(cmd = 1) // msgId 101：AccountLoginCsReq
    public ProtocolMessage accountLogin(DispatchSession session, GamePackets.AccountLoginCsReq pkt) throws Exception { // AuthFacade.accountLogin：DispatchSession session, GamePackets.AccountLoginCsReq pkt
        var req = AccountLoginCsReq.parseFrom(pkt.payload()); // Netty/WebSocket 帧 payload 解码 AccountLoginCsReq
        ProtocolMessage out = accountPlayerService.handleAccountLogin(req); // 校验 token、写 Redis 登录态，组装 AccountLoginScRsp
        var rsp = AccountLoginScRsp.parseFrom(out.payload()); // 从回包 payload 解析 retcode 与 accountId
        if (rsp.getRetcode() == RetCode.OK) { // 登录成功才绑定会话，失败保持未登录态
            session.accountId(rsp.getAccountId()); // 写入 Netty ChannelAttrs.ACCOUNT_ID 或 WsState.accountId
            session.playerId(null); // 新登录清除旧 playerId，须重新选角才能 dispatchKey=playerId
        } // accountLogin 方法体结束

        return out; // AccountLoginScRsp 经 ClientRequestTask -> session.send 编码出站
    } // 编译单元结束

    @RequestHandler(cmd = 3) // msgId 103：SelectPlayerCsReq
    public ProtocolMessage selectPlayer(DispatchSession session, GamePackets.SelectPlayerCsReq pkt) throws Exception { // AuthFacade.selectPlayer：DispatchSession session, GamePackets.SelectPlayerCsReq pkt
        long aid = session.accountId() != null ? session.accountId() : 0L; // 须先 accountLogin 成功才有 accountId
        var req = SelectPlayerCsReq.parseFrom(pkt.payload()); // 解码 roleId 等 SelectPlayerCsReq 字段
        ProtocolMessage out = accountPlayerService.handleSelectPlayer(req, aid, session.marker()); // marker=netty:channelId 或 ws:sessionId 用于多连接互斥
        var parsed = SelectPlayerScRsp.parseFrom(out.payload()); // 解析 SelectPlayerScRsp retcode 与 playerInfo
        if (parsed.getRetcode() == RetCode.OK && parsed.hasPlayerInfo()) { // 选角成功且含 playerInfo 才绑定
            session.playerId(parsed.getPlayerInfo().getPlayerId()); // 写入 dispatchKey 与 PlayerPushRegistry 推送键
        } // selectPlayer 方法体结束

        return out; // SelectPlayerScRsp 经 session.send 编码为 [length][msgId][protobuf] 出站
    } // 编译单元结束

    @RequestHandler(cmd = 5) // msgId 105：PlayerLogoutCsReq
    public ProtocolMessage logout(DispatchSession session, GamePackets.PlayerLogoutCsReq pkt) throws Exception { // AuthFacade.logout：DispatchSession session, GamePackets.PlayerLogoutCsReq pkt
        long aid = session.accountId() != null ? session.accountId() : 0L; // 登出须已登录，accountId 来自 session
        var req = PlayerLogoutCsReq.parseFrom(pkt.payload()); // 解码 PlayerLogoutCsReq
        ProtocolMessage out = accountPlayerService.handleLogout(req, aid, session.playerId()); // 清理 Redis 登录态与在线标记
        session.accountId(null); // 清除 Netty ChannelAttr 或 WsState 上的账号绑定
        session.playerId(null); // 清除玩家，后续消息 dispatchKey 回退 marker.hashCode()
        return out; // PlayerLogoutScRsp 经 ClientRequestTask 编码回客户端
    } // logout 方法体结束
} // 编译单元结束
