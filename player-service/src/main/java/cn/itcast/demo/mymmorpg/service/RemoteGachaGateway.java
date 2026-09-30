package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.GachaCommandClient;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DoGachaCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ExchangeGachaCeilingCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaHistoryCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaInfoCsReq;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "game.gacha.remote.enabled", havingValue = "true")
public class RemoteGachaGateway implements RemoteGachaClient {

    private final GachaCommandClient gachaCommandClient;

    public RemoteGachaGateway(GachaCommandClient gachaCommandClient) {
        this.gachaCommandClient = gachaCommandClient;
    }

    @Override
    public ProtocolMessage handleGetGachaInfo(long playerId, GetGachaInfoCsReq req) {
        return new ProtocolMessage(MessageId.GET_GACHA_INFO_SC_RSP,
                gachaCommandClient.info(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleDoGacha(long playerId, DoGachaCsReq req) {
        return new ProtocolMessage(MessageId.DO_GACHA_SC_RSP,
                gachaCommandClient.draw(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleExchangeCeiling(long playerId, ExchangeGachaCeilingCsReq req) {
        return new ProtocolMessage(MessageId.EXCHANGE_GACHA_CEILING_SC_RSP,
                gachaCommandClient.exchange(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleGetGachaHistory(long playerId, GetGachaHistoryCsReq req) {
        return new ProtocolMessage(MessageId.GET_GACHA_HISTORY_SC_RSP,
                gachaCommandClient.history(playerId, req.toByteArray()));
    }
}
