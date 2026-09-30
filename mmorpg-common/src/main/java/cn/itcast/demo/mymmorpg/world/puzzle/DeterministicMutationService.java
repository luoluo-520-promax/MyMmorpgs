package cn.itcast.demo.mymmorpg.world.puzzle;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 确定性破坏：服务端下发 Seed + Timestamp，客户端立即本地演算碎片与重生倒计时。
 */
@Service
public class DeterministicMutationService {

    public record MutationSeed(String destroyableId, long seed, long serverTimestampMs, long respawnMs) {
    }

    private final ConcurrentHashMap<String, MutationSeed> issued = new ConcurrentHashMap<>();

    public Map<String, Object> issueLocalPlayback(
            String destroyableId, long nowMs, long respawnCooldownMs) {
        long seed = (destroyableId.hashCode() & 0xFFFFFFFFL) ^ nowMs;
        long respawn = respawnCooldownMs <= 0 ? 300_000L : respawnCooldownMs;
        MutationSeed ms = new MutationSeed(destroyableId, seed, nowMs, respawn);
        issued.put(destroyableId, ms);

        Random rng = new Random(seed);
        int fragmentCount = 6 + rng.nextInt(8);
        float spreadX = 0.5f + rng.nextFloat() * 2f;
        float spreadY = 1f + rng.nextFloat() * 3f;
        float spreadZ = 0.5f + rng.nextFloat() * 2f;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("destroyableId", destroyableId);
        body.put("seed", seed);
        body.put("serverTimestampMs", nowMs);
        body.put("clientPlayImmediately", true);
        body.put("fragmentCount", fragmentCount);
        body.put("fragmentSpread", Map.of("x", spreadX, "y", spreadY, "z", spreadZ));
        body.put("respawnCountdownMs", respawn);
        body.put("optimisticCollisionOff", true);
        return body;
    }

    /**
     * 乐观地貌改写：客户端先行渲染，服务端异步校验；失败则 1s 过渡回滚。
     */
    public Map<String, Object> optimisticTerrain(
            String mutationId, boolean serverValid, long nowMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("mutationId", mutationId);
        if (serverValid) {
            body.put("confirmed", true);
            body.put("rollbackAnimMs", 0);
        } else {
            body.put("confirmed", false);
            body.put("rollbackAnimMs", 1000);
            body.put("smoothRevert", true);
            body.put("reason", "element_gauge_insufficient");
        }
        body.put("atMs", nowMs);
        return body;
    }

    public MutationSeed seedOf(String destroyableId) {
        return issued.get(destroyableId);
    }
}
