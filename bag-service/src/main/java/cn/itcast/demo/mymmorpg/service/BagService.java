/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/BagService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：背包协议业务，查/用/丢/排序/出售道具，Redis bag:info 缓存，活动发奖端口。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 背包 GET_BAG_INFO/USE_ITEM 等协议，Redis bag:info 缓存

import cn.itcast.demo.mymmorpg.entity.ItemConfig; // item_config 表，道具名称/堆叠/售价/经验值
import cn.itcast.demo.mymmorpg.entity.Player; // 玩家实体，含 level/gold
import cn.itcast.demo.mymmorpg.entity.PlayerBagItem; // player_bag_item 表，背包槽位与数量
import cn.itcast.demo.mymmorpg.protocol.BagRetCode; // 背包专用返回码：ITEM_NOT_FOUND/BAG_FULL 等
import cn.itcast.demo.mymmorpg.port.ActivityItemGrantPort; // 活动领奖时调用 grantItemsForActivity
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import cn.itcast.demo.mymmorpg.protocol.MessageId; // GET_BAG_INFO/USE_ITEM 等响应 msgId
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一 msgId + payload
import cn.itcast.demo.mymmorpg.protocol.RetCode; // PLAYER_NOT_SELECTED / PLAYER_NOT_FOUND
import cn.itcast.demo.mymmorpg.protocol.protobuf.BagInfo; // 背包摘要（容量/已用槽位）
import cn.itcast.demo.mymmorpg.protocol.protobuf.BagItemInfo; // 单格道具 Protobuf
import cn.itcast.demo.mymmorpg.protocol.protobuf.DiscardItemCsReq; // 丢弃道具请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.DiscardItemScRsp; // 丢弃道具响应
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetBagInfoCsReq; // 查背包请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetBagInfoScRsp; // 查背包响应，含 loading 标志
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemEffectResult; // 使用道具效果：经验/HP/MP
import cn.itcast.demo.mymmorpg.protocol.protobuf.SellItemCsReq; // 出售道具请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.SellItemScRsp; // 出售道具响应
import cn.itcast.demo.mymmorpg.protocol.protobuf.SortBagCsReq; // 排序背包请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.SortBagScRsp; // 排序背包响应
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward; // 活动奖励：itemId + count
import cn.itcast.demo.mymmorpg.protocol.protobuf.UseItemCsReq; // 使用道具请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.UseItemScRsp; // 使用道具响应
import cn.itcast.demo.mymmorpg.repository.PlayerBagItemRepository; // player_bag_item 表 CRUD
import cn.itcast.demo.mymmorpg.repository.PlayerRepository; // player 表存在性校验
import cn.itcast.demo.mymmorpg.support.ItemPolicy; // Groovy 解析道具 effectJson 得经验/HP/MP
import com.fasterxml.jackson.databind.ObjectMapper; // bag:info:{playerId} JSON 序列化 BagInfoCache
import org.springframework.beans.factory.annotation.Value; // 读取 game.player-preload.enabled 开关
import org.springframework.data.redis.core.StringRedisTemplate; // bag:info:{playerId} Redis 读写
import org.springframework.stereotype.Service; // BagFacade 与 ActivityService 注入本服务
import org.springframework.transaction.annotation.Transactional; // 用/丢/卖/排序/发奖涉及 player_bag_item 写

import java.time.Duration; // bag:info 缓存 TTL 5 分钟
import java.util.ArrayList; // 构建 BagItemInfo 可变列表
import java.util.Comparator; // 背包排序比较器（kind/level/count/name）
import java.util.List; // 背包行/道具 Protobuf 列表
import java.util.Objects; // requireNonNull 防 Redis key/json 为 null
import java.util.Optional; // findByIdAndPlayerId 可选背包行

@Service // 背包协议 Handler 与 ActivityItemGrantPort 发奖共用
public class BagService implements ActivityItemGrantPort { // player_bag_item CRUD + bag:info Redis 快照

    /** 默认背包容量 50 格，player_bag_item 槽位上限 */
    public static final int DEFAULT_CAPACITY = 50; // GetBagInfoScRsp.capacity 默认值

    /** item_config.kind=1 表示经验类消耗品，当前仅支持使用该 kind */
    public static final int KIND_EXP = 1; // handleUseItem 仅允许 kind=1 经验道具

    /** 排序类型：按道具 kind 升序 */
    public static final int SORT_BY_KIND = 1; // SortBagCsReq.sortType=1

    /** 排序类型：按 level_required 升序 */
    public static final int SORT_BY_LEVEL = 2; // SortBagCsReq.sortType=2

    /** 排序类型：按堆叠数量降序 */
    public static final int SORT_BY_COUNT = 3; // SortBagCsReq.sortType=3

    /** 排序类型：按道具名称字典序 */
    public static final int SORT_BY_NAME = 4; // SortBagCsReq.sortType=4

    /** Redis 背包快照键前缀 bag:info:{playerId}，JSON 存 BagInfoCache */
    private static final String REDIS_BAG_INFO_KEY_PREFIX = "bag:info:"; // GET/SETEX/DEL 背包快照

