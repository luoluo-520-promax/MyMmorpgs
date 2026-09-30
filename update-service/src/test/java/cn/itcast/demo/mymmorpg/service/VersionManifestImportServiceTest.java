/**
 * 文件说明：VersionManifestImportService 单元测试类。
 * 职责：验证 JSON 清单导入校验、checksum 补齐与按 versionCode 幂等 upsert，并输出中文测试日志。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.ClientVersionRelease;
import cn.itcast.demo.mymmorpg.model.update.VersionManifestPayload;
import cn.itcast.demo.mymmorpg.repository.ClientVersionReleaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VersionManifestImportService 单元测试：Mock 仓储，日志输出具体入参与断言结果。
 */
public class VersionManifestImportServiceTest {

    private static final Logger log = LoggerFactory.getLogger(VersionManifestImportServiceTest.class);

    @Mock
    private ClientVersionReleaseRepository releaseRepository;

    private AutoCloseable mocks;
    private VersionManifestImportService importService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeMethod
    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        importService = new VersionManifestImportService(
                releaseRepository, objectMapper, new PatchLifecycleService(releaseRepository));
        log.info("[测试前置] VersionManifestImportService 已初始化");
    }

    @AfterMethod
    @AfterEach
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @org.junit.jupiter.api.Test
    public void importFromJson_successAndFillChecksum() throws Exception {
        String versionCode = "1.2.0";
        long versionNumber = 10200L;
        long minClient = 9000L;
        String json = """
                {
                  "versionCode": "%s",
                  "versionNumber": %d,
                  "minClientVersionNumber": %d,
                  "baseUrl": "https://cdn.example.com/",
                  "resourcePacks": [{"path":"assets/ui/main.bundle","checksum":"aaa111","size":100,"packType":"resource"}]
                }
                """.formatted(versionCode, versionNumber, minClient);
        log.info("[测试开始] 场景=导入清单并补齐checksum | versionCode={} | versionNumber={} | minClient={} | jsonLen={} | 期望active=true",
                versionCode, versionNumber, minClient, json.length());

        when(releaseRepository.findByVersionCode(versionCode)).thenReturn(Optional.empty());
        when(releaseRepository.findAll()).thenReturn(java.util.List.of());
        when(releaseRepository.save(any(ClientVersionRelease.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientVersionRelease saved = importService.importFromJson(json);
        ArgumentCaptor<ClientVersionRelease> captor = ArgumentCaptor.forClass(ClientVersionRelease.class);
        verify(releaseRepository).save(captor.capture());
        ClientVersionRelease toSave = captor.getValue();
        VersionManifestPayload stored = objectMapper.readValue(toSave.getManifest(), VersionManifestPayload.class);

        log.info("[测试断言] 场景=导入清单并补齐checksum | versionCode={} | versionNumber={} | active={} | manifestChecksumLen={} | resourcePacksCount={}",
                saved.getVersionCode(), saved.getVersionNumber(), saved.getActive(),
                stored.manifestChecksum == null ? 0 : stored.manifestChecksum.length(),
                stored.resourcePacks.size());
        assertThat(saved.getVersionCode()).isEqualTo(versionCode);
        assertThat(saved.getVersionNumber()).isEqualTo(versionNumber);
        assertThat(saved.getActive()).isTrue();
        assertThat(stored.manifestChecksum).isNotBlank();
        assertThat(stored.resourcePacks).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void importFromJson_rejectBlankVersionCode() {
        String json = """
                {"versionCode":"","versionNumber":10000,"minClientVersionNumber":9000}
                """;
        log.info("[测试开始] 场景=导入拒绝空versionCode | json={} | 期望异常=IllegalArgumentException", json);

        assertThatThrownBy(() -> importService.importFromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("versionCode");
        log.info("[测试断言] 场景=导入拒绝空versionCode | 异常类型=IllegalArgumentException | message含versionCode=true");
    }

    @Test
    @org.junit.jupiter.api.Test
    public void importFromJson_rejectInvalidVersionNumber() {
        String versionCode = "1.0.0";
        long versionNumber = 0L;
        String json = """
                {"versionCode":"%s","versionNumber":%d,"minClientVersionNumber":9000}
                """.formatted(versionCode, versionNumber);
        log.info("[测试开始] 场景=导入拒绝非法versionNumber | versionCode={} | versionNumber={} | 期望异常=IllegalArgumentException",
                versionCode, versionNumber);

        assertThatThrownBy(() -> importService.importFromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("versionNumber");
        log.info("[测试断言] 场景=导入拒绝非法versionNumber | 异常类型=IllegalArgumentException | message含versionNumber=true");
    }
}
