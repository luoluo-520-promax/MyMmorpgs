package cn.itcast.demo.mymmorpg.world.narrative;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色探索技能：感知宝箱、翻译古文、风场感知等，鼓励换角探索。
 */
@Service
public class ExplorationSkillService {

    public enum ExploreSkill {
        TREASURE_SENSE("感知隐藏宝箱"),
        ANCIENT_TRANSLATE("翻译古代文字"),
        WIND_READ("感知风场路径"),
        ELEMENT_TRACE("追踪元素痕迹"),
        NIGHT_VISION("夜间视野");

        private final String label;

        ExploreSkill(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** characterTemplateId → 技能 */
    private final ConcurrentHashMap<String, Set<ExploreSkill>> characterSkills =
            new ConcurrentHashMap<>();
    /** playerId → 当前出战角色 */
    private final ConcurrentHashMap<Long, String> activeCharacter = new ConcurrentHashMap<>();

    public void bindCharacterSkills(String characterId, Set<ExploreSkill> skills) {
        if (characterId == null || characterId.isBlank()) {
            return;
        }
        characterSkills.put(characterId, skills == null ? Set.of() : Set.copyOf(skills));
    }

    public Map<String, Object> switchCharacter(long playerId, String characterId) {
        if (!characterSkills.containsKey(characterId)) {
            return Map.of("ok", false, "error", "unknown_character");
        }
        activeCharacter.put(playerId, characterId);
        return snapshot(playerId);
    }

    public boolean hasSkill(long playerId, ExploreSkill skill) {
        String charId = activeCharacter.get(playerId);
        if (charId == null) {
            return false;
        }
        return characterSkills.getOrDefault(charId, Set.of()).contains(skill);
    }

    /**
     * 扫描周围隐藏内容（需 TREASURE_SENSE）。
     */
    public Map<String, Object> senseHidden(long playerId, List<String> candidatePointIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!hasSkill(playerId, ExploreSkill.TREASURE_SENSE)) {
            body.put("ok", false);
            body.put("error", "need_treasure_sense");
            body.put("hint", "切换具备感知技能的角色");
            return body;
        }
        body.put("ok", true);
        body.put("revealed", candidatePointIds == null ? List.of() : List.copyOf(candidatePointIds));
        body.put("skill", ExploreSkill.TREASURE_SENSE.name());
        return body;
    }

    public Map<String, Object> translate(long playerId, String glyphId, String cipherText) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!hasSkill(playerId, ExploreSkill.ANCIENT_TRANSLATE)) {
            body.put("ok", false);
            body.put("error", "need_ancient_translate");
            return body;
        }
        body.put("ok", true);
        body.put("glyphId", glyphId);
        body.put("plainText", cipherText == null ? "" : new StringBuilder(cipherText).reverse().toString());
        body.put("skill", ExploreSkill.ANCIENT_TRANSLATE.name());
        return body;
    }

    public Map<String, Object> snapshot(long playerId) {
        String charId = activeCharacter.get(playerId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("activeCharacter", charId == null ? "" : charId);
        body.put("skills", charId == null ? List.of()
                : characterSkills.getOrDefault(charId, Set.of()).stream()
                .map(s -> Map.of("id", s.name(), "label", s.label())).toList());
        return body;
    }
}
