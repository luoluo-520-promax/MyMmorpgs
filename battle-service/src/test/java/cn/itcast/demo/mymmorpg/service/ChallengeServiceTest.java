package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.challenge.ChallengeConfigDocument;
import cn.itcast.demo.mymmorpg.challenge.ChallengeConfigLoader;
import cn.itcast.demo.mymmorpg.challenge.ChallengeManager;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.FinishChallengeCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.FinishChallengeScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.StartChallengeCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.StartChallengeScRsp;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChallengeServiceTest {

    private ChallengeService challengeService;
    private BattleService battleService;
    private ChallengeConfigLoader challengeConfigLoader;

    @BeforeMethod
    public void setUp() {
        battleService = mock(BattleService.class);
        challengeConfigLoader = mock(ChallengeConfigLoader.class);
        ChallengeConfigDocument doc = new ChallengeConfigDocument();
        doc.setId(7);
        doc.setWaveCount(3);
        doc.setStarThresholdsSec(List.of(60, 120, 180));
        when(challengeConfigLoader.requireOrFallback(anyInt())).thenReturn(doc);
        when(battleService.startChallengeBattle(anyLong(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new BattleService.ChallengeBattleStart(RetCode.OK, 55_001L));
        challengeService = new ChallengeService(new ChallengeManager(), battleService, challengeConfigLoader);
    }

    @Test
    public void startAndFinish_victory_usesRealBattleId() throws Exception {
        ProtocolMessage startMsg = challengeService.handleStartChallenge(1001L,
                StartChallengeCsReq.newBuilder()
                        .setChallengeId(7)
                        .setChallengeType(1)
                        .setLineupId(3)
                        .build());
        assertThat(startMsg.msgId()).isEqualTo(MessageId.START_CHALLENGE_SC_RSP);
        StartChallengeScRsp start = StartChallengeScRsp.parseFrom(startMsg.payload());
        assertThat(start.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(start.getChallengeUid()).isGreaterThan(0);
        assertThat(start.getBattleId()).isEqualTo(55_001L);
        assertThat(start.getWaveCount()).isEqualTo(3);
        verify(battleService).startChallengeBattle(eq(1001L), eq(3), eq(7), eq(1));

        ProtocolMessage finishMsg = challengeService.handleFinishChallenge(1001L,
                FinishChallengeCsReq.newBuilder()
                        .setChallengeUid(start.getChallengeUid())
                        .setVictory(true)
                        .build());
        FinishChallengeScRsp finish = FinishChallengeScRsp.parseFrom(finishMsg.payload());
        assertThat(finish.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(finish.getStars()).isBetween(1, 3);
        assertThat(finish.getScore()).isGreaterThan(0);
        verify(battleService).forceEndChallengeBattle(eq(1001L), eq(55_001L));
    }

    @Test
    public void start_rejectsDuplicateActive() throws Exception {
        challengeService.handleStartChallenge(2002L,
                StartChallengeCsReq.newBuilder().setChallengeId(1).setLineupId(1).build());
        ProtocolMessage second = challengeService.handleStartChallenge(2002L,
                StartChallengeCsReq.newBuilder().setChallengeId(2).setLineupId(1).build());
        StartChallengeScRsp rsp = StartChallengeScRsp.parseFrom(second.payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.CHALLENGE_ALREADY_ACTIVE);
    }

    @Test
    public void start_propagatesBattleFailure() throws Exception {
        when(battleService.startChallengeBattle(anyLong(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new BattleService.ChallengeBattleStart(RetCode.BATTLE_ALREADY_ACTIVE, 0L));
        ProtocolMessage msg = challengeService.handleStartChallenge(3003L,
                StartChallengeCsReq.newBuilder().setChallengeId(9).setLineupId(1).build());
        StartChallengeScRsp rsp = StartChallengeScRsp.parseFrom(msg.payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BATTLE_ALREADY_ACTIVE);
        assertThat(rsp.getBattleId()).isZero();
    }
}
