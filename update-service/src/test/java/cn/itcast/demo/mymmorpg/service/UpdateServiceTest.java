/**
 * 文件说明：UpdateService 单元测试类。
 * 职责：使用 Mockito 模拟依赖，验证版本清单拉取与资源 checksum 校验的核心/异常路径，并输出中文测试日志。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.ClientVersionRelease;
import cn.itcast.demo.mymmorpg.model.update.ManifestChecksumUtil;
import cn.itcast.demo.mymmorpg.model.update.ResourceFileEntry;
import cn.itcast.demo.mymmorpg.model.update.VersionManifestPayload;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.UpdateRetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.FileChecksumEntry;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetVersionManifestCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetVersionManifestScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.VerifyResourceChecksumCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.VerifyResourceChecksumScRsp;
import cn.itcast.demo.mymmorpg.repository.ClientVersionReleaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * UpdateService 单元测试：Mock 外部依赖，日志输出具体入参与断言结果。
 */
public class UpdateServiceTest {

    private static final Logger log = LoggerFactory.getLogger(UpdateServiceTest.class);

    @Mock
    private ClientVersionReleaseRepository releaseRepository;

    private AutoCloseable mocks;
    private UpdateService updateService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeMethod
    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        updateService = new UpdateService(releaseRepository, objectMapper);
        log.info("[测试前置] UpdateService 已初始化 | sampleVersion=1.0.0 | sampleVersionNumber=10000 | minClientVersionNumber=9000");
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
    public void handleGetVersionManifest_versionNotFound() throws Exception {
        String clientVersion = "0.9.0";
        long clientVersionNumber = 9000L;
        log.info("[测试开始] 场景=清单版本不存在 | clientVersion={} | clientVersionNumber={} | 期望retcode={}",
                clientVersion, clientVersionNumber, UpdateRetCode.VERSION_NOT_FOUND);

        when(releaseRepository.findFirstByActiveTrueOrderByVersionNumberDesc()).thenReturn(Optional.empty());
        ProtocolMessage msg = updateService.handleGetVersionManifest(
                GetVersionManifestCsReq.newBuilder()
                        .setClientVersion(clientVersion)
                        .setClientVersionNumber(clientVersionNumber)
                        .build());
        GetVersionManifestScRsp rsp = GetVersionManifestScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=清单版本不存在 | msgId={} | retcode={} | versionCode={} | forceUpdate={}",
                msg.msgId(), rsp.getRetcode(), rsp.getVersionCode(), rsp.getForceUpdate());
        assertThat(msg.msgId()).isEqualTo(MessageId.GET_VERSION_MANIFEST_SC_RSP);
        assertThat(rsp.getRetcode()).isEqualTo(UpdateRetCode.VERSION_NOT_FOUND);
        assertThat(rsp.getForceUpdate()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleGetVersionManifest_manifestInvalid() throws Exception {
        String clientVersion = "0.9.5";
        long clientVersionNumber = 9500L;
        log.info("[测试开始] 场景=清单JSON损坏 | clientVersion={} | clientVersionNumber={} | 期望retcode={}",
                clientVersion, clientVersionNumber, UpdateRetCode.MANIFEST_INVALID);

        ClientVersionRelease broken = new ClientVersionRelease();
        broken.setVersionCode("1.0.0");
        broken.setVersionNumber(10000L);
        broken.setMinClientVersionNumber(9000L);
        broken.setManifest("{not-json");
        broken.setActive(true);
        when(releaseRepository.findFirstByActiveTrueOrderByVersionNumberDesc()).thenReturn(Optional.of(broken));

        GetVersionManifestScRsp rsp = GetVersionManifestScRsp.parseFrom(
                updateService.handleGetVersionManifest(
                        GetVersionManifestCsReq.newBuilder()
                                .setClientVersion(clientVersion)
                                .setClientVersionNumber(clientVersionNumber)
                                .build()).payload());

        log.info("[测试断言] 场景=清单JSON损坏 | retcode={} | versionCode={} | downloadFilesCount={}",
                rsp.getRetcode(), rsp.getVersionCode(), rsp.getDownloadFilesCount());
        assertThat(rsp.getRetcode()).isEqualTo(UpdateRetCode.MANIFEST_INVALID);
        assertThat(rsp.getDownloadFilesCount()).isZero();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleGetVersionManifest_ok() throws Exception {
        String clientVersion = "0.9.5";
        long clientVersionNumber = 9500L;
        String serverVersion = "1.0.0";
        long serverVersionNumber = 10000L;
        log.info("[测试开始] 场景=拉取清单成功 | clientVersion={} | clientVersionNumber={} | serverVersion={} | serverVersionNumber={} | minClient=9000 | 期望retcode={}",
                clientVersion, clientVersionNumber, serverVersion, serverVersionNumber, UpdateRetCode.OK);

        ClientVersionRelease release = release(serverVersion, serverVersionNumber, sampleManifest());
        when(releaseRepository.findFirstByActiveTrueOrderByVersionNumberDesc()).thenReturn(Optional.of(release));

        ProtocolMessage msg = updateService.handleGetVersionManifest(
                GetVersionManifestCsReq.newBuilder()
                        .setClientVersion(clientVersion)
                        .setClientVersionNumber(clientVersionNumber)
                        .build());
        GetVersionManifestScRsp rsp = GetVersionManifestScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=拉取清单成功 | msgId={} | retcode={} | versionCode={} | versionNumber={} | forceUpdate={} | downloadFilesCount={} | deleteFiles={} | baseUrl={} | firstDownloadPath={}",
                msg.msgId(), rsp.getRetcode(), rsp.getVersionCode(), rsp.getVersionNumber(),
                rsp.getForceUpdate(), rsp.getDownloadFilesCount(), rsp.getDeleteFilesList(),
                rsp.getBaseUrl(),
                rsp.getDownloadFilesCount() > 0 ? rsp.getDownloadFiles(0).getPath() : "");
        assertThat(msg.msgId()).isEqualTo(MessageId.GET_VERSION_MANIFEST_SC_RSP);
        assertThat(rsp.getRetcode()).isEqualTo(UpdateRetCode.OK);
        assertThat(rsp.getVersionCode()).isEqualTo(serverVersion);
        assertThat(rsp.getVersionNumber()).isEqualTo(serverVersionNumber);
        assertThat(rsp.getForceUpdate()).isFalse();
        assertThat(rsp.getDownloadFilesCount()).isGreaterThan(0);
        assertThat(rsp.getDeleteFilesList()).contains("assets/ui/old_main.bundle");
        assertThat(rsp.getDownloadFiles(0).getDownloadUrl()).contains("assets/ui/main.bundle");
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleGetVersionManifest_forceUpdate() throws Exception {
        long clientVersionNumber = 8000L;
        long minClientVersionNumber = 9000L;
        log.info("[测试开始] 场景=客户端版本过低需强更 | clientVersionNumber={} | minClientVersionNumber={} | 期望retcode={} | 期望forceUpdate=true",
                clientVersionNumber, minClientVersionNumber, UpdateRetCode.FORCE_UPDATE_REQUIRED);

        ClientVersionRelease release = release("1.0.0", 10000L, sampleManifest());
        when(releaseRepository.findFirstByActiveTrueOrderByVersionNumberDesc()).thenReturn(Optional.of(release));

        GetVersionManifestScRsp rsp = GetVersionManifestScRsp.parseFrom(
                updateService.handleGetVersionManifest(
                        GetVersionManifestCsReq.newBuilder()
                                .setClientVersionNumber(clientVersionNumber)
                                .build()).payload());

        log.info("[测试断言] 场景=客户端版本过低需强更 | retcode={} | forceUpdate={} | versionCode={} | downloadFilesCount={}",
                rsp.getRetcode(), rsp.getForceUpdate(), rsp.getVersionCode(), rsp.getDownloadFilesCount());
        assertThat(rsp.getRetcode()).isEqualTo(UpdateRetCode.FORCE_UPDATE_REQUIRED);
        assertThat(rsp.getForceUpdate()).isTrue();
        assertThat(rsp.getVersionCode()).isEqualTo("1.0.0");
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleVerifyResourceChecksum_versionNotFound() throws Exception {
        String path = "assets/ui/main.bundle";
        String localChecksum = "aaa111";
        log.info("[测试开始] 场景=校验时版本不存在 | path={} | localChecksum={} | 期望retcode={}",
                path, localChecksum, UpdateRetCode.VERSION_NOT_FOUND);

        when(releaseRepository.findFirstByActiveTrueOrderByVersionNumberDesc()).thenReturn(Optional.empty());
        ProtocolMessage msg = updateService.handleVerifyResourceChecksum(
                VerifyResourceChecksumCsReq.newBuilder()
                        .addLocalFiles(FileChecksumEntry.newBuilder().setPath(path).setChecksum(localChecksum).build())
                        .build());
        VerifyResourceChecksumScRsp rsp = VerifyResourceChecksumScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=校验时版本不存在 | msgId={} | retcode={} | invalidFilesCount={} | repairFilesCount={}",
                msg.msgId(), rsp.getRetcode(), rsp.getInvalidFilesCount(), rsp.getRepairFilesCount());
        assertThat(msg.msgId()).isEqualTo(MessageId.VERIFY_RESOURCE_CHECKSUM_SC_RSP);
        assertThat(rsp.getRetcode()).isEqualTo(UpdateRetCode.VERSION_NOT_FOUND);
        assertThat(rsp.getInvalidFilesCount()).isZero();
        assertThat(rsp.getRepairFilesCount()).isZero();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleVerifyResourceChecksum_ok() throws Exception {
        String path = "assets/ui/main.bundle";
        String checksum = "aaa111";
        log.info("[测试开始] 场景=资源校验一致 | path={} | localChecksum={} | expectedChecksum={} | 期望retcode={}",
                path, checksum, checksum, UpdateRetCode.OK);

        ClientVersionRelease release = release("1.0.0", 10000L, sampleManifest());
        when(releaseRepository.findFirstByActiveTrueOrderByVersionNumberDesc()).thenReturn(Optional.of(release));

        VerifyResourceChecksumScRsp rsp = VerifyResourceChecksumScRsp.parseFrom(
                updateService.handleVerifyResourceChecksum(
                        VerifyResourceChecksumCsReq.newBuilder()
                                .addLocalFiles(FileChecksumEntry.newBuilder().setPath(path).setChecksum(checksum).build())
                                .build()).payload());

        log.info("[测试断言] 场景=资源校验一致 | retcode={} | invalidFilesCount={} | repairFilesCount={}",
                rsp.getRetcode(), rsp.getInvalidFilesCount(), rsp.getRepairFilesCount());
        assertThat(rsp.getRetcode()).isEqualTo(UpdateRetCode.OK);
        assertThat(rsp.getInvalidFilesCount()).isZero();
        assertThat(rsp.getRepairFilesCount()).isZero();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleVerifyResourceChecksum_mismatch() throws Exception {
        String path = "assets/ui/main.bundle";
        String expectedChecksum = "aaa111";
        String localChecksum = "bbb222";
        log.info("[测试开始] 场景=资源checksum不一致 | path={} | localChecksum={} | expectedChecksum={} | 期望retcode={}",
                path, localChecksum, expectedChecksum, UpdateRetCode.CHECKSUM_MISMATCH);

        ClientVersionRelease release = release("1.0.0", 10000L, sampleManifest());
        when(releaseRepository.findFirstByActiveTrueOrderByVersionNumberDesc()).thenReturn(Optional.of(release));

        VerifyResourceChecksumScRsp rsp = VerifyResourceChecksumScRsp.parseFrom(
                updateService.handleVerifyResourceChecksum(
                        VerifyResourceChecksumCsReq.newBuilder()
                                .addLocalFiles(FileChecksumEntry.newBuilder().setPath(path).setChecksum(localChecksum).build())
                                .build()).payload());

        log.info("[测试断言] 场景=资源checksum不一致 | retcode={} | invalidFiles={} | repairFilesCount={} | repairPath={} | repairChecksum={} | repairUrl={}",
                rsp.getRetcode(), rsp.getInvalidFilesList(), rsp.getRepairFilesCount(),
                rsp.getRepairFilesCount() > 0 ? rsp.getRepairFiles(0).getPath() : "",
                rsp.getRepairFilesCount() > 0 ? rsp.getRepairFiles(0).getChecksum() : "",
                rsp.getRepairFilesCount() > 0 ? rsp.getRepairFiles(0).getDownloadUrl() : "");
        assertThat(rsp.getRetcode()).isEqualTo(UpdateRetCode.CHECKSUM_MISMATCH);
        assertThat(rsp.getInvalidFilesList()).containsExactly(path);
        assertThat(rsp.getRepairFilesCount()).isEqualTo(1);
        assertThat(rsp.getRepairFiles(0).getPath()).isEqualTo(path);
        assertThat(rsp.getRepairFiles(0).getChecksum()).isEqualTo(expectedChecksum);
        assertThat(rsp.getRepairFiles(0).getDownloadUrl()).isEqualTo("https://cdn.example.com/assets/ui/main.bundle");
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleVerifyResourceChecksum_manifestInvalid() throws Exception {
        String path = "assets/ui/main.bundle";
        String localChecksum = "aaa111";
        log.info("[测试开始] 场景=校验时清单损坏 | path={} | localChecksum={} | 期望retcode={}",
                path, localChecksum, UpdateRetCode.MANIFEST_INVALID);

        ClientVersionRelease broken = new ClientVersionRelease();
        broken.setVersionCode("1.0.0");
        broken.setVersionNumber(10000L);
        broken.setManifest("%%%");
        broken.setActive(true);
        when(releaseRepository.findFirstByActiveTrueOrderByVersionNumberDesc()).thenReturn(Optional.of(broken));

        VerifyResourceChecksumScRsp rsp = VerifyResourceChecksumScRsp.parseFrom(
                updateService.handleVerifyResourceChecksum(
                        VerifyResourceChecksumCsReq.newBuilder()
                                .addLocalFiles(FileChecksumEntry.newBuilder().setPath(path).setChecksum(localChecksum).build())
                                .build()).payload());

        log.info("[测试断言] 场景=校验时清单损坏 | retcode={} | invalidFilesCount={} | repairFilesCount={}",
                rsp.getRetcode(), rsp.getInvalidFilesCount(), rsp.getRepairFilesCount());
        assertThat(rsp.getRetcode()).isEqualTo(UpdateRetCode.MANIFEST_INVALID);
    }

    private ClientVersionRelease release(String code, long number, VersionManifestPayload manifest) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        manifest.manifestChecksum = ManifestChecksumUtil.sha256Hex(mapper.writeValueAsString(manifest));
        ClientVersionRelease release = new ClientVersionRelease();
        release.setVersionCode(code);
        release.setVersionNumber(number);
        release.setMinClientVersionNumber(manifest.minClientVersionNumber);
        release.setManifest(mapper.writeValueAsString(manifest));
        release.setActive(true);
        return release;
    }

    private VersionManifestPayload sampleManifest() {
        VersionManifestPayload manifest = new VersionManifestPayload();
        manifest.versionCode = "1.0.0";
        manifest.versionNumber = 10000;
        manifest.minClientVersionNumber = 9000;
        manifest.baseUrl = "https://cdn.example.com/";
        manifest.patchToolVersion = "1.0.0";
        manifest.resourcePacks = List.of(file("assets/ui/main.bundle", "aaa111"));
        manifest.deleteFiles = List.of("assets/ui/old_main.bundle");
        return manifest;
    }

    private ResourceFileEntry file(String path, String checksum) {
        ResourceFileEntry e = new ResourceFileEntry();
        e.path = path;
        e.checksum = checksum;
        e.size = 100;
        e.packType = "resource";
        return e;
    }
}
