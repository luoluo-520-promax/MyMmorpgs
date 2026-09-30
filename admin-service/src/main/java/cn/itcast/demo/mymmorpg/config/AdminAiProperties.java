package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理端 AI 助手配置（模型网关密钥仅通过环境变量注入）。
 */
@ConfigurationProperties(prefix = "admin.ai")
public class AdminAiProperties {

    /** 是否启用外部大模型；false 时仅使用本地模板生成。 */
    private boolean enabled = false;
    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-4o-mini";
    /** 单次请求超时（建议 3s 量级；默认 3000）。 */
    private long timeoutMs = 3_000L;
    /** 失败重试次数（不含首次）。 */
    private int maxRetries = 2;
    /** Token 预算（窗口内累计，超预算自动降级模板）。 */
    private long tokenBudget = 1_000_000L;
    /** 提示词版本，写入审计与 draft 响应。 */
    private String promptVersion = "activity-copilot-v1";
    /** 二次确认 token 有效期（毫秒）。 */
    private long confirmTtlMs = 30 * 60 * 1000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getTokenBudget() {
        return tokenBudget;
    }

    public void setTokenBudget(long tokenBudget) {
        this.tokenBudget = tokenBudget;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public long getConfirmTtlMs() {
        return confirmTtlMs;
    }

    public void setConfirmTtlMs(long confirmTtlMs) {
        this.confirmTtlMs = confirmTtlMs;
    }
}
