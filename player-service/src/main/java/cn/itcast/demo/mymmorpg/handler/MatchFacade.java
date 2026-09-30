package cn.itcast.demo.mymmorpg.handler;

import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.Modules;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CancelMatchCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnqueueMatchCsReq;
import cn.itcast.demo.mymmorpg.service.MatchCommandGateway;
import org.springframework.stereotype.Component;

@Component
@MessageRoute(module = Modules.MATCH)
public class MatchFacade {

    private final MatchCommandGateway matchCommandGateway;

    public MatchFacade(MatchCommandGateway matchCommandGateway) {
        this.matchCommandGateway = matchCommandGateway;
    }

    @RequestHandler(cmd = 1)
    public ProtocolMessage enqueue(DispatchSession session, GamePackets.EnqueueMatchCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return matchCommandGateway.handleEnqueueMatch(pid, EnqueueMatchCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 3)
    public ProtocolMessage cancel(DispatchSession session, GamePackets.CancelMatchCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return matchCommandGateway.handleCancelMatch(pid, CancelMatchCsReq.parseFrom(pkt.payload()));
    }
}
