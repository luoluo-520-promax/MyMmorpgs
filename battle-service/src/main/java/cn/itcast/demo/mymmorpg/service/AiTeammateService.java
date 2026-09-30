package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.ai.bt.BehaviorTree;
import cn.itcast.demo.mymmorpg.ai.bt.BehaviorTreeDebugger;
import cn.itcast.demo.mymmorpg.ai.teammate.TeammateStrategy;
import cn.itcast.demo.mymmorpg.ai.threat.ThreatTable;
import cn.itcast.demo.mymmorpg.metrics.AiMetrics;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 队友 / Boss：优先走行为树（BT）配置，兼容原有文本指挥（集火/治疗等）。
 * 实时战斗不依赖 LLM，避免高延迟。支持策略模式（保守/进攻/辅助）。
 */
@Service
public class AiTeammateService {

    public record AiBot(String botId, String battleId, long ownerPlayerId, String role,
                        double x, double z, String lastCommand, String lastAction, long updatedAtMs,
                        int phase, boolean enraged, String strategy) {
        public AiBot(String botId, String battleId, long ownerPlayerId, String role,
                     double x, double z, String lastCommand, String lastAction, long updatedAtMs,
                     int phase, boolean enraged) {
            this(botId, battleId, ownerPlayerId, role, x, z, lastCommand, lastAction, updatedAtMs,
                    phase, enraged, TeammateStrategy.AGGRESSIVE.name());
        }
    }

    private final ConcurrentHashMap<String, List<AiBot>> byBattle = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BehaviorTree.Node> bossTrees = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BehaviorTree.Blackboard> bossBoards = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ThreatTable> threatTables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> strategyOverrides = new ConcurrentHashMap<>();
    private final BehaviorTreeDebugger btDebugger = new BehaviorTreeDebugger();
    private final AiMetrics aiMetrics = new AiMetrics();

