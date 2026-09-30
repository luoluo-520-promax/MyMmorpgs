package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.UpdateCommandClient;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetVersionManifestCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.VerifyResourceChecksumCsReq;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "game.update.remote.enabled", havingValue = "true")
public class RemoteUpdateGateway implements RemoteUpdateClient {

    private final UpdateCommandClient updateCommandClient;

    public RemoteUpdateGateway(UpdateCommandClient updateCommandClient) {
        this.updateCommandClient = updateCommandClient;
    }

    @Override
    public ProtocolMessage handleGetVersionManifest(GetVersionManifestCsReq req) {
        byte[] payload = updateCommandClient.manifest(req.toByteArray());
        return new ProtocolMessage(MessageId.GET_VERSION_MANIFEST_SC_RSP, payload);
    }

    @Override
    public ProtocolMessage handleVerifyResourceChecksum(VerifyResourceChecksumCsReq req) {
        byte[] payload = updateCommandClient.verify(req.toByteArray());
        return new ProtocolMessage(MessageId.VERIFY_RESOURCE_CHECKSUM_SC_RSP, payload);
    }
}
