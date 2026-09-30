package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.handler.DispatchSession;
import cn.itcast.demo.mymmorpg.handler.MessageRoute;
import cn.itcast.demo.mymmorpg.handler.RequestHandler;
import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.Modules;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetVersionManifestCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.VerifyResourceChecksumCsReq;
import org.springframework.stereotype.Component;

/**
 * 客户端版本/资源更新协议门面（登录前可调用）。
 */
@Component
@MessageRoute(module = Modules.UPDATE)
public class UpdateFacade {

    private final UpdateCommandGateway updateCommandGateway;

    public UpdateFacade(UpdateCommandGateway updateCommandGateway) {
        this.updateCommandGateway = updateCommandGateway;
    }

    @RequestHandler(cmd = 1)
    public ProtocolMessage manifest(DispatchSession session, GamePackets.GetVersionManifestCsReq pkt) throws Exception {
        return updateCommandGateway.handleGetVersionManifest(GetVersionManifestCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 3)
    public ProtocolMessage verify(DispatchSession session, GamePackets.VerifyResourceChecksumCsReq pkt) throws Exception {
        return updateCommandGateway.handleVerifyResourceChecksum(VerifyResourceChecksumCsReq.parseFrom(pkt.payload()));
    }
}
