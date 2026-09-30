package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;

public interface RemoteBagClient {
    ProtocolMessage handleGetBagInfo(long playerId, GetBagInfoCsReq req);
    ProtocolMessage handleUseItem(long playerId, UseItemCsReq req);
    ProtocolMessage handleDiscardItem(long playerId, DiscardItemCsReq req);
    ProtocolMessage handleSortBag(long playerId, SortBagCsReq req);
    ProtocolMessage handleSellItem(long playerId, SellItemCsReq req);
    ProtocolMessage handleEquipItem(long playerId, EquipItemCsReq req);
    ProtocolMessage handleUnequipItem(long playerId, UnequipItemCsReq req);
}
