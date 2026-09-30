package cn.itcast.demo.mymmorpg.world.sideplay;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大世界捉宠 / 驯服 / 骑乘：发现生物 → CATCH_CREATURE 服务端摇奖 → 命名养成 / 协同战斗。
 */
@Service
public class CreatureCatchService {

    public static final String PROTOCOL_CATCH = "CATCH_CREATURE";

    public record CreatureTemplate(
            String species,
            float mountSpeed,
            float mountJump,
            float mountStaminaMul,
            int modelId) {
        public CreatureTemplate {
            species = species == null ? "" : species.trim();
            mountSpeed = mountSpeed <= 0f ? 12f : mountSpeed;
            mountJump = mountJump <= 0f ? 8f : mountJump;
            mountStaminaMul = mountStaminaMul <= 0f ? 1.2f : mountStaminaMul;
        }
    }

    public record WildCreature(
            String creatureId,
            String species,
            int sceneId,
            float x, float y, float z,
            int catchDifficulty,
            int baseAtk,
            int baseHp,
            int currentHp) {

        public WildCreature {
            catchDifficulty = Math.max(1, Math.min(99, catchDifficulty));
            baseAtk = Math.max(1, baseAtk);
            baseHp = Math.max(1, baseHp);
            currentHp = currentHp <= 0 ? baseHp : Math.min(baseHp, currentHp);
        }

        public WildCreature(
                String creatureId, String species, int sceneId,
                float x, float y, float z, int catchDifficulty, int baseAtk, int baseHp) {
            this(creatureId, species, sceneId, x, y, z, catchDifficulty, baseAtk, baseHp, baseHp);
        }
    }

    public record OwnedPet(
            String instanceId,
            String species,
            int level,
            int atk,
            int hp,
            long caughtAtMs,
            String displayName,
            boolean mountable) {

        public OwnedPet(
                String instanceId, String species, int level, int atk, int hp, long caughtAtMs) {
            this(instanceId, species, level, atk, hp, caughtAtMs, species, false);
        }
    }

    private final ConcurrentHashMap<String, WildCreature> wild = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<OwnedPet>> pets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CreatureTemplate> templates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> mounted = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public void registerTemplate(CreatureTemplate template) {
        if (template != null && !template.species().isBlank()) {
            templates.put(template.species(), template);
        }
    }

    public void spawn(WildCreature creature) {
        wild.put(creature.creatureId(), creature);
    }

    public Map<String, Object> tryCatch(long playerId, String creatureId, int ballPower, long nowMs) {
        return catchCreature(playerId, creatureId, "item_catch_ball", ballPower, 0, nowMs);
    }

    /**
     * CATCH_CREATURE：概率 = 残血越高越好 + 世界等级系数；SecureRandom 服务端摇奖，客户端不可传结果。
     */
    public Map<String, Object> catchCreature(
            long playerId, String creatureId, String itemId, int ballPower, int worldLevel, long nowMs) {
        WildCreature c = wild.get(creatureId);
        if (c == null) {
            return Map.of("ok", false, "error", "creature_not_found", "protocol", PROTOCOL_CATCH);
        }
        if (itemId == null || itemId.isBlank()) {
            return Map.of("ok", false, "error", "item_required", "protocol", PROTOCOL_CATCH);
        }
        int power = Math.max(1, ballPower);
        double hpRatio = (double) c.currentHp() / c.baseHp();
        int lowHpBonus = (int) Math.round((1.0 - hpRatio) * 40);
        int worldPenalty = Math.max(0, worldLevel) * 2;
        int chance = Math.min(95, Math.max(5, 100 - c.catchDifficulty() + power + lowHpBonus - worldPenalty));
        boolean success = secureRandom.nextInt(100) < chance;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("protocol", PROTOCOL_CATCH);
        body.put("creatureId", creatureId);
        body.put("species", c.species());
        body.put("itemId", itemId);
        body.put("hpRatio", Math.round(hpRatio * 1000d) / 1000d);
        body.put("worldLevel", worldLevel);
        body.put("chance", chance);
        body.put("caught", success);
        if (success) {
            wild.remove(creatureId);
            boolean mountable = templates.containsKey(c.species());
            OwnedPet pet = new OwnedPet(
                    "pet-" + playerId + "-" + nowMs,
                    c.species(), 1, c.baseAtk(), c.baseHp(), nowMs, c.species(), mountable);
            pets.computeIfAbsent(playerId, id -> new ArrayList<>()).add(pet);
            body.put("pet", toView(pet));
        }
        return body;
    }

