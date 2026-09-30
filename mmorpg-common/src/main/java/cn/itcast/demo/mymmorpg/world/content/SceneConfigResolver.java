package cn.itcast.demo.mymmorpg.world.content;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * SceneActor 加载配置入口：优先灰度，失败回退全局基线。
 */
@Service
public class SceneConfigResolver {

    private final OpenWorldConfigPatchService configPatchService;

    public SceneConfigResolver(OpenWorldConfigPatchService configPatchService) {
        this.configPatchService = configPatchService;
    }

    public Map<String, Object> resolveCell(PlayerConfigContext ctx, String gridCell) {
        if (ctx == null) {
            return configPatchService.getCell(gridCell);
        }
        return configPatchService.resolveForPlayer(ctx, gridCell);
    }

    public Map<String, Object> resolveCell(
            long playerId, long accountId, int zoneId, boolean betaTester, String gridCell) {
        return resolveCell(new PlayerConfigContext(playerId, accountId, zoneId, betaTester), gridCell);
    }
}
