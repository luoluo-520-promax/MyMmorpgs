package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.ActivityImportDocument;
import cn.itcast.demo.mymmorpg.model.ActivityImportValidator;
import cn.itcast.demo.mymmorpg.model.RewardTierPayload;
import cn.itcast.demo.mymmorpg.model.admin.ActivityImportDryRunResult;
import cn.itcast.demo.mymmorpg.repository.ActivityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class ActivityImportValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void fullSample_passesValidation() throws Exception {
        String json = readResource("/import/activity_full.json");
        ActivityImportService service = new ActivityImportService(mock(ActivityRepository.class), objectMapper);
        ActivityImportDryRunResult result = service.dryRunFromJson(json);
        assertThat(result.isValid()).isTrue();
        assertThat(result.getDocumentCount()).isEqualTo(1);
        assertThat(result.getUpsertIds()).containsExactly(9001L);
    }

    @Test
    public void missingType_failsWithChineseMessage() {
        ActivityImportDocument doc = new ActivityImportDocument();
        doc.name = "测试";
        doc.rewardMethod = 1;
        RewardTierPayload tier = new RewardTierPayload();
        tier.index = 1;
        tier.itemId = 1;
        tier.count = 1;
        doc.rewardTiers = java.util.List.of(tier);

        ActivityImportValidator.ValidationOutcome outcome = ActivityImportValidator.validate(doc);
        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.errors().get(0)).contains("type");
    }

    @Test
    public void importFromJson_rejectsInvalidDocument() {
        ActivityImportService service = new ActivityImportService(mock(ActivityRepository.class), objectMapper);
        assertThatThrownBy(() -> service.importFromJson("{\"name\":\"x\",\"rewardMethod\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    private static String readResource(String path) throws Exception {
        try (InputStream in = ActivityImportValidatorTest.class.getResourceAsStream(path)) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
