package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.NpcDialogueService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/npc")
public class InternalNpcDialogueController {

    private final NpcDialogueService npcDialogueService;

    public InternalNpcDialogueController(NpcDialogueService npcDialogueService) {
        this.npcDialogueService = npcDialogueService;
    }

    @PostMapping("/dialogue")
    public Map<String, Object> dialogue(@RequestHeader("X-Player-Id") long playerId,
                                        @RequestParam(defaultValue = "guide") String npcId,
                                        @RequestBody(required = false) Map<String, Object> body) {
        String message = body == null || body.get("message") == null ? "" : String.valueOf(body.get("message"));
        return npcDialogueService.talk(playerId, npcId, message);
    }
}
