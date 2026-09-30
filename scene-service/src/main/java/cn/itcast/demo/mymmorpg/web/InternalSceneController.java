package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import cn.itcast.demo.mymmorpg.service.SceneActorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "scene-service")
@RequestMapping("/internal/scene")
public class InternalSceneController {

    private final SceneActorService sceneActorService;

    public InternalSceneController(SceneActorService sceneActorService) {
        this.sceneActorService = sceneActorService;
    }

    @PostMapping(value = "/enter", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> enter(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = sceneActorService.handleEnterScene(playerId, EnterSceneCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/cur", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> cur(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = sceneActorService.handleGetCurSceneInfo(playerId, GetCurSceneInfoCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/move", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> move(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = sceneActorService.handleMove(playerId, MoveCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/switch-line", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> switchLine(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = sceneActorService.handleSwitchLine(playerId, SwitchLineCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/nearby", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> nearby(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = sceneActorService.handleGetNearby(playerId, GetNearbyEntitiesCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/transfer", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> transfer(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = sceneActorService.handleTransferScene(playerId, TransferSceneCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/resume", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> resume(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = sceneActorService.handleResumeScene(playerId, ResumeSceneCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leave(@RequestHeader("X-Player-Id") long playerId) {
        sceneActorService.onPlayerLeave(playerId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect(@RequestHeader("X-Player-Id") long playerId) {
        sceneActorService.onPlayerDisconnect(playerId);
        return ResponseEntity.ok().build();
    }
}
