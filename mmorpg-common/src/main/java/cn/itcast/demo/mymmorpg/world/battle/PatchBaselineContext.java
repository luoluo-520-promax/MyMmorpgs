package cn.itcast.demo.mymmorpg.world.battle;

import cn.itcast.demo.mymmorpg.world.content.OpenWorldConfigPatchService;

import java.util.Map;

/**
 * 回放基线上下文：绑定录制时刻的 config_version 与 server_epoch。
 */
public record PatchBaselineContext(
        String configVersion,
        String gitCommitSha,
        long serverEpoch,
        OpenWorldConfigPatchService.ConfigSnapshot configSnapshot) {

    public static PatchBaselineContext capture(
            OpenWorldConfigPatchService configPatch,
            long serverEpoch,
            String sceneInstanceId) {
        String cv = configPatch.currentConfigVersion();
        Map<String, Object> snapResult = configPatch.snapshotForInstance(sceneInstanceId, System.currentTimeMillis());
        String gitSha = String.valueOf(snapResult.getOrDefault("gitCommitSha", cv));
        return new PatchBaselineContext(cv, gitSha, serverEpoch, null);
    }
}
