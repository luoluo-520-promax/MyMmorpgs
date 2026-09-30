package cn.itcast.demo.mymmorpg.skin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SkinConfig {

    private int skinId;
    private int itemId;
    private int avatarId;
    private String name = "";
    private int rarity = 1;
    private String resourceKey = "";
    private String previewIcon = "";
    private String obtainTips = "";
    @JsonProperty("isDefault")
    private boolean defaultSkin;
    private boolean enabled = true;

    public int skinId() {
        return skinId;
    }

    public int getSkinId() {
        return skinId;
    }

    public void setSkinId(int skinId) {
        this.skinId = skinId;
    }

    public int itemId() {
        return itemId;
    }

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public int getAvatarId() {
        return avatarId;
    }

    public void setAvatarId(int avatarId) {
        this.avatarId = avatarId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public int getRarity() {
        return rarity;
    }

    public void setRarity(int rarity) {
        this.rarity = rarity;
    }

    public String getResourceKey() {
        return resourceKey;
    }

    public void setResourceKey(String resourceKey) {
        this.resourceKey = resourceKey == null ? "" : resourceKey;
    }

    public String getPreviewIcon() {
        return previewIcon;
    }

    public void setPreviewIcon(String previewIcon) {
        this.previewIcon = previewIcon == null ? "" : previewIcon;
    }

    public String getObtainTips() {
        return obtainTips;
    }

    public void setObtainTips(String obtainTips) {
        this.obtainTips = obtainTips == null ? "" : obtainTips;
    }

    public boolean isDefault() {
        return defaultSkin;
    }

    public void setDefault(boolean defaultSkin) {
        this.defaultSkin = defaultSkin;
    }

    @JsonProperty("isDefault")
    public void setIsDefault(boolean defaultSkin) {
        this.defaultSkin = defaultSkin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
