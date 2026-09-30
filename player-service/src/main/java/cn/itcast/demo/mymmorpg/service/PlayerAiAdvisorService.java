package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.PlayerBattleStatsClient;
import cn.itcast.demo.mymmorpg.config.PlayerAiProperties;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.entity.PlayerBagItem;
import cn.itcast.demo.mymmorpg.entity.PlayerSkill;
import cn.itcast.demo.mymmorpg.model.admin.BattleStatsSnapshot;
import cn.itcast.demo.mymmorpg.model.ai.PlayerAiProfile;
import cn.itcast.demo.mymmorpg.model.ai.PlayerBattleLite;
import cn.itcast.demo.mymmorpg.model.ai.SkillUsageAgg;
import cn.itcast.demo.mymmorpg.repository.PlayerBagItemRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerSkillRepository;
import cn.itcast.demo.mymmorpg.service.ai.PlayerAiModelClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 玩家 AI 顾问：读取当前角色画像 + 全服胜率/技能使用率，生成个性化建议。
 */
@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "player-service")
@EnableConfigurationProperties(PlayerAiProperties.class)
public class PlayerAiAdvisorService {

    private final PlayerAiProperties properties;
    private final PlayerRepository playerRepository;
    private final PlayerSkillRepository playerSkillRepository;
    private final PlayerBagItemRepository playerBagItemRepository;
    private final ObjectProvider<BattleService> localBattleService;
    private final ObjectProvider<BattleStatsCollector> localStats;
    private final ObjectProvider<PlayerBattleStatsClient> remoteStats;
    private final PlayerAiModelClient modelClient;
    private final ObjectProvider<PlayerAiPlatformService> aiPlatformService;

    public PlayerAiAdvisorService(PlayerAiProperties properties,
                                  PlayerRepository playerRepository,
                                  PlayerSkillRepository playerSkillRepository,
                                  PlayerBagItemRepository playerBagItemRepository,
                                  ObjectProvider<BattleService> localBattleService,
                                  ObjectProvider<BattleStatsCollector> localStats,
                                  ObjectProvider<PlayerBattleStatsClient> remoteStats,
                                  PlayerAiModelClient modelClient,
                                  ObjectProvider<PlayerAiPlatformService> aiPlatformService) {
        this.properties = properties;
        this.playerRepository = playerRepository;
        this.playerSkillRepository = playerSkillRepository;
        this.playerBagItemRepository = playerBagItemRepository;
        this.localBattleService = localBattleService;
        this.localStats = localStats;
        this.remoteStats = remoteStats;
        this.modelClient = modelClient;
        this.aiPlatformService = aiPlatformService;
    }

