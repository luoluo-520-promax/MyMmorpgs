package cn.itcast.demo.mymmorpg.world.traverse;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色专属「世界交互技」：攀爬耐力减免、滑翔加速、矿脉特化等。
 * 移动手段解锁绑定角色/道具，而非账号全局。
 */
@Service
public class WorldSkillService {

    public enum WorldSkillType {
        CLIMB_STAMINA_REDUCE,
        GLIDE_SPEED_UP,
        SPRINT_NO_STAMINA,
        ORE_SPECIALIST,
        HOOK_RANGE_UP,
        WATER_WALK
    }

    public record CharacterWorldProfile(
            String characterId,
            Set<WorldSkillType> skills,
            /** 该角色可解锁的移动手段 */
            Set<TraverseModeService.Mode> unlockableModes,
            /** 可选：需持有道具才解锁移动手段 */
            String requiredItemId) {

        public CharacterWorldProfile {
            skills = skills == null ? Set.of() : Set.copyOf(skills);
            unlockableModes = unlockableModes == null ? Set.of() : Set.copyOf(unlockableModes);
        }
    }

    private final ConcurrentHashMap<String, CharacterWorldProfile> profiles = new ConcurrentHashMap<>();
    /** playerId → 已绑定解锁的 mode→characterId */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<TraverseModeService.Mode, String>> boundModes =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> activeCharacter = new ConcurrentHashMap<>();

    public void bindProfile(CharacterWorldProfile profile) {
        profiles.put(profile.characterId(), profile);
    }

    public Map<String, Object> setActiveCharacter(long playerId, String characterId) {
        if (!profiles.containsKey(characterId)) {
            return Map.of("ok", false, "error", "unknown_character");
        }
        activeCharacter.put(playerId, characterId);
        return snapshot(playerId);
    }

    /**
     * 解锁移动手段：必须关联特定角色（或道具），写入绑定表。
     */
    public Map<String, Object> unlockModeBound(
            long playerId,
            TraverseModeService.Mode mode,
            String characterId,
            Set<String> ownedItems,
            TraverseModeService traverse) {
        CharacterWorldProfile profile = profiles.get(characterId);
        if (profile == null) {
            return Map.of("ok", false, "error", "unknown_character");
        }
        if (!profile.unlockableModes().contains(mode)) {
            return Map.of("ok", false, "error", "character_cannot_unlock_mode",
                    "characterId", characterId, "mode", mode.name());
        }
        if (profile.requiredItemId() != null && !profile.requiredItemId().isBlank()) {
            Set<String> items = ownedItems == null ? Set.of() : ownedItems;
            if (!items.contains(profile.requiredItemId())) {
                return Map.of("ok", false, "error", "missing_item",
                        "requiredItemId", profile.requiredItemId());
            }
        }
        boundModes.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(mode, characterId);
        traverse.unlock(playerId, mode);
        activeCharacter.put(playerId, characterId);
        Map<String, Object> body = new LinkedHashMap<>(snapshot(playerId));
        body.put("unlockedMode", mode.name());
        body.put("boundCharacter", characterId);
        return body;
    }

    public boolean canUseMode(long playerId, TraverseModeService.Mode mode) {
        return boundModes.getOrDefault(playerId, new ConcurrentHashMap<>()).containsKey(mode);
    }

    public Map<String, Object> activeSkills(long playerId) {
        String charId = activeCharacter.get(playerId);
        if (charId == null) {
            return Map.of("ok", true, "skills", List.of(), "activeCharacter", "");
        }
        CharacterWorldProfile p = profiles.get(charId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("activeCharacter", charId);
        body.put("skills", p == null ? List.of()
                : p.skills().stream().map(Enum::name).toList());
        return body;
    }

    public boolean hasSkill(long playerId, WorldSkillType type) {
        String charId = activeCharacter.get(playerId);
        if (charId == null) {
            return false;
        }
        CharacterWorldProfile p = profiles.get(charId);
        return p != null && p.skills().contains(type);
    }

    public Map<String, Object> snapshot(long playerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("activeCharacter", activeCharacter.getOrDefault(playerId, ""));
        Map<String, String> bounds = new LinkedHashMap<>();
        boundModes.getOrDefault(playerId, new ConcurrentHashMap<>())
                .forEach((m, c) -> bounds.put(m.name(), c));
        body.put("boundModes", bounds);
        body.putAll(activeSkills(playerId));
        return body;
    }
}
