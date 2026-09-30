package cn.itcast.demo.mymmorpg.service; // 更新服务导入域，负责把外部 JSON 清单写入数据库

import cn.itcast.demo.mymmorpg.entity.ClientVersionRelease; // 版本发布实体，用于持久化导入结果
import cn.itcast.demo.mymmorpg.model.update.ManifestChecksumUtil; // SHA-256 工具，用于补齐清单 checksum
import cn.itcast.demo.mymmorpg.model.update.ManifestSignatureUtil;
import cn.itcast.demo.mymmorpg.model.update.VersionManifestPayload; // 版本清单载体，用于承接 JSON 反序列化结果
import cn.itcast.demo.mymmorpg.repository.ClientVersionReleaseRepository; // 版本清单仓库，用于按 versionCode upsert
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 解析器，用于导入与回写 manifest 文本
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service; // 声明为 Spring 服务组件
import org.springframework.transaction.annotation.Transactional; // 保证导入过程的读写一致性

/**
 * 从 JSON 导入客户端版本清单并持久化。
 */
@Service
public class VersionManifestImportService {

    private final ClientVersionReleaseRepository releaseRepository;
    private final ObjectMapper objectMapper;
    private final PatchLifecycleService patchLifecycleService;
    private String signingSecret = "";
    private String signingKeyId = "default";

    public VersionManifestImportService(
            ClientVersionReleaseRepository releaseRepository,
            ObjectMapper objectMapper,
            PatchLifecycleService patchLifecycleService) {
        this.releaseRepository = releaseRepository;
        this.objectMapper = objectMapper;
        this.patchLifecycleService = patchLifecycleService;
    }

    @Value("${game.update.manifest-signing-secret:}")
    void setSigningSecret(String signingSecret) {
        this.signingSecret = signingSecret == null ? "" : signingSecret;
    }

    @Value("${game.update.manifest-signing-key-id:default}")
    void setSigningKeyId(String signingKeyId) {
        this.signingKeyId = signingKeyId == null || signingKeyId.isBlank() ? "default" : signingKeyId;
    }

    @Transactional
    public ClientVersionRelease importFromJson(String json) throws Exception {
        VersionManifestPayload manifest = objectMapper.readValue(json, VersionManifestPayload.class);
        validate(manifest);
        if (manifest.manifestChecksum == null || manifest.manifestChecksum.isBlank()) {
            manifest.manifestChecksum = computeChecksum(manifest);
        }
        if (manifest.signingKeyId == null || manifest.signingKeyId.isBlank()) {
            manifest.signingKeyId = signingKeyId;
        }
        if ((manifest.manifestSignature == null || manifest.manifestSignature.isBlank())
                && !signingSecret.isBlank()) {
            String canonical = canonicalForSign(manifest);
            manifest.manifestSignature = ManifestSignatureUtil.signHmacSha256(canonical, signingSecret);
        }
        ClientVersionRelease release = releaseRepository
                .findByVersionCode(manifest.versionCode)
                .orElseGet(ClientVersionRelease::new);
        release.setVersionCode(manifest.versionCode);
        release.setVersionNumber(manifest.versionNumber);
        release.setMinClientVersionNumber(manifest.minClientVersionNumber);
        release.setActive(true);
        release.setManifest(objectMapper.writeValueAsString(manifest));
        ClientVersionRelease saved = releaseRepository.save(release);
        // 新全量版本发布后，归档窗口外的旧版本
        patchLifecycleService.archiveOlderThanKeepWindow();
        return saved;
    }

    private void validate(VersionManifestPayload manifest) {
        if (manifest.versionCode == null || manifest.versionCode.isBlank()) {
            throw new IllegalArgumentException("versionCode 不能为空");
        }
        if (manifest.versionNumber <= 0) {
            throw new IllegalArgumentException("versionNumber 必须大于 0");
        }
    }

    private String computeChecksum(VersionManifestPayload manifest) throws Exception {
        VersionManifestPayload copy = objectMapper.readValue(
                objectMapper.writeValueAsString(manifest), VersionManifestPayload.class);
        copy.manifestChecksum = "";
        copy.manifestSignature = "";
        return ManifestChecksumUtil.sha256Hex(objectMapper.writeValueAsString(copy));
    }

    private String canonicalForSign(VersionManifestPayload manifest) throws Exception {
        VersionManifestPayload copy = objectMapper.readValue(
                objectMapper.writeValueAsString(manifest), VersionManifestPayload.class);
        copy.manifestSignature = "";
        return objectMapper.writeValueAsString(copy);
    }
}
