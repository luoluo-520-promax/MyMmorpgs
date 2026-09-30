package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.ChatReportService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "chat-service")
@RequestMapping("/internal/chat")
public class InternalChatReportController {

    private final ChatReportService chatReportService;

    public InternalChatReportController(ChatReportService chatReportService) {
        this.chatReportService = chatReportService;
    }

    @PostMapping("/report")
    public Map<String, Object> report(@RequestHeader("X-Player-Id") long playerId,
                                      @RequestParam long targetPlayerId,
                                      @RequestParam(defaultValue = "2") int channel,
                                      @RequestParam(required = false) String content,
                                      @RequestParam(required = false) String reason) {
        return chatReportService.report(playerId, targetPlayerId, channel, content, reason);
    }

    @GetMapping("/reports/open")
    public List<Map<String, Object>> openReports(@RequestParam(defaultValue = "20") int limit) {
        return chatReportService.listOpen(limit);
    }

    @PostMapping("/reports/resolve")
    public Map<String, Object> resolve(@RequestParam String reportId,
                                       @RequestParam(defaultValue = "true") boolean accept,
                                       @RequestParam(required = false) String note) {
        return chatReportService.resolve(reportId, accept, note);
    }
}
