package cn.itcast.demo.mymmorpg.service; // 更新服务核心业务，负责返回版本清单与资源校验结果

import cn.itcast.demo.mymmorpg.entity.ClientVersionRelease; // 版本发布记录实体，用于读取最新生效清单
import cn.itcast.demo.mymmorpg.model.update.PatchPlanBuilder; // 补丁计划构建器，用于生成下载和修复方案
import cn.itcast.demo.mymmorpg.model.update.ResourceFileEntry; // 清单中的资源文件条目，用于拼装下发数据
import cn.itcast.demo.mymmorpg.model.update.VersionManifestPayload; // 版本清单载体，用于解析数据库中保存的 manifest JSON
import cn.itcast.demo.mymmorpg.protocol.MessageId; // 协议消息编号，用于封装返回包
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 协议消息封装体，用于返回二进制 payload
import cn.itcast.demo.mymmorpg.protocol.UpdateRetCode; // 更新业务返回码，用于标识版本缺失、强更等结果
import cn.itcast.demo.mymmorpg.protocol.protobuf.FileChecksumEntry; // 客户端上报的本地文件 checksum 条目
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetVersionManifestCsReq; // 拉取版本清单的客户端请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetVersionManifestScRsp; // 拉取版本清单的服务端响应
import cn.itcast.demo.mymmorpg.protocol.protobuf.ResourceFileInfo; // 响应中携带的资源文件下载信息
import cn.itcast.demo.mymmorpg.protocol.protobuf.VerifyResourceChecksumCsReq; // 客户端本地资源校验请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.VerifyResourceChecksumScRsp; // 客户端本地资源校验响应
import cn.itcast.demo.mymmorpg.repository.ClientVersionReleaseRepository; // 版本清单仓库，用于查询当前激活版本
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 解析器，用于把 manifest 字符串转成对象
import org.springframework.stereotype.Service; // 声明为 Spring 服务组件

import java.util.HashMap; // 用于构建客户端本地文件 checksum 索引
import java.util.List; // 用于承载下载、删除和修复文件列表
import java.util.Map; // 用于 path -> checksum 的快速查询
import java.util.Optional; // 用于表达可能不存在的最新版本记录

/**
 * 客户端版本清单查询与资源校验服务。
 * <p>
 * 文件维护说明：本类负责从数据库读取最新 active 版本清单，
 * 根据客户端版本号计算补丁计划，并在客户端上报本地 checksum 时返回需要修复的资源列表。
 * 这里的实现必须与客户端更新逻辑保持严格一致，因此修改时应优先保证协议字段与业务规则稳定。
 */
@Service // 将更新业务暴露为可注入的 Spring 服务
public class UpdateService { // 更新域核心服务，承载清单查询与文件校验逻辑

    private final ClientVersionReleaseRepository releaseRepository; // 用于读取最新 active 版本清单
    private final ObjectMapper objectMapper; // 用于解析 manifest JSON 与补齐默认值

    /**
     * 构造更新服务，注入版本仓库与 JSON 解析器
     * @param releaseRepository 客户端版本发布记录仓储，用于查询当前 active 清单
     * @param objectMapper JSON 解析器，用于反序列化 manifest 字段
     */
    public UpdateService(ClientVersionReleaseRepository releaseRepository, ObjectMapper objectMapper) {
        this.releaseRepository = releaseRepository; // 保存版本仓库引用，供后续查询最新版本
        this.objectMapper = objectMapper; // 保存 JSON 解析器引用，供 manifest 反序列化使用
    }

