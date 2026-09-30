package cn.itcast.demo.mymmorpg.model.admin;

/**
 * 配置导入结果摘要，供 admin / internal API 返回。
 */
public class ImportResultItem {

    private Long id;
    private String code;
    private Integer type;
    private String versionCode;
    private Long versionNumber;

    public ImportResultItem() {
    }

    public static ImportResultItem activity(Long id, Integer type) {
        ImportResultItem item = new ImportResultItem();
        item.id = id;
        item.type = type;
        return item;
    }

    public static ImportResultItem manifest(String versionCode, Long versionNumber) {
        ImportResultItem item = new ImportResultItem();
        item.versionCode = versionCode;
        item.versionNumber = versionNumber;
        return item;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(String versionCode) {
        this.versionCode = versionCode;
    }

    public Long getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Long versionNumber) {
        this.versionNumber = versionNumber;
    }
}
