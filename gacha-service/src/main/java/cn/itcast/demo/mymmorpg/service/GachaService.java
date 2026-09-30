package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.GachaBagGrantClient;
import cn.itcast.demo.mymmorpg.gacha.GachaBannerConfig;
import cn.itcast.demo.mymmorpg.gacha.GachaConfigService;
import cn.itcast.demo.mymmorpg.gacha.GachaDrawEngine;
import cn.itcast.demo.mymmorpg.port.BagItemGrantPort;
import cn.itcast.demo.mymmorpg.protocol.BagRetCode;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DoGachaCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DoGachaScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ExchangeGachaCeilingCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ExchangeGachaCeilingScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GachaBannerInfo;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GachaHistoryEntry;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GachaItem;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaHistoryCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaHistoryScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaInfoCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaInfoScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 抽卡业务：卡池列表、单抽/十连、保底、天井兑换、历史与概率审计；保底/历史存 Redis，发奖走背包幂等。
 * <p>作为独立微服务（gacha-service）运行，也可嵌入 player-service 单体模式。
 */
@Service
public class GachaService {

    private static final Logger log = LoggerFactory.getLogger(GachaService.class);

    private static final String KEY_BANNER = "gacha:banner:";
    private static final String KEY_CEILING = "gacha:ceiling:";
    private static final String KEY_HISTORY = "gacha:history:";
    private static final int CEILING_THRESHOLD = 300;
    private static final int CEILING_REWARD_ITEM = 11001;
    private static final int HISTORY_MAX = 100;
    private static final Duration PITY_TTL = Duration.ofDays(90);

    /** 与 {@link GachaDrawEngine} 对齐的公示概率（软保底前基础率）。 */
    private static final double BASE_RATE_5 = 0.6;
    private static final double BASE_RATE_4 = 5.1;
    private static final int SOFT_PITY_5 = 90;
    private static final int SOFT_PITY_4 = 10;
    private static final int UP_RATE_PERCENT = 50;

    private final GachaConfigService gachaConfigService;
    private final GachaDrawEngine drawEngine;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectProvider<BagItemGrantPort> bagItemGrantPort;
    private final ObjectProvider<GachaBagGrantClient> bagGrantClient;
    private final ObjectProvider<GachaAuditLogService> auditLogService;

    public GachaService(GachaConfigService gachaConfigService,
                        GachaDrawEngine drawEngine,
                        StringRedisTemplate stringRedisTemplate,
                        ObjectProvider<BagItemGrantPort> bagItemGrantPort,
                        ObjectProvider<GachaBagGrantClient> bagGrantClient) {
        this(gachaConfigService, drawEngine, stringRedisTemplate, bagItemGrantPort, bagGrantClient, null);
    }

    public GachaService(GachaConfigService gachaConfigService,
                        GachaDrawEngine drawEngine,
                        StringRedisTemplate stringRedisTemplate,
                        ObjectProvider<BagItemGrantPort> bagItemGrantPort,
                        ObjectProvider<GachaBagGrantClient> bagGrantClient,
                        ObjectProvider<GachaAuditLogService> auditLogService) {
        this.gachaConfigService = gachaConfigService;
        this.drawEngine = drawEngine;
        this.stringRedisTemplate = stringRedisTemplate;
        this.bagItemGrantPort = bagItemGrantPort;
        this.bagGrantClient = bagGrantClient;
        this.auditLogService = auditLogService;
    }

    public void reloadConfig() {
        gachaConfigService.reload();
    }

