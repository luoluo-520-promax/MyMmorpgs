package cn.itcast.demo.mymmorpg.port.remote;

import cn.itcast.demo.mymmorpg.config.PortRemoteProperties;
import cn.itcast.demo.mymmorpg.port.BattleScenePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "true")
public class RestBattleScenePort implements BattleScenePort {

    private static final Logger log = LoggerFactory.getLogger(RestBattleScenePort.class);

    private final InternalApiRestClient restClient;
    private final PortRemoteProperties properties;

    public RestBattleScenePort(InternalApiRestClient restClient, PortRemoteProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public Optional<MonsterBattleRef> findMonsterForBattle(long playerId, long enemyEntityId) {
        try {
            MonsterBattleRef ref = restClient.get(
                    properties.getSceneServiceUrl() + "/internal/scene/battle/monster?enemyEntityId=" + enemyEntityId,
                    playerId, MonsterBattleRef.class);
            return Optional.ofNullable(ref);
        } catch (Exception e) {
            log.warn("远程 BattleScenePort.findMonsterForBattle 失败 playerId={} enemy={}", playerId, enemyEntityId, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<EncounterLock> markMonsterInCombat(long playerId, long enemyEntityId) {
        try {
            EncounterLock lock = restClient.postJson(
                    properties.getSceneServiceUrl() + "/internal/scene/battle/mark-combat",
                    playerId,
                    Map.of("enemyEntityId", enemyEntityId),
                    EncounterLock.class);
            return Optional.ofNullable(lock);
        } catch (Exception e) {
            log.warn("远程 BattleScenePort.markMonsterInCombat 失败 playerId={} enemy={}", playerId, enemyEntityId, e);
            return Optional.empty();
        }
    }

    @Override
    public void releaseMonsterFromCombat(long playerId, long enemyEntityId) {
        try {
            restClient.postJsonVoid(
                    properties.getSceneServiceUrl() + "/internal/scene/battle/release-combat",
                    playerId,
                    Map.of("enemyEntityId", enemyEntityId));
        } catch (Exception e) {
            log.warn("远程 BattleScenePort.releaseMonsterFromCombat 失败 playerId={} enemy={}", playerId, enemyEntityId, e);
        }
    }

    @Override
    public void removeMonsterFromScene(long playerId, long enemyEntityId) {
        try {
            restClient.postJsonVoid(
                    properties.getSceneServiceUrl() + "/internal/scene/battle/remove-monster",
                    playerId,
                    Map.of("enemyEntityId", enemyEntityId));
        } catch (Exception e) {
            log.warn("远程 BattleScenePort.removeMonsterFromScene 失败 playerId={} enemy={}", playerId, enemyEntityId, e);
        }
    }

    @Override
    public boolean isPlayerInScene(long playerId) {
        try {
            Boolean inScene = restClient.get(
                    properties.getSceneServiceUrl() + "/internal/scene/battle/player-in-scene",
                    playerId, Boolean.class);
            return Boolean.TRUE.equals(inScene);
        } catch (Exception e) {
            log.warn("远程 BattleScenePort.isPlayerInScene 失败 playerId={}", playerId, e);
            return false;
        }
    }

    @Override
    public Optional<Integer> getEntityTypeInLine(long playerId, long entityId) {
        try {
            Integer type = restClient.get(
                    properties.getSceneServiceUrl() + "/internal/scene/battle/entity-type?entityId=" + entityId,
                    playerId, Integer.class);
            return Optional.ofNullable(type);
        } catch (Exception e) {
            log.warn("远程 BattleScenePort.getEntityTypeInLine 失败 playerId={} entity={}", playerId, entityId, e);
            return Optional.empty();
        }
    }
}