    /** 背包快照缓存 TTL 5 分钟，道具变更时 evict */
    private static final Duration REDIS_BAG_INFO_TTL = Duration.ofMinutes(5); // SETEX bag:info:{playerId} TTL

    private final PlayerRepository playerRepository; // 校验 playerId 在 player 表存在
    private final PlayerCachePort playerCachePort;
    private final PlayerBagItemRepository bagItemRepository; // player_bag_item 表槽位 CRUD
    private final ConfigQueryService configQueryService; // 查 item_config 名称/堆叠/售价
    private final ItemPolicy itemPolicy; // Groovy 解析 effectJson 得经验/HP/MP
    private final ItemEventPublisher itemEventPublisher; // 发布 ItemUsed/Discarded/Sold MQ 事件
    private final PlayerProgressPort progressService;
    private final StringRedisTemplate stringRedisTemplate; // bag:info:{playerId} GET/SET/DEL
    private final ObjectMapper objectMapper; // BagInfoCache↔JSON 序列化
    private final PlayerDataLoadPort playerDataLoadPort;
    private final boolean preloadEnabled; // game.player-preload.enabled 门控 GetBagInfo loading

    public BagService( // 文件维护说明
            PlayerRepository playerRepository, // 查背包前校验 player 存在
            PlayerBagItemRepository bagItemRepository, // player_bag_item 槽位读写
            ConfigQueryService configQueryService, // item_config 静态配置
            ItemPolicy itemPolicy, // 道具 effect 解析
            ItemEventPublisher itemEventPublisher, // 道具变更领域事件
            PlayerProgressPort progressService,
            PlayerCachePort playerCachePort,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            PlayerDataLoadPort playerDataLoadPort,
            @Value("${game.player-preload.enabled:false}") boolean preloadEnabled) {
        this.playerRepository = playerRepository;
        this.bagItemRepository = bagItemRepository;
        this.configQueryService = configQueryService;
        this.itemPolicy = itemPolicy;
        this.itemEventPublisher = itemEventPublisher;
        this.progressService = progressService;
        this.playerCachePort = playerCachePort;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.playerDataLoadPort = playerDataLoadPort;
        this.preloadEnabled = preloadEnabled;
    }

    /** 查背包入口：预加载未就绪时返回 loading=true */
    public ProtocolMessage handleGetBagInfo(long playerId, GetBagInfoCsReq req) { // 查背包入口：预加载未就绪时返回 loading=true
        if (preloadEnabled && playerId > 0 // 预加载开关开启且已选角
                && !playerDataLoadPort.isReady(playerId, PlayerDataLoadPort.DataType.BAG)) { // DataType.BAG 尚未就绪
            return bagInfoLoadingMsg(); // GET_BAG_INFO_SC_RSP loading=true，客户端轮询
        }
        return loadBagNow(playerId); // 同步查 DB/Redis 返回完整背包
    }

    /** 立即加载背包：Redis 缓存命中则直接返回，否则查 DB 并写缓存 */
    public ProtocolMessage loadBagNow(long playerId) { // 立即加载背包：Redis 缓存命中则直接返回，否则查 DB 并写缓存
        if (playerId <= 0) { // WebSocket 未绑定 playerId
            return bagInfoMsg(RetCode.PLAYER_NOT_SELECTED, DEFAULT_CAPACITY, 0, List.of()); // PLAYER_NOT_SELECTED
        }
        if (playerRepository.findById(playerId).isEmpty()) { // player 表无此 ID
            return bagInfoMsg(RetCode.PLAYER_NOT_FOUND, DEFAULT_CAPACITY, 0, List.of()); // PLAYER_NOT_FOUND
        }
        BagInfoCache cached = readBagInfoCache(playerId); // GET bag:info:{playerId}
        if (cached != null) { // Redis 缓存命中
            return bagInfoMsg(cached.retcode, cached.capacity, cached.usedSlots, restoreCachedItems(cached.items)); // 直接返回快照
        }
        List<PlayerBagItem> rows = bagItemRepository.findByPlayerIdOrderBySlotIndexAsc(playerId); // 按 slot_index 升序查 player_bag_item
        List<BagItemInfo> items = new ArrayList<>(rows.size()); // 组装 BagItemInfo 响应列表
        for (PlayerBagItem row : rows) { // 逐槽位转 Protobuf
            ItemConfig cfg = configQueryService.findItemById(row.getItemConfigId()); // 关联 item_config 取名称/kind
            if (cfg == null) { // item_config 已删
                continue; // 跳过脏数据槽位
            }
            items.add(toProto(row, cfg)); // player_bag_item 行 + 配置 → BagItemInfo
        }
        int used = bagItemRepository.countByPlayerId(playerId); // 已占用槽位数
        writeBagInfoCache(playerId, BagInfoCache.of(BagRetCode.OK, DEFAULT_CAPACITY, used, toCachedItems(items))); // SETEX bag:info:{playerId} 5min
        return bagInfoMsg(BagRetCode.OK, DEFAULT_CAPACITY, used, items); // GET_BAG_INFO_SC_RSP 完整背包
    }

