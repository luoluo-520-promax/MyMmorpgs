package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.service.SkinService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 皮肤内部发放/道具解锁（商城履约、背包使用皮肤卡）。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "player-service")
@RequestMapping("/internal/player/skin")
public class InternalSkinController {

    private final SkinService skinService;

    public InternalSkinController(SkinService skinService) {
        this.skinService = skinService;
    }

    @PostMapping("/grant")
    public ResponseEntity<Integer> grant(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestBody Map<String, Object> body) {
        int skinId = body.get("skinId") == null ? 0 : ((Number) body.get("skinId")).intValue();
        if (skinId <= 0) {
            return ResponseEntity.ok(RetCode.SKIN_NOT_FOUND);
        }
        return ResponseEntity.ok(skinService.grantSkinRet(playerId, skinId));
    }

    @PostMapping("/unlock-by-item")
    public ResponseEntity<Integer> unlockByItem(@RequestHeader("X-Player-Id") long playerId,
                                                @RequestBody Map<String, Object> body) {
        int itemId = body.get("itemId") == null ? 0 : ((Number) body.get("itemId")).intValue();
        if (itemId <= 0) {
            return ResponseEntity.ok(RetCode.SKIN_ITEM_INVALID);
        }
        return ResponseEntity.ok(skinService.unlockByItem(playerId, itemId));
    }
}
