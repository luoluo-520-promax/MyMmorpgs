package cn.itcast.demo.mymmorpg.rpc;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 内部物理 RPC 端口：高频校验走二进制 RSocket，社交/GM 仍走 HTTP。
 */
public interface InternalPhysicsRpcPort {

    record PhysicsValidateReq(
            long playerId, String physicsStateHash,
            float vx, float vy, float vz, float gravityScale,
            float nx, float ny, float nz, boolean onIceSurface) {
    }

    CompletableFuture<Map<String, Object>> validateHashAsync(PhysicsValidateReq req);

    Map<String, Object> validateHash(PhysicsValidateReq req);
}