    public Map<String, Object> spawn(String battleId, long ownerPlayerId, String commandHint) {
        if (battleId == null || battleId.isBlank() || ownerPlayerId <= 0) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        String role = inferRole(commandHint);
        TeammateStrategy strategy = TeammateStrategy.fromHint(commandHint);
        String botId = "ai-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        AiBot bot = new AiBot(botId, battleId, ownerPlayerId, role, 0, 0,
                commandHint == null ? "" : commandHint, "spawn", System.currentTimeMillis(), 1, false,
                strategy.name());
        byBattle.computeIfAbsent(battleId, k -> new ArrayList<>()).add(bot);
        if ("boss".equals(role)) {
            bossTrees.putIfAbsent(botId, BehaviorTree.defaultBossTree());
            BehaviorTree.Blackboard bb = new BehaviorTree.Blackboard();
            bb.put("hpRatio", 1.0);
            bb.put("phase", 1);
            bb.put("threatTargetId", ownerPlayerId);
            bossBoards.put(botId, bb);
            ThreatTable table = new ThreatTable();
            table.configure(ThreatTable.PriorityMode.DAMAGE_WEIGHTED, ownerPlayerId, 1.25);
            threatTables.put(botId, table);
            btDebugger.loadDefault(botId);
        }
        return toView(bot, true);
    }

    /**
     * 切换 AI 队友策略；params 可覆盖默认 JSON 参数。
     */
    public Map<String, Object> setStrategy(String battleId, String botId, String strategyName,
                                           Map<String, Object> params) {
        List<AiBot> bots = byBattle.get(battleId);
        if (bots == null) {
            return Map.of("ok", false, "error", "battle_not_found");
        }
        TeammateStrategy strategy;
        try {
            strategy = TeammateStrategy.valueOf(strategyName == null ? "" : strategyName.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return Map.of("ok", false, "error", "invalid_strategy");
        }
        for (int i = 0; i < bots.size(); i++) {
            AiBot bot = bots.get(i);
            if (!bot.botId().equals(botId)) {
                continue;
            }
            Map<String, Object> merged = new LinkedHashMap<>(strategy.defaultParams());
            if (params != null) {
                merged.putAll(params);
            }
            strategyOverrides.put(botId, merged);
            AiBot next = new AiBot(bot.botId(), bot.battleId(), bot.ownerPlayerId(), bot.role(),
                    bot.x(), bot.z(), bot.lastCommand(), bot.lastAction(), System.currentTimeMillis(),
                    bot.phase(), bot.enraged(), strategy.name());
            bots.set(i, next);
            Map<String, Object> out = toView(next, true);
            out.put("strategyParams", merged);
            return out;
        }
        return Map.of("ok", false, "error", "bot_not_found");
    }

    public Map<String, Object> configureBossTree(String botId, List<Map<String, Object>> nodes) {
        if (botId == null || botId.isBlank()) {
            return Map.of("ok", false, "error", "invalid_bot");
        }
        bossTrees.put(botId, BehaviorTree.fromConfig(nodes));
        bossBoards.computeIfAbsent(botId, k -> new BehaviorTree.Blackboard());
        btDebugger.loadFromConfig(botId, nodes);
        return Map.of("ok", true, "botId", botId, "engine", "behavior_tree");
    }

    public Map<String, Object> loadBehaviorTree(String treeId, List<Map<String, Object>> nodes) {
        if (treeId == null || treeId.isBlank()) {
            return Map.of("ok", false, "error", "invalid_tree");
        }
        if (nodes == null || nodes.isEmpty()) {
            btDebugger.loadDefault(treeId);
        } else {
            btDebugger.loadFromConfig(treeId, nodes);
        }
        return Map.of("ok", true, "treeId", treeId, "trees", btDebugger.listTreeIds());
    }

    public Map<String, Object> tickBehaviorTree(String treeId, double hpRatio, long threatTargetId) {
        long t0 = System.nanoTime();
        BehaviorTree.Blackboard bb = btDebugger.blackboard(treeId);
        bb.put("hpRatio", Math.max(0.0, Math.min(1.0, hpRatio)));
        bb.put("threatTargetId", threatTargetId);
        BehaviorTreeDebugger.TickTrace trace = btDebugger.tick(treeId, System.currentTimeMillis());
        aiMetrics.recordBtTick(System.nanoTime() - t0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("treeId", treeId);
        out.put("status", trace.status());
        out.put("lastAction", trace.lastAction());
        out.put("blackboard", trace.blackboard());
        return out;
    }

    public Map<String, Object> inspectBehaviorTree(String treeId) {
        return btDebugger.gmInspect(treeId);
    }

    public Map<String, Object> tickBoss(String battleId, String botId, double hpRatio, long threatTargetId) {
        BehaviorTree.Node tree = bossTrees.get(botId);
        BehaviorTree.Blackboard bb = bossBoards.get(botId);
        if (tree == null || bb == null) {
            return Map.of("ok", false, "error", "boss_bt_not_found");
        }
        long t0 = System.nanoTime();
        ThreatTable threat = threatTables.computeIfAbsent(botId, id -> new ThreatTable());
        if (threatTargetId > 0) {
            threat.addDamage(threatTargetId, 100L, System.currentTimeMillis());
        }
        long focus = threat.topThreatTarget(threatTargetId);
        bb.put("hpRatio", Math.max(0.0, Math.min(1.0, hpRatio)));
        bb.put("threatTargetId", focus > 0 ? focus : threatTargetId);
        BehaviorTree.Status status = tree.tick(bb);
        aiMetrics.recordBtTick(System.nanoTime() - t0);
        updateBotFromBoard(battleId, botId, bb);
        btDebugger.blackboard(botId).put("hpRatio", bb.get("hpRatio", 1.0));
        btDebugger.blackboard(botId).put("threatTargetId", bb.get("threatTargetId", 0L));
        btDebugger.tick(botId, System.currentTimeMillis());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("status", status.name());
        out.put("lastAction", bb.get("lastAction", ""));
        out.put("skill", bb.get("skill", ""));
        out.put("phase", bb.get("phase", 1));
        out.put("enraged", bb.get("enraged", false));
        out.put("focusTargetId", bb.get("focusTargetId", 0L));
        out.put("threat", threat.toView(System.currentTimeMillis()));
        out.put("decisionReason", "bt:" + bb.get("lastAction", ""));
        return out;
    }

    public Map<String, Object> command(String battleId, String botId, String command) {
        List<AiBot> bots = byBattle.get(battleId);
        if (bots == null) {
            return Map.of("ok", false, "error", "battle_not_found");
        }
        for (int i = 0; i < bots.size(); i++) {
            AiBot bot = bots.get(i);
            if (!bot.botId().equals(botId)) {
                continue;
            }
            if ("boss".equals(bot.role()) && bossTrees.containsKey(botId)) {
                return tickBoss(battleId, botId, 1.0, bot.ownerPlayerId());
            }
            String cmd = command == null ? "" : command.trim();
            String action;
            if (cmd.isEmpty()) {
                TeammateStrategy strategy = TeammateStrategy.valueOf(bot.strategy());
                action = strategy.preferAction(bot.role());
            } else {
                action = interpret(cmd, bot.role());
            }
            double[] pos = moveFor(action, bot.x(), bot.z());
            AiBot next = new AiBot(bot.botId(), bot.battleId(), bot.ownerPlayerId(), bot.role(),
                    pos[0], pos[1], cmd, action, System.currentTimeMillis(), bot.phase(), bot.enraged(),
                    bot.strategy());
            bots.set(i, next);
            Map<String, Object> view = toView(next, true);
            view.put("strategyParams", strategyOverrides.getOrDefault(botId,
                    TeammateStrategy.valueOf(bot.strategy()).defaultParams()));
            return view;
        }
        return Map.of("ok", false, "error", "bot_not_found");
    }

    public Map<String, Object> status(String battleId) {
        List<AiBot> bots = byBattle.getOrDefault(battleId, List.of());
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiBot b : bots) {
            list.add(toView(b, false));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("battleId", battleId);
        out.put("bots", list);
        out.put("aiMetrics", aiMetrics.snapshot());
        return out;
    }

    public AiMetrics aiMetrics() {
        return aiMetrics;
    }

    private void updateBotFromBoard(String battleId, String botId, BehaviorTree.Blackboard bb) {
        List<AiBot> bots = byBattle.get(battleId);
        if (bots == null) {
            return;
        }
        for (int i = 0; i < bots.size(); i++) {
            AiBot bot = bots.get(i);
            if (!bot.botId().equals(botId)) {
                continue;
            }
            AiBot next = new AiBot(bot.botId(), bot.battleId(), bot.ownerPlayerId(), bot.role(),
                    bot.x(), bot.z(), bot.lastCommand(),
                    String.valueOf(bb.get("lastAction", bot.lastAction())),
                    System.currentTimeMillis(),
                    bb.get("phase", bot.phase()),
                    bb.get("enraged", bot.enraged()),
                    bot.strategy());
            bots.set(i, next);
            return;
        }
    }

    private static String inferRole(String hint) {
        if (hint == null) {
            return "dps";
        }
        String h = hint.toLowerCase(Locale.ROOT);
        if (h.contains("boss") || h.contains("首领")) {
            return "boss";
        }
        if (h.contains("治疗") || h.contains("heal") || h.contains("奶")) {
            return "healer";
        }
        if (h.contains("坦") || h.contains("tank")) {
            return "tank";
        }
        return "dps";
    }

    private static String interpret(String cmd, String role) {
        String c = cmd.toLowerCase(Locale.ROOT);
        if (c.contains("集火") || c.contains("focus") || c.contains("攻击")) {
            return "focus_fire";
        }
        if (c.contains("治疗") || c.contains("heal")) {
            return "heal";
        }
        if (c.contains("散开") || c.contains("spread")) {
            return "spread";
        }
        if (c.contains("跟随") || c.contains("follow")) {
            return "follow";
        }
        if (c.contains("防守") || c.contains("defend")) {
            return "defend";
        }
        return "idle_" + role;
    }

    private static double[] moveFor(String action, double x, double z) {
        return switch (action) {
            case "focus_fire" -> new double[]{x + 2, z};
            case "spread" -> new double[]{x + 5, z + 5};
            case "follow" -> new double[]{x + 1, z + 1};
            case "heal", "defend" -> new double[]{x - 1, z};
            default -> new double[]{x, z};
        };
    }

    private static Map<String, Object> toView(AiBot bot, boolean wrapOk) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (wrapOk) {
            m.put("ok", true);
        }
        m.put("botId", bot.botId());
        m.put("battleId", bot.battleId());
        m.put("ownerPlayerId", bot.ownerPlayerId());
        m.put("role", bot.role());
        m.put("strategy", bot.strategy());
        m.put("x", bot.x());
        m.put("z", bot.z());
        m.put("lastCommand", bot.lastCommand());
        m.put("lastAction", bot.lastAction());
        m.put("updatedAtMs", bot.updatedAtMs());
        m.put("phase", bot.phase());
        m.put("enraged", bot.enraged());
        m.put("engine", "boss".equals(bot.role()) ? "behavior_tree" : "rules");
        return m;
    }
}
