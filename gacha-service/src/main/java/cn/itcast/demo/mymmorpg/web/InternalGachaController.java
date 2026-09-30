package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DoGachaCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ExchangeGachaCeilingCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaHistoryCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaInfoCsReq;
import cn.itcast.demo.mymmorpg.service.GachaService;
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

/**
 * 抽卡服内部 API：协议命令 + 概率配置/审计运维面。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "gacha-service")
@RequestMapping("/internal/gacha")
public class InternalGachaController {

    private final GachaService gachaService;

    public InternalGachaController(GachaService gachaService) {
        this.gachaService = gachaService;
    }

    @PostMapping(value = "/info", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> info(@RequestHeader("X-Player-Id") long playerId,
                                       @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = gachaService.handleGetGachaInfo(playerId, GetGachaInfoCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/draw", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> draw(@RequestHeader("X-Player-Id") long playerId,
                                       @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = gachaService.handleDoGacha(playerId, DoGachaCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/ceiling/exchange", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> exchange(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = gachaService.handleExchangeCeiling(playerId, ExchangeGachaCeilingCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/history", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> history(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = gachaService.handleGetGachaHistory(playerId, GetGachaHistoryCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    /** 公开概率配置（运营/合规审计只读）。 */
    @GetMapping("/probability")
    public Map<String, Object> probability() {
        return gachaService.probabilityConfig();
    }

    /** 玩家抽卡审计摘要（保底进度 + 最近历史条数）。 */
    @GetMapping("/audit")
    public Map<String, Object> audit(@RequestHeader("X-Player-Id") long playerId,
                                     @RequestParam(defaultValue = "20") int limit) {
        return gachaService.auditSummary(playerId, limit);
    }

    @PostMapping("/ops/reload")
    public Map<String, Object> reload() {
        gachaService.reloadConfig();
        return Map.of("ok", true, "banners", gachaService.probabilityConfig().get("banners"));
    }
}
