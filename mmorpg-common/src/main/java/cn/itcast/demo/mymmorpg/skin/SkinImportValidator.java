package cn.itcast.demo.mymmorpg.skin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 皮肤配置导入校验（对齐「角色皮肤设计与商店上架方案」）。
 */
public final class SkinImportValidator {

    private SkinImportValidator() {
    }

    public static List<String> validate(List<SkinConfig> skins) {
        List<String> errors = new ArrayList<>();
        if (skins == null || skins.isEmpty()) {
            errors.add("skins 不能为空");
            return errors;
        }
        Set<Integer> skinIds = new HashSet<>();
        Set<Integer> itemIds = new HashSet<>();
        boolean hasDefault = false;
        for (int i = 0; i < skins.size(); i++) {
            SkinConfig s = skins.get(i);
            String path = "skins[" + i + "]";
            if (s == null) {
                errors.add(path + " 为空");
                continue;
            }
            if (s.skinId() <= 0) {
                errors.add(path + ".skinId 必须 > 0");
            } else if (!skinIds.add(s.skinId())) {
                errors.add(path + ".skinId 重复: " + s.skinId());
            }
            if (s.getName() == null || s.getName().isBlank()) {
                errors.add(path + ".name 不能为空");
            }
            if (s.getResourceKey() == null || s.getResourceKey().isBlank()) {
                errors.add(path + ".resourceKey 不能为空");
            }
            if (s.getPreviewIcon() == null || s.getPreviewIcon().isBlank()) {
                errors.add(path + ".previewIcon 不能为空");
            }
            if (s.getRarity() < 1) {
                errors.add(path + ".rarity 必须 >= 1");
            }
            if (s.isDefault()) {
                hasDefault = true;
                if (s.itemId() > 0) {
                    errors.add(path + " 默认皮不可配置 itemId（不可上架）");
                }
            } else if (s.itemId() <= 0) {
                errors.add(path + ".itemId 付费皮必须 > 0");
            } else if (!itemIds.add(s.itemId())) {
                errors.add(path + ".itemId 重复: " + s.itemId());
            }
        }
        if (!hasDefault) {
            errors.add("至少需要一条 isDefault=true 的默认皮肤");
        }
        return errors;
    }
}
