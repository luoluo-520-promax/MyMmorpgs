package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import cn.itcast.demo.mymmorpg.service.BagService;
import cn.itcast.demo.mymmorpg.service.EquipEnhanceService;
import cn.itcast.demo.mymmorpg.service.RelicScoringService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "bag-service")
@RequestMapping("/internal/bag")
public class InternalBagController {

    private final BagService bagService;
    private final EquipEnhanceService equipEnhanceService;
    private final RelicScoringService relicScoringService;

    public InternalBagController(
            BagService bagService,
            EquipEnhanceService equipEnhanceService,
            RelicScoringService relicScoringService) {
        this.bagService = bagService;
        this.equipEnhanceService = equipEnhanceService;
        this.relicScoringService = relicScoringService;
    }

    @PostMapping(value = "/info", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> info(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = bagService.handleGetBagInfo(playerId, GetBagInfoCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/use", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> use(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = bagService.handleUseItem(playerId, UseItemCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/discard", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> discard(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = bagService.handleDiscardItem(playerId, DiscardItemCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/sort", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> sort(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = bagService.handleSortBag(playerId, SortBagCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/sell", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> sell(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = bagService.handleSellItem(playerId, SellItemCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/equip", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> equip(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = bagService.handleEquipItem(playerId, EquipItemCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/unequip", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> unequip(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = bagService.handleUnequipItem(playerId, UnequipItemCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping("/equip/enhance")
    public Map<String, Object> enhance(
            @RequestHeader("X-Player-Id") long playerId,
            @RequestParam long itemUid,
            @RequestParam(defaultValue = "0") long seed) {
        return equipEnhanceService.enhance(playerId, itemUid, seed);
    }

    /** 定向锁定词条（装备强化路径）。 */
    @PostMapping("/relic/lock")
    public Map<String, Object> relicLock(
            @RequestHeader("X-Player-Id") long playerId,
            @RequestParam long itemUid,
            @RequestParam int subIndex,
            @RequestParam(defaultValue = "true") boolean useRelicService) {
        if (useRelicService) {
            return relicScoringService.lockSubStat(playerId, itemUid, subIndex);
        }
        return equipEnhanceService.lockSubStat(playerId, itemUid, subIndex);
    }

    @PostMapping("/relic/enhance")
    public Map<String, Object> relicEnhance(
            @RequestHeader("X-Player-Id") long playerId,
            @RequestParam long itemUid,
            @RequestParam(defaultValue = "0") long seed) {
        return relicScoringService.enhance(playerId, itemUid, seed);
    }
}
