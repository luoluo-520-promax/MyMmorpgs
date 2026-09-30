package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.challenge.ChallengeConfigDocument;
import cn.itcast.demo.mymmorpg.challenge.ChallengeConfigLoader;
import cn.itcast.demo.mymmorpg.challenge.ChallengeManager;
import cn.itcast.demo.mymmorpg.challenge.ChallengeRuntime;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.FinishChallengeCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.FinishChallengeScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.StartChallengeCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.StartChallengeScRsp;
import org.springframework.stereotype.Service;

/**
 * 挑战关卡编排：读取 JSON 配置决定波次/星级，调用 BattleService 开战。
 */
@Service
public class ChallengeService {

    private final ChallengeManager challengeManager;
    private final BattleService battleService;
    private final ChallengeConfigLoader challengeConfigLoader;

    public ChallengeService(
            ChallengeManager challengeManager,
            BattleService battleService,
            ChallengeConfigLoader challengeConfigLoader) {
        this.challengeManager = challengeManager;
        this.battleService = battleService;
        this.challengeConfigLoader = challengeConfigLoader;
    }

    public ProtocolMessage handleStartChallenge(long playerId, StartChallengeCsReq req) {
        if (playerId <= 0) {
            return startRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0, 0);
        }
        if (req.getChallengeId() <= 0) {
            return startRsp(RetCode.CHALLENGE_NOT_FOUND, 0, 0, 0, 0);
        }
        if (challengeManager.activeUid(playerId) != null) {
            return startRsp(RetCode.CHALLENGE_ALREADY_ACTIVE, 0, req.getChallengeId(), 0, 0);
        }
        ChallengeConfigDocument cfg = challengeConfigLoader.requireOrFallback(req.getChallengeId());
        int waveCount = Math.max(1, cfg.getWaveCount());
        int lineupId = req.getLineupId() > 0 ? req.getLineupId() : 1;
        BattleService.ChallengeBattleStart battleStart = battleService.startChallengeBattle(
                playerId, lineupId, req.getChallengeId(), req.getChallengeType());
        if (battleStart.retcode() != RetCode.OK) {
            return startRsp(battleStart.retcode(), 0, req.getChallengeId(), 0, 0);
        }
        long uid = challengeManager.nextUid();
        ChallengeRuntime runtime = new ChallengeRuntime(
                uid, playerId, req.getChallengeType(), req.getChallengeId(), waveCount,
                cfg.getStarThresholdsSec());
        runtime.setBattleId(battleStart.battleId());
        challengeManager.put(runtime);
        return startRsp(RetCode.OK, uid, req.getChallengeId(), waveCount, runtime.getBattleId());
    }

    public ProtocolMessage handleFinishChallenge(long playerId, FinishChallengeCsReq req) {
        if (playerId <= 0) {
            return finishRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0);
        }
        ChallengeRuntime runtime = challengeManager.get(req.getChallengeUid());
        if (runtime == null || runtime.getPlayerId() != playerId) {
            return finishRsp(RetCode.CHALLENGE_NOT_ACTIVE, req.getChallengeUid(), 0, 0);
        }
        if (runtime.getStatus() != ChallengeRuntime.STATUS_RUNNING) {
            return finishRsp(RetCode.CHALLENGE_NOT_ACTIVE, runtime.getChallengeUid(),
                    runtime.getStars(), runtime.getScore());
        }
        runtime.finish(req.getVictory());
        battleService.forceEndChallengeBattle(playerId, runtime.getBattleId());
        challengeManager.remove(runtime.getChallengeUid());
        return finishRsp(RetCode.OK, runtime.getChallengeUid(), runtime.getStars(), runtime.getScore());
    }

    private static ProtocolMessage startRsp(int retcode, long uid, int challengeId, int waveCount, long battleId) {
        StartChallengeScRsp body = StartChallengeScRsp.newBuilder()
                .setRetcode(retcode)
                .setChallengeUid(uid)
                .setChallengeId(challengeId)
                .setWaveCount(waveCount)
                .setBattleId(battleId)
                .build();
        return new ProtocolMessage(MessageId.START_CHALLENGE_SC_RSP, body.toByteArray());
    }

    private static ProtocolMessage finishRsp(int retcode, long uid, int stars, int score) {
        FinishChallengeScRsp body = FinishChallengeScRsp.newBuilder()
                .setRetcode(retcode)
                .setChallengeUid(uid)
                .setStars(stars)
                .setScore(score)
                .build();
        return new ProtocolMessage(MessageId.FINISH_CHALLENGE_SC_RSP, body.toByteArray());
    }
}
