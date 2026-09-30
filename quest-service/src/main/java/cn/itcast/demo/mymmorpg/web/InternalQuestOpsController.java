package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.quest.QuestConfigService;
import cn.itcast.demo.mymmorpg.quest.QuestCsvImporter;
import cn.itcast.demo.mymmorpg.quest.QuestImportValidator;
import cn.itcast.demo.mymmorpg.quest.QuestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务配置热更与导入：{@code /internal/quest/ops/*}
 */
@RestController
@RequestMapping("/internal/quest/ops")
public class InternalQuestOpsController {

    private final QuestConfigService questConfigService;
    private final ObjectMapper objectMapper;

    public InternalQuestOpsController(QuestConfigService questConfigService, ObjectMapper objectMapper) {
        this.questConfigService = questConfigService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        boolean ok = questConfigService.reload();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", ok);
        body.put("version", questConfigService.version());
        body.put("count", questConfigService.listAll().size());
        return body;
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importJson(
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestBody String body) throws Exception {
        QuestConfigService.QuestsFile file = objectMapper.readValue(body, QuestConfigService.QuestsFile.class);
        List<QuestTemplate> quests = file.quests == null ? List.of() : file.quests;
        return respondImport(dryRun, quests);
    }

    @PostMapping(value = "/import", consumes = {"text/csv", "application/csv", MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<Map<String, Object>> importCsv(
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestBody String body) {
        return respondImport(dryRun, QuestCsvImporter.parse(body));
    }

    private ResponseEntity<Map<String, Object>> respondImport(boolean dryRun, List<QuestTemplate> quests) {
        List<String> errors = QuestImportValidator.validate(quests);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("dryRun", dryRun);
        resp.put("count", quests.size());
        resp.put("errors", errors);
        if (!errors.isEmpty()) {
            resp.put("ok", false);
            return ResponseEntity.badRequest().body(resp);
        }
        if (dryRun) {
            resp.put("ok", true);
            return ResponseEntity.ok(resp);
        }
        boolean applied = questConfigService.applyQuests(quests);
        resp.put("ok", applied);
        resp.put("version", questConfigService.version());
        return applied ? ResponseEntity.ok(resp) : ResponseEntity.badRequest().body(resp);
    }
}
