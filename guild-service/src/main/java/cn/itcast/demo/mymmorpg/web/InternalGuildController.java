package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.GuildExpeditionService;
import cn.itcast.demo.mymmorpg.service.GuildService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "guild-service")
@RequestMapping("/internal/guild")
public class InternalGuildController {

    private final GuildService guildService;
    private final GuildExpeditionService expeditionService;

    public InternalGuildController(GuildService guildService, GuildExpeditionService expeditionService) {
        this.guildService = guildService;
        this.expeditionService = expeditionService;
    }

    @PostMapping("/create")
    public Map<String, Object> create(@RequestHeader("X-Player-Id") long playerId,
                                      @RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", ""));
        return guildService.create(playerId, name);
    }

    @PostMapping("/join")
    public Map<String, Object> join(@RequestHeader("X-Player-Id") long playerId,
                                    @RequestParam long guildId) {
        return guildService.join(playerId, guildId);
    }

    @PostMapping("/leave")
    public Map<String, Object> leave(@RequestHeader("X-Player-Id") long playerId) {
        return guildService.leave(playerId);
    }

    @PostMapping("/transfer")
    public Map<String, Object> transfer(@RequestHeader("X-Player-Id") long playerId,
                                        @RequestParam long targetPlayerId) {
        return guildService.transfer(playerId, targetPlayerId, true);
    }

    @PostMapping("/tech/upgrade")
    public Map<String, Object> upgradeTech(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestParam String techLine) {
        return guildService.upgradeTech(playerId, techLine);
    }

    @GetMapping("/{guildId}")
    public Map<String, Object> info(@PathVariable long guildId) {
        return guildService.info(guildId);
    }

    @GetMapping("/mine")
    public Map<String, Object> mine(@RequestHeader("X-Player-Id") long playerId) {
        return guildService.myGuild(playerId);
    }

    @PostMapping("/login-touch")
    public Map<String, Object> loginTouch(@RequestHeader("X-Player-Id") long playerId) {
        guildService.touchPlayerLogin(playerId);
        return Map.of("ok", true);
    }

    @PostMapping("/expedition/open")
    public Map<String, Object> openExpedition(@RequestHeader("X-Player-Id") long playerId,
                                             @RequestParam long guildId) {
        return expeditionService.open(playerId, guildId);
    }

    @PostMapping("/expedition/damage")
    public Map<String, Object> damage(@RequestBody Map<String, Object> body) {
        long guildId = ((Number) body.getOrDefault("guildId", 0L)).longValue();
        long playerId = ((Number) body.getOrDefault("playerId", 0L)).longValue();
        long damage = ((Number) body.getOrDefault("damage", 0L)).longValue();
        return expeditionService.reportDamage(guildId, playerId, damage);
    }

    @PostMapping("/expedition/settle")
    public Map<String, Object> settle(@RequestParam long guildId) {
        return expeditionService.settle(guildId);
    }
}
