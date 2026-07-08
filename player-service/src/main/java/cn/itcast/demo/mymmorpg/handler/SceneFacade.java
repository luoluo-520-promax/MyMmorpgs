/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/SceneFacade.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：门面类 SceneFacade，协调协议层与业务服务。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.handler.MessageRoute; // module=Modules.SCENE，msgId=3*100+cmd
import cn.itcast.demo.mymmorpg.handler.RequestHandler; // 进场景/移动/切线等 cmd 与 GamePackets 子类对齐
import cn.itcast.demo.mymmorpg.handler.DispatchSession; // Netty/WebSocket 统一会话，playerId 作 dispatchKey
import cn.itcast.demo.mymmorpg.protocol.GamePackets; // 场景 CsReq PayloadPacket，payload() 为 protobuf 字节
import cn.itcast.demo.mymmorpg.protocol.Modules; // SCENE 模块编号，GameMessageFactory 计算 signedMsgId
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 回包；EnterScene/SwitchLine 成功触发 afterResponse 绑定推送
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import cn.itcast.demo.mymmorpg.service.SceneActorService; // 场景 Actor、AOI、分线管理，按 playerId 串行
import org.springframework.stereotype.Component; // GameMessageFactory @PostConstruct 扫描 @MessageRoute Bean
@Component // Spring 单例 Facade，启动时注册 msgId 301/303/305/308/310 路由
@MessageRoute(module = Modules.SCENE) // signedMsgId(3,cmd) 写入 GameMessageFactory.routes
public class SceneFacade { // SCENE 模块 protobuf 请求入口，由 ClientRequestTask 反射调用
    /** 场景分线 Actor 服务，处理进场景、移动同步、附近实体 */
    private final SceneActorService sceneActorService; // 构造注入，SceneActor 按 playerId 分片串行处理移动/AOI
    public SceneFacade(SceneActorService sceneActorService) { // GameMessageFactory 扫描时绑定 MethodHandle 到此实例
        this.sceneActorService = sceneActorService; // 持有 SceneActorService，供 enter/move 等 handler 委托
    } // 编译单元结束

    @RequestHandler(cmd = 1) // msgId 301：EnterSceneCsReq；成功回包后 NettyDispatchSession/WsDispatchSession.afterResponse 绑定推送
    public ProtocolMessage enter(DispatchSession session, GamePackets.EnterSceneCsReq pkt) throws Exception { // SceneFacade.enter：DispatchSession session, GamePackets.EnterSceneCsReq pkt
        long pid = session.playerId() != null ? session.playerId() : 0L; // 未选角 pid=0，SceneActorService 回 EnterSceneScRsp 错误 retcode
        return sceneActorService.handleEnterScene(pid, EnterSceneCsReq.parseFrom(pkt.payload())); // parseFrom 解码 CsReq，EnterSceneScRsp 经 session.send 编码出站
    } // enter 方法体结束

    @RequestHandler(cmd = 3) // msgId 303：GetCurSceneInfoCsReq
    public ProtocolMessage cur(DispatchSession session, GamePackets.GetCurSceneInfoCsReq pkt) throws Exception { // SceneFacade.cur：DispatchSession session, GamePackets.GetCurSceneInfoCsReq pk
        long pid = session.playerId() != null ? session.playerId() : 0L; // AuthFacade 选角后写入 session.playerId，作 dispatchKey 与业务主键
        return sceneActorService.handleGetCurSceneInfo(pid, GetCurSceneInfoCsReq.parseFrom(pkt.payload())); // GetCurSceneInfoScRsp 含当前场景/分线 protobuf 字段
    } // cur 方法体结束

    @RequestHandler(cmd = 5) // msgId 305：MoveCsReq，高频消息，dispatchKey=playerId 串行处理
    public ProtocolMessage move(DispatchSession session, GamePackets.MoveCsReq pkt) throws Exception { // SceneFacade.move：DispatchSession session, GamePackets.MoveCsReq pkt
        long pid = session.playerId() != null ? session.playerId() : 0L; // 移动同步须已选角，否则 SceneActor 拒绝并回错误 ScRsp
        return sceneActorService.handleMove(pid, MoveCsReq.parseFrom(pkt.payload())); // MoveScRsp 确认坐标，AOI 广播由 SceneActor 异步推送
    } // move 方法体结束

    @RequestHandler(cmd = 8) // msgId 308：SwitchLineCsReq；成功时 afterResponse 刷新 PlayerPushRegistry 绑定
    public ProtocolMessage switchLine(DispatchSession session, GamePackets.SwitchLineCsReq pkt) throws Exception { // SceneFacade.switchLine：DispatchSession session, GamePackets.SwitchLineCsReq pkt
        long pid = session.playerId() != null ? session.playerId() : 0L; // 切线须绑定 playerId，DispatchThreadModel 保证同玩家串行
        return sceneActorService.handleSwitchLine(pid, SwitchLineCsReq.parseFrom(pkt.payload())); // SwitchLineScRsp OK 时 afterResponse 刷新 Netty/WebSocket 推送通道
    } // switchLine 方法体结束

    @RequestHandler(cmd = 10) // msgId 310：GetNearbyEntitiesCsReq，AOI 附近实体
    public ProtocolMessage nearby(DispatchSession session, GamePackets.GetNearbyEntitiesCsReq pkt) throws Exception { // SceneFacade.nearby：DispatchSession session, GamePackets.GetNearbyEntitiesCsReq 
        long pid = session.playerId() != null ? session.playerId() : 0L; // AOI 查询依赖进场景后的 Actor 上下文
        return sceneActorService.handleGetNearby(pid, GetNearbyEntitiesCsReq.parseFrom(pkt.payload())); // GetNearbyEntitiesScRsp 含附近玩家/NPC protobuf 列表
    } // nearby 方法体结束
} // 编译单元结束