    public Map<String, Object> advise(long accountId, long playerId, String question, String topic) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("玩家 AI 顾问未启用（player.ai.enabled=false）");
        }
        Player player = playerRepository.findByIdAndAccountId(playerId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在或不属于当前账号"));
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }

        String traceId = UUID.randomUUID().toString().replace("-", "");
        PlayerAiProfile profile = buildProfile(player);
        BattleStatsSnapshot global = loadGlobalSnapshot();
        PlayerBattleLite personal = loadPersonalBattle(playerId);
        profile.setPersonalBattle(personal);

        List<String> suggestions = buildSuggestions(profile, global, question, topic);
        List<String> warnings = new ArrayList<>();
        if (global.getEndedTotal() < 20) {
            warnings.add("全服战斗样本不足（<" + 20 + "），胜率/使用率仅供参考");
        }
        warnings.add("战斗统计来自当前 battle-service 进程内存，重启后清零");

        String contextMd = buildContextMarkdown(profile, global, question);
        String adviceMarkdown = buildAdviceMarkdown(profile, global, suggestions);
        String modelUsed = "rule";
        Optional<String> llm = modelClient.polishAdvice(contextMd, question);
        if (llm.isPresent()) {
            adviceMarkdown = adviceMarkdown + "\n\n## 模型补充\n\n" + llm.get();
            modelUsed = properties.getModel();
        } else if (properties.isLlmEnabled()) {
            warnings.add("外部模型不可用，已仅使用规则建议");
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "OK");
        resp.put("traceId", traceId);
        resp.put("promptVersion", properties.getPromptVersion());
        resp.put("model", modelUsed);
        resp.put("topic", resolveTopic(topic, question));
        resp.put("profile", profile);
        resp.put("globalStats", trimGlobal(global));
        resp.put("suggestions", suggestions);
        resp.put("adviceMarkdown", adviceMarkdown);
        resp.put("warnings", warnings);
        PlayerAiPlatformService platform = aiPlatformService.getIfAvailable();
        if (platform != null) {
            try {
                resp.put("personalizedRecommend", platform.recommend(playerId, resolveTopic(topic, question), 3));
            } catch (Exception ignored) {
                warnings.add("个性化推荐暂不可用");
            }
        }
        return resp;
    }

    PlayerAiProfile buildProfile(Player player) {
        PlayerAiProfile profile = new PlayerAiProfile();
        profile.setPlayerId(player.getId());
        profile.setName(player.getName());
        profile.setLevel(nvl(player.getLevel(), 1));
        profile.setExp(nvl(player.getExp(), 0L));
        profile.setGold(nvl(player.getGold(), 0L));
        profile.setVipRight(nvl(player.getVipRight(), 0));
        profile.setStrength(nvl(player.getStrength(), 0));
        profile.setAgility(nvl(player.getAgility(), 0));
        profile.setIntelligence(nvl(player.getIntelligence(), 0));
        profile.setTalentPoints(nvl(player.getTalentPoints(), 0));
        profile.setTalentJson(player.getTalentJson() == null ? "{}" : player.getTalentJson());
        profile.setPowerScore(nvl(player.getPowerScore(), 0));

        List<Integer> skills = playerSkillRepository.findByIdPlayerIdOrderByLearnTimeAsc(player.getId()).stream()
                .map(PlayerSkill::getId)
                .filter(id -> id != null && id.getSkillId() != null)
                .map(id -> id.getSkillId())
                .toList();
        profile.setLearnedSkillIds(skills);

        List<PlayerAiProfile.BagItemLite> bag = playerBagItemRepository
                .findByPlayerIdOrderBySlotIndexAsc(player.getId()).stream()
                .limit(30)
                .map(this::toBagLite)
                .toList();
        profile.setBagItems(bag);

        profile.setPowerRankHint(rankAmong(playerRepository.findTop50ByOrderByPowerScoreDesc(), player.getId()));
        profile.setLevelRankHint(rankAmong(playerRepository.findTop50ByOrderByLevelDesc(), player.getId()));
        return profile;
    }

    static List<String> buildSuggestions(PlayerAiProfile profile, BattleStatsSnapshot global,
                                         String question, String topic) {
        List<String> out = new ArrayList<>();
        String q = question == null ? "" : question;
        String resolved = resolveTopic(topic, q);
        Set<Integer> learned = Set.copyOf(profile.getLearnedSkillIds());

        PlayerBattleLite personal = profile.getPersonalBattle();
        if (personal != null && personal.getEndedTotal() > 0) {
            out.add(String.format(Locale.ROOT,
                    "你的近期战绩：%d 场，胜率 %.1f%%（胜/负/平=%d/%d/%d），全服胜率 %.1f%%。",
                    personal.getEndedTotal(), personal.getWinRate() * 100,
                    personal.getWinCount(), personal.getLoseCount(), personal.getDrawCount(),
                    global.getWinRate() * 100));
            if (personal.getWinRate() + 0.08 < global.getWinRate() && personal.getEndedTotal() >= 5) {
                out.add("你的胜率低于全服均值：建议优先学习高使用率技能，并携带治疗道具再挑战。");
            } else if (personal.getWinRate() > global.getWinRate() + 0.1 && personal.getEndedTotal() >= 5) {
                out.add("你的胜率高于全服：可尝试更高难度怪或排行榜冲分。");
            }
        } else {
            out.add("暂无你的个人战斗样本；建议先打几场战斗，顾问会结合你的胜率持续优化建议。");
        }

        if (!global.getTopSkillUsage().isEmpty()) {
            List<SkillUsageAgg> missing = global.getTopSkillUsage().stream()
                    .filter(s -> !learned.contains(s.getSkillId()))
                    .limit(3)
                    .toList();
            if (!missing.isEmpty()) {
                String skills = missing.stream()
                        .map(s -> String.format(Locale.ROOT, "技能%d(使用率%.1f%%)", s.getSkillId(), s.getUsageRate() * 100))
                        .collect(Collectors.joining("、"));
                out.add("全服高频技能中你尚未学习：" + skills + "。建议优先学习以提升胜率。");
            } else {
                SkillUsageAgg top = global.getTopSkillUsage().get(0);
                out.add(String.format(Locale.ROOT,
                        "全服最高使用率技能为 %d（%.1f%%），你已掌握主流技能池中的热门技能。",
                        top.getSkillId(), top.getUsageRate() * 100));
            }
        }

        if (!global.getByMonsterTemplate().isEmpty()) {
            global.getByMonsterTemplate().entrySet().stream().findFirst().ifPresent(e -> {
                var agg = e.getValue();
                if (agg.getWinRate() >= 0.65) {
                    out.add(String.format(Locale.ROOT,
                            "怪物模板 %d 全服胜率较高（%.1f%%，样本%d）：适合练级与熟悉技能循环。",
                            agg.getMonsterTemplateId(), agg.getWinRate() * 100, agg.getTotal()));
                } else if (agg.getWinRate() <= 0.35 && agg.getTotal() >= 10) {
                    out.add(String.format(Locale.ROOT,
                            "怪物模板 %d 全服胜率偏低（%.1f%%）：建议提升战力/等级后再挑战。",
                            agg.getMonsterTemplateId(), agg.getWinRate() * 100));
                }
            });
        }

        boolean hasHealItem = profile.getBagItems().stream()
                .anyMatch(i -> i.getItemConfigId() == 1001 && i.getCount() > 0);
        if (!hasHealItem && global.getAvgDurationSec() > 60) {
            out.add("全服平均战斗偏长，且你背包未见治疗道具(1001)：建议准备治疗道具降低翻车风险。");
        }

        if (profile.getTalentPoints() > 0) {
            out.add("你有未分配天赋点 " + profile.getTalentPoints() + "：按主属性（力/敏/智）加点可提升战力 "
                    + profile.getPowerScore() + "。");
        }

        if (profile.getPowerRankHint() != null) {
            out.add("你在战力榜 Top50 中约第 " + profile.getPowerRankHint() + " 名，可继续冲刺更高名次。");
        } else {
            out.add(String.format(Locale.ROOT,
                    "当前战力 %d、等级 %d；尚未进入战力 Top50，提升装备与技能有助于提高胜率。",
                    profile.getPowerScore(), profile.getLevel()));
        }

        if ("BUILD".equals(resolved)) {
            out.add(String.format(Locale.ROOT,
                    "属性面板：力%d / 敏%d / 智%d。若偏输出可优先力量或敏捷，再对照全服热门技能构筑。",
                    profile.getStrength(), profile.getAgility(), profile.getIntelligence()));
        }
        if (out.isEmpty()) {
            out.add("暂无足够数据给出细化建议，可先完成几场战斗或学习技能后再来询问。");
        }
        return out;
    }

    private BattleStatsSnapshot loadGlobalSnapshot() {
        BattleService battleService = localBattleService.getIfAvailable();
        if (battleService != null) {
            return battleService.statsSnapshot();
        }
        BattleStatsCollector local = localStats.getIfAvailable();
        if (local != null) {
            return local.snapshot(0, 0);
        }
        PlayerBattleStatsClient client = remoteStats.getIfAvailable();
        if (client != null) {
            try {
                return client.snapshot();
            } catch (Exception ignored) {
                // fall through
            }
        }
        BattleStatsSnapshot empty = new BattleStatsSnapshot();
        empty.getNotes().add("无法获取战斗统计，已返回空快照");
        return empty;
    }

    private PlayerBattleLite loadPersonalBattle(long playerId) {
        BattleService battleService = localBattleService.getIfAvailable();
        if (battleService != null) {
            return battleService.playerBattleStats(playerId);
        }
        BattleStatsCollector local = localStats.getIfAvailable();
        if (local != null) {
            return local.playerStats(playerId);
        }
        PlayerBattleStatsClient client = remoteStats.getIfAvailable();
        if (client != null) {
            try {
                return client.playerStats(playerId);
            } catch (Exception ignored) {
                // fall through
            }
        }
        PlayerBattleLite lite = new PlayerBattleLite();
        lite.setPlayerId(playerId);
        return lite;
    }

    private static Map<String, Object> trimGlobal(BattleStatsSnapshot g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("endedTotal", g.getEndedTotal());
        m.put("winRate", g.getWinRate());
        m.put("avgDurationSec", g.getAvgDurationSec());
        m.put("skillCastTotal", g.getSkillCastTotal());
        m.put("topSkillUsage", g.getTopSkillUsage());
        m.put("byMonsterTemplate", g.getByMonsterTemplate());
        m.put("notes", g.getNotes());
        return m;
    }

    private static String buildContextMarkdown(PlayerAiProfile p, BattleStatsSnapshot g, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("问题: ").append(question).append('\n');
        sb.append("玩家: ").append(p.getName()).append(" Lv").append(p.getLevel())
                .append(" 战力").append(p.getPowerScore()).append('\n');
        sb.append("技能: ").append(p.getLearnedSkillIds()).append('\n');
        sb.append("个人胜率: ").append(String.format(Locale.ROOT, "%.2f", p.getPersonalBattle().getWinRate()))
                .append(" / 全服胜率: ").append(String.format(Locale.ROOT, "%.2f", g.getWinRate())).append('\n');
        sb.append("热门技能: ").append(g.getTopSkillUsage()).append('\n');
        return sb.toString();
    }

    private static String buildAdviceMarkdown(PlayerAiProfile p, BattleStatsSnapshot g, List<String> suggestions) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 给你的战斗建议\n\n");
        sb.append("角色 **").append(p.getName()).append("**（Lv").append(p.getLevel())
                .append(" / 战力 ").append(p.getPowerScore()).append("）\n\n");
        for (int i = 0; i < suggestions.size(); i++) {
            sb.append(i + 1).append(". ").append(suggestions.get(i)).append('\n');
        }
        sb.append("\n_数据出处: BattleStatsCollector / Player 表 / player_skill / player_bag_item_\n");
        return sb.toString();
    }

    private static String resolveTopic(String topic, String question) {
        if (topic != null && !topic.isBlank()) {
            return topic.trim().toUpperCase(Locale.ROOT);
        }
        String q = question == null ? "" : question;
        if (containsAny(q, "技能", "学习", "构筑", "天赋", "属性")) {
            return "BUILD";
        }
        if (containsAny(q, "任务", "quest")) {
            return "QUEST";
        }
        return "BATTLE";
    }

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private PlayerAiProfile.BagItemLite toBagLite(PlayerBagItem item) {
        return new PlayerAiProfile.BagItemLite(
                nvl(item.getItemConfigId(), 0),
                nvl(item.getCount(), 0),
                nvl(item.getEquipSlot(), 0));
    }

    private static Integer rankAmong(List<Player> top, Long playerId) {
        if (top == null || playerId == null) {
            return null;
        }
        for (int i = 0; i < top.size(); i++) {
            if (playerId.equals(top.get(i).getId())) {
                return i + 1;
            }
        }
        return null;
    }

    private static int nvl(Integer v, int d) {
        return v == null ? d : v;
    }

    private static long nvl(Long v, long d) {
        return v == null ? d : v;
    }
}
