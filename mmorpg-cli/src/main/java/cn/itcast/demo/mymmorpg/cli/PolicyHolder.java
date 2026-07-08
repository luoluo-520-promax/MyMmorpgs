package cn.itcast.demo.mymmorpg.cli;

import cn.itcast.demo.mymmorpg.entity.ItemConfig;
import cn.itcast.demo.mymmorpg.support.BattlePolicy;
import cn.itcast.demo.mymmorpg.support.ChatPolicy;
import cn.itcast.demo.mymmorpg.support.ItemPolicy;
import cn.itcast.demo.mymmorpg.support.ScenePolicy;
import cn.itcast.demo.mymmorpg.support.SkillPolicy;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 各服务默认策略的 CLI 副本（与 *PolicyConfiguration 逻辑一致，无需 Spring 容器）。
 */
final class PolicyHolder {

    static final BattlePolicy BATTLE = new BattlePolicy() {
        @Override
        public int computeDamage(int attackerAttack, int targetDefense, int actionType, int skillId) {
            int base = Math.max(1, attackerAttack - targetDefense);
            if (actionType == 2 && skillId > 0) {
                return (int) (base * 1.5d);
            }
            return base;
        }

        @Override
        public int computeHeal(int actionType, int itemId) {
            if (actionType != 3) {
                return 0;
            }
            return itemId > 0 ? 150 : 100;
        }
    };

    static final SkillPolicy SKILL = new SkillPolicy() {
        @Override
        public int computeDamage(int playerLevel, int skillId, int targetType) {
            int lv = Math.max(1, playerLevel);
            return 80 + lv * 12 + skillId % 50;
        }

        @Override
        public int computeHeal(int playerLevel, int skillId) {
            int lv = Math.max(1, playerLevel);
            return 60 + lv * 8;
        }
    };

    static final ItemPolicy ITEM = new ItemPolicy() {
        @Override
        public int parseExpReward(ItemConfig config) {
            return parseIntField(config == null ? null : config.getEffectParams(), "exp");
        }

        @Override
        public int parseHpRestore(ItemConfig config) {
            return parseIntField(config == null ? null : config.getEffectParams(), "hp");
        }

        @Override
        public int parseMpRestore(ItemConfig config) {
            return parseIntField(config == null ? null : config.getEffectParams(), "mp");
        }

        private int parseIntField(String json, String field) {
            if (json == null || json.isBlank()) {
                return 0;
            }
            Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)");
            var matcher = pattern.matcher(json);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
        }
    };

    private static final List<String> BLOCKED_CHAT_WORDS = List.of("法轮", "赌博", "色情");

    static final ChatPolicy CHAT = new ChatPolicy() {
        @Override
        public String filterContent(int channel, int msgType, String rawContent) {
            if (rawContent == null || rawContent.isBlank()) {
                return null;
            }
            String text = rawContent.trim();
            if (text.length() > 512) {
                text = text.substring(0, 512);
            }
            for (String word : BLOCKED_CHAT_WORDS) {
                if (text.contains(word)) {
                    return null;
                }
            }
            return text;
        }
    };

    static final ScenePolicy SCENE = (mapId, playerId) -> mapId > 0 && playerId > 0;

    private PolicyHolder() {
    }
}
