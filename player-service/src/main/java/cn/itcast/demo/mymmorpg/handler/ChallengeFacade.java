package cn.itcast.demo.mymmorpg.handler;

import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.Modules;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.FinishChallengeCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.StartChallengeCsReq;
import cn.itcast.demo.mymmorpg.service.ChallengeService;
import org.springframework.stereotype.Component;

@Component
@MessageRoute(module = Modules.CHALLENGE)
public class ChallengeFacade {

    private final ChallengeService challengeService;

    public ChallengeFacade(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @RequestHandler(cmd = 1)
    public ProtocolMessage start(DispatchSession session, GamePackets.StartChallengeCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return challengeService.handleStartChallenge(pid, StartChallengeCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 3)
    public ProtocolMessage finish(DispatchSession session, GamePackets.FinishChallengeCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return challengeService.handleFinishChallenge(pid, FinishChallengeCsReq.parseFrom(pkt.payload()));
    }
}
