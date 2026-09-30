package cn.itcast.demo.mymmorpg.model.update; // 更新补丁规划模型，负责把清单与本地 checksum 比对成下载计划

import java.util.ArrayList; // 用于收集需要下载和修复的文件列表
import java.util.HashMap; // 用于按路径索引清单条目，便于回查修复文件
import java.util.List; // 用于返回补丁计划中的多个文件集合
import java.util.Map; // 用于 localChecksums 与清单条目的快速匹配

/**
 * 根据服务端清单与客户端本地 checksum 生成下载/删除/修复计划。
 */
public final class PatchPlanBuilder { // 更新补丁计划的纯工具类，所有方法均为静态逻辑

    private PatchPlanBuilder() { // 禁止实例化，避免误把工具类当作业务对象使用
    }

    public record PatchPlan( // 补丁计划结果，聚合下载、删除、修复和强更标记
            List<ResourceFileEntry> downloadFiles, // 需要客户端下载或重新下载的文件
            List<String> deleteFiles, // 客户端需要删除的旧文件路径
            List<String> invalidFiles, // 客户端 checksum 不一致的文件路径
            List<ResourceFileEntry> repairFiles, // 需要按服务端清单修复的文件条目
            boolean forceUpdate) { // 是否需要强制整包更新
    }

    /**
     * 对比客户端版本与服务端清单，生成补丁计划。
     *
     * @param manifest           服务端最新清单
     * @param clientVersionNumber 客户端当前数值版本
     * @param localChecksums     客户端本地 path -> checksum，可为 null
     */
    public static PatchPlan build( // 生成下载、删除与修复计划，是更新服务的核心比对逻辑
            VersionManifestPayload manifest, // 服务端最新版本清单
            long clientVersionNumber, // 客户端当前版本号，用于判断是否需要强更
            Map<String, String> localChecksums) { // 客户端本地 checksum 映射，可能为空
        boolean forceUpdate = clientVersionNumber > 0 // 客户端上报了有效版本号时才参与强更判断
                && clientVersionNumber < manifest.minClientVersionNumber; // 低于最小兼容版本时返回强制更新
        List<ResourceFileEntry> allFiles = allEntries(manifest); // 汇总资源包、音频包和配置文件
        Map<String, String> local = localChecksums != null ? localChecksums : Map.of(); // 本地 checksum 为空时使用空映射兜底

        List<ResourceFileEntry> downloadFiles = new ArrayList<>(); // 收集需要下载或重下的文件
        List<String> invalidFiles = new ArrayList<>(); // 收集 checksum 不一致的本地文件路径
        List<ResourceFileEntry> repairFiles = new ArrayList<>(); // 收集需要修复的文件条目，供校验接口返回

        for (ResourceFileEntry entry : allFiles) { // 逐个比对服务端期望资源与客户端本地状态
            String localChecksum = local.get(entry.path); // 按路径取出客户端本地 checksum
            if (localChecksum == null) { // 本地没有该文件时，说明必须下载
                downloadFiles.add(entry); // 加入下载计划，供客户端补齐缺失文件
            } else if (!ManifestChecksumUtil.matches(entry.checksum, localChecksum)) { // 本地 checksum 与服务端不一致时需要重下
                invalidFiles.add(entry.path); // 记录异常路径，供客户端先标记为损坏文件
                repairFiles.add(entry); // 记录服务端对应条目，供校验接口返回修复下载信息
                downloadFiles.add(entry); // 同时加入下载列表，让客户端直接重新获取正确版本
            }
        }

        return new PatchPlan( // 返回完整补丁计划，客户端可据此执行更新流程
                downloadFiles, // 需要下载的文件集合
                new ArrayList<>(manifest.deleteFiles), // 直接沿用清单中的删除列表，清理旧资源
                invalidFiles, // checksum 不一致的文件路径
                repairFiles, // 用于修复的文件条目
                forceUpdate); // 是否要求客户端跳过增量更新
    }

    /**
     * 校验客户端上报的文件 checksum，返回不一致路径。
     */
    public static List<String> findInvalidFiles( // 根据客户端上报的本地 checksum 找出异常文件路径
            VersionManifestPayload manifest, // 服务端最新版本清单
            Map<String, String> localChecksums) { // 客户端本地 checksum 映射
        List<String> invalid = new ArrayList<>(); // 收集比对失败的文件路径
        if (localChecksums == null || localChecksums.isEmpty()) { // 客户端没有上报任何文件时直接返回空结果
            return invalid; // 无本地文件信息，无法判定具体异常路径
        }
        for (ResourceFileEntry entry : allEntries(manifest)) { // 遍历服务端要求的所有文件
            String local = localChecksums.get(entry.path); // 查询该路径在客户端的本地 checksum
            if (local != null && !ManifestChecksumUtil.matches(entry.checksum, local)) { // 存在但 checksum 不一致时记为异常
                invalid.add(entry.path); // 将文件路径加入异常列表，供客户端修复
            }
        }
        return invalid; // 返回所有 checksum 不一致的文件路径
    }

    public static List<ResourceFileEntry> repairFilesFor( // 根据异常路径回查对应服务端文件条目
            VersionManifestPayload manifest, // 服务端最新版本清单
            List<String> invalidPaths) { // 客户端判定为损坏的文件路径集合
        if (invalidPaths == null || invalidPaths.isEmpty()) { // 没有异常路径时无需生成修复列表
            return List.of(); // 返回空集合，避免客户端误执行下载
        }
        Map<String, ResourceFileEntry> index = new HashMap<>(); // 先构建 path 到条目的索引，便于快速定位修复文件
        for (ResourceFileEntry entry : allEntries(manifest)) { // 遍历所有服务端文件条目
            index.put(entry.path, entry); // 以路径作为键保存完整文件信息
        }
        List<ResourceFileEntry> repairs = new ArrayList<>(); // 收集需要返给客户端的修复文件条目
        for (String path : invalidPaths) { // 按客户端报告的异常路径逐个回查
            ResourceFileEntry entry = index.get(path); // 从索引中取出服务端对应文件
            if (entry != null) { // 只有服务端清单中存在该路径时才返回修复信息
                repairs.add(entry); // 将该条目加入修复列表，供客户端重新下载
            }
        }
        return repairs; // 返回按异常路径映射出的修复文件清单
    }

    private static List<ResourceFileEntry> allEntries(VersionManifestPayload manifest) { // 汇总所有需要参与更新比对的文件条目
        List<ResourceFileEntry> all = new ArrayList<>(); // 创建总列表，按资源类型顺序合并
        if (manifest.resourcePacks != null) { // 资源包列表存在时加入总集合
            all.addAll(manifest.resourcePacks); // 主资源包优先参与下载计划
        }
        if (manifest.audioLanguagePacks != null) { // 音频语言包列表存在时加入总集合
            all.addAll(manifest.audioLanguagePacks); // 语言资源也需要参与完整性校验
        }
        if (manifest.configFiles != null) { // 配置文件列表存在时加入总集合
            all.addAll(manifest.configFiles); // 配置类资源同样纳入更新和修复流程
        }
        return all; // 返回聚合后的全部文件条目
    }
}
