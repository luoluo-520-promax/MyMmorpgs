package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.PassService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@ConditionalOnBean(PassService.class)
@RequestMapping("/internal/shop/pass")
public class InternalPassController {

    private final PassService passService;

    public InternalPassController(PassService passService) {
        this.passService = passService;
    }

    @GetMapping
    public Map<String, Object> status(@RequestHeader("X-Player-Id") long playerId,
                                      @RequestParam(defaultValue = "1") int seasonId) {
        return passService.status(playerId, seasonId);
    }

    @PostMapping("/xp")
    public Map<String, Object> addXp(@RequestHeader("X-Player-Id") long playerId,
                                     @RequestParam(defaultValue = "1") int seasonId,
                                     @RequestParam int xp) {
        return passService.addDailyXp(playerId, seasonId, xp);
    }

    @PostMapping("/unlock-paid")
    public Map<String, Object> unlockPaid(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestParam(defaultValue = "1") int seasonId) {
        return passService.unlockPaid(playerId, seasonId);
    }

    @PostMapping("/monthly-card")
    public Map<String, Object> monthlyCard(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestParam(defaultValue = "1") int seasonId,
                                           @RequestParam(defaultValue = "30") int days) {
        return passService.activateMonthlyCard(playerId, seasonId, days);
    }

    @PostMapping("/monthly-card/claim")
    public Map<String, Object> claimMonthly(@RequestHeader("X-Player-Id") long playerId,
                                            @RequestParam(defaultValue = "1") int seasonId) {
        return passService.claimMonthlyDaily(playerId, seasonId);
    }

    @PostMapping("/monthly-card/auto-renew")
    public Map<String, Object> setAutoRenew(@RequestHeader("X-Player-Id") long playerId,
                                            @RequestParam(defaultValue = "1") int seasonId,
                                            @RequestParam boolean enabled) {
        return passService.setAutoRenew(playerId, seasonId, enabled);
    }

    @PostMapping("/monthly-card/renew-if-due")
    public Map<String, Object> renewIfDue(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestParam(defaultValue = "1") int seasonId) {
        return passService.renewMonthlyCardIfDue(playerId, seasonId);
    }

    @PostMapping("/compensation/claim")
    public Map<String, Object> claimCompensation(@RequestHeader("X-Player-Id") long playerId,
                                                 @RequestParam(defaultValue = "1") int seasonId) {
        return passService.claimCompensation(playerId, seasonId);
    }

    @PostMapping("/platform-sync")
    public Map<String, Object> syncFromPlatform(@RequestHeader("X-Player-Id") long playerId,
                                                @RequestParam(defaultValue = "1") int seasonId,
                                                @RequestParam String platform,
                                                @RequestParam String platformReceipt) {
        return passService.syncFromPlatform(playerId, seasonId, platform, platformReceipt);
    }

    @PostMapping("/first-charge-double")
    public Map<String, Object> firstCharge(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestParam(defaultValue = "1") int seasonId,
                                           @RequestParam int amount) {
        return passService.applyFirstChargeDouble(playerId, seasonId, amount);
    }

    @PostMapping("/claim")
    public Map<String, Object> claim(@RequestHeader("X-Player-Id") long playerId,
                                     @RequestParam(defaultValue = "1") int seasonId,
                                     @RequestParam int level,
                                     @RequestParam(defaultValue = "FREE") String tier) {
        return passService.claim(playerId, seasonId, level, PassService.Tier.valueOf(tier));
    }

    @PostMapping("/purchase-level/request")
    public Map<String, Object> requestPurchaseLevel(@RequestHeader("X-Player-Id") long playerId,
                                                    @RequestParam(defaultValue = "1") int seasonId,
                                                    @RequestParam int targetLevel) {
        return passService.requestPurchaseLevel(playerId, seasonId, targetLevel);
    }

    @PostMapping("/purchase-level/confirm")
    public Map<String, Object> confirmPurchaseLevel(@RequestHeader("X-Player-Id") long playerId,
                                                    @RequestParam(defaultValue = "1") int seasonId,
                                                    @RequestParam String confirmToken) {
        return passService.confirmPurchaseLevel(playerId, seasonId, confirmToken);
    }

    @PostMapping("/quest-completed")
    public Map<String, Object> onQuestCompleted(@RequestHeader("X-Player-Id") long playerId,
                                                @RequestParam(defaultValue = "1") int seasonId,
                                                @RequestParam int questId,
                                                @RequestParam(defaultValue = "80") int xp) {
        return passService.onQuestCompleted(playerId, seasonId, questId, xp);
    }

    @PostMapping("/auto-renew")
    public Map<String, Object> autoRenew(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestParam(defaultValue = "1") int seasonId,
                                         @RequestParam boolean enabled) {
        return passService.setAutoRenew(playerId, seasonId, enabled);
    }

    @PostMapping("/monthly-card/renew")
    public Map<String, Object> renewMonthly(@RequestHeader("X-Player-Id") long playerId,
                                            @RequestParam(defaultValue = "1") int seasonId) {
        return passService.renewMonthlyCardIfDue(playerId, seasonId);
    }

    @PostMapping("/compensation")
    public Map<String, Object> compensation(@RequestHeader("X-Player-Id") long playerId,
                                            @RequestParam(defaultValue = "1") int seasonId) {
        return passService.claimCompensation(playerId, seasonId);
    }

    @PostMapping("/sync-platform")
    public Map<String, Object> syncPlatform(@RequestHeader("X-Player-Id") long playerId,
                                            @RequestParam(defaultValue = "1") int seasonId,
                                            @RequestParam String platform,
                                            @RequestParam String receipt) {
        return passService.syncFromPlatform(playerId, seasonId, platform, receipt);
    }
}
