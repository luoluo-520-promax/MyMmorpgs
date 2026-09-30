package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ShopImportClient;
import cn.itcast.demo.mymmorpg.service.ai.AiActivityDraftStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AdminAiShopServiceTest {

    private AdminPermissionService permissionService;
    private ShopImportClient shopImportClient;
    private AdminAiShopService service;

    @BeforeMethod
    public void setUp() {
        permissionService = mock(AdminPermissionService.class);
        AdminOperationLogService logService = mock(AdminOperationLogService.class);
        shopImportClient = mock(ShopImportClient.class);
        when(shopImportClient.importJson(eq(true), anyString())).thenReturn(Map.of("ok", true, "count", 1));
        when(shopImportClient.importJson(eq(false), anyString())).thenReturn(Map.of("ok", true, "count", 1));
        service = new AdminAiShopService(permissionService, logService, shopImportClient,
                new AiActivityDraftStore(), new ObjectMapper());
    }

    @Test
    public void draft_deniedWithoutPermission() {
        when(permissionService.hasPermission(1L, AdminAiShopService.PERM_AI_SHOP)).thenReturn(false);
        assertThatThrownBy(() -> service.draft(1L, "做一个首充礼包", "shop_pack", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void draft_shopPack_returnsConfirmToken() {
        when(permissionService.hasPermission(1L, AdminAiShopService.PERM_AI_SHOP)).thenReturn(true);
        Map<String, Object> result = service.draft(1L, "首充超值礼包", "shop_pack", true);
        assertThat(result.get("status")).isEqualTo("OK");
        assertThat(result.get("template")).isEqualTo("first_charge");
        assertThat(result.get("confirmToken")).isNotNull();
        assertThat(result.get("document")).isNotNull();
    }
}
