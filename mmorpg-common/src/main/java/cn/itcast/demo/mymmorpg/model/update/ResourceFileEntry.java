package cn.itcast.demo.mymmorpg.model.update; // 更新清单中的单个资源文件条目模型

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 忽略未知字段，保证清单扩展时兼容

/**
 * 清单中的单个资源/配置文件条目。
 */
@JsonIgnoreProperties(ignoreUnknown = true) // 允许导入更高版本清单时跳过额外字段
public class ResourceFileEntry { // 描述一个需要下载、校验或修复的资源文件

    /** 客户端相对路径。 */
    public String path = ""; // 资源在客户端目录中的相对路径
    /** SHA-256 十六进制小写。 */
    public String checksum = ""; // 服务端期望的文件摘要，用于完整性校验
    /** 文件大小（字节）。 */
    public long size; // 文件大小用于客户端下载进度展示
    /** 可选独立下载 URL，空则拼接 baseUrl + path。 */
    public String downloadUrl = ""; // 单文件专属下载地址，空值时由 baseUrl 与 path 拼接
    /** 资源类型：resource、audio、config。 */
    public String packType = "resource"; // 资源分类，便于客户端区分更新策略
}