    public Map<String, Object> tameAndName(long playerId, String instanceId, String displayName) {
        List<OwnedPet> list = pets.get(playerId);
        if (list == null) {
            return Map.of("ok", false, "error", "no_pets");
        }
        for (int i = 0; i < list.size(); i++) {
            OwnedPet p = list.get(i);
            if (p.instanceId().equals(instanceId)) {
                String name = displayName == null || displayName.isBlank() ? p.species() : displayName.trim();
                OwnedPet next = new OwnedPet(
                        p.instanceId(), p.species(), p.level(), p.atk(), p.hp(),
                        p.caughtAtMs(), name, true);
                list.set(i, next);
                return Map.of("ok", true, "pet", toView(next), "tamed", true);
            }
        }
        return Map.of("ok", false, "error", "pet_not_found");
    }

    /** 骑乘：复用 SceneMoveCmd.mountCreatureUid；属性来自 creature_template。 */
    public Map<String, Object> mount(long playerId, String instanceId) {
        OwnedPet pet = findPet(playerId, instanceId);
        if (pet == null) {
            return Map.of("ok", false, "error", "pet_not_found");
        }
        if (!pet.mountable()) {
            return Map.of("ok", false, "error", "not_mountable");
        }
        CreatureTemplate t = templates.getOrDefault(pet.species(),
                new CreatureTemplate(pet.species(), 12f, 8f, 1.2f, 0));
        mounted.put(playerId, instanceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("mountCreatureUid", instanceId);
        body.put("modelId", t.modelId());
        body.put("mountSpeed", t.mountSpeed());
        body.put("mountJump", t.mountJump());
        body.put("staminaMul", t.mountStaminaMul());
        body.put("aoiSync", true);
        return body;
    }

    public Map<String, Object> dismount(long playerId) {
        String uid = mounted.remove(playerId);
        return uid == null
                ? Map.of("ok", false, "error", "not_mounted")
                : Map.of("ok", true, "dismounted", uid);
    }

    /** 确定性捕捉（测试） */
    public Map<String, Object> forceCatch(long playerId, String creatureId, long nowMs) {
        WildCreature c = wild.get(creatureId);
        if (c == null) {
            return Map.of("ok", false, "error", "creature_not_found");
        }
        wild.remove(creatureId);
        OwnedPet pet = new OwnedPet(
                "pet-" + playerId + "-" + nowMs,
                c.species(), 1, c.baseAtk(), c.baseHp(), nowMs);
        pets.computeIfAbsent(playerId, id -> new ArrayList<>()).add(pet);
        return Map.of("ok", true, "caught", true, "pet", toView(pet));
    }

    public Map<String, Object> train(long playerId, String instanceId) {
        List<OwnedPet> list = pets.get(playerId);
        if (list == null) {
            return Map.of("ok", false, "error", "no_pets");
        }
        for (int i = 0; i < list.size(); i++) {
            OwnedPet p = list.get(i);
            if (p.instanceId().equals(instanceId)) {
                OwnedPet grown = new OwnedPet(
                        p.instanceId(), p.species(), p.level() + 1,
                        p.atk() + 3, p.hp() + 10, p.caughtAtMs(),
                        p.displayName(), p.mountable());
                list.set(i, grown);
                return Map.of("ok", true, "pet", toView(grown));
            }
        }
        return Map.of("ok", false, "error", "pet_not_found");
    }

    public Map<String, Object> roster(long playerId) {
        List<Map<String, Object>> list = pets.getOrDefault(playerId, List.of()).stream()
                .map(this::toView).toList();
        return Map.of("ok", true, "playerId", playerId, "pets", list);
    }

    public List<OwnedPet> listPets(long playerId) {
        return List.copyOf(pets.getOrDefault(playerId, List.of()));
    }

    private OwnedPet findPet(long playerId, String instanceId) {
        List<OwnedPet> list = pets.get(playerId);
        if (list == null) {
            return null;
        }
        for (OwnedPet p : list) {
            if (p.instanceId().equals(instanceId)) {
                return p;
            }
        }
        return null;
    }

    private Map<String, Object> toView(OwnedPet p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("instanceId", p.instanceId());
        m.put("species", p.species());
        m.put("level", p.level());
        m.put("atk", p.atk());
        m.put("hp", p.hp());
        m.put("displayName", p.displayName());
        m.put("mountable", p.mountable());
        return m;
    }
}
