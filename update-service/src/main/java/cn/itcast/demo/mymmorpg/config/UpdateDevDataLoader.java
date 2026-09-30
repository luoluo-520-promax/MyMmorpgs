package cn.itcast.demo.mymmorpg.config; // update-service 开发环境种子数据加载配置，写入示例版本清单

import cn.itcast.demo.mymmorpg.entity.ClientVersionRelease; // 版本发布实体，用于写入示例清单记录
import cn.itcast.demo.mymmorpg.model.update.ManifestChecksumUtil; // SHA-256 工具，用于生成示例 manifest checksum
import cn.itcast.demo.mymmorpg.model.update.ResourceFileEntry; // 资源文件条目，用于构造示例清单
import cn.itcast.demo.mymmorpg.model.update.VersionManifestPayload; // 版本清单载体，用于组织示例版本数据
import cn.itcast.demo.mymmorpg.repository.ClientVersionReleaseRepository; // 版本发布仓库，用于判断是否需要初始化数据
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 工具，用于把示例清单序列化后保存
import org.slf4j.Logger; // 日志接口，用于记录初始化结果
import org.slf4j.LoggerFactory; // 日志工厂，用于创建本类 logger
import org.springframework.boot.CommandLineRunner; // 应用启动后执行一次的初始化入口
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean; // 仅在版本仓库存在时才加载示例数据
import org.springframework.context.annotation.Profile; // 仅在 dev 环境启用示例数据
import org.springframework.stereotype.Component; // 声明为 Spring 组件

import java.util.List; // 用于组织示例资源条目列表

/**
 * 开发环境写入示例客户端版本清单。
 * <p>
 * 文件维护说明：本类仅在 dev profile 下工作，用于给 update-service 提供一条可用的示例版本记录，
 * 方便本地联调、接口调试和客户端更新流程验证。示例数据只负责初始化，不参与线上业务逻辑。
 */
@Component // 作为启动时自动执行的开发辅助组件
@Profile("dev") // 仅在开发环境加载，避免污染生产数据
@ConditionalOnBean(ClientVersionReleaseRepository.class) // 确保仓库可用后再执行种子数据写入
public class UpdateDevDataLoader implements CommandLineRunner { // 启动时自动执行一次的开发数据加载器

    private static final Logger log = LoggerFactory.getLogger(UpdateDevDataLoader.class); // 记录示例数据初始化过程

    private final ClientVersionReleaseRepository releaseRepository; // 版本清单仓库，用于判断是否已有数据
    private final ObjectMapper objectMapper; // JSON 序列化工具，用于写入 manifest 字段

    public UpdateDevDataLoader(ClientVersionReleaseRepository releaseRepository, ObjectMapper objectMapper) {
        this.releaseRepository = releaseRepository; // 保存仓库引用，供启动时检查数据是否存在
        this.objectMapper = objectMapper; // 保存 JSON 工具引用，供示例清单序列化使用
    }

    /**
     * 应用启动后执行一次，写入示例版本数据
     * @param args
     * @throws Exception
     */
    @Override
    public void run(String... args) throws Exception {
        if (releaseRepository.count() > 0) { // 数据库已有版本记录时不重复写入，避免覆盖人工导入数据
            return; // 直接跳过初始化，保持现有版本清单不变
        }
        VersionManifestPayload manifest = sampleManifest(); // 构造一份完整的示例版本清单
        manifest.manifestChecksum = ManifestChecksumUtil.sha256Hex(objectMapper.writeValueAsString(manifest)); // 计算示例清单摘要，供客户端验证完整性

        ClientVersionRelease release = new ClientVersionRelease(); // 创建新的版本发布实体
        release.setVersionCode(manifest.versionCode); // 写入版本字符串，便于后续唯一查询
        release.setVersionNumber(manifest.versionNumber); // 写入数值版本，用于客户端比较升级
        release.setMinClientVersionNumber(manifest.minClientVersionNumber); // 写入最小兼容版本，用于触发强更
        release.setActive(true); // 示例版本默认激活，确保本地联调可直接查询到
        release.setManifest(objectMapper.writeValueAsString(manifest)); // 保存序列化后的完整清单 JSON
        releaseRepository.save(release); // 将示例版本写入数据库
        log.info("已写入示例 client_version_release {}，manifestChecksum={}", // 输出初始化结果，便于开发时确认数据已就绪
                manifest.versionCode, manifest.manifestChecksum); // 同时记录版本号与 checksum，便于排查更新接口返回值
    }

    /**
     * 构造一份可用于联调的示例版本清单
     * @return
     */
    private static VersionManifestPayload sampleManifest() {
        VersionManifestPayload manifest = new VersionManifestPayload(); // 创建清单对象，逐项填充示例资源
        manifest.versionCode = "1.0.0"; // 示例版本字符串，便于客户端直观看到版本号
        manifest.versionNumber = 10000; // 示例数值版本，用于演示版本比较逻辑
        manifest.minClientVersionNumber = 9000; // 示例最小兼容版本，用于演示强更判断
        manifest.baseUrl = "https://cdn.example.com/mmorpg/1.0.0/"; // 示例资源根地址，方便验证下载 URL 拼接
        manifest.patchToolVersion = "1.0.0"; // 示例补丁工具版本，供客户端或运维端展示
        manifest.resourcePacks = List.of( // 示例主资源包列表，模拟客户端更新内容
                entry("assets/ui/main.bundle", "aaa111", 1024000, "resource"), // 主界面资源包，演示新增下载项
                entry("assets/scene/starter.bundle", "bbb222", 2048000, "resource")); // 场景资源包，演示多文件补丁计划
        manifest.audioLanguagePacks = List.of( // 示例音频/语言包列表，模拟多语言资源更新
                entry("audio/zh-CN/voice.pak", "ccc333", 512000, "audio"), // 中文语音包，演示按类型区分下载
                entry("audio/en-US/voice.pak", "ddd444", 512000, "audio")); // 英文语音包，演示国际化资源下发
        manifest.configFiles = List.of( // 示例配置文件列表，模拟活动配置和客户端配置更新
                entry("config/activity.json", "eee555", 8192, "config"), // 活动配置文件，演示配置类更新内容
                entry("config/client_settings.json", "fff666", 4096, "config")); // 客户端设置文件，演示轻量配置补丁
        manifest.deleteFiles = List.of("assets/ui/old_main.bundle", "config/legacy_activity.json"); // 示例旧文件删除列表，用于演示清理逻辑
        return manifest; // 返回完整示例清单，供启动时写入数据库
    }

    /**
     * 创建单个资源文件条目的辅助方法
     * @param path
     * @param checksum
     * @param size
     * @param packType
     * @return
     */
    private static ResourceFileEntry entry(String path, String checksum, long size, String packType) {
        ResourceFileEntry e = new ResourceFileEntry(); // 新建资源条目对象，便于逐项赋值
        e.path = path; // 设置客户端相对路径，作为下载和校验主键
        e.checksum = checksum; // 设置示例 checksum，便于客户端比对
        e.size = size; // 设置文件大小，便于进度展示
        e.packType = packType; // 设置资源类型，便于客户端分类处理
        return e; // 返回构造好的资源条目
    }
}
