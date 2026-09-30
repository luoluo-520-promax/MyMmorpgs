package cn.itcast.demo.mymmorpg.skin;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SkinImportValidatorTest {

    @Test
    public void acceptsValidCatalog() {
        SkinConfig def = skin(1001, 0, true);
        SkinConfig paid = skin(1003, 71003, false);
        assertThat(SkinImportValidator.validate(List.of(def, paid))).isEmpty();
    }

    @Test
    public void rejectsPaidSkinWithoutItemId() {
        SkinConfig def = skin(1001, 0, true);
        SkinConfig paid = skin(1003, 0, false);
        assertThat(SkinImportValidator.validate(List.of(def, paid)))
                .anyMatch(e -> e.contains("itemId"));
    }

    @Test
    public void rejectsDefaultWithItemId() {
        SkinConfig def = skin(1001, 71001, true);
        assertThat(SkinImportValidator.validate(List.of(def)))
                .anyMatch(e -> e.contains("默认皮"));
    }

    private static SkinConfig skin(int skinId, int itemId, boolean isDefault) {
        SkinConfig s = new SkinConfig();
        s.setSkinId(skinId);
        s.setItemId(itemId);
        s.setName("skin-" + skinId);
        s.setRarity(1);
        s.setResourceKey("skin/" + skinId);
        s.setPreviewIcon("ui/skin/" + skinId);
        s.setDefault(isDefault);
        s.setEnabled(true);
        return s;
    }
}
