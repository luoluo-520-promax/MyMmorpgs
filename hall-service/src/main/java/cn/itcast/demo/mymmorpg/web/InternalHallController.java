package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AddFriendCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimMailCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetFriendListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetMailListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetRankingCsReq;
import cn.itcast.demo.mymmorpg.service.HallService;
import cn.itcast.demo.mymmorpg.service.WorldEventSquadService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "hall-service")
@RequestMapping("/internal/hall")
public class InternalHallController {

    private final HallService hallService;
    private final WorldEventSquadService worldEventSquadService;

    public InternalHallController(HallService hallService,
                                  WorldEventSquadService worldEventSquadService) {
        this.hallService = hallService;
        this.worldEventSquadService = worldEventSquadService;
    }

    @PostMapping(value = "/friends", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> friends(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = hallService.handleGetFriendList(playerId, GetFriendListCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/friends/add", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> addFriend(@RequestHeader("X-Player-Id") long playerId,
                                            @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = hallService.handleAddFriend(playerId, AddFriendCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/mails", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> mails(@RequestHeader("X-Player-Id") long playerId,
                                        @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = hallService.handleGetMailList(playerId, GetMailListCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/mails/claim", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> claimMail(@RequestHeader("X-Player-Id") long playerId,
                                            @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = hallService.handleClaimMail(playerId, ClaimMailCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/ranking", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> ranking(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = hallService.handleGetRanking(playerId, GetRankingCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    /** 系统发信（深渊等玩法结算附件）。 */
    @PostMapping("/mails/send")
    public Map<String, Object> sendMail(@RequestBody Map<String, Object> body) {
        long playerId = ((Number) body.getOrDefault("playerId", 0L)).longValue();
        String title = String.valueOf(body.getOrDefault("title", "系统邮件"));
        String mailBody = String.valueOf(body.getOrDefault("body", ""));
        Object attachments = body.get("attachmentsJson");
        String attachmentsJson = attachments == null ? null : String.valueOf(attachments);
        var mail = hallService.sendSystemMail(playerId, title, mailBody, attachmentsJson);
        return Map.of("ok", true, "mailId", mail.getId() == null ? 0L : mail.getId());
    }

    @PostMapping("/squads/create")
    public Map<String, Object> createSquad(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestParam String eventId,
                                           @RequestParam(defaultValue = "4") int maxSize) {
        return worldEventSquadService.toView(worldEventSquadService.create(playerId, eventId, maxSize));
    }

    @PostMapping("/squads/join")
    public Map<String, Object> joinSquad(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestParam String squadId) {
        return worldEventSquadService.toView(worldEventSquadService.join(playerId, squadId));
    }

    @PostMapping("/squads/leave")
    public Map<String, Object> leaveSquad(@RequestHeader("X-Player-Id") long playerId) {
        return worldEventSquadService.toView(worldEventSquadService.leave(playerId));
    }

    @GetMapping("/squads/mine")
    public Map<String, Object> mySquad(@RequestHeader("X-Player-Id") long playerId) {
        return worldEventSquadService.toView(worldEventSquadService.currentOf(playerId));
    }

    @GetMapping("/squads/open")
    public List<Map<String, Object>> openSquads(@RequestParam String eventId) {
        return worldEventSquadService.listOpenByEvent(eventId).stream()
                .map(worldEventSquadService::toView)
                .toList();
    }
}