    /** 使用道具：当前仅支持 kind=1 经验类消耗品 */
    @Transactional // 扣 player_bag_item.count + 加 player.exp 同事务
    public ProtocolMessage handleUseItem(long playerId, UseItemCsReq req) { // 文件维护说明
        long itemUid = req.getItemUid(); // player_bag_item.id 主键
        if (playerId <= 0) { // 未选角
            return useItemMsg(RetCode.PLAYER_NOT_SELECTED, itemUid, 0, null, null); // USE_ITEM_SC_RSP 拒绝
        }
        Player player = playerCachePort.findById(playerId); // 读 Player 缓存取 level
        if (player == null) { // 角色不存在
            return useItemMsg(RetCode.PLAYER_NOT_FOUND, itemUid, 0, null, null); // PLAYER_NOT_FOUND
        }
        Optional<PlayerBagItem> bagOpt = bagItemRepository.findByIdAndPlayerId(itemUid, playerId); // 校验道具归属该玩家
        if (bagOpt.isEmpty()) { // 背包行不存在或不属于该 playerId
            return useItemMsg(BagRetCode.ITEM_NOT_FOUND, itemUid, 0, null, null); // ITEM_NOT_FOUND
        }
        PlayerBagItem row = bagOpt.get(); // 目标背包槽位行
        int useCount = req.getCount(); // 客户端请求使用数量
        if (useCount <= 0 || useCount > row.getCount()) { // 数量非法或超过堆叠
            return useItemMsg(BagRetCode.COUNT_NOT_ENOUGH, itemUid, 0, null, null); // COUNT_NOT_ENOUGH
        }
        ItemConfig cfg = configQueryService.findItemById(row.getItemConfigId()); // 查 item_config 静态属性
        if (cfg == null) { // 配置缺失
            return useItemMsg(BagRetCode.ITEM_NOT_FOUND, itemUid, 0, null, null); // 视为道具无效
        }
        int plv = player.getLevel() == null ? 1 : player.getLevel(); // 玩家当前等级
        if (plv < (cfg.getLevelRequired() == null ? 0 : cfg.getLevelRequired())) { // 等级未达 item_config.level_required
            return useItemMsg(BagRetCode.LEVEL_NOT_ENOUGH, itemUid, 0, null, null); // LEVEL_NOT_ENOUGH
        }
        if (req.getTargetParam() != 0) { // 当前不支持对场景实体目标使用
            return useItemMsg(BagRetCode.INVALID_TARGET, itemUid, 0, null, null); // INVALID_TARGET
        }
        int kind = cfg.getKind() == null ? 0 : cfg.getKind(); // item_config.kind
        if (kind != KIND_EXP) { // 非经验类消耗品
            return useItemMsg(BagRetCode.ITEM_UNAVAILABLE, itemUid, 0, null, null); // ITEM_UNAVAILABLE
        }
        int expPer = itemPolicy.parseExpReward(cfg); // Groovy 解析 effectJson 单颗经验
        int hpPer = itemPolicy.parseHpRestore(cfg); // Groovy 解析单颗 HP 回复
        int mpPer = itemPolicy.parseMpRestore(cfg); // Groovy 解析单颗 MP 回复
        int totalExp = expPer * useCount; // 总经验 = 单颗 × 使用数量
        var effB = ItemEffectResult.newBuilder(); // 构造道具效果 Protobuf
        if (totalExp > 0) { // 有经验奖励
            Player updated = progressService.addExp(player, totalExp); // 加经验，可能升级写 player 表
            if (updated != null) { // addExp 返回更新后 Player
                player = updated; // 更新本地引用
            }
            effB.setExpGained(totalExp); // 响应携带获得经验
        }
        int hp = hpPer * useCount; // 总 HP 回复
        int mp = mpPer * useCount; // 总 MP 回复
        if (hp > 0) { // 有 HP 效果
            effB.setHpRestored(hp); // 写入 HP 回复量
        }
        if (mp > 0) { // 有 MP 效果
            effB.setMpRestored(mp); // 写入 MP 回复量
        }
        consumeStack(row, useCount); // 扣减 player_bag_item.count 或 DELETE 整行
        if (totalExp <= 0) { // 无经验变更时 addExp 未触发 save
            playerCachePort.saveCacheAndMarkDirty(player); // 手动 mark dirty 同步缓存
        }
        itemEventPublisher.publishItemUsed(playerId, itemUid, cfg.getId(), useCount); // MQ 上报道具使用
        evictBagInfoCache(playerId); // DEL bag:info:{playerId} 强制下次重查
        ItemEffectResult eff = effB.build(); // 完成 ItemEffectResult
        boolean hasEffect = eff.getExpGained() > 0 || eff.getHpRestored() > 0 || eff.getMpRestored() > 0 // 任一数值效果>0
                || eff.getBuffIdsCount() > 0; // 或附带 Buff ID
        return useItemMsg(BagRetCode.OK, itemUid, useCount, hasEffect ? eff : null, null); // USE_ITEM_SC_RSP 成功
    }

