package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.service.PlayerDataLoadStateService;
import cn.itcast.demo.mymmorpg.service.PlayerEntityCacheService;
import cn.itcast.demo.mymmorpg.service.PlayerProgressService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

/**
 * 供各域微服务通过 RestTemplate 调用的玩家 Port 内部 API。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "player-service")
@RequestMapping("/internal/player")
public class InternalPlayerPortController {

    private final PlayerEntityCacheService playerEntityCacheService;
    private final PlayerNotificationPort playerNotificationPort;
    private final PlayerProgressService playerProgressService;
    private final PlayerDataLoadStateService playerDataLoadStateService;

    public InternalPlayerPortController(
            PlayerEntityCacheService playerEntityCacheService,
            PlayerNotificationPort playerNotificationPort,
            PlayerProgressService playerProgressService,
            PlayerDataLoadStateService playerDataLoadStateService) {
        this.playerEntityCacheService = playerEntityCacheService;
        this.playerNotificationPort = playerNotificationPort;
        this.playerProgressService = playerProgressService;
        this.playerDataLoadStateService = playerDataLoadStateService;
    }

    @GetMapping("/cache/{playerId}")
    public ResponseEntity<Player> getCache(@PathVariable long playerId) {
        Player player = playerEntityCacheService.findById(playerId);
        return player == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(player);
    }

    @PostMapping("/cache/save")
    public ResponseEntity<Player> saveCache(@RequestHeader("X-Player-Id") long playerId, @RequestBody Player player) {
        if (player == null || player.getId() == null || player.getId() != playerId) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(playerEntityCacheService.saveCacheAndMarkDirty(player));
    }

    @PostMapping("/notify/send")
    public ResponseEntity<Void> notifySend(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestBody Map<String, Object> body) {
        int msgId = ((Number) body.get("msgId")).intValue();
        String payloadB64 = (String) body.get("payload");
        byte[] payload = payloadB64 == null ? new byte[0] : Base64.getDecoder().decode(payloadB64);
        playerNotificationPort.send(playerId, msgId, payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/notify/broadcast")
    public ResponseEntity<Void> notifyBroadcast(@RequestBody Map<String, Object> body) {
        int msgId = ((Number) body.get("msgId")).intValue();
        String payloadB64 = (String) body.get("payload");
        long exclude = body.get("excludePlayerId") == null ? 0L : ((Number) body.get("excludePlayerId")).longValue();
        byte[] payload = payloadB64 == null ? new byte[0] : Base64.getDecoder().decode(payloadB64);
        playerNotificationPort.broadcastAllOnline(msgId, payload, exclude == 0L ? null : exclude);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/progress/add-exp")
    public ResponseEntity<Player> addExp(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> playerMap = (Map<String, Object>) body.get("player");
        int expReward = ((Number) body.get("expReward")).intValue();
        Player player = mapToPlayer(playerMap);
        if (player == null || player.getId() == null || player.getId() != playerId) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        return ResponseEntity.ok(playerProgressService.addExp(player, expReward));
    }

    @GetMapping("/data-load/{playerId}/{type}")
    public ResponseEntity<Boolean> dataLoadReady(@PathVariable long playerId, @PathVariable String type) {
        PlayerDataLoadPort.DataType dataType = PlayerDataLoadPort.DataType.valueOf(type);
        return ResponseEntity.ok(playerDataLoadStateService.isReady(playerId, dataType));
    }

    private static Player mapToPlayer(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Player player = new Player();
        if (map.get("id") != null) {
            player.setId(((Number) map.get("id")).longValue());
        }
        if (map.get("accountId") != null) {
            player.setAccountId(((Number) map.get("accountId")).longValue());
        }
        if (map.get("name") != null) {
            player.setName((String) map.get("name"));
        }
        if (map.get("level") != null) {
            player.setLevel(((Number) map.get("level")).intValue());
        }
        if (map.get("exp") != null) {
            player.setExp(((Number) map.get("exp")).longValue());
        }
        if (map.get("gold") != null) {
            player.setGold(((Number) map.get("gold")).longValue());
        }
        if (map.get("strength") != null) {
            player.setStrength(((Number) map.get("strength")).intValue());
        }
        if (map.get("agility") != null) {
            player.setAgility(((Number) map.get("agility")).intValue());
        }
        if (map.get("intelligence") != null) {
            player.setIntelligence(((Number) map.get("intelligence")).intValue());
        }
        if (map.get("talentPoints") != null) {
            player.setTalentPoints(((Number) map.get("talentPoints")).intValue());
        }
        if (map.get("powerScore") != null) {
            player.setPowerScore(((Number) map.get("powerScore")).intValue());
        }
        return player;
    }
}
