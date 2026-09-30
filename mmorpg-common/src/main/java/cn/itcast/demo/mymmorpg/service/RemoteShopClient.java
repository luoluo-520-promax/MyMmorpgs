package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CreateShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopPurchaseHistoryCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopShelfCsReq;

public interface RemoteShopClient {

    ProtocolMessage handleGetShelf(long playerId, GetShopShelfCsReq req);

    ProtocolMessage handleCreateOrder(long playerId, CreateShopOrderCsReq req);

    ProtocolMessage handleGetOrder(long playerId, GetShopOrderCsReq req);

    ProtocolMessage handlePurchaseHistory(long playerId, GetShopPurchaseHistoryCsReq req);
}
