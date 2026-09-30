package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.gacha.GachaConfigService;
import cn.itcast.demo.mymmorpg.service.ConfigCacheReloadService;
import cn.itcast.demo.mymmorpg.shop.ShopConfigService;
import cn.itcast.demo.mymmorpg.skin.SkinConfigRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 玩家服策划表缓存热更：清空 Redis 配置缓存，并重载皮肤 / 抽卡 / 商城 JSON。
 */
@RestController
@RequestMapping("/internal/ops")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "player-service")
public class InternalConfigOpsController {

    private final ConfigCacheReloadService configCacheReloadService;
    private final ObjectProvider<SkinConfigRepository> skinConfigRepository;
    private final ObjectProvider<GachaConfigService> gachaConfigService;
    private final ObjectProvider<ShopConfigService> shopConfigService;

    public InternalConfigOpsController(ConfigCacheReloadService configCacheReloadService,
                                       ObjectProvider<SkinConfigRepository> skinConfigRepository,
                                       ObjectProvider<GachaConfigService> gachaConfigService,
                                       ObjectProvider<ShopConfigService> shopConfigService) {
        this.configCacheReloadService = configCacheReloadService;
        this.skinConfigRepository = skinConfigRepository;
        this.gachaConfigService = gachaConfigService;
        this.shopConfigService = shopConfigService;
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        List<String> cleared = configCacheReloadService.evictConfigCaches();
        boolean skinReloaded = false;
        SkinConfigRepository skins = skinConfigRepository.getIfAvailable();
        if (skins != null) {
            skinReloaded = skins.reload();
        }
        boolean gachaReloaded = false;
        GachaConfigService gacha = gachaConfigService.getIfAvailable();
        if (gacha != null) {
            gachaReloaded = gacha.reload();
        }
        boolean shopReloaded = false;
        ShopConfigService shop = shopConfigService.getIfAvailable();
        if (shop != null) {
            shopReloaded = shop.reload();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("service", "player-service");
        body.put("clearedCaches", cleared);
        body.put("skinReloaded", skinReloaded);
        body.put("gachaReloaded", gachaReloaded);
        body.put("shopReloaded", shopReloaded);
        body.put("message", "config caches evicted");
        return body;
    }
}
