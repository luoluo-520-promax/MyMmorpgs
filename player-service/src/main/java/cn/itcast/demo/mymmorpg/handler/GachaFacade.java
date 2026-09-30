package cn.itcast.demo.mymmorpg.handler;

import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.Modules;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DoGachaCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ExchangeGachaCeilingCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaHistoryCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaInfoCsReq;
import cn.itcast.demo.mymmorpg.service.GachaCommandGateway;
import org.springframework.stereotype.Component;

@Component
@MessageRoute(module = Modules.GACHA)
public class GachaFacade {

    private final GachaCommandGateway gachaCommandGateway;

    public GachaFacade(GachaCommandGateway gachaCommandGateway) {
        this.gachaCommandGateway = gachaCommandGateway;
    }

    @RequestHandler(cmd = 1)
    public ProtocolMessage info(DispatchSession session, GamePackets.GetGachaInfoCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return gachaCommandGateway.handleGetGachaInfo(pid, GetGachaInfoCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 3)
    public ProtocolMessage doGacha(DispatchSession session, GamePackets.DoGachaCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return gachaCommandGateway.handleDoGacha(pid, DoGachaCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 5)
    public ProtocolMessage exchange(DispatchSession session, GamePackets.ExchangeGachaCeilingCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return gachaCommandGateway.handleExchangeCeiling(pid, ExchangeGachaCeilingCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 7)
    public ProtocolMessage history(DispatchSession session, GamePackets.GetGachaHistoryCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return gachaCommandGateway.handleGetGachaHistory(pid, GetGachaHistoryCsReq.parseFrom(pkt.payload()));
    }
}
