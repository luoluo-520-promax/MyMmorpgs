package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.ClientVersionRelease;
import cn.itcast.demo.mymmorpg.model.update.ManifestSignatureUtil;
import cn.itcast.demo.mymmorpg.model.update.VersionManifestPayload;
import cn.itcast.demo.mymmorpg.protocol.UpdateRetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetVersionManifestCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetVersionManifestScRsp;
import cn.itcast.demo.mymmorpg.repository.ClientVersionReleaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 更新增强流程：Manifest 签名、灰度白名单、补丁归档生命周期。
 */
public class UpdateEnhancementFlowTest {

    @Mock
    private ClientVersionReleaseRepository releaseRepository;

    private AutoCloseable mocks;
    private UpdateService updateService;
    private VersionManifestImportService importService;
    private PatchLifecycleService lifecycleService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        updateService = new UpdateService(releaseRepository, objectMapper);
        lifecycleService = new PatchLifecycleService(releaseRepository);
        lifecycleService.setKeepActiveVersions(2);
        importService = new VersionManifestImportService(releaseRepository, objectMapper, lifecycleService);
        importService.setSigningSecret("test-signing-secret");
        importService.setSigningKeyId("test-key");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void import_signsManifestAndSetsSigningKeyId() throws Exception {
        String json = """
                {
                  "versionCode": "2.0.0",
                  "versionNumber": 20000,
                  "minClientVersionNumber": 10000,
                  "baseUrl": "https://cdn.example.com/",
                  "resourcePacks": [{"path":"a.bin","checksum":"abc","size":10,"packType":"resource"}]
                }
                """;
        when(releaseRepository.findByVersionCode("2.0.0")).thenReturn(Optional.empty());
        when(releaseRepository.findAll()).thenReturn(List.of());
        when(releaseRepository.save(any(ClientVersionRelease.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientVersionRelease saved = importService.importFromJson(json);
        VersionManifestPayload stored = objectMapper.readValue(saved.getManifest(), VersionManifestPayload.class);

        assertThat(stored.manifestChecksum).isNotBlank();
        assertThat(stored.manifestSignature).isNotBlank();
        assertThat(stored.signingKeyId).isEqualTo("test-key");
        VersionManifestPayload forSign = unsignedCopy(stored);
        String expected = ManifestSignatureUtil.signHmacSha256(
                objectMapper.writeValueAsString(forSign), "test-signing-secret");
        assertThat(stored.manifestSignature).isEqualTo(expected);
    }

    @Test
    public void grayWhitelist_nonWhitelistedFallsBackToPreviousActive() throws Exception {
        ClientVersionRelease latest = release("3.0.0", 30000L, grayManifest(30000L, List.of(1001L)));
        ClientVersionRelease previous = release("2.5.0", 25000L, openManifest(25000L));
        when(releaseRepository.findFirstByActiveTrueOrderByVersionNumberDesc()).thenReturn(Optional.of(latest));
        when(releaseRepository.findAll()).thenReturn(List.of(latest, previous));

        GetVersionManifestScRsp denied = GetVersionManifestScRsp.parseFrom(
                updateService.handleGetVersionManifest(GetVersionManifestCsReq.newBuilder()
                        .setClientVersionNumber(26000)
                        .setAccountId(9999)
                        .build()).payload());
        assertThat(denied.getRetcode()).isEqualTo(UpdateRetCode.OK);
        assertThat(denied.getVersionNumber()).isEqualTo(25000L);

        GetVersionManifestScRsp allowed = GetVersionManifestScRsp.parseFrom(
                updateService.handleGetVersionManifest(GetVersionManifestCsReq.newBuilder()
                        .setClientVersionNumber(26000)
                        .setAccountId(1001)
                        .build()).payload());
        assertThat(allowed.getRetcode()).isEqualTo(UpdateRetCode.OK);
        assertThat(allowed.getVersionNumber()).isEqualTo(30000L);
        assertThat(allowed.getSupportsRange()).isTrue();
    }

    @Test
    public void patchLifecycle_archivesOlderThanKeepWindow() throws Exception {
        ClientVersionRelease v1 = release("1.0.0", 10000L, openManifest(10000L));
        ClientVersionRelease v2 = release("1.1.0", 11000L, openManifest(11000L));
        ClientVersionRelease v3 = release("1.2.0", 12000L, openManifest(12000L));
        when(releaseRepository.findAll()).thenReturn(List.of(v1, v2, v3));
        when(releaseRepository.save(any(ClientVersionRelease.class))).thenAnswer(inv -> inv.getArgument(0));

        int archived = lifecycleService.archiveOlderThanKeepWindow();
        assertThat(archived).isEqualTo(1);
        assertThat(v1.getActive()).isFalse();
        assertThat(v2.getActive()).isTrue();
        assertThat(v3.getActive()).isTrue();
    }

    private VersionManifestPayload unsignedCopy(VersionManifestPayload src) throws Exception {
        VersionManifestPayload copy = objectMapper.readValue(
                objectMapper.writeValueAsString(src), VersionManifestPayload.class);
        copy.manifestSignature = "";
        return copy;
    }

    private ClientVersionRelease release(String code, long number, VersionManifestPayload manifest) throws Exception {
        ClientVersionRelease r = new ClientVersionRelease();
        r.setVersionCode(code);
        r.setVersionNumber(number);
        r.setMinClientVersionNumber(manifest.minClientVersionNumber);
        r.setActive(true);
        r.setManifest(objectMapper.writeValueAsString(manifest));
        return r;
    }

    private static VersionManifestPayload openManifest(long versionNumber) {
        VersionManifestPayload m = new VersionManifestPayload();
        m.versionCode = "v" + versionNumber;
        m.versionNumber = versionNumber;
        m.minClientVersionNumber = 9000;
        m.grayPercent = 100;
        m.supportsRange = true;
        return m;
    }

    private static VersionManifestPayload grayManifest(long versionNumber, List<Long> accounts) {
        VersionManifestPayload m = openManifest(versionNumber);
        m.grayAccountIds = accounts;
        m.grayPercent = 0;
        return m;
    }
}
