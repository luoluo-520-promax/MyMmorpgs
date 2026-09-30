package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.AdminPlayerPredictionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@RequestMapping("/admin/ai/prediction")
public class AdminAiPredictionController {

    private final AdminPlayerPredictionService predictionService;

    public AdminAiPredictionController(AdminPlayerPredictionService predictionService) {
        this.predictionService = predictionService;
    }

    /**
     * POST /admin/ai/prediction/{playerId}
     * body: loginDays7, questCompletionRate, onlineMinutes7, rechargeAmount30d, ...
     */
    @PostMapping("/{playerId}")
    public ResponseEntity<Map<String, Object>> predict(@PathVariable long playerId,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(predictionService.predict(playerId, body));
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> models() {
        return ResponseEntity.ok(predictionService.modelSnapshot());
    }

    @PostMapping("/models/rollback")
    public ResponseEntity<Map<String, Object>> rollback(@RequestParam String modelName,
                                                        @RequestParam String version) {
        return ResponseEntity.ok(predictionService.rollbackModel(modelName, version));
    }
}
