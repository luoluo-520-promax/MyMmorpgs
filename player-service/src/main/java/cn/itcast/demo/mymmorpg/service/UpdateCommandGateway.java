package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetVersionManifestCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.VerifyResourceChecksumCsReq;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UpdateCommandGateway {

    private final UpdateService localService;
    private final ObjectProvider<RemoteUpdateClient> remoteClient;
    private final boolean remoteEnabled;

    public UpdateCommandGateway(
            UpdateService localService,
            ObjectProvider<RemoteUpdateClient> remoteClient,
            @Value("${game.update.remote.enabled:false}") boolean remoteEnabled) {
        this.localService = localService;
        this.remoteClient = remoteClient;
        this.remoteEnabled = remoteEnabled;
    }

    public ProtocolMessage handleGetVersionManifest(GetVersionManifestCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleGetVersionManifest(req);
        }
        return localService.handleGetVersionManifest(req);
    }

    public ProtocolMessage handleVerifyResourceChecksum(VerifyResourceChecksumCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleVerifyResourceChecksum(req);
        }
        return localService.handleVerifyResourceChecksum(req);
    }

    private boolean useRemote() {
        return remoteEnabled && remoteClient.getIfAvailable() != null;
    }
}
