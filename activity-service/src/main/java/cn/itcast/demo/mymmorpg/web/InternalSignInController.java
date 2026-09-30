package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.SignInService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@ConditionalOnBean(SignInService.class)
@RequestMapping("/internal/activity/signin")
public class InternalSignInController {

    private final SignInService signInService;

    public InternalSignInController(SignInService signInService) {
        this.signInService = signInService;
    }

    @GetMapping
    public Map<String, Object> status(@RequestHeader("X-Player-Id") long playerId) {
        return signInService.status(playerId);
    }

    @PostMapping
    public Map<String, Object> sign(@RequestHeader("X-Player-Id") long playerId) {
        return signInService.signToday(playerId);
    }

    @PostMapping("/makeup")
    public Map<String, Object> makeup(@RequestHeader("X-Player-Id") long playerId,
                                      @RequestParam int day,
                                      @RequestParam(defaultValue = "DIAMOND") String costType) {
        return signInService.makeupSign(playerId, day, costType);
    }

    @PutMapping("/rewards/{day}")
    public Map<String, Object> setReward(@PathVariable int day,
                                         @RequestParam int itemId,
                                         @RequestParam int count) {
        return signInService.setDynamicReward(day, itemId, count);
    }

    @PostMapping("/monthly-card/activate")
    public Map<String, Object> activateMonthly(@RequestHeader("X-Player-Id") long playerId) {
        return signInService.activateMonthlyCard(playerId);
    }

    @PostMapping("/monthly-card/claim")
    public Map<String, Object> claimMonthly(@RequestHeader("X-Player-Id") long playerId) {
        return signInService.claimMonthlyCardDaily(playerId);
    }
}
