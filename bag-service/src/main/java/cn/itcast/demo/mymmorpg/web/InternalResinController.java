package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.EquipRandomizer;
import cn.itcast.demo.mymmorpg.service.ResinService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@ConditionalOnBean(ResinService.class)
@RequestMapping("/internal/bag")
public class InternalResinController {

    private final ResinService resinService;
    private final EquipRandomizer equipRandomizer;

    public InternalResinController(ResinService resinService, EquipRandomizer equipRandomizer) {
        this.resinService = resinService;
        this.equipRandomizer = equipRandomizer;
    }

    @GetMapping("/resin")
    public Map<String, Object> status(@RequestHeader("X-Player-Id") long playerId) {
        return resinService.status(playerId);
    }

    @PostMapping("/resin/consume")
    public Map<String, Object> consume(@RequestHeader("X-Player-Id") long playerId,
                                       @RequestParam int amount) {
        return resinService.consume(playerId, amount);
    }

    @PostMapping("/resin/buy")
    public Map<String, Object> buy(@RequestHeader("X-Player-Id") long playerId) {
        return resinService.buy(playerId);
    }

    @PostMapping("/equip/roll")
    public Map<String, Object> rollEquip(@RequestParam int itemConfigId,
                                         @RequestParam(defaultValue = "3") int rarity) {
        EquipRandomizer.AffixRoll roll = equipRandomizer.roll(itemConfigId, rarity);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("mainStat", roll.mainStat());
        out.put("mainValue", roll.mainValue());
        out.put("subStats", roll.subStats());
        return out;
    }
}
