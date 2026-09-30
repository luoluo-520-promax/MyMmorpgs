package cn.itcast.demo.mymmorpg.model.update; // 更新清单载体模型，承载客户端版本与资源列表信息

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 忽略未知字段，保证向前向后兼容

import java.util.ArrayList; // 用于初始化默认列表，避免空指针
import java.util.List; // 用于表示多类资源文件和删除文件集合

/**
 * 客户端版本清单：游戏资源包、音频语言包、配置文件与删除列表。
 */
@JsonIgnoreProperties(ignoreUnknown = true) // 允许导入更高版本清单时忽略新增字段
public class VersionManifestPayload { // 更新服务和客户端之间交换的版本清单对象

    /** 版本号字符串，如 1.2.0。 */
    public String versionCode = "0.0.0"; // 人类可读的版本号，供展示与日志使用
    /** 数值版本号，便于比较，如 10200。 */
    public long versionNumber; // 用于大小比较和补丁决策的数值版本
    /** 低于此版本号强制整包更新。 */
    public long minClientVersionNumber; // 小于该值时客户端必须执行强更
    /** 低于此版本号建议更新（不强制）。 */
    public long optionalClientVersionNumber;
    /** CDN/资源服根 URL。 */
    public String baseUrl = ""; // 下载资源时使用的根地址前缀
    /** 补丁/校验工具版本。 */
    public String patchToolVersion = "1.0.0"; // 客户端补丁工具版本，供下载器或修补器判断兼容性
    /** 游戏资源包文件列表。 */
    public List<ResourceFileEntry> resourcePacks = new ArrayList<>(); // 主资源包列表，参与补丁下载计划
    /** 音频/语言包文件列表。 */
    public List<ResourceFileEntry> audioLanguagePacks = new ArrayList<>(); // 音频和语言资源列表，参与补丁下载计划
    /** 配置文件列表（含活动配置等）。 */
    public List<ResourceFileEntry> configFiles = new ArrayList<>(); // 配置类文件列表，参与补丁下载与校验
    /** 升级后需删除的本地文件相对路径。 */
    public List<String> deleteFiles = new ArrayList<>(); // 客户端升级后需要清理的旧文件路径
    /** 清单内容 SHA-256（不含本字段与签名字段），客户端用于完整性校验。 */
    public String manifestChecksum = ""; // 清单自身摘要，供客户端判断服务端版本文件是否被篡改
    /** 清单签名（HMAC-SHA256 hex），防中间人篡改 Manifest。 */
    public String manifestSignature = "";
    /** 签名密钥标识。 */
    public String signingKeyId = "";
    /** 是否支持 HTTP Range 断点续传。 */
    public boolean supportsRange = true;
    /** 灰度账号白名单；空表示全量开放。 */
    public List<Long> grayAccountIds = new ArrayList<>();
    /** 灰度百分比 0-100；与白名单同时生效时白名单优先。 */
    public int grayPercent = 100;
}
