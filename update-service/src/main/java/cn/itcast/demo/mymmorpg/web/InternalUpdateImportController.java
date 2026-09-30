package cn.itcast.demo.mymmorpg.web; // 更新清单导入接口层，提供 JSON 导入入口给运维或管理端

import cn.itcast.demo.mymmorpg.entity.ClientVersionRelease; // 导入结果实体，用于返回 versionCode 和 versionNumber
import cn.itcast.demo.mymmorpg.model.admin.ImportResultItem; // 通用导入结果 DTO，用于统一返回导入成功信息
import cn.itcast.demo.mymmorpg.service.VersionManifestImportService; // JSON 导入服务，负责清单 upsert 与 checksum 补齐
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 仅在 update-service 场景启用导入接口
import org.springframework.http.MediaType; // 指定接口接受的内容类型为 JSON
import org.springframework.web.bind.annotation.PostMapping; // 暴露 POST 导入接口
import org.springframework.web.bind.annotation.RequestBody; // 从请求体读取原始 JSON 文本
import org.springframework.web.bind.annotation.RequestMapping; // 统一内部导入路由前缀
import org.springframework.web.bind.annotation.RestController; // 声明为 REST 控制器

/**
 * 版本清单 JSON 导入入口
 */
@RestController // 将导入能力暴露为 HTTP 接口，供内部管理流程调用
@RequestMapping("/internal/update/import") // 版本清单导入的内部统一路由前缀
@ConditionalOnProperty(name = "spring.application.name", havingValue = "update-service") // 仅在更新服务进程中加载该控制器
public class InternalUpdateImportController {

    private final VersionManifestImportService importService; // 负责清单导入与持久化的服务

    public InternalUpdateImportController(VersionManifestImportService importService) {
        this.importService = importService; // 注入导入服务，供 JSON 导入接口使用
    }

    /**
     * 接收 JSON 清单文本并执行导入
     * @param json
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ImportResultItem importJson(@RequestBody String json) throws Exception { // 直接接收原始 JSON 字符串，保持导入内容完整性
        ClientVersionRelease release = importService.importFromJson(json); // 解析、校验并保存版本清单
        return ImportResultItem.manifest(release.getVersionCode(), release.getVersionNumber()); // 返回导入后的版本信息给调用方确认
    }
}
