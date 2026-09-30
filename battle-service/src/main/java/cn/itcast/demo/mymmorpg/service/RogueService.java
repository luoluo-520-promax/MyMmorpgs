package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetRogueInfoCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetRogueInfoScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueAllocateTalentCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueAllocateTalentScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueEncounterScNotify;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueMoveCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueMoveScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueQuitCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueQuitScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueSelectBlessingCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueSelectBlessingScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.StartRogueCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.StartRogueScRsp;
import cn.itcast.demo.mymmorpg.rogue.RogueManager;
import cn.itcast.demo.mymmorpg.rogue.RogueRuntime;
import org.springframework.stereotype.Service;

/**
 * 肉鸽编排：房间移动时调用 BattleService 开战，形成可玩闭环。
 */
@Service
public class RogueService {

    private final RogueManager rogueManager;
    private final BattleService battleService;
    private final PlayerNotificationPort playerNotificationPort;

    public RogueService(
            RogueManager rogueManager,
            BattleService battleService,
            PlayerNotificationPort playerNotificationPort) {
        this.rogueManager = rogueManager;
        this.battleService = battleService;
        this.playerNotificationPort = playerNotificationPort;
    }

    public ProtocolMessage handleStart(long playerId, StartRogueCsReq req) {
        if (playerId <= 0) {
            return startRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0, 0);
        }
        if (req.getRogueId() <= 0) {
            return startRsp(RetCode.ROGUE_INVALID_MOVE, 0, 0, 0, 0);
        }
        if (rogueManager.isActive(playerId)) {
            return startRsp(RetCode.ROGUE_ALREADY_ACTIVE, req.getRogueId(), 0, 0, 0);
        }
        RogueRuntime runtime = new RogueRuntime(playerId, req.getRogueId(), req.getDifficulty());
        rogueManager.put(runtime);
        return startRsp(RetCode.OK, runtime.getRogueId(), runtime.getFloor(),
                runtime.getRoomId(), runtime.getCurrency());
    }

    public ProtocolMessage handleGetInfo(long playerId, GetRogueInfoCsReq req) {
        if (playerId <= 0) {
            return infoRsp(RetCode.PLAYER_NOT_SELECTED, null);
        }
        return infoRsp(RetCode.OK, rogueManager.get(playerId));
    }

    public ProtocolMessage handleMove(long playerId, RogueMoveCsReq req) {
        if (playerId <= 0) {
            return moveRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0, 0, 0);
        }
        RogueRuntime runtime = rogueManager.get(playerId);
        if (runtime == null) {
            return moveRsp(RetCode.ROGUE_NOT_ACTIVE, 0, 0, 0, 0, 0);
        }
        if (req.getTargetRoomId() <= 0 || req.getTargetRoomId() == runtime.getRoomId()) {
            return moveRsp(RetCode.ROGUE_INVALID_MOVE, runtime.getRoomId(),
                    runtime.getFloor(), runtime.getWave(), runtime.getCurrency(), runtime.getBattleId());
        }
        if (runtime.getBattleId() > 0) {
            // 上一场战斗未结算，不允许换房
            return moveRsp(RetCode.BATTLE_ALREADY_ACTIVE, runtime.getRoomId(),
                    runtime.getFloor(), runtime.getWave(), runtime.getCurrency(), runtime.getBattleId());
        }
        runtime.moveTo(req.getTargetRoomId());
        int lineupId = req.getLineupId() > 0 ? req.getLineupId() : 1;
        BattleService.ChallengeBattleStart battleStart = battleService.startRogueBattle(
                playerId, lineupId, runtime.getRogueId(), runtime.getRoomId(), runtime.getFloor());
        if (battleStart.retcode() != RetCode.OK) {
            rogueManager.touch(runtime);
            return moveRsp(battleStart.retcode(), runtime.getRoomId(),
                    runtime.getFloor(), runtime.getWave(), runtime.getCurrency(), 0);
        }
        runtime.setBattleId(battleStart.battleId());
        rogueManager.touch(runtime);
        RogueEncounterScNotify notify = RogueEncounterScNotify.newBuilder()
                .setBattleId(battleStart.battleId())
                .setRogueId(runtime.getRogueId())
                .setRoomId(runtime.getRoomId())
                .setFloor(runtime.getFloor())
                .setWave(runtime.getWave())
                .build();
        playerNotificationPort.send(playerId, MessageId.ROGUE_ENCOUNTER_SC_NOTIFY, notify.toByteArray());
        return moveRsp(RetCode.OK, runtime.getRoomId(), runtime.getFloor(),
                runtime.getWave(), runtime.getCurrency(), runtime.getBattleId());
    }

    public ProtocolMessage handleSelectBlessing(long playerId, RogueSelectBlessingCsReq req) {
        if (playerId <= 0) {
            return blessingRsp(RetCode.PLAYER_NOT_SELECTED, null);
        }
        RogueRuntime runtime = rogueManager.get(playerId);
        if (runtime == null) {
            return blessingRsp(RetCode.ROGUE_NOT_ACTIVE, null);
        }
        if (!runtime.addBlessing(req.getBlessingId())) {
            return blessingRsp(RetCode.ROGUE_INVALID_MOVE, runtime);
        }
        rogueManager.touch(runtime);
        return blessingRsp(RetCode.OK, runtime);
    }

    public ProtocolMessage handleQuit(long playerId, RogueQuitCsReq req) {
        if (playerId <= 0) {
            return quitRsp(RetCode.PLAYER_NOT_SELECTED);
        }
        RogueRuntime runtime = rogueManager.remove(playerId);
        if (runtime == null) {
            return quitRsp(RetCode.ROGUE_NOT_ACTIVE);
        }
        if (runtime.getBattleId() > 0) {
            battleService.forceEndChallengeBattle(playerId, runtime.getBattleId());
        }
        return quitRsp(RetCode.OK);
    }

    public ProtocolMessage handleAllocateTalent(long playerId, RogueAllocateTalentCsReq req) {
        if (playerId <= 0) {
            return allocateRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0);
        }
        RogueRuntime runtime = rogueManager.get(playerId);
        if (runtime == null) {
            return allocateRsp(RetCode.ROGUE_NOT_ACTIVE, 0, 0, 0);
        }
        int points = req.getPoints() <= 0 ? 1 : (int) req.getPoints();
        int talentId = req.getTalentId();
        if (talentId <= 0) {
            return allocateRsp(RetCode.ROGUE_INVALID_MOVE, 0, 0, runtime.getTalentPoints());
        }
        if (!runtime.allocateTalent(talentId, points)) {
            return allocateRsp(RetCode.ROGUE_TALENT_POINTS_NOT_ENOUGH, talentId,
                    runtime.getTalentLevel(talentId), runtime.getTalentPoints());
        }
        rogueManager.touch(runtime);
        return allocateRsp(RetCode.OK, talentId, runtime.getTalentLevel(talentId), runtime.getTalentPoints());
    }

    /**
     * 战斗结算回写：胜负影响局内货币/天赋点/自动祝福，并清除 battleId。
     *
     * @param result 0 败 1 胜 2 平局/逃跑
     */
    public void onBattleSettled(long playerId, long battleId, int result) {
        RogueRuntime runtime = rogueManager.get(playerId);
        if (runtime != null && runtime.getBattleId() == battleId) {
            runtime.applyBattleResult(result);
            rogueManager.touch(runtime);
        }
    }

    private static ProtocolMessage startRsp(int ret, int rogueId, int floor, int roomId, int currency) {
        StartRogueScRsp body = StartRogueScRsp.newBuilder()
                .setRetcode(ret)
                .setRogueId(rogueId)
                .setFloor(floor)
                .setRoomId(roomId)
                .setCurrency(currency)
                .build();
        return new ProtocolMessage(MessageId.START_ROGUE_SC_RSP, body.toByteArray());
    }

    private static ProtocolMessage infoRsp(int ret, RogueRuntime runtime) {
        GetRogueInfoScRsp.Builder b = GetRogueInfoScRsp.newBuilder().setRetcode(ret);
        if (runtime != null) {
            b.setActive(true)
                    .setRogueId(runtime.getRogueId())
                    .setFloor(runtime.getFloor())
                    .setWave(runtime.getWave())
                    .setRoomId(runtime.getRoomId())
                    .setCurrency(runtime.getCurrency())
                    .setBattleId(runtime.getBattleId())
                    .setTalentPoints(runtime.getTalentPoints())
                    .addAllBlessings(runtime.getBlessings());
        } else {
            b.setActive(false);
        }
        return new ProtocolMessage(MessageId.GET_ROGUE_INFO_SC_RSP, b.build().toByteArray());
    }

    private static ProtocolMessage moveRsp(int ret, int roomId, int floor, int wave, int currency, long battleId) {
        RogueMoveScRsp body = RogueMoveScRsp.newBuilder()
                .setRetcode(ret)
                .setRoomId(roomId)
                .setFloor(floor)
                .setWave(wave)
                .setCurrency(currency)
                .setBattleId(battleId)
                .build();
        return new ProtocolMessage(MessageId.ROGUE_MOVE_SC_RSP, body.toByteArray());
    }

    private static ProtocolMessage blessingRsp(int ret, RogueRuntime runtime) {
        RogueSelectBlessingScRsp.Builder b = RogueSelectBlessingScRsp.newBuilder().setRetcode(ret);
        if (runtime != null) {
            b.addAllBlessings(runtime.getBlessings());
        }
        return new ProtocolMessage(MessageId.ROGUE_SELECT_BLESSING_SC_RSP, b.build().toByteArray());
    }

    private static ProtocolMessage quitRsp(int ret) {
        return new ProtocolMessage(MessageId.ROGUE_QUIT_SC_RSP,
                RogueQuitScRsp.newBuilder().setRetcode(ret).build().toByteArray());
    }

    private static ProtocolMessage allocateRsp(int ret, int talentId, int talentLevel, int remaining) {
        RogueAllocateTalentScRsp body = RogueAllocateTalentScRsp.newBuilder()
                .setRetcode(ret)
                .setTalentId(talentId)
                .setTalentLevel(talentLevel)
                .setTalentPoints(remaining)
                .build();
        return new ProtocolMessage(MessageId.ROGUE_ALLOCATE_TALENT_SC_RSP, body.toByteArray());
    }
}
