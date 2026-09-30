package cn.itcast.demo.mymmorpg.challenge;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进行中的挑战运行时索引。
 */
@Component
public class ChallengeManager {

    private final Map<Long, ChallengeRuntime> byUid = new ConcurrentHashMap<>();
    private final Map<Long, Long> activeByPlayer = new ConcurrentHashMap<>();
    private final AtomicLong uidSeq = new AtomicLong(10_000);

    public long nextUid() {
        return uidSeq.incrementAndGet();
    }

    public void put(ChallengeRuntime runtime) {
        byUid.put(runtime.getChallengeUid(), runtime);
        activeByPlayer.put(runtime.getPlayerId(), runtime.getChallengeUid());
    }

    public ChallengeRuntime get(long challengeUid) {
        return byUid.get(challengeUid);
    }

    public Long activeUid(long playerId) {
        return activeByPlayer.get(playerId);
    }

    public void remove(long challengeUid) {
        ChallengeRuntime rt = byUid.remove(challengeUid);
        if (rt != null) {
            activeByPlayer.remove(rt.getPlayerId(), challengeUid);
        }
    }
}