    /**
     * 处理客户端拉取版本清单请求：读取最新 active 版本，计算补丁计划，判断是否需要强制更新
     * @param req 客户端版本清单请求，包含 clientVersion / clientVersionNumber
     * @return 封装后的协议消息，payload 为 GetVersionManifestScRsp
     */
    public ProtocolMessage handleGetVersionManifest(GetVersionManifestCsReq req) {
        Optional<ClientVersionRelease> opt = releaseRepository.findFirstByActiveTrueOrderByVersionNumberDesc();
        if (opt.isEmpty()) {
            return manifestRsp(UpdateRetCode.VERSION_NOT_FOUND, null, null, false);
        }
        VersionManifestPayload manifest = parseManifest(opt.get());
        if (manifest == null) {
            return manifestRsp(UpdateRetCode.MANIFEST_INVALID, null, null, false);
        }
        // 灰度白名单：非白名单账号回退到次新全量版本（若有），否则仍返回当前但标记无新补丁
        if (!isGrayAllowed(manifest, req.getAccountId())) {
            Optional<ClientVersionRelease> fallback = findPreviousActive(opt.get());
            if (fallback.isPresent()) {
                VersionManifestPayload older = parseManifest(fallback.get());
                if (older != null) {
                    manifest = older;
                }
            }
        }
        PatchPlanBuilder.PatchPlan plan = PatchPlanBuilder.build(
                manifest,
                req.getClientVersionNumber(),
                Map.of());
        if (plan.forceUpdate()) {
            return manifestRsp(UpdateRetCode.FORCE_UPDATE_REQUIRED, manifest, plan, true);
        }
        return manifestRsp(UpdateRetCode.OK, manifest, plan, plan.forceUpdate());
    }

    private boolean isGrayAllowed(VersionManifestPayload manifest, long accountId) {
        if (manifest.grayPercent >= 100
                && (manifest.grayAccountIds == null || manifest.grayAccountIds.isEmpty())) {
            return true;
        }
        if (manifest.grayAccountIds != null && !manifest.grayAccountIds.isEmpty()) {
            return accountId > 0 && manifest.grayAccountIds.contains(accountId);
        }
        if (manifest.grayPercent <= 0) {
            return false;
        }
        if (accountId <= 0) {
            return false;
        }
        return Math.floorMod(accountId, 100) < manifest.grayPercent;
    }

