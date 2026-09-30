package cn.itcast.demo.mymmorpg.config;



import org.springframework.boot.context.properties.ConfigurationProperties;



import java.util.ArrayList;

import java.util.List;



@ConfigurationProperties(prefix = "admin.http")

public class AdminHttpProperties {



    private boolean ipWhitelistEnabled = true;

    private List<String> ips = new ArrayList<>(List.of("127.0.0.1"));

    /** 是否启用 HMAC / API Key 鉴权（开发环境可关闭） */

    private boolean authEnabled = true;

    /** HMAC 共享密钥，生产环境必须通过环境变量注入 */

    private String hmacSecret = "";

    /** 可选 API Key，与 HMAC 二选一或同时配置（网关/脚本场景） */

    private String apiKey = "";



    public boolean isIpWhitelistEnabled() {

        return ipWhitelistEnabled;

    }



    public void setIpWhitelistEnabled(boolean ipWhitelistEnabled) {

        this.ipWhitelistEnabled = ipWhitelistEnabled;

    }



    public List<String> getIps() {

        return ips;

    }



    public void setIps(List<String> ips) {

        this.ips = ips;

    }



    public boolean isAuthEnabled() {

        return authEnabled;

    }



    public void setAuthEnabled(boolean authEnabled) {

        this.authEnabled = authEnabled;

    }



    public String getHmacSecret() {

        return hmacSecret;

    }



    public void setHmacSecret(String hmacSecret) {

        this.hmacSecret = hmacSecret;

    }



    public String getApiKey() {

        return apiKey;

    }



    public void setApiKey(String apiKey) {

        this.apiKey = apiKey;

    }

}