    /** 概率与保底规则公示（合规/运营审计）。 */
    public Map<String, Object> probabilityConfig() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> banners = new ArrayList<>();
        for (GachaBannerConfig cfg : gachaConfigService.listActive(now)) {
            Map<String, Object> b = new HashMap<>();
            b.put("bannerId", cfg.getId());
            b.put("bannerType", cfg.getBannerType());
            b.put("name", cfg.getName());
            b.put("beginTime", cfg.getBeginTime());
            b.put("endTime", cfg.getEndTime());
            b.put("rateUpItems5", cfg.getRateUpItems5());
            b.put("rateUpItems4", cfg.getRateUpItems4());
            banners.add(b);
        }
        Map<String, Object> rates = new HashMap<>();
        rates.put("baseRate5Percent", BASE_RATE_5);
        rates.put("baseRate4Percent", BASE_RATE_4);
        rates.put("softPity5", SOFT_PITY_5);
        rates.put("softPity4", SOFT_PITY_4);
        rates.put("upWinPercent", UP_RATE_PERCENT);
        rates.put("algo", "PITY_V2");
        rates.put("auditStream", "gacha:audit:stream");
        rates.put("hardPityNote", "连续未出 UP 五星后下次五星必为 UP（大保底）");
        rates.put("ceilingThreshold", CEILING_THRESHOLD);
        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("rates", rates);
        out.put("banners", banners);
        return out;
    }

    /** 玩家抽卡审计：保底进度 + 近期历史。 */
    public Map<String, Object> auditSummary(long playerId, int limit) {
        if (playerId <= 0) {
            return Map.of("ok", false, "error", "invalid_player");
        }
        long now = System.currentTimeMillis();
        List<Map<String, Object>> pityList = new ArrayList<>();
        for (GachaBannerConfig cfg : gachaConfigService.listActive(now)) {
            PityState pity = loadPity(playerId, cfg.getBannerType());
            Map<String, Object> row = new HashMap<>();
            row.put("bannerType", cfg.getBannerType());
            row.put("bannerName", cfg.getName());
            row.put("pity5", pity.pity5);
            row.put("pity4", pity.pity4);
            row.put("failedUpCount", pity.failedUp);
            pityList.add(row);
        }
        CeilingState ceiling = loadCeiling(playerId);
        int capped = Math.max(1, Math.min(limit, HISTORY_MAX));
        List<String> raw = stringRedisTemplate.opsForList().range(KEY_HISTORY + playerId, 0, capped - 1L);
        List<Map<String, Object>> history = new ArrayList<>();
        if (raw != null) {
            for (String line : raw) {
                String[] p = line.split(",");
                if (p.length < 1) {
                    continue;
                }
                try {
                    Map<String, Object> h = new HashMap<>();
                    h.put("itemId", Integer.parseInt(p[0]));
                    h.put("rarity", p.length > 1 ? Integer.parseInt(p[1]) : 0);
                    h.put("bannerType", p.length > 2 ? Integer.parseInt(p[2]) : 0);
                    h.put("createdAt", p.length > 3 ? Long.parseLong(p[3]) : 0L);
                    history.add(h);
                } catch (NumberFormatException ignored) {
                    // skip corrupt line
                }
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("playerId", playerId);
        out.put("pity", pityList);
        out.put("ceilingNum", ceiling.num);
        out.put("ceilingClaimed", ceiling.claimed);
        out.put("history", history);
        out.put("algo", "PITY_V2");
        GachaAuditLogService audit = auditLog();
        if (audit != null) {
            out.put("pityAuditExport", audit.auditExport(playerId, capped));
        }
        return out;
    }

    public ProtocolMessage handleGetGachaInfo(long playerId, GetGachaInfoCsReq req) {
        if (playerId <= 0) {
            return infoRsp(RetCode.PLAYER_NOT_SELECTED, List.of(), 0, 0);
        }
        long now = System.currentTimeMillis();
        List<GachaBannerInfo> banners = new ArrayList<>();
        for (GachaBannerConfig cfg : gachaConfigService.listActive(now)) {
            PityState pity = loadPity(playerId, cfg.getBannerType());
            banners.add(GachaBannerInfo.newBuilder()
                    .setBannerId(cfg.getId())
                    .setBannerType(cfg.getBannerType())
                    .setName(cfg.getName())
                    .setBeginTime(cfg.getBeginTime())
                    .setEndTime(cfg.getEndTime())
                    .setPity5(pity.pity5)
                    .setPity4(pity.pity4)
                    .addAllRateUpItems5(cfg.getRateUpItems5())
                    .addAllRateUpItems4(cfg.getRateUpItems4())
                    .build());
        }
        CeilingState ceiling = loadCeiling(playerId);
        return infoRsp(RetCode.OK, banners, ceiling.num, ceiling.claimed);
    }

    public ProtocolMessage handleDoGacha(long playerId, DoGachaCsReq req) {
        if (playerId <= 0) {
            return doRsp(RetCode.PLAYER_NOT_SELECTED, List.of(), 0, 0, 0);
        }
        int times = req.getTimes();
        if (times != 1 && times != 10) {
            return doRsp(RetCode.GACHA_INVALID_TIMES, List.of(), 0, 0, 0);
        }
        long now = System.currentTimeMillis();
        GachaBannerConfig banner = gachaConfigService.findByType(req.getBannerType(), now);
        if (banner == null) {
            return doRsp(RetCode.GACHA_BANNER_NOT_FOUND, List.of(), 0, 0, 0);
        }
        GachaBannerConfig normal = gachaConfigService.findNormal(now);
        PityState pity = loadPity(playerId, req.getBannerType());
        CeilingState ceiling = loadCeiling(playerId);
        int startCeiling = ceiling.num;
        List<GachaItem> items = new ArrayList<>(times);
        List<ItemReward> rewards = new ArrayList<>(times);
        List<int[]> historyBuf = new ArrayList<>(times);
        List<int[]> auditBuf = new ArrayList<>(times);
        for (int i = 0; i < times; i++) {
            int pity5Before = pity.pity5;
            int pity4Before = pity.pity4;
            int failedUpBefore = pity.failedUp;
            GachaDrawEngine.DrawResult draw = drawEngine.doOneDraw(
                    req.getBannerType(), banner, normal, pity.pity5, pity.pity4, pity.failedUp);
            pity = new PityState(draw.pity5After(), draw.pity4After(), draw.failedUpCountAfter());
            ceiling = new CeilingState(ceiling.num + 1, ceiling.claimed);
            items.add(GachaItem.newBuilder().setItemId(draw.itemId()).setRarity(draw.rarity()).build());
            rewards.add(ItemReward.newBuilder().setItemId(draw.itemId()).setCount(1).build());
            historyBuf.add(new int[]{draw.itemId(), draw.rarity(), req.getBannerType()});
            boolean softPity = pity5Before >= SOFT_PITY_5 - 1 || pity4Before >= SOFT_PITY_4 - 1;
            boolean isUp = draw.rarity() >= 5 && banner.getRateUpItems5() != null
                    && banner.getRateUpItems5().contains(draw.itemId());
            auditBuf.add(new int[]{
                    draw.itemId(), draw.rarity(), pity5Before, draw.pity5After(),
                    pity4Before, draw.pity4After(), failedUpBefore, draw.failedUpCountAfter(),
                    isUp ? 1 : 0, softPity ? 1 : 0});
        }
        String idem = "gacha:" + playerId + ":" + req.getBannerType() + ":"
                + (startCeiling + 1) + "-" + (startCeiling + times);
        int grantRc = grantItems(playerId, idem, rewards);
        if (grantRc != BagRetCode.OK) {
            log.warn("抽卡发奖失败 playerId={} rc={}", playerId, grantRc);
            return doRsp(RetCode.INTERNAL_ERROR, List.of(), 0, 0, 0);
        }
        for (int i = 0; i < historyBuf.size(); i++) {
            int[] h = historyBuf.get(i);
            appendHistory(playerId, h[0], h[1], h[2], now + i);
            int[] a = auditBuf.get(i);
            persistAudit(playerId, req.getBannerType(), banner.getId(), a, now + i);
        }
        savePity(playerId, req.getBannerType(), pity);
        saveCeiling(playerId, ceiling);
        return doRsp(RetCode.OK, items, pity.pity5, pity.pity4, ceiling.num);
    }

    public ProtocolMessage handleExchangeCeiling(long playerId, ExchangeGachaCeilingCsReq req) {
        if (playerId <= 0) {
            return exchangeRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0);
        }
        CeilingState ceiling = loadCeiling(playerId);
        if (ceiling.num - ceiling.claimed < CEILING_THRESHOLD) {
            return exchangeRsp(RetCode.GACHA_CEILING_NOT_READY, 0, ceiling.claimed);
        }
        CeilingState next = new CeilingState(ceiling.num, ceiling.claimed + CEILING_THRESHOLD);
        String idem = "gacha:ceiling:" + playerId + ":" + next.claimed;
        int grantRc = grantItems(playerId, idem, List.of(
                ItemReward.newBuilder().setItemId(CEILING_REWARD_ITEM).setCount(1).build()));
        if (grantRc != BagRetCode.OK) {
            return exchangeRsp(RetCode.INTERNAL_ERROR, 0, ceiling.claimed);
        }
        saveCeiling(playerId, next);
        appendHistory(playerId, CEILING_REWARD_ITEM, 5, 0, System.currentTimeMillis());
        return exchangeRsp(RetCode.OK, CEILING_REWARD_ITEM, next.claimed);
    }

    public ProtocolMessage handleGetGachaHistory(long playerId, GetGachaHistoryCsReq req) {
        if (playerId <= 0) {
            return historyRsp(RetCode.PLAYER_NOT_SELECTED, List.of());
        }
        int limit = req.getLimit() <= 0 ? 20 : Math.min(req.getLimit(), HISTORY_MAX);
        List<String> raw = stringRedisTemplate.opsForList().range(KEY_HISTORY + playerId, 0, limit - 1L);
        List<GachaHistoryEntry> entries = new ArrayList<>();
        if (raw != null) {
            for (String line : raw) {
                GachaHistoryEntry e = parseHistory(line);
                if (e != null) {
                    entries.add(e);
                }
            }
        }
        return historyRsp(RetCode.OK, entries);
    }

    private int grantItems(long playerId, String idempotencyKey, List<ItemReward> rewards) {
        BagItemGrantPort local = bagItemGrantPort.getIfAvailable();
        if (local != null) {
            return local.grantItemsIdempotent(playerId, idempotencyKey, rewards);
        }
        GachaBagGrantClient remote = bagGrantClient.getIfAvailable();
        if (remote == null) {
            log.warn("抽卡发奖端口不可用 playerId={}", playerId);
            return -1;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemReward r : rewards) {
            Map<String, Object> m = new HashMap<>();
            m.put("itemId", r.getItemId());
            m.put("count", r.getCount());
            items.add(m);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("idempotencyKey", idempotencyKey);
        body.put("rewards", items);
        try {
            Integer rc = remote.grant(playerId, body);
            return rc == null ? -1 : rc;
        } catch (Exception e) {
            log.warn("远程抽卡发奖失败 playerId={}", playerId, e);
            return -1;
        }
    }

    private void appendHistory(long playerId, int itemId, int rarity, int bannerType, long ts) {
        try {
            String line = itemId + "," + rarity + "," + bannerType + "," + ts;
            String key = KEY_HISTORY + playerId;
            stringRedisTemplate.opsForList().leftPush(key, line);
            stringRedisTemplate.opsForList().trim(key, 0, HISTORY_MAX - 1L);
            stringRedisTemplate.expire(key, PITY_TTL);
        } catch (Exception e) {
            log.warn("抽卡历史写入失败 playerId={}", playerId, e);
        }
    }

    private void persistAudit(long playerId, int bannerType, int bannerId, int[] a, long ts) {
        GachaAuditLogService audit = auditLog();
        if (audit == null || a == null || a.length < 10) {
            return;
        }
        audit.recordDraw(
                playerId, bannerType, bannerId,
                a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7],
                a[8] == 1, a[9] == 1, ts);
    }

    private GachaAuditLogService auditLog() {
        return auditLogService == null ? null : auditLogService.getIfAvailable();
    }

    private static GachaHistoryEntry parseHistory(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] p = line.split(",");
        try {
            int itemId = Integer.parseInt(p[0]);
            int rarity = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            int bannerType = p.length > 2 ? Integer.parseInt(p[2]) : 0;
            long ts = p.length > 3 ? Long.parseLong(p[3]) : 0L;
            return GachaHistoryEntry.newBuilder()
                    .setItemId(itemId)
                    .setRarity(rarity)
                    .setBannerType(bannerType)
                    .setCreatedAt(ts)
                    .build();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private PityState loadPity(long playerId, int bannerType) {
        String raw = stringRedisTemplate.opsForValue().get(KEY_BANNER + playerId + ":" + bannerType);
        if (raw == null || raw.isBlank()) {
            return new PityState(0, 0, 0);
        }
        String[] parts = raw.split(",");
        try {
            int p5 = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            int p4 = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int fail = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return new PityState(p5, p4, fail);
        } catch (NumberFormatException e) {
            return new PityState(0, 0, 0);
        }
    }

    private void savePity(long playerId, int bannerType, PityState pity) {
        String key = KEY_BANNER + playerId + ":" + bannerType;
        stringRedisTemplate.opsForValue().set(key,
                pity.pity5 + "," + pity.pity4 + "," + pity.failedUp, PITY_TTL);
    }

    private CeilingState loadCeiling(long playerId) {
        String raw = stringRedisTemplate.opsForValue().get(KEY_CEILING + playerId);
        if (raw == null || raw.isBlank()) {
            return new CeilingState(0, 0);
        }
        String[] parts = raw.split(",");
        try {
            int num = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            int claimed = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return new CeilingState(num, claimed);
        } catch (NumberFormatException e) {
            return new CeilingState(0, 0);
        }
    }

    private void saveCeiling(long playerId, CeilingState ceiling) {
        stringRedisTemplate.opsForValue().set(KEY_CEILING + playerId,
                ceiling.num + "," + ceiling.claimed, PITY_TTL);
    }

    private static ProtocolMessage infoRsp(int ret, List<GachaBannerInfo> banners, int ceilingNum, int claimed) {
        GetGachaInfoScRsp body = GetGachaInfoScRsp.newBuilder()
                .setRetcode(ret)
                .addAllBanners(banners)
                .setCeilingNum(ceilingNum)
                .setCeilingClaimed(claimed)
                .build();
        return new ProtocolMessage(MessageId.GET_GACHA_INFO_SC_RSP, body.toByteArray());
    }

    private static ProtocolMessage doRsp(int ret, List<GachaItem> items, int pity5, int pity4, int ceiling) {
        DoGachaScRsp body = DoGachaScRsp.newBuilder()
                .setRetcode(ret)
                .addAllItems(items)
                .setPity5(pity5)
                .setPity4(pity4)
                .setCeilingNum(ceiling)
                .build();
        return new ProtocolMessage(MessageId.DO_GACHA_SC_RSP, body.toByteArray());
    }

    private static ProtocolMessage exchangeRsp(int ret, int itemId, int claimed) {
        ExchangeGachaCeilingScRsp body = ExchangeGachaCeilingScRsp.newBuilder()
                .setRetcode(ret)
                .setItemId(itemId)
                .setCeilingClaimed(claimed)
                .build();
        return new ProtocolMessage(MessageId.EXCHANGE_GACHA_CEILING_SC_RSP, body.toByteArray());
    }

    private static ProtocolMessage historyRsp(int ret, List<GachaHistoryEntry> entries) {
        GetGachaHistoryScRsp body = GetGachaHistoryScRsp.newBuilder()
                .setRetcode(ret)
                .addAllEntries(entries)
                .build();
        return new ProtocolMessage(MessageId.GET_GACHA_HISTORY_SC_RSP, body.toByteArray());
    }

    private record PityState(int pity5, int pity4, int failedUp) {
    }

    private record CeilingState(int num, int claimed) {
    }
}
