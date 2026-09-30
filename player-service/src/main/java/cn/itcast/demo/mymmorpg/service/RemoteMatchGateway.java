package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.MatchCommandClient;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CancelMatchCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnqueueMatchCsReq;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "game.match.remote.enabled", havingValue = "true")
public class RemoteMatchGateway implements RemoteMatchClient {

    private final MatchCommandClient matchCommandClient;

    public RemoteMatchGateway(MatchCommandClient matchCommandClient) {
        this.matchCommandClient = matchCommandClient;
    }

    @Override
    public ProtocolMessage handleEnqueueMatch(long playerId, EnqueueMatchCsReq req) {
        return new ProtocolMessage(MessageId.ENQUEUE_MATCH_SC_RSP, matchCommandClient.enqueue(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleCancelMatch(long playerId, CancelMatchCsReq req) {
        return new ProtocolMessage(MessageId.CANCEL_MATCH_SC_RSP, matchCommandClient.cancel(playerId, req.toByteArray()));
    }
}