    /** 丢弃道具：扣减数量或删除行 */
    @Transactional // player_bag_item 写操作需事务
    public ProtocolMessage handleDiscardItem(long playerId, DiscardItemCsReq req) { // 文件维护说明
        long itemUid = req.getItemUid(); // player_bag_item.id
        if (playerId <= 0) { // 未选角
            return discardMsg(RetCode.PLAYER_NOT_SELECTED, itemUid, 0); // DISCARD_ITEM_SC_RSP 拒绝
        }
        Optional<PlayerBagItem> bagOpt = bagItemRepository.findByIdAndPlayerId(itemUid, playerId); // 校验归属
        if (bagOpt.isEmpty()) { // 道具不存在
            return discardMsg(BagRetCode.ITEM_NOT_FOUND, itemUid, 0); // ITEM_NOT_FOUND
        }
        PlayerBagItem row = bagOpt.get(); // 目标背包行
        int c = req.getCount(); // 丢弃数量
        if (c <= 0 || c > row.getCount()) { // 数量非法
            return discardMsg(BagRetCode.COUNT_NOT_ENOUGH, itemUid, 0); // COUNT_NOT_ENOUGH
        }
        ItemConfig cfg = configQueryService.findItemById(row.getItemConfigId()); // 查 item_config 取 id
        int cfgId = cfg != null ? cfg.getId() : row.getItemConfigId(); // 配置缺失时用行内 configId
        consumeStack(row, c); // 扣减堆叠或删行
        itemEventPublisher.publishItemDiscarded(playerId, itemUid, cfgId, c); // MQ 上报丢弃
        evictBagInfoCache(playerId); // DEL bag:info:{playerId}
        return discardMsg(BagRetCode.OK, itemUid, c); // DISCARD_ITEM_SC_RSP 成功
    }

    /** 背包排序：重排 slot_index 并返回新列表 */
    @Transactional // 批量 UPDATE player_bag_item.slot_index
    public ProtocolMessage handleSortBag(long playerId, SortBagCsReq req) { // 文件维护说明
        if (playerId <= 0) { // 未选角
            return sortRsp(RetCode.PLAYER_NOT_SELECTED, List.of()); // SORT_BAG_SC_RSP 拒绝
        }
        if (playerRepository.findById(playerId).isEmpty()) { // player 不存在
            return sortRsp(RetCode.PLAYER_NOT_FOUND, List.of()); // PLAYER_NOT_FOUND
        }
        List<PlayerBagItem> rows = new ArrayList<>(bagItemRepository.findByPlayerIdOrderBySlotIndexAsc(playerId)); // 拷贝当前槽位行到内存排序
        int st = req.getSortType(); // 1=kind 2=level 3=count 4=name
        Comparator<PlayerBagItem> cmp = switch (st) { // 按 sortType 选比较器
            case SORT_BY_KIND -> Comparator.comparingInt(a -> { // 按 item_config.kind 升序
                ItemConfig c = configQueryService.findItemById(a.getItemConfigId()); // 查道具 kind
                return c != null && c.getKind() != null ? c.getKind() : 0; // 缺失配置视为 kind=0
            }); // SORT_BY_KIND 比较器：按 item_config.kind 升序排背包槽
            case SORT_BY_LEVEL -> Comparator.comparingInt(a -> { // 按 level_required 升序
                ItemConfig c = configQueryService.findItemById(a.getItemConfigId()); // 查需求等级
                return c != null && c.getLevelRequired() != null ? c.getLevelRequired() : 0; // 缺失视为 0
            }); // SORT_BY_LEVEL 比较器：按 level_required 升序排背包槽
            case SORT_BY_COUNT -> Comparator.comparingInt(PlayerBagItem::getCount).reversed(); // 堆叠数量多的靠前
            case SORT_BY_NAME -> Comparator.comparing(a -> { // 按道具名字典序
                ItemConfig c = configQueryService.findItemById(a.getItemConfigId()); // 查道具名
                return c != null && c.getName() != null ? c.getName() : ""; // 缺失名为空串
            }); // SORT_BY_NAME 比较器：按道具名字典序排背包槽
            default -> Comparator.comparingLong(PlayerBagItem::getId); // 未知 sortType 按 itemUid
        }; // switch(sortType) 选定 Comparator 后 rows.sort 重写 slot_index
        rows.sort(cmp); // 内存排序背包行
        int i = 0; // 新 slot_index 从 0 递增
        for (PlayerBagItem r : rows) { // 按排序结果重写槽位
            r.setSlotIndex(i++); // 赋值 slot_index 0..n-1
            bagItemRepository.save(r); // UPDATE player_bag_item
        }
        List<BagItemInfo> items = listBagItems(playerId); // 排序后重新组装 Protobuf
        writeBagInfoCache(playerId, BagInfoCache.of(BagRetCode.OK, DEFAULT_CAPACITY, bagItemRepository.countByPlayerId(playerId), toCachedItems(items))); // 刷新 bag:info 缓存
        return sortRsp(BagRetCode.OK, items); // SORT_BAG_SC_RSP 含新顺序
    }

