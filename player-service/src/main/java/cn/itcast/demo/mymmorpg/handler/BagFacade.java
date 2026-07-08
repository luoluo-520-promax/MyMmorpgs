/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/BagFacade.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：门面类 BagFacade，协调协议层与业务服务。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.handler.MessageRoute; // module=Modules.BAG
import cn.itcast.demo.mymmorpg.handler.RequestHandler; // cmd 1/3/5/7/9 对应背包协议
import cn.itcast.demo.mymmorpg.handler.DispatchSession; // 从 session 取 playerId 作为业务主键
import cn.itcast.demo.mymmorpg.protocol.GamePackets; // CsReq PayloadPacket 包装类
import cn.itcast.demo.mymmorpg.protocol.Modules; // BAG 模块编号
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一回包结构，经 GameMessageEncoder/BinaryFrameSender 出站
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import cn.itcast.demo.mymmorpg.service.BagService; // 背包 CRUD 领域服务
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort; // 异步预加载数据类型枚举 BAG
import cn.itcast.demo.mymmorpg.port.PlayerDataPreloadPort; // 缓存未命中时后台加载
import org.springframework.stereotype.Component; // GameMessageFactory 扫描注册
@Component // Spring 单例，@MessageRoute 供 GameMessageFactory 发现 BAG 模块路由
@MessageRoute(module = Modules.BAG) // msgId = abs(Modules.BAG)*100+cmd
public class BagFacade { // BAG 模块 protobuf 请求入口，DispatchThreadModel 按 playerId 串行
    private final BagService bagService; // 背包领域服务：查询/使用/丢弃/整理/出售
    private final PlayerDataPreloadPort preloadService;
    public BagFacade(BagService bagService, PlayerDataPreloadPort preloadService) {
        this.bagService = bagService;
        this.preloadService = preloadService;
    } // 编译单元结束

    @RequestHandler(cmd = 1) // msgId：GetBagInfoCsReq
    public ProtocolMessage info(DispatchSession session, GamePackets.GetBagInfoCsReq pkt) throws Exception { // BagFacade.info：DispatchSession session, GamePackets.GetBagInfoCsReq pkt
        long pid = session.playerId() != null ? session.playerId() : 0L; // 未选角 pid=0，BagService 回 GetBagInfoScRsp 非 OK retcode
        ProtocolMessage out = bagService.handleGetBagInfo(pid, // 委托 BagService 组装 GetBagInfoScRsp protobuf
                GetBagInfoCsReq.parseFrom(pkt.payload())); // Netty/WebSocket 帧 payload 解码为 GetBagInfoCsReq
        GetBagInfoScRsp rsp = GetBagInfoScRsp.parseFrom(out.payload()); // 从 ProtocolMessage.payload 解析 ScRsp 检查 loading 标志
        if (rsp.getLoading()) { // Redis/DB 背包缓存未就绪，客户端应稍后重试
            preloadService.trigger(pid, PlayerDataLoadPort.DataType.BAG); // 后台异步加载 BAG 数据到缓存
        } // info 方法体结束

        return out; // GetBagInfoScRsp 经 ClientRequestTask -> session.send -> GameMessageEncoder 编码出站
    } // 编译单元结束

    @RequestHandler(cmd = 3) // msgId：UseItemCsReq
    public ProtocolMessage use(DispatchSession session, GamePackets.UseItemCsReq pkt) throws Exception { // BagFacade.use：DispatchSession session, GamePackets.UseItemCsReq pkt
        long pid = session.playerId() != null ? session.playerId() : 0L; // 使用物品须已选角，playerId 来自 AuthFacade 写入的 session
        return bagService.handleUseItem(pid, // 扣减物品、触发效果，返回 UseItemScRsp
                UseItemCsReq.parseFrom(pkt.payload())); // 解码 itemId/slot 等 UseItemCsReq 字段
    } // use 方法体结束

    @RequestHandler(cmd = 5) // msgId：DiscardItemCsReq
    public ProtocolMessage discard(DispatchSession session, GamePackets.DiscardItemCsReq pkt) throws Exception { // BagFacade.discard：DispatchSession session, GamePackets.DiscardItemCsReq pkt
        long pid = session.playerId() != null ? session.playerId() : 0L; // dispatchKey=playerId，同玩家背包操作串行
        return bagService.handleDiscardItem(pid, // 从背包移除物品，返回 DiscardItemScRsp
                DiscardItemCsReq.parseFrom(pkt.payload())); // 解码 slot/count 等 DiscardItemCsReq 字段
    } // discard 方法体结束

    @RequestHandler(cmd = 7) // msgId：SortBagCsReq
    public ProtocolMessage sort(DispatchSession session, GamePackets.SortBagCsReq pkt) throws Exception { // BagFacade.sort：DispatchSession session, GamePackets.SortBagCsReq pkt
        long pid = session.playerId() != null ? session.playerId() : 0L; // 整理背包须绑定 playerId
        return bagService.handleSortBag(pid, // 重排格子，返回 SortBagScRsp 含新布局
                SortBagCsReq.parseFrom(pkt.payload())); // SortBagCsReq 通常无业务字段，parseFrom 校验帧完整性
    } // sort 方法体结束

    @RequestHandler(cmd = 9) // msgId：SellItemCsReq
    public ProtocolMessage sell(DispatchSession session, GamePackets.SellItemCsReq pkt) throws Exception { // BagFacade.sell：DispatchSession session, GamePackets.SellItemCsReq pkt
        long pid = session.playerId() != null ? session.playerId() : 0L; // 出售物品须已选角
        return bagService.handleSellItem(pid, // 扣物品加金币，返回 SellItemScRsp
                SellItemCsReq.parseFrom(pkt.payload())); // 解码 slot/npcId 等 SellItemCsReq 字段
    } // sell 方法体结束
} // 编译单元结束
