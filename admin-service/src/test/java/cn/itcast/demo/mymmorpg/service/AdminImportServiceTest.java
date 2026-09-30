package cn.itcast.demo.mymmorpg.service;



import cn.itcast.demo.mymmorpg.client.ActivityImportClient;
import cn.itcast.demo.mymmorpg.client.QuestImportClient;
import cn.itcast.demo.mymmorpg.client.ShopImportClient;
import cn.itcast.demo.mymmorpg.client.UpdateImportClient;
import cn.itcast.demo.mymmorpg.model.admin.ImportResultItem;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AdminImportServiceTest {

    private AdminPermissionService permissionService;
    private AdminOperationLogService operationLogService;
    private ActivityImportClient activityImportClient;
    private UpdateImportClient updateImportClient;
    private ShopImportClient shopImportClient;
    private QuestImportClient questImportClient;
    private AdminImportService service;

    @BeforeMethod
    public void setUp() {
        permissionService = mock(AdminPermissionService.class);
        operationLogService = mock(AdminOperationLogService.class);
        activityImportClient = mock(ActivityImportClient.class);
        updateImportClient = mock(UpdateImportClient.class);
        shopImportClient = mock(ShopImportClient.class);
        questImportClient = mock(QuestImportClient.class);
        ConfigStagingValidationHook stagingHook = mock(ConfigStagingValidationHook.class);
        when(stagingHook.validateBeforeApply(anyString(), anyString()))
                .thenReturn(java.util.Map.of("ok", true));
        service = new AdminImportService(
                permissionService, operationLogService, activityImportClient, updateImportClient,
                shopImportClient, questImportClient, new ConfigPublishAuditService(), stagingHook,
                new ReferenceIntegrityValidator(), new com.fasterxml.jackson.databind.ObjectMapper());
    }



    @Test

    public void importActivitiesFromJson_deniedWithoutPermission() {

        when(permissionService.hasPermission(1L, AdminImportService.PERM_IMPORT_ACTIVITY)).thenReturn(false);

        assertThatThrownBy(() -> service.importActivitiesFromJson(1L, "{}"))

                .isInstanceOf(IllegalStateException.class);

    }



    @Test

    public void importActivitiesFromJson_recordsOperationLog() {

        ImportResultItem item = ImportResultItem.activity(10L, 1);

        when(permissionService.hasPermission(1L, AdminImportService.PERM_IMPORT_ACTIVITY)).thenReturn(true);

        when(activityImportClient.importJson("{}")).thenReturn(List.of(item));



        List<ImportResultItem> result = service.importActivitiesFromJson(1L, "{}");



        assertThat(result).hasSize(1);

        verify(operationLogService).record(eq(1L), eq("IMPORT_ACTIVITY_JSON"), eq(null), anyString());

    }



    @Test

    public void importManifestFromJson_recordsOperationLog() {

        ImportResultItem item = ImportResultItem.manifest("2.0.0", 20000L);

        when(permissionService.hasPermission(2L, AdminImportService.PERM_IMPORT_MANIFEST)).thenReturn(true);

        when(updateImportClient.importJson("{}")).thenReturn(item);



        ImportResultItem result = service.importManifestFromJson(2L, "{}");



        assertThat(result.getVersionCode()).isEqualTo("2.0.0");

        verify(operationLogService).record(eq(2L), eq("IMPORT_MANIFEST_JSON"), eq(20000L), anyString());

    }

}

