package cn.itcast.demo.mymmorpg.port;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DiscardItemCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EquipItemCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetBagInfoCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SellItemCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SortBagCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.UnequipItemCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.UseItemCsReq;

/**
 * 背包协议命令端口：gateway 仅依赖本接口，可不编译依赖 bag-service 实现类。
 */
public interface BagCommandPort {

    ProtocolMessage handleGetBagInfo(long playerId, GetBagInfoCsReq req);

    ProtocolMessage loadBagNow(long playerId);

    ProtocolMessage handleUseItem(long playerId, UseItemCsReq req);

    ProtocolMessage handleDiscardItem(long playerId, DiscardItemCsReq req);

    ProtocolMessage handleSortBag(long playerId, SortBagCsReq req);

    ProtocolMessage handleSellItem(long playerId, SellItemCsReq req);

    ProtocolMessage handleEquipItem(long playerId, EquipItemCsReq req);

    ProtocolMessage handleUnequipItem(long playerId, UnequipItemCsReq req);
}