    private Optional<ClientVersionRelease> findPreviousActive(ClientVersionRelease current) {
        return releaseRepository.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getActive()))
                .filter(r -> r.getVersionNumber() != null && current.getVersionNumber() != null
                        && r.getVersionNumber() < current.getVersionNumber())
                .max(java.util.Comparator.comparing(ClientVersionRelease::getVersionNumber));
    }

    /**
     * 处理客户端本地资源校验请求：比对本地 checksum 与服务端清单，返回异常文件及修复下载信息
     * @param req 客户端上报的本地文件 path + checksum 列表
     * @return 封装后的协议消息，payload 为 VerifyResourceChecksumScRsp
     */
    public ProtocolMessage handleVerifyResourceChecksum(VerifyResourceChecksumCsReq req) {
        Optional<ClientVersionRelease> opt = releaseRepository.findFirstByActiveTrueOrderByVersionNumberDesc(); // 重新读取最新 active 清单，确保校验基线一致
        if (opt.isEmpty()) { // 没有版本记录时无法判断客户端文件是否有效
            return verifyRsp(UpdateRetCode.VERSION_NOT_FOUND, List.of(), List.of()); // 返回版本不存在，避免误导客户端修复
        }
        VersionManifestPayload manifest = parseManifest(opt.get()); // 解析当前版本清单，获取服务端期望 checksum
        if (manifest == null) { // manifest 数据损坏时不执行校验，直接返回错误
            return verifyRsp(UpdateRetCode.MANIFEST_INVALID, List.of(), List.of()); // 提示清单无效，等待运维修复
        }
        Map<String, String> local = new HashMap<>(); // 构建客户端上报文件路径到 checksum 的索引
        for (FileChecksumEntry entry : req.getLocalFilesList()) { // 逐个读取客户端本地文件校验结果
            local.put(entry.getPath(), entry.getChecksum()); // 以相对路径为键保存本地 checksum，便于与清单比对
        }
        List<String> invalid = PatchPlanBuilder.findInvalidFiles(manifest, local); // 查找 checksum 不一致的文件路径
        List<ResourceFileEntry> repairs = PatchPlanBuilder.repairFilesFor(manifest, invalid); // 把异常文件映射回服务端清单中的下载信息
        int retcode = invalid.isEmpty() ? UpdateRetCode.OK : UpdateRetCode.CHECKSUM_MISMATCH; // 有任意文件不一致时返回校验失败状态
        return verifyRsp(retcode, invalid, repairs); // 返回异常文件与修复包信息，供客户端重新拉取
    }

    /**
     * 解析版本发布记录中的 manifest JSON，并用数据库字段回填缺失的版本元数据
     * @param release 当前 active 的客户端版本发布记录
     * @return 解析成功的清单对象；JSON 损坏时返回 null
     */
    private VersionManifestPayload parseManifest(ClientVersionRelease release) {
        try {
            VersionManifestPayload payload = objectMapper.readValue(release.getManifest(), VersionManifestPayload.class); // 将数据库中的 manifest JSON 解析成对象
            if (payload.versionCode == null || payload.versionCode.isBlank()) { // JSON 中缺少版本字符串时，回填数据库中的版本号
                payload.versionCode = release.getVersionCode(); // 保证响应中的版本标识始终可用
            }
            if (payload.versionNumber <= 0) { // JSON 中缺少数值版本时，回填数据库中的版本号
                payload.versionNumber = release.getVersionNumber(); // 保证补丁计划可正确比较版本大小
            }
            if (payload.minClientVersionNumber <= 0) { // JSON 中缺少最小兼容版本时，回填数据库中的下限值
                payload.minClientVersionNumber = release.getMinClientVersionNumber(); // 保证强更判断不依赖脏数据
            }
            return payload; // 返回补齐后的清单对象，供后续补丁计算使用
        } catch (Exception e) { // 反序列化失败说明数据库中 manifest 已损坏
            return null; // 上层收到 null 后会返回清单无效错误码
        }
    }

    /**
     * 组装版本清单响应包，写入返回码、版本信息、强更标记以及下载/删除文件列表
     * @param retcode 业务返回码（OK / 版本不存在 / 清单无效 / 需要强更等）
     * @param manifest 服务端版本清单，可为 null 表示不下发版本详情
     * @param plan 补丁计划，包含需下载与需删除的文件列表，可为 null
     * @param forceUpdate 是否要求客户端强制全量更新
     * @return GET_VERSION_MANIFEST_SC_RSP 协议消息
     */
    private ProtocolMessage manifestRsp(
            int retcode,
            VersionManifestPayload manifest,
            PatchPlanBuilder.PatchPlan plan,
            boolean forceUpdate) {
        var b = GetVersionManifestScRsp.newBuilder().setRetcode(retcode); // 构建版本清单响应包，先写入业务返回码
        if (manifest != null) { // 只有清单有效时才下发版本与下载信息
            b.setVersionCode(manifest.versionCode)
                    .setVersionNumber(manifest.versionNumber)
                    .setForceUpdate(forceUpdate)
                    .setManifestChecksum(manifest.manifestChecksum != null ? manifest.manifestChecksum : "")
                    .setPatchToolVersion(manifest.patchToolVersion != null ? manifest.patchToolVersion : "")
                    .setBaseUrl(manifest.baseUrl != null ? manifest.baseUrl : "")
                    .setManifestSignature(manifest.manifestSignature != null ? manifest.manifestSignature : "")
                    .setSigningKeyId(manifest.signingKeyId != null ? manifest.signingKeyId : "")
                    .setSupportsRange(manifest.supportsRange);
            if (plan != null) { // 仅在成功计算出补丁计划时才附带文件列表
                for (ResourceFileEntry entry : plan.downloadFiles()) { // 遍历所有需要下载或修复的资源文件
                    b.addDownloadFiles(toProto(entry, manifest.baseUrl)); // 将服务端资源条目转换为协议对象并加入下载列表
                }
                plan.deleteFiles().forEach(b::addDeleteFiles); // 将服务端要求删除的旧文件路径原样返回给客户端
            }
        }
        return new ProtocolMessage(MessageId.GET_VERSION_MANIFEST_SC_RSP, b.build().toByteArray()); // 包装成统一协议消息返回给调用方
    }

    /**
     * 组装资源校验响应包，写入返回码、异常文件路径以及修复文件下载信息
     * @param retcode 业务返回码（OK / checksum 不一致 / 版本或清单异常）
     * @param invalid checksum 不一致的本地文件路径列表
     * @param repairs 需要重新下载修复的服务端资源条目
     * @return VERIFY_RESOURCE_CHECKSUM_SC_RSP 协议消息
     */
    private ProtocolMessage verifyRsp(int retcode, List<String> invalid, List<ResourceFileEntry> repairs) {
        var b = VerifyResourceChecksumScRsp.newBuilder().setRetcode(retcode); // 构建资源校验响应包，先写入校验结果码
        invalid.forEach(b::addInvalidFiles); // 将 checksum 不一致的本地文件路径逐个返回给客户端
        Optional<ClientVersionRelease> opt = releaseRepository.findFirstByActiveTrueOrderByVersionNumberDesc(); // 重新读取最新清单，用于拼接修复文件下载地址
        String baseUrl = ""; // 默认不带下载根路径，避免在缺少清单时拼接错误 URL
        if (opt.isPresent()) { // 只有存在有效版本时才尝试提取资源根路径
            VersionManifestPayload manifest = parseManifest(opt.get()); // 解析 manifest，获取 baseUrl 与下载信息
            if (manifest != null && manifest.baseUrl != null) { // manifest 有效且声明了资源根路径时才覆盖默认值
                baseUrl = manifest.baseUrl; // 用最新清单中的 baseUrl 作为下载地址前缀
            }
            if (manifest != null) { // 仅在清单有效时才将修复文件转换为协议对象
                for (ResourceFileEntry entry : repairs) { // 遍历需要修复的文件清单
                    b.addRepairFiles(toProto(entry, baseUrl)); // 将修复文件及下载 URL 下发给客户端重新获取
                }
            }
        }
        return new ProtocolMessage(MessageId.VERIFY_RESOURCE_CHECKSUM_SC_RSP, b.build().toByteArray()); // 返回统一协议消息，供客户端执行修复流程
    }

    /**
     * 将清单中的资源条目转换为协议对象，并补齐最终下载地址
     * @param entry 服务端资源文件条目
     * @param baseUrl 资源根路径，在条目未配置独立 downloadUrl 时用于拼接
     * @return 可直接下发给客户端的 ResourceFileInfo
     */
    private ResourceFileInfo toProto(ResourceFileEntry entry, String baseUrl) {
        String url = entry.downloadUrl != null && !entry.downloadUrl.isBlank() // 优先使用清单中单独配置的下载地址
                ? entry.downloadUrl
                : joinUrl(baseUrl, entry.path); // 未配置独立地址时，使用 baseUrl 与 path 拼接出下载地址
        return ResourceFileInfo.newBuilder()
                .setPath(entry.path != null ? entry.path : "") // 资源相对路径是客户端定位文件的主键
                .setChecksum(entry.checksum != null ? entry.checksum : "") // 下发服务端期望 checksum，供客户端下载后校验
                .setSize(entry.size) // 下发文件大小，便于客户端预估下载进度
                .setDownloadUrl(url) // 下发最终可访问的下载链接
                .setPackType(entry.packType != null ? entry.packType : "resource") // 下发资源类型，供客户端区分资源包、音频包和配置包
                .build();
    }

    /**
     * 安全拼接资源根路径与相对文件路径，处理空值与斜杠重复问题
     * @param baseUrl 资源根地址，可为空白
     * @param path 资源相对路径，可为空白
     * @return 拼接后的下载地址或兜底路径
     */
    private static String joinUrl(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) { // 没有资源根路径时直接返回相对路径，避免拼接出非法 URL
            return path != null ? path : ""; // 兜底返回空字符串，防止空指针
        }
        if (path == null || path.isBlank()) { // 没有文件路径时只返回 baseUrl，便于诊断配置
            return baseUrl; // 直接使用根地址作为下载前缀
        }
        if (baseUrl.endsWith("/") && path.startsWith("/")) { // 避免双斜杠导致下载地址格式不规范
            return baseUrl + path.substring(1); // 去掉 path 开头的斜杠后再拼接
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) { // baseUrl 和 path 都没有斜杠时需要补一个分隔符
            return baseUrl + "/" + path; // 以单个斜杠连接根路径与文件路径
        }
        return baseUrl + path; // 其余情况说明两者已有正确的分隔符，直接拼接即可
    }
}
