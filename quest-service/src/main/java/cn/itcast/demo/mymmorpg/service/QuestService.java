package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.entity.PlayerQuestProgress;
import cn.itcast.demo.mymmorpg.event.QuestCompletedEvent;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AcceptQuestCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AcceptQuestScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimQuestRewardCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimQuestRewardScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetQuestListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetQuestListScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.QuestInfo;
import cn.itcast.demo.mymmorpg.quest.QuestConfigService;
import cn.itcast.demo.mymmorpg.quest.QuestConditionEvaluator;
import cn.itcast.demo.mymmorpg.quest.QuestGraph;
import cn.itcast.demo.mymmorpg.quest.QuestTemplate;
import cn.itcast.demo.mymmorpg.repository.PlayerQuestProgressRepository;
import jforgame.commons.eventbus.EventBus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务业务：模板来自 {@link QuestConfigService}，进度落库 player_quest_progress。
 */
@Service
public class QuestService {

    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_ACCEPTED = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_CLAIMED = 3;

    private final QuestConfigService questConfigService;
    private final PlayerQuestProgressRepository questProgressRepository;
    private final ObjectProvider<PlayerProgressPort> playerProgressPort;
    private final ObjectProvider<PlayerCachePort> playerCachePort;
    private final ObjectProvider<EventBus> eventBus;

    public QuestService(
            QuestConfigService questConfigService,
            PlayerQuestProgressRepository questProgressRepository,
            ObjectProvider<PlayerProgressPort> playerProgressPort,
            ObjectProvider<PlayerCachePort> playerCachePort,
            ObjectProvider<EventBus> eventBus) {
        this.questConfigService = questConfigService;
        this.questProgressRepository = questProgressRepository;
        this.playerProgressPort = playerProgressPort;
        this.playerCachePort = playerCachePort;
        this.eventBus = eventBus;
    }

    public ProtocolMessage handleGetQuestList(long playerId, GetQuestListCsReq req) {
        if (playerId <= 0) {
            return listRsp(RetCode.PLAYER_NOT_SELECTED, List.of());
        }
        List<QuestInfo> quests = new ArrayList<>();
        for (QuestTemplate tpl : questConfigService.listAll()) {
            quests.add(toQuestInfo(tpl, getOrDefaultState(playerId, tpl.questId)));
        }
        return listRsp(RetCode.OK, quests);
    }

    @Transactional
    public ProtocolMessage handleAcceptQuest(long playerId, AcceptQuestCsReq req) {
        if (playerId <= 0) {
            return acceptRsp(RetCode.PLAYER_NOT_SELECTED, null);
        }
        QuestTemplate tpl = questConfigService.find(req.getQuestId());
        if (tpl == null) {
            return acceptRsp(RetCode.QUEST_NOT_FOUND, null);
        }
        Set<Integer> done = claimedOrCompletedQuestIds(playerId);
        QuestGraph graph = QuestGraph.fromTemplates(questConfigService.listAll());
        if (!graph.isUnlocked(tpl.questId, done)) {
            return acceptRsp(RetCode.QUEST_LOCKED, toQuestInfo(tpl, getOrDefaultState(playerId, tpl.questId)));
        }
        Map<String, Object> condCtx = new HashMap<>();
        condCtx.put("claimedQuestIds", done);
        PlayerCachePort cache = playerCachePort.getIfAvailable();
        if (cache != null) {
            Player player = cache.findById(playerId);
            if (player != null) {
                condCtx.put("level", player.getLevel() == null ? 1 : player.getLevel());
                condCtx.put("vip", 0);
            }
        }
        if (!QuestConditionEvaluator.evaluate(tpl.conditionJson, condCtx)) {
            return acceptRsp(RetCode.QUEST_LOCKED, toQuestInfo(tpl, getOrDefaultState(playerId, tpl.questId)));
        }
        PlayerQuestProgress state = getOrCreateState(playerId, tpl.questId);
        if (state.getStatus() != STATUS_AVAILABLE) {
            return acceptRsp(RetCode.QUEST_ALREADY_ACCEPTED, toQuestInfo(tpl, state));
        }
        state.setStatus(STATUS_ACCEPTED);
        state.setProgress(0);
        boolean completedNow = false;
        if (tpl.completeOnAccept) {
            state.setProgress(tpl.target);
            state.setStatus(STATUS_COMPLETED);
            completedNow = true;
        }
        questProgressRepository.save(state);
        if (completedNow) {
            publishQuestCompleted(playerId, tpl.questId);
        }
        return acceptRsp(RetCode.OK, toQuestInfo(tpl, state));
    }

    @Transactional
    public ProtocolMessage handleClaimQuestReward(long playerId, ClaimQuestRewardCsReq req) {
        if (playerId <= 0) {
            return claimRsp(RetCode.PLAYER_NOT_SELECTED, req.getQuestId(), 0, 0);
        }
        QuestTemplate tpl = questConfigService.find(req.getQuestId());
        if (tpl == null) {
            return claimRsp(RetCode.QUEST_NOT_FOUND, req.getQuestId(), 0, 0);
        }
        PlayerQuestProgress state = getOrCreateState(playerId, tpl.questId);
        if (state.getStatus() == STATUS_CLAIMED) {
            return claimRsp(RetCode.QUEST_ALREADY_CLAIMED, tpl.questId, 0, 0);
        }
        if (state.getStatus() != STATUS_COMPLETED) {
            return claimRsp(RetCode.QUEST_NOT_COMPLETED, tpl.questId, 0, 0);
        }
        state.setStatus(STATUS_CLAIMED);
        questProgressRepository.save(state);
        PlayerCachePort cache = playerCachePort.getIfAvailable();
        PlayerProgressPort progress = playerProgressPort.getIfAvailable();
        if (cache != null && progress != null) {
            Player player = cache.findById(playerId);
            if (player != null) {
                progress.addExp(player, tpl.expReward);
                progress.addGold(player, tpl.goldReward);
            }
        }
        return claimRsp(RetCode.OK, tpl.questId, tpl.expReward, tpl.goldReward);
    }