    /** 出售道具：加 gold、扣堆叠，绑定道具不可售 */
    @Transactional // 扣背包 + 加 player.gold 同事务
    public ProtocolMessage handleSellItem(long playerId, SellItemCsReq req) { // 文件维护说明
        long itemUid = req.getItemUid(); // player_bag_item.id
        if (playerId <= 0) { // 未选角
            return sellMsg(RetCode.PLAYER_NOT_SELECTED, itemUid, 0, 0, null); // SELL_ITEM_SC_RSP 拒绝
        }
        Player player = playerCachePort.findById(playerId); // 读 player.gold
        if (player == null) { // 角色不存在
            return sellMsg(RetCode.PLAYER_NOT_FOUND, itemUid, 0, 0, null); // PLAYER_NOT_FOUND
        }
        Optional<PlayerBagItem> bagOpt = bagItemRepository.findByIdAndPlayerId(itemUid, playerId); // 校验归属
        if (bagOpt.isEmpty()) { // 道具不存在
            return sellMsg(BagRetCode.ITEM_NOT_FOUND, itemUid, 0, 0, null); // ITEM_NOT_FOUND
        }
        PlayerBagItem row = bagOpt.get(); // 待出售背包行
        int c = req.getCount(); // 出售数量
        if (c <= 0 || c > row.getCount()) { // 数量非法
            return sellMsg(BagRetCode.COUNT_NOT_ENOUGH, itemUid, 0, 0, null); // COUNT_NOT_ENOUGH
        }
        if (row.isBound()) { // bind=1 绑定道具
            return sellMsg(BagRetCode.ITEM_BOUND, itemUid, 0, 0, null); // ITEM_BOUND 不可售
        }
        ItemConfig cfg = configQueryService.findItemById(row.getItemConfigId()); // 查 sell_price
        if (cfg == null) { // 配置缺失
            return sellMsg(BagRetCode.ITEM_NOT_FOUND, itemUid, 0, 0, null); // ITEM_NOT_FOUND
        }
        int unitPrice = cfg.getSellPrice() == null ? 0 : cfg.getSellPrice(); // item_config.sell_price 单价
        if (unitPrice <= 0) { // 不可出售道具
            return sellMsg(BagRetCode.SELL_PRICE_INVALID, itemUid, 0, 0, null); // SELL_PRICE_INVALID
        }
        long currency = (long) unitPrice * c; // 总金币 = 单价 × 数量
        long newGold = (player.getGold() == null ? 0L : player.getGold()) + currency; // 累加 player.gold
        player.setGold(newGold); // 写回 gold 字段
        playerCachePort.saveCacheAndMarkDirty(player); // 持久化 gold 并 mark dirty
        consumeStack(row, c); // 扣减背包堆叠
        BagItemInfo remaining = null; // 部分出售时剩余堆叠
        Optional<PlayerBagItem> after = bagItemRepository.findByIdAndPlayerId(itemUid, playerId); // 扣减后是否还有余量
        if (after.isPresent()) { // 行仍存在
            remaining = toProto(after.get(), cfg); // 部分出售后仍有堆叠，组装剩余槽位 protobuf 供 SellItemScRsp 展示
        }
        int gained = currency > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) currency; // 协议 int 上限截断
        itemEventPublisher.publishItemSold(playerId, itemUid, cfg.getId(), c, gained); // MQ 上报出售
        evictBagInfoCache(playerId); // DEL bag:info:{playerId}
        return sellMsg(BagRetCode.OK, itemUid, c, gained, remaining); // SELL_ITEM_SC_RSP 含获得金币
    }

    /** 查背包全部道具并转 Protobuf（跳过配置缺失行） */
    private List<BagItemInfo> listBagItems(long playerId) { // 查背包全部道具并转 Protobuf（跳过配置缺失行）
        List<BagItemInfo> items = new ArrayList<>(); // 背包 Protobuf 收集器
        for (PlayerBagItem row : bagItemRepository.findByPlayerIdOrderBySlotIndexAsc(playerId)) { // 按 slot_index 遍历
            ItemConfig cfg = configQueryService.findItemById(row.getItemConfigId()); // 关联 item_config
            if (cfg != null) { // 配置存在
                items.add(toProto(row, cfg)); // 转 BagItemInfo
            }
        }
        return items; // 完整背包 Protobuf 列表
    }

    /** 扣减堆叠：余量≤0 则 DELETE，否则 UPDATE count */
    private void consumeStack(PlayerBagItem row, int useCount) { // 扣减堆叠：余量≤0 则 DELETE，否则 UPDATE count
        int left = row.getCount() - useCount; // 扣减后剩余数量
        if (left <= 0) { // 整格消耗完
            bagItemRepository.delete(row); // DELETE player_bag_item 行
        } else { // 部分消耗
            row.setCount(left); // 更新 count 字段
            bagItemRepository.save(row); // UPDATE player_bag_item
        }
    }

    /** 构造 GetBagInfoScRsp ProtocolMessage */
    private ProtocolMessage bagInfoMsg(int retcode, int capacity, int usedSlots, List<BagItemInfo> items) { // 构造 GetBagInfoScRsp ProtocolMessage
        var b = GetBagInfoScRsp.newBuilder() // GET_BAG_INFO 响应构建器
                .setRetcode(retcode) // 业务 retcode
                .setCapacity(capacity) // 背包总容量（50）
                .setUsedSlots(usedSlots); // 已用槽位数
        b.setLoading(false); // 完整数据，非预载占位
        items.forEach(b::addItems); // 逐格 addItems
        return new ProtocolMessage(MessageId.GET_BAG_INFO_SC_RSP, b.build().toByteArray()); // msgId=GET_BAG_INFO_SC_RSP
    }

    /** 预加载未就绪时的 loading 占位响应 */
    private ProtocolMessage bagInfoLoadingMsg() { // 预加载未就绪时的 loading 占位响应
        var b = GetBagInfoScRsp.newBuilder() // loading 占位响应
                .setRetcode(BagRetCode.OK) // retcode 仍为 OK
                .setCapacity(DEFAULT_CAPACITY) // 占位容量 50
                .setUsedSlots(0) // 占位已用 0
                .setLoading(true); // loading=true 客户端轮询或等 DataReady Notify
        return new ProtocolMessage(MessageId.GET_BAG_INFO_SC_RSP, b.build().toByteArray()); // msgId=GET_BAG_INFO_SC_RSP
    }

    /** PlayerBagItem + ItemConfig → BagItemInfo Protobuf */
    private BagItemInfo toProto(PlayerBagItem row, ItemConfig cfg) { // PlayerBagItem + ItemConfig → BagItemInfo Protobuf
        return BagItemInfo.newBuilder() // 单格道具 Protobuf
                .setItemUid(row.getId()) // player_bag_item 主键 itemUid
                .setItemId(cfg.getId()) // item_config.id
                .setItemName(cfg.getName()) // 道具显示名
                .setCount(row.getCount()) // 堆叠数量
                .setBind(row.isBound()) // bind=1 不可出售
                .setItemKind(cfg.getKind() == null ? 0 : cfg.getKind()) // item_config.kind
                .setLevelRequired(cfg.getLevelRequired() == null ? 0 : cfg.getLevelRequired()) // 使用等级门槛
                .setDescription(cfg.getDescription() == null ? "" : cfg.getDescription()) // 道具描述
                .build(); // 完成 BagItemInfo
    }

    /** 构造 UseItemScRsp */
    private ProtocolMessage useItemMsg( // 文件维护说明
            int retcode, // USE_ITEM_SC_RSP 业务 retcode
            long itemUid, // 使用的 player_bag_item.id
            int usedCount, // 实际消耗堆叠数量
            ItemEffectResult eff, // 经验/HP/MP 效果，无效果时为 null
            BagInfo bagInfo) { // 可选附带背包快照，当前未使用
        var b = UseItemScRsp.newBuilder() // USE_ITEM 响应构建器
                .setRetcode(retcode) // 业务 retcode
                .setItemUid(itemUid) // 使用的 itemUid
                .setUsedCount(usedCount); // 实际消耗数量
        if (eff != null) { // 有效果数据
            b.setEffectResult(eff); // 经验/HP/MP 效果
        }
        if (bagInfo != null) { // 可选附带背包快照
            b.setBagInfo(bagInfo); // 嵌入 BagInfo
        }
        return new ProtocolMessage(MessageId.USE_ITEM_SC_RSP, b.build().toByteArray()); // msgId=USE_ITEM_SC_RSP
    }

    /** 构造 DiscardItemScRsp */
    private ProtocolMessage discardMsg(int retcode, long itemUid, int discarded) { // 构造 DiscardItemScRsp
        return new ProtocolMessage( // 封装 msgId + Protobuf 字节数组，经 WebSocket 下发客户端
                MessageId.DISCARD_ITEM_SC_RSP, // msgId=DISCARD_ITEM_SC_RSP
                DiscardItemScRsp.newBuilder() // DISCARD_ITEM 响应 Protobuf 构建器
                        .setRetcode(retcode) // 业务 retcode
                        .setItemUid(itemUid) // 丢弃的 itemUid
                        .setDiscardedCount(discarded) // 实际丢弃数量
                        .build() // 完成 DiscardItemScRsp
                        .toByteArray()); // DISCARD_ITEM_SC_RSP msgId + payload 字节
    }

    /** 构造 SortBagScRsp */
    private ProtocolMessage sortRsp(int retcode, List<BagItemInfo> items) { // 构造 SortBagScRsp
        var b = SortBagScRsp.newBuilder().setRetcode(retcode); // SORT_BAG 响应
        items.forEach(b::addItems); // 排序后完整 items
        return new ProtocolMessage(MessageId.SORT_BAG_SC_RSP, b.build().toByteArray()); // msgId=SORT_BAG_SC_RSP
    }

    /** 构造 SellItemScRsp */
    private ProtocolMessage sellMsg(int retcode, long itemUid, int sold, int currency, BagItemInfo remaining) { // 构造 SellItemScRsp
        var b = SellItemScRsp.newBuilder() // SELL_ITEM 响应
                .setRetcode(retcode) // 业务 retcode
                .setItemUid(itemUid) // 出售的 itemUid
                .setSoldCount(sold) // 出售数量
                .setCurrencyGained(currency); // 获得金币
        if (remaining != null) { // 部分出售有余量
            b.setRemainingItems(remaining); // 剩余堆叠 BagItemInfo
        }
        return new ProtocolMessage(MessageId.SELL_ITEM_SC_RSP, b.build().toByteArray()); // msgId=SELL_ITEM_SC_RSP
    }

    /** 活动领奖发道具：ActivityService 经 ActivityItemGrantPort 调用 */
    @Override // ActivityItemGrantPort.grantItemsForActivity 实现
    @Transactional // 多笔 addItemCount 原子提交 player_bag_item
    public int grantItemsForActivity(long playerId, List<ItemReward> rewards) { // 文件维护说明
        if (playerId <= 0) { // 未选角
            return RetCode.PLAYER_NOT_SELECTED; // 无法发奖
        }
        if (playerRepository.findById(playerId).isEmpty()) { // player 不存在
            return RetCode.PLAYER_NOT_FOUND; // 无法发奖
        }
        try { // 逐条活动奖励入库，BAG_FULL 时捕获转 retcode
            for (ItemReward r : rewards) { // 逐条活动奖励
                addItemCount(playerId, r.getItemId(), r.getCount(), true); // itemId+count，bind=1 活动绑定
            }
            evictBagInfoCache(playerId); // 背包变更清 bag:info 缓存
            return BagRetCode.OK; // 发奖成功
        } catch (BagFullException e) { // 50 格已满
            return BagRetCode.BAG_FULL; // 槽位不足
        }
    }

    /** 读 bag:info:{playerId} JSON 缓存 */
    private BagInfoCache readBagInfoCache(long playerId) { // 读 bag:info:{playerId} JSON 缓存
        try { // 读 bag:info:{playerId} JSON，损坏时视为 cache miss
            String json = stringRedisTemplate.opsForValue().get(REDIS_BAG_INFO_KEY_PREFIX + playerId); // GET bag:info:{playerId}
            if (json == null || json.isBlank()) { // 缓存 miss
                return null; // 回退查 DB
            }
            return objectMapper.readValue(json, BagInfoCache.class); // JSON→BagInfoCache
        } catch (Exception ignore) { // JSON 损坏
            return null; // 视为 miss
        }
    }

    /** 写 bag:info:{playerId} JSON 缓存，TTL 5 分钟 */
    private void writeBagInfoCache(long playerId, BagInfoCache cache) { // 写 bag:info:{playerId} JSON 缓存，TTL 5 分钟
        try { // 序列化 BagInfoCache 写 SETEX bag:info:{playerId}
            String json = objectMapper.writeValueAsString(cache); // BagInfoCache→JSON
            String key = Objects.requireNonNull(REDIS_BAG_INFO_KEY_PREFIX + playerId); // 完整键 bag:info:{playerId}
            Duration ttl = Objects.requireNonNull(REDIS_BAG_INFO_TTL); // TTL 5 分钟
            stringRedisTemplate.opsForValue().set(key, Objects.requireNonNull(json), ttl); // SETEX bag:info
        } catch (Exception ignore) { // 写缓存失败不影响主流程
        }
    }

    /** 道具变更后删除 bag:info:{playerId} */
    private void evictBagInfoCache(long playerId) { // 道具变更后删除 bag:info:{playerId}
        stringRedisTemplate.delete(REDIS_BAG_INFO_KEY_PREFIX + playerId); // DEL bag:info:{playerId}
    }

    /** BagItemCache 列表 → BagItemInfo Protobuf 列表 */
    private List<BagItemInfo> restoreCachedItems(List<BagItemCache> caches) { // BagItemCache 列表 → BagItemInfo Protobuf 列表
        if (caches == null || caches.isEmpty()) { // 缓存无道具
            return List.of(); // 空背包
        }
        List<BagItemInfo> items = new ArrayList<>(caches.size()); // 从缓存条目还原 Protobuf
        for (BagItemCache c : caches) { // 逐格还原
            items.add(BagItemInfo.newBuilder() // 从 bag:info JSON 单格还原 BagItemInfo
                    .setItemUid(c.itemUid) // player_bag_item.id
                    .setItemId(c.itemId) // item_config.id
                    .setItemName(c.itemName == null ? "" : c.itemName) // 道具名
                    .setCount(c.count) // 堆叠数
                    .setBind(c.bind) // 绑定标志
                    .setItemKind(c.itemKind) // kind
                    .setLevelRequired(c.levelRequired) // 等级需求
                    .setDescription(c.description == null ? "" : c.description) // 道具描述
                    .build()); // 单格 BagItemInfo 从 bag:info JSON 缓存还原
        }
        return items; // 缓存命中时的 BagItemInfo 列表
    }

    /** BagItemInfo → BagItemCache，供 JSON 缓存序列化 */
    private List<BagItemCache> toCachedItems(List<BagItemInfo> items) { // BagItemInfo → BagItemCache，供 JSON 缓存序列化
        if (items == null || items.isEmpty()) { // 空背包
            return List.of(); // 空缓存条目
        }
        List<BagItemCache> caches = new ArrayList<>(items.size()); // JSON 缓存条目收集器
        for (BagItemInfo i : items) { // 逐格转缓存 DTO
            BagItemCache c = new BagItemCache(); // 单格 bag:info JSON 条目
            c.itemUid = i.getItemUid(); // itemUid
            c.itemId = i.getItemId(); // item_config.id
            c.itemName = i.getItemName(); // 名称
            c.count = i.getCount(); // 数量
            c.bind = i.getBind(); // player_bag_item.bind=1 活动奖励不可出售
            c.itemKind = i.getItemKind(); // kind
            c.levelRequired = i.getLevelRequired(); // 等级需求
            c.description = i.getDescription(); // 描述
            caches.add(c); // 加入缓存列表
        }
        return caches; // 写入 bag:info JSON 的 items 数组
    }

    /** 向背包添加道具：优先合并同 configId 未满堆叠，否则开新槽 */
    private void addItemCount(long playerId, int itemConfigId, int count, boolean bindReward) { // 向背包添加道具：优先合并同 configId 未满堆叠，否则开新槽
        if (count <= 0) { // 零/负数忽略
            return; // 无需添加
        }
        ItemConfig cfg = configQueryService.findItemById(itemConfigId); // 查 stack_limit
        if (cfg == null) { // 未知道具
            throw new IllegalArgumentException("unknown item_config_id=" + itemConfigId); // 活动配置错误
        }
        int stackLimit = cfg.getStackLimit() == null || cfg.getStackLimit() <= 0 ? 1 : cfg.getStackLimit(); // item_config.stack_limit
        int remaining = count; // 待入库数量
        while (remaining > 0) { // 可能需多行（超 stack_limit 或开新槽）
            List<PlayerBagItem> rows = bagItemRepository.findByPlayerIdOrderBySlotIndexAsc(playerId); // 当前全部槽位
            PlayerBagItem merge = null; // 可合并的未满堆叠行
            for (PlayerBagItem row : rows) { // 找同 configId 且 count<stackLimit 的行
                if (row.getItemConfigId() == itemConfigId && row.getCount() < stackLimit) { // 可合并
                    merge = row; // 选中合并目标
                    break; // 找到即停
                }
            }
            if (merge != null) { // 合并到已有行
                int space = stackLimit - merge.getCount(); // 该行剩余堆叠空间
                int add = Math.min(remaining, space); // 本次最多填入数量
                merge.setCount(merge.getCount() + add); // 累加 count
                bagItemRepository.save(merge); // UPDATE player_bag_item
                remaining -= add; // 减少待入库量
            } else { // 需开新槽
                int usedSlots = bagItemRepository.countByPlayerId(playerId); // 当前已用槽位
                if (usedSlots >= DEFAULT_CAPACITY) { // 50 格已满
                    throw new BagFullException(); // 抛出 BAG_FULL
                }
                int maxSlot = rows.stream().mapToInt(r -> r.getSlotIndex() == null ? 0 : r.getSlotIndex()).max().orElse(-1); // 当前最大 slot_index
                int add = Math.min(remaining, stackLimit); // 新行初始 count
                PlayerBagItem row = new PlayerBagItem(); // 新建 player_bag_item 行实体
                row.setPlayerId(playerId); // 归属玩家
                row.setItemConfigId(itemConfigId); // 道具 configId
                row.setCount(add); // 初始堆叠
                row.setBind(bindReward ? 1 : 0); // 活动奖励 bind=1
                row.setSlotIndex(maxSlot + 1); // 新槽位 = max+1
                bagItemRepository.save(row); // INSERT player_bag_item
                remaining -= add; // 减少待入库量
            }
        }
    }

    /** 背包已满时抛出，grantItemsForActivity 捕获转 BagRetCode.BAG_FULL */
    private static final class BagFullException extends RuntimeException { // 背包已满时抛出，grantItemsForActivity 捕获转 BagRetCode.BAG_FULL
        private static final long serialVersionUID = 1L; // 序列化版本号
    }

    /** bag:info:{playerId} Redis JSON 缓存结构 */
    public static class BagInfoCache { // bag:info:{playerId} Redis JSON 缓存结构
        public int retcode; // 快照时的 BagRetCode
        public int capacity; // 背包总容量 DEFAULT_CAPACITY=50
        public int usedSlots; // player_bag_item 已占用槽位数
        public List<BagItemCache> items; // 各格道具 JSON 快照数组

        public static BagInfoCache of(int retcode, int capacity, int usedSlots, List<BagItemCache> items) { // 文件维护说明
            BagInfoCache cache = new BagInfoCache(); // 构造 bag:info JSON 根对象
            cache.retcode = retcode; // 写入 retcode
            cache.capacity = capacity; // 写入容量
            cache.usedSlots = usedSlots; // 写入已用槽位
            cache.items = items; // 写入道具快照数组
            return cache; // 供 ObjectMapper 序列化
        }
    }

    /** 单格道具 JSON 缓存条目 */
    public static class BagItemCache { // 单格道具 JSON 缓存条目
        public long itemUid; // player_bag_item.id
        public int itemId; // item_config.id
        public String itemName; // item_config.name 显示名
        public int count; // player_bag_item.count 堆叠数
        public boolean bind; // player_bag_item.bind 1=不可出售
        public int itemKind; // item_config.kind
        public int levelRequired; // item_config.level_required
        public String description; // item_config.description
    }
}
