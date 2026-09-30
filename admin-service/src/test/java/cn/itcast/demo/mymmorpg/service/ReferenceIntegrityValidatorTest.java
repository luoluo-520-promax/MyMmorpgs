package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 配置依赖图校验：缺失 ID 列表与导入阻断。
 */
public class ReferenceIntegrityValidatorTest {

    private final ReferenceIntegrityValidator validator = new ReferenceIntegrityValidator();

    @Test
    public void validate_returnsMissingIdLists() {
        Map<String, Object> result = validator.validate(
                List.of(
                        Map.of("type", "item", "id", "1001", "path", "$.reward.itemId"),
                        Map.of("type", "quest", "id", "2001", "path", "$.preQuestId"),
                        Map.of("type", "shop_product", "id", "P1", "path", "$.productId")),
                Set.of("1002"),
                Set.of("2002"),
                Set.of("P2"));

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat(result.get("missingItemIds")).isEqualTo(List.of("1001"));
        assertThat(result.get("missingQuestIds")).isEqualTo(List.of("2001"));
        assertThat(result.get("missingShopProductIds")).isEqualTo(List.of("P1"));
        assertThat((List<?>) result.get("issues")).hasSize(3);
    }

    @Test
    public void extractRefsFromJsonTree_walksNestedRewards() {
        Map<String, Object> tree = Map.of(
                "activities", List.of(
                        Map.of("activityId", 1, "rewardItemId", 55, "preQuestId", 88)));
        List<Map<String, Object>> refs = validator.extractRefsFromJsonTree(tree, "$");
        assertThat(refs.stream().anyMatch(r -> "item".equals(r.get("type")) && "55".equals(r.get("id")))).isTrue();
        assertThat(refs.stream().anyMatch(r -> "quest".equals(r.get("type")) && "88".equals(r.get("id")))).isTrue();
    }

    @Test
    public void adminImport_blocksWhenKnownCatalogMissingIds() throws Exception {
        AdminPermissionService permissionService = org.mockito.Mockito.mock(AdminPermissionService.class);
        org.mockito.Mockito.when(permissionService.hasPermission(1L, AdminImportService.PERM_IMPORT_ACTIVITY))
                .thenReturn(true);
        ConfigStagingValidationHook staging = org.mockito.Mockito.mock(ConfigStagingValidationHook.class);
        org.mockito.Mockito.when(staging.validateBeforeApply(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Map.of("ok", true));

        AdminImportService service = new AdminImportService(
                permissionService,
                org.mockito.Mockito.mock(AdminOperationLogService.class),
                org.mockito.Mockito.mock(cn.itcast.demo.mymmorpg.client.ActivityImportClient.class),
                org.mockito.Mockito.mock(cn.itcast.demo.mymmorpg.client.UpdateImportClient.class),
                org.mockito.Mockito.mock(cn.itcast.demo.mymmorpg.client.ShopImportClient.class),
                org.mockito.Mockito.mock(cn.itcast.demo.mymmorpg.client.QuestImportClient.class),
                new ConfigPublishAuditService(),
                staging,
                validator,
                new com.fasterxml.jackson.databind.ObjectMapper());

        String json = "[{\"rewardItemId\":9999}]";
        assertThatThrownBy(() -> service.importActivitiesFromJson(
                1L, json, Set.of("1"), Set.of(), Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missingItemIds")
                .hasMessageContaining("9999");
    }
}
