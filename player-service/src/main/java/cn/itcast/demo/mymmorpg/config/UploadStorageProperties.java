package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "game.upload")
public class UploadStorageProperties {

    /** 分片与合并文件的本地存储目录 */
    private String storageDir = "./data/uploads";

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }
}