    @Transactional
    public void advanceProgress(long playerId, int questId, int delta) {
        QuestTemplate tpl = questConfigService.find(questId);
        if (tpl == null || delta <= 0) {
            return;
        }
        PlayerQuestProgress state = getOrCreateState(playerId, questId);
        if (state.getStatus() != STATUS_ACCEPTED) {
            return;
        }
        int next = Math.min(tpl.target, state.getProgress() + delta);
        state.setProgress(next);
        boolean completedNow = false;
        if (next >= tpl.target) {
            state.setStatus(STATUS_COMPLETED);
            completedNow = true;
        }
        questProgressRepository.save(state);
        if (completedNow) {
            publishQuestCompleted(playerId, questId);
        }
    }

    /**
     * 战斗胜利：推进已接取的日常任务（questType=3）进度 +1。
     */
    @Transactional
    public List<Integer> onBattleWon(long playerId) {
        List<Integer> advanced = new ArrayList<>();
        if (playerId <= 0) {
            return advanced;
        }
        for (QuestTemplate tpl : questConfigService.listAll()) {
            if (tpl.questType != 3) {
                continue;
            }
            PlayerQuestProgress state = getOrCreateState(playerId, tpl.questId);
            if (state.getStatus() != STATUS_ACCEPTED) {
                continue;
            }
            advanceProgress(playerId, tpl.questId, 1);
            advanced.add(tpl.questId);
        }
        return advanced;
    }

    private void publishQuestCompleted(long playerId, int questId) {
        EventBus bus = eventBus.getIfAvailable();
        if (bus != null) {
            bus.publish(new QuestCompletedEvent(playerId, questId));
        }
    }

    private Set<Integer> claimedOrCompletedQuestIds(long playerId) {
        Set<Integer> done = new HashSet<>();
        for (PlayerQuestProgress p : questProgressRepository.findByPlayerId(playerId)) {
            Integer st = p.getStatus();
            if (st != null && (st == STATUS_COMPLETED || st == STATUS_CLAIMED)) {
                done.add(p.getQuestId());
            }
        }
        return done;
    }

    private PlayerQuestProgress getOrDefaultState(long playerId, int questId) {
        return questProgressRepository.findByPlayerIdAndQuestId(playerId, questId)
                .orElseGet(() -> {
                    PlayerQuestProgress s = new PlayerQuestProgress();
                    s.setPlayerId(playerId);
                    s.setQuestId(questId);
                    s.setStatus(STATUS_AVAILABLE);
                    s.setProgress(0);
                    return s;
                });
    }

    private PlayerQuestProgress getOrCreateState(long playerId, int questId) {
        return questProgressRepository.findByPlayerIdAndQuestId(playerId, questId)
                .orElseGet(() -> {
                    PlayerQuestProgress s = new PlayerQuestProgress();
                    s.setPlayerId(playerId);
                    s.setQuestId(questId);
                    s.setStatus(STATUS_AVAILABLE);
                    s.setProgress(0);
                    return s;
                });
    }

    private static QuestInfo toQuestInfo(QuestTemplate tpl, PlayerQuestProgress state) {
        return QuestInfo.newBuilder()
                .setQuestId(tpl.questId)
                .setName(tpl.name == null ? "" : tpl.name)
                .setDescription(tpl.description == null ? "" : tpl.description)
                .setQuestType(tpl.questType)
                .setStatus(state.getStatus() == null ? STATUS_AVAILABLE : state.getStatus())
                .setProgress(state.getProgress() == null ? 0 : state.getProgress())
                .setTarget(tpl.target)
                .setExpReward(tpl.expReward)
                .setGoldReward(tpl.goldReward)
                .build();
    }

    private static ProtocolMessage listRsp(int retcode, List<QuestInfo> quests) {
        GetQuestListScRsp.Builder b = GetQuestListScRsp.newBuilder().setRetcode(retcode);
        b.addAllQuests(quests);
        return new ProtocolMessage(MessageId.GET_QUEST_LIST_SC_RSP, b.build().toByteArray());
    }

    private static ProtocolMessage acceptRsp(int retcode, QuestInfo quest) {
        AcceptQuestScRsp.Builder b = AcceptQuestScRsp.newBuilder().setRetcode(retcode);
        if (quest != null) {
            b.setQuest(quest);
        }
        return new ProtocolMessage(MessageId.ACCEPT_QUEST_SC_RSP, b.build().toByteArray());
    }

    private static ProtocolMessage claimRsp(int retcode, int questId, int expGained, int goldGained) {
        ClaimQuestRewardScRsp body = ClaimQuestRewardScRsp.newBuilder()
                .setRetcode(retcode)
                .setQuestId(questId)
                .setExpGained(expGained)
                .setGoldGained(goldGained)
                .build();
        return new ProtocolMessage(MessageId.CLAIM_QUEST_REWARD_SC_RSP, body.toByteArray());
    }
}
