package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DoGachaCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ExchangeGachaCeilingCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaHistoryCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaInfoCsReq;

public interface RemoteGachaClient {

    ProtocolMessage handleGetGachaInfo(long playerId, GetGachaInfoCsReq req);

    ProtocolMessage handleDoGacha(long playerId, DoGachaCsReq req);

    ProtocolMessage handleExchangeCeiling(long playerId, ExchangeGachaCeilingCsReq req);

    ProtocolMessage handleGetGachaHistory(long playerId, GetGachaHistoryCsReq req);
}
