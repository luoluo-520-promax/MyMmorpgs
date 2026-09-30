package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetVersionManifestCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.VerifyResourceChecksumCsReq;

/**
 * 更新服务远程 HTTP 调用端口。
 */
public interface RemoteUpdateClient {

    ProtocolMessage handleGetVersionManifest(GetVersionManifestCsReq req);

    ProtocolMessage handleVerifyResourceChecksum(VerifyResourceChecksumCsReq req);
}
