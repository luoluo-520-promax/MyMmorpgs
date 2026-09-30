package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.port.BattleScenePort;
import cn.itcast.demo.mymmorpg.service.SceneActorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * 供 battle-service 远程调用的场景战斗 Port 内部 API。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "scene-service")
@RequestMapping("/internal/scene/battle")
public class InternalSceneBattleController {

    private final SceneActorService sceneActorService;

    public InternalSceneBattleController(SceneActorService sceneActorService) {
        this.sceneActorService = sceneActorService;
    }

    @GetMapping("/monster")
    public ResponseEntity<BattleScenePort.MonsterBattleRef> findMonster(
            @RequestHeader("X-Player-Id") long playerId,
            @RequestParam long enemyEntityId) {
        Optional<BattleScenePort.MonsterBattleRef> ref =
                sceneActorService.findMonsterForBattle(playerId, enemyEntityId);
        return ref.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/mark-combat")
    public ResponseEntity<BattleScenePort.EncounterLock> markCombat(
            @RequestHeader("X-Player-Id") long playerId,
            @RequestBody Map<String, Object> body) {
        long enemyEntityId = ((Number) body.get("enemyEntityId")).longValue();
        Optional<BattleScenePort.EncounterLock> lock =
                sceneActorService.markMonsterInCombat(playerId, enemyEntityId);
        return lock.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/release-combat")
    public ResponseEntity<Void> releaseCombat(@RequestHeader("X-Player-Id") long playerId,
                                              @RequestBody Map<String, Object> body) {
        long enemyEntityId = ((Number) body.get("enemyEntityId")).longValue();
        sceneActorService.releaseMonsterFromCombat(playerId, enemyEntityId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/remove-monster")
    public ResponseEntity<Void> removeMonster(@RequestHeader("X-Player-Id") long playerId,
                                              @RequestBody Map<String, Object> body) {
        long enemyEntityId = ((Number) body.get("enemyEntityId")).longValue();
        sceneActorService.removeMonsterFromScene(playerId, enemyEntityId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/player-in-scene")
    public ResponseEntity<Boolean> playerInScene(@RequestHeader("X-Player-Id") long playerId) {
        return ResponseEntity.ok(sceneActorService.isPlayerInScene(playerId));
    }

    @GetMapping("/entity-type")
    public ResponseEntity<Integer> entityType(@RequestHeader("X-Player-Id") long playerId,
                                              @RequestParam long entityId) {
        Optional<Integer> type = sceneActorService.getEntityTypeInLine(playerId, entityId);
        return type.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
