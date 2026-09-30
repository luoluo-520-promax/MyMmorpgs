package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import cn.itcast.demo.mymmorpg.service.SkillService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "skill-service")
@RequestMapping("/internal/skill")
public class InternalSkillController {

    private final SkillService skillService;

    public InternalSkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @PostMapping(value = "/list", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> list(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = skillService.handleGetPlayerSkills(playerId, GetPlayerSkillsCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/learn", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> learn(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = skillService.handleLearnSkill(playerId, LearnSkillCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/cast", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> cast(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = skillService.handleCastSkill(playerId, CastSkillCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }
}
