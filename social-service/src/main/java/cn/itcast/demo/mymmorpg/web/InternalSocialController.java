package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.SocialAchievementService;
import cn.itcast.demo.mymmorpg.service.SocialRelationGraphService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "social-service")
@RequestMapping("/internal/social")
public class InternalSocialController {

    private final SocialRelationGraphService relationGraphService;
    private final SocialAchievementService achievementService;

    public InternalSocialController(SocialRelationGraphService relationGraphService,
                                    SocialAchievementService achievementService) {
        this.relationGraphService = relationGraphService;
        this.achievementService = achievementService;
    }

    @GetMapping("/graph")
    public Map<String, Object> graph(@RequestHeader("X-Player-Id") long playerId) {
        return relationGraphService.graphOf(playerId);
    }

    @PostMapping("/friends/add")
    public Map<String, Object> addFriend(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestParam long friendId) {
        return relationGraphService.addFriend(playerId, friendId);
    }

    @PostMapping("/friends/remove")
    public Map<String, Object> removeFriend(@RequestHeader("X-Player-Id") long playerId,
                                            @RequestParam long friendId) {
        return relationGraphService.removeFriend(playerId, friendId);
    }

    @PostMapping("/block")
    public Map<String, Object> block(@RequestHeader("X-Player-Id") long playerId,
                                     @RequestParam long targetId) {
        return relationGraphService.block(playerId, targetId);
    }

    @PostMapping("/unblock")
    public Map<String, Object> unblock(@RequestHeader("X-Player-Id") long playerId,
                                       @RequestParam long targetId) {
        return relationGraphService.unblock(playerId, targetId);
    }

    @GetMapping("/block/list")
    public Map<String, Object> blockList(@RequestHeader("X-Player-Id") long playerId) {
        return relationGraphService.listBlocked(playerId);
    }

    @PostMapping("/intimacy/add")
    public Map<String, Object> addIntimacy(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestParam long friendId,
                                          @RequestParam(defaultValue = "1") int delta) {
        return relationGraphService.addIntimacy(playerId, friendId, delta);
    }

    @GetMapping("/achievements")
    public Map<String, Object> achievements(@RequestHeader("X-Player-Id") long playerId) {
        return achievementService.profile(playerId);
    }

    @PostMapping("/achievements/equip-title")
    public Map<String, Object> equipTitle(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestParam String title) {
        return achievementService.equipTitle(playerId, title);
    }

    @PostMapping("/achievements/equip-frame")
    public Map<String, Object> equipFrame(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestParam String frame) {
        return achievementService.equipFrame(playerId, frame);
    }

    /** 本地/联调：直接灌入事件（MQ 关闭时可用） */
    @PostMapping("/events/ingest")
    public Map<String, Object> ingest(@RequestParam String type,
                                      @RequestParam long actorId,
                                      @RequestParam(defaultValue = "0") long targetId) {
        achievementService.applySocialEvent(type, actorId, targetId);
        return Map.of("ok", true, "type", type, "actorId", actorId, "targetId", targetId);
    }
}
