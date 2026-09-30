package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetRogueInfoScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueAllocateTalentCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueAllocateTalentScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueMoveCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueQuitCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueSelectBlessingCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.StartRogueCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.StartRogueScRsp;
import cn.itcast.demo.mymmorpg.rogue.RogueManager;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RogueServiceTest {

    private RogueService rogueService;
    private BattleService battleService;
    private PlayerNotificationPort notificationPort;

    @BeforeMethod
    public void setUp() {
        battleService = mock(BattleService.class);
        notificationPort = mock(PlayerNotificationPort.class);
        when(battleService.startRogueBattle(anyLong(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new BattleService.ChallengeBattleStart(RetCode.OK, 77_001L));
        rogueService = new RogueService(new RogueManager(), battleService, notificationPort);
    }

    @Test
    public void startMoveBlessingQuit_flow() throws Exception {
        StartRogueScRsp start = StartRogueScRsp.parseFrom(
                rogueService.handleStart(42L, StartRogueCsReq.newBuilder()
                        .setRogueId(1).setDifficulty(2).build()).payload());
        assertThat(start.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(start.getFloor()).isEqualTo(1);

        GetRogueInfoScRsp info = GetRogueInfoScRsp.parseFrom(
                rogueService.handleGetInfo(42L, cn.itcast.demo.mymmorpg.protocol.protobuf.GetRogueInfoCsReq
                        .getDefaultInstance()).payload());
        assertThat(info.getActive()).isTrue();

        var move = cn.itcast.demo.mymmorpg.protocol.protobuf.RogueMoveScRsp.parseFrom(
                rogueService.handleMove(42L, RogueMoveCsReq.newBuilder()
                        .setTargetRoomId(2).setLineupId(1).build()).payload());
        assertThat(move.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(move.getRoomId()).isEqualTo(2);
        assertThat(move.getBattleId()).isEqualTo(77_001L);
        verify(battleService).startRogueBattle(eq(42L), eq(1), eq(1), eq(2), eq(1));
        verify(notificationPort).send(eq(42L), eq(MessageId.ROGUE_ENCOUNTER_SC_NOTIFY), org.mockito.ArgumentMatchers.any());

        var bless = cn.itcast.demo.mymmorpg.protocol.protobuf.RogueSelectBlessingScRsp.parseFrom(
                rogueService.handleSelectBlessing(42L,
                        RogueSelectBlessingCsReq.newBuilder().setBlessingId(101).build()).payload());
        assertThat(bless.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(bless.getBlessingsList()).contains(101);

        rogueService.onBattleSettled(42L, 77_001L, 1);
        GetRogueInfoScRsp afterBattle = GetRogueInfoScRsp.parseFrom(
                rogueService.handleGetInfo(42L, cn.itcast.demo.mymmorpg.protocol.protobuf.GetRogueInfoCsReq
                        .getDefaultInstance()).payload());
        assertThat(afterBattle.getBattleId()).isEqualTo(0L);
        assertThat(afterBattle.getTalentPoints()).isEqualTo(1);
        assertThat(afterBattle.getCurrency()).isGreaterThan(info.getCurrency());

        RogueAllocateTalentScRsp alloc = RogueAllocateTalentScRsp.parseFrom(
                rogueService.handleAllocateTalent(42L, RogueAllocateTalentCsReq.newBuilder()
                        .setTalentId(1).setPoints(1).build()).payload());
        assertThat(alloc.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(alloc.getTalentLevel()).isEqualTo(1);
        assertThat(alloc.getTalentPoints()).isEqualTo(0);

        RogueAllocateTalentScRsp noPts = RogueAllocateTalentScRsp.parseFrom(
                rogueService.handleAllocateTalent(42L, RogueAllocateTalentCsReq.newBuilder()
                        .setTalentId(1).setPoints(1).build()).payload());
        assertThat(noPts.getRetcode()).isEqualTo(RetCode.ROGUE_TALENT_POINTS_NOT_ENOUGH);

        var quit = cn.itcast.demo.mymmorpg.protocol.protobuf.RogueQuitScRsp.parseFrom(
                rogueService.handleQuit(42L, RogueQuitCsReq.getDefaultInstance()).payload());
        assertThat(quit.getRetcode()).isEqualTo(RetCode.OK);
        verify(battleService, never()).forceEndChallengeBattle(anyLong(), anyLong());
    }

    @Test
    public void quit_forceEndsActiveBattle() throws Exception {
        rogueService.handleStart(8L, StartRogueCsReq.newBuilder().setRogueId(1).build());
        rogueService.handleMove(8L, RogueMoveCsReq.newBuilder().setTargetRoomId(2).setLineupId(1).build());
        var quit = cn.itcast.demo.mymmorpg.protocol.protobuf.RogueQuitScRsp.parseFrom(
                rogueService.handleQuit(8L, RogueQuitCsReq.getDefaultInstance()).payload());
        assertThat(quit.getRetcode()).isEqualTo(RetCode.OK);
        verify(battleService).forceEndChallengeBattle(eq(8L), eq(77_001L));
    }

    @Test
    public void start_rejectsDuplicate() throws Exception {
        rogueService.handleStart(7L, StartRogueCsReq.newBuilder().setRogueId(1).build());
        StartRogueScRsp second = StartRogueScRsp.parseFrom(
                rogueService.handleStart(7L, StartRogueCsReq.newBuilder().setRogueId(2).build()).payload());
        assertThat(second.getRetcode()).isEqualTo(RetCode.ROGUE_ALREADY_ACTIVE);
    }
}
