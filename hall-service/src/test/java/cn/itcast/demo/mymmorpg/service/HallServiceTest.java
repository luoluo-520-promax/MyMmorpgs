/**
 * 文件说明：HallService 单元测试类。
 * 职责：使用 Mockito 模拟依赖，验证好友、邮件、排行榜核心路径与异常路径，并输出中文测试日志。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.port.MailItemGrantPort;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AddFriendCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AddFriendScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimMailCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimMailScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetFriendListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetFriendListScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetMailListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetMailListScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetRankingCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetRankingScRsp;
import cn.itcast.demo.mymmorpg.entity.PlayerFriend;
import cn.itcast.demo.mymmorpg.entity.PlayerMail;
import cn.itcast.demo.mymmorpg.repository.PlayerFriendRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerMailRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.support.RankingScoreStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HallService 单元测试：Mock 外部依赖，日志输出具体入参与断言结果。
 */
public class HallServiceTest {

    private static final Logger log = LoggerFactory.getLogger(HallServiceTest.class);

    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private PlayerFriendRepository playerFriendRepository;
    @Mock
    private PlayerMailRepository playerMailRepository;
    @Mock
    private ObjectProvider<PlayerProgressPort> playerProgressPortProvider;
    @Mock
    private ObjectProvider<PlayerCachePort> playerCachePortProvider;
    @Mock
    private ObjectProvider<MailItemGrantPort> mailItemGrantPortProvider;
    @Mock
    private PlayerProgressPort playerProgressPort;
    @Mock
    private PlayerCachePort playerCachePort;
    @Mock
    private MailItemGrantPort mailItemGrantPort;
    @Mock
    private PlayerPresenceQuery playerPresenceQuery;

    private AutoCloseable mocks;
    private HallService hallService;
    private final Map<Long, List<PlayerFriend>> friendsStore = new ConcurrentHashMap<>();
    private final Map<Long, List<PlayerMail>> mailsStore = new ConcurrentHashMap<>();
    private final AtomicLong mailIdSeq = new AtomicLong(1);

    @BeforeMethod
    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        friendsStore.clear();
        mailsStore.clear();
        mailIdSeq.set(1);
        when(playerProgressPortProvider.getIfAvailable()).thenReturn(playerProgressPort);
        when(playerCachePortProvider.getIfAvailable()).thenReturn(playerCachePort);
        when(mailItemGrantPortProvider.getIfAvailable()).thenReturn(mailItemGrantPort);
        lenient().when(playerPresenceQuery.isOnline(anyLong())).thenReturn(false);
        stubFriendRepo();
        stubMailRepo();
        hallService = new HallService(
                playerRepository, playerFriendRepository, playerMailRepository,
                playerProgressPortProvider, playerCachePortProvider, mailItemGrantPortProvider,
                playerPresenceQuery, new RankingScoreStore(), new ObjectMapper());
        log.info("[测试前置] HallService 已初始化 | welcomeMailGold=100 | rankingLimitMax=50");
    }

    private void stubFriendRepo() {
        lenient().when(playerFriendRepository.findByPlayerId(anyLong())).thenAnswer(inv -> {
            long playerId = inv.getArgument(0);
            return new ArrayList<>(friendsStore.getOrDefault(playerId, List.of()));
        });
        lenient().when(playerFriendRepository.existsByPlayerIdAndFriendId(anyLong(), anyLong())).thenAnswer(inv -> {
            long playerId = inv.getArgument(0);
            long friendId = inv.getArgument(1);
            return friendsStore.getOrDefault(playerId, List.of()).stream()
                    .anyMatch(f -> friendId == f.getFriendId());
        });
        lenient().when(playerFriendRepository.save(any(PlayerFriend.class))).thenAnswer(inv -> {
            PlayerFriend rel = inv.getArgument(0);
            friendsStore.computeIfAbsent(rel.getPlayerId(), id -> new ArrayList<>()).add(rel);
            return rel;
        });
    }

    private void stubMailRepo() {
        lenient().when(playerMailRepository.countByPlayerId(anyLong())).thenAnswer(inv -> {
            long playerId = inv.getArgument(0);
            return (long) mailsStore.getOrDefault(playerId, List.of()).size();
        });
        lenient().when(playerMailRepository.findByPlayerIdOrderByIdAsc(anyLong())).thenAnswer(inv -> {
            long playerId = inv.getArgument(0);
            return new ArrayList<>(mailsStore.getOrDefault(playerId, List.of()));
        });
        lenient().when(playerMailRepository.save(any(PlayerMail.class))).thenAnswer(inv -> {
            PlayerMail mail = inv.getArgument(0);
            if (mail.getId() == null) {
                mail.setId(mailIdSeq.getAndIncrement());
                mailsStore.computeIfAbsent(mail.getPlayerId(), id -> new ArrayList<>()).add(mail);
            }
            return mail;
        });
        lenient().when(playerMailRepository.findByIdAndPlayerId(anyLong(), anyLong())).thenAnswer(inv -> {
            long mailId = inv.getArgument(0);
            long playerId = inv.getArgument(1);
            return mailsStore.getOrDefault(playerId, List.of()).stream()
                    .filter(m -> mailId == m.getId())
                    .findFirst();
        });
    }

    @AfterMethod
    @AfterEach
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleGetFriendList_invalidPlayer() throws Exception {
        long playerId = 0L;
        log.info("[测试开始] 场景=好友列表未选角色 | playerId={} | 期望retcode={}",
                playerId, RetCode.PLAYER_NOT_SELECTED);

        ProtocolMessage msg = hallService.handleGetFriendList(playerId, GetFriendListCsReq.newBuilder().build());
        GetFriendListScRsp rsp = GetFriendListScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=好友列表未选角色 | msgId={} | retcode={} | friendsCount={}",
                msg.msgId(), rsp.getRetcode(), rsp.getFriendsCount());
        assertThat(msg.msgId()).isEqualTo(MessageId.GET_FRIEND_LIST_SC_RSP);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
        assertThat(rsp.getFriendsCount()).isZero();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleGetFriendList_emptySuccess() throws Exception {
        long playerId = 10L;
        log.info("[测试开始] 场景=好友列表为空 | playerId={} | 期望retcode={} | 期望friendsCount=0",
                playerId, RetCode.OK);

        ProtocolMessage msg = hallService.handleGetFriendList(playerId, GetFriendListCsReq.newBuilder().build());
        GetFriendListScRsp rsp = GetFriendListScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=好友列表为空 | retcode={} | friendsCount={}",
                rsp.getRetcode(), rsp.getFriendsCount());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getFriendsCount()).isZero();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleAddFriend_invalidPlayer() throws Exception {
        long playerId = -1L;
        long targetPlayerId = 20L;
        log.info("[测试开始] 场景=加好友未选角色 | playerId={} | targetPlayerId={} | 期望retcode={}",
                playerId, targetPlayerId, RetCode.PLAYER_NOT_SELECTED);

        AddFriendCsReq req = AddFriendCsReq.newBuilder().setTargetPlayerId(targetPlayerId).build();
        AddFriendScRsp rsp = AddFriendScRsp.parseFrom(hallService.handleAddFriend(playerId, req).payload());

        log.info("[测试断言] 场景=加好友未选角色 | retcode={} | hasFriend={}",
                rsp.getRetcode(), rsp.hasFriend());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
        assertThat(rsp.hasFriend()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleAddFriend_invalidTarget() throws Exception {
        long playerId = 10L;
        long targetPlayerId = 10L;
        log.info("[测试开始] 场景=加好友目标非法 | playerId={} | targetPlayerId={} | 期望retcode={}",
                playerId, targetPlayerId, RetCode.FRIEND_NOT_FOUND);

        AddFriendCsReq req = AddFriendCsReq.newBuilder().setTargetPlayerId(targetPlayerId).build();
        AddFriendScRsp rsp = AddFriendScRsp.parseFrom(hallService.handleAddFriend(playerId, req).payload());

        log.info("[测试断言] 场景=加好友目标非法 | retcode={} | hasFriend={}",
                rsp.getRetcode(), rsp.hasFriend());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.FRIEND_NOT_FOUND);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleAddFriend_targetNotFound() throws Exception {
        long playerId = 10L;
        long targetPlayerId = 999L;
        log.info("[测试开始] 场景=加好友目标不存在 | playerId={} | targetPlayerId={} | 期望retcode={}",
                playerId, targetPlayerId, RetCode.PLAYER_NOT_FOUND);

        when(playerRepository.existsById(targetPlayerId)).thenReturn(false);
        AddFriendCsReq req = AddFriendCsReq.newBuilder().setTargetPlayerId(targetPlayerId).build();
        AddFriendScRsp rsp = AddFriendScRsp.parseFrom(hallService.handleAddFriend(playerId, req).payload());

        log.info("[测试断言] 场景=加好友目标不存在 | retcode={} | existsById={}",
                rsp.getRetcode(), false);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_FOUND);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleAddFriend_successAndAlreadyExists() throws Exception {
        long playerId = 10L;
        long targetPlayerId = 20L;
        Player target = buildPlayer(targetPlayerId, "friend-20", 5);
        log.info("[测试开始] 场景=加好友成功 | playerId={} | targetPlayerId={} | targetName={} | targetLevel={} | 期望retcode={}",
                playerId, targetPlayerId, target.getName(), target.getLevel(), RetCode.OK);

        when(playerRepository.existsById(targetPlayerId)).thenReturn(true);
        when(playerRepository.findById(targetPlayerId)).thenReturn(Optional.of(target));

        AddFriendCsReq req = AddFriendCsReq.newBuilder().setTargetPlayerId(targetPlayerId).build();
        AddFriendScRsp okRsp = AddFriendScRsp.parseFrom(hallService.handleAddFriend(playerId, req).payload());
        log.info("[测试断言] 场景=加好友成功 | retcode={} | friendId={} | friendName={} | friendLevel={}",
                okRsp.getRetcode(), okRsp.getFriend().getPlayerId(),
                okRsp.getFriend().getPlayerName(), okRsp.getFriend().getLevel());
        assertThat(okRsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(okRsp.getFriend().getPlayerId()).isEqualTo(targetPlayerId);
        assertThat(okRsp.getFriend().getPlayerName()).isEqualTo("friend-20");
        assertThat(okRsp.getFriend().getLevel()).isEqualTo(5);

        log.info("[测试开始] 场景=加好友已存在 | playerId={} | targetPlayerId={} | 期望retcode={}",
                playerId, targetPlayerId, RetCode.FRIEND_ALREADY_EXISTS);
        AddFriendScRsp dupRsp = AddFriendScRsp.parseFrom(hallService.handleAddFriend(playerId, req).payload());
        log.info("[测试断言] 场景=加好友已存在 | retcode={} | hasFriend={}",
                dupRsp.getRetcode(), dupRsp.hasFriend());
        assertThat(dupRsp.getRetcode()).isEqualTo(RetCode.FRIEND_ALREADY_EXISTS);

        GetFriendListScRsp listRsp = GetFriendListScRsp.parseFrom(
                hallService.handleGetFriendList(playerId, GetFriendListCsReq.newBuilder().build()).payload());
        log.info("[测试断言] 场景=加好友后查列表 | playerId={} | friendsCount={} | firstFriendId={}",
                playerId, listRsp.getFriendsCount(),
                listRsp.getFriendsCount() > 0 ? listRsp.getFriends(0).getPlayerId() : -1L);
        assertThat(listRsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(listRsp.getFriendsCount()).isEqualTo(1);
        assertThat(listRsp.getFriends(0).getPlayerId()).isEqualTo(targetPlayerId);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleGetMailList_invalidPlayer() throws Exception {
        long playerId = 0L;
        log.info("[测试开始] 场景=邮件列表未选角色 | playerId={} | 期望retcode={}",
                playerId, RetCode.PLAYER_NOT_SELECTED);

        GetMailListScRsp rsp = GetMailListScRsp.parseFrom(
                hallService.handleGetMailList(playerId, GetMailListCsReq.newBuilder().build()).payload());

        log.info("[测试断言] 场景=邮件列表未选角色 | retcode={} | mailsCount={}",
                rsp.getRetcode(), rsp.getMailsCount());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
        assertThat(rsp.getMailsCount()).isZero();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleGetMailList_seedsWelcomeMail() throws Exception {
        long playerId = 30L;
        int expectedGold = 100;
        log.info("[测试开始] 场景=首次打开邮箱发欢迎邮件 | playerId={} | 期望retcode={} | 期望mailsCount=1 | 期望gold={}",
                playerId, RetCode.OK, expectedGold);

        GetMailListScRsp rsp = GetMailListScRsp.parseFrom(
                hallService.handleGetMailList(playerId, GetMailListCsReq.newBuilder().build()).payload());

        log.info("[测试断言] 场景=首次打开邮箱发欢迎邮件 | retcode={} | mailsCount={} | mailId={} | title={} | claimed={} | gold={}",
                rsp.getRetcode(), rsp.getMailsCount(),
                rsp.getMails(0).getMailId(), rsp.getMails(0).getTitle(),
                rsp.getMails(0).getClaimed(),
                rsp.getMails(0).getAttachmentsCount() > 0 ? rsp.getMails(0).getAttachments(0).getGold() : 0);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getMailsCount()).isEqualTo(1);
        assertThat(rsp.getMails(0).getClaimed()).isFalse();
        assertThat(rsp.getMails(0).getAttachments(0).getGold()).isEqualTo(expectedGold);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleClaimMail_invalidPlayer() throws Exception {
        long playerId = 0L;
        long mailId = 1L;
        log.info("[测试开始] 场景=领邮件未选角色 | playerId={} | mailId={} | 期望retcode={}",
                playerId, mailId, RetCode.PLAYER_NOT_SELECTED);

        ClaimMailCsReq req = ClaimMailCsReq.newBuilder().setMailId(mailId).build();
        ClaimMailScRsp rsp = ClaimMailScRsp.parseFrom(hallService.handleClaimMail(playerId, req).payload());

        log.info("[测试断言] 场景=领邮件未选角色 | retcode={} | mailId={} | goldGained={}",
                rsp.getRetcode(), rsp.getMailId(), rsp.getGoldGained());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
        assertThat(rsp.getGoldGained()).isZero();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleClaimMail_mailNotFound() throws Exception {
        long playerId = 40L;
        long mailId = 999L;
        log.info("[测试开始] 场景=领邮件不存在 | playerId={} | mailId={} | 期望retcode={}",
                playerId, mailId, RetCode.MAIL_NOT_FOUND);

        ClaimMailCsReq req = ClaimMailCsReq.newBuilder().setMailId(mailId).build();
        ClaimMailScRsp rsp = ClaimMailScRsp.parseFrom(hallService.handleClaimMail(playerId, req).payload());

        log.info("[测试断言] 场景=领邮件不存在 | retcode={} | mailId={} | goldGained={}",
                rsp.getRetcode(), rsp.getMailId(), rsp.getGoldGained());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.MAIL_NOT_FOUND);
        assertThat(rsp.getMailId()).isEqualTo(mailId);
        assertThat(rsp.getGoldGained()).isZero();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleClaimMail_successThenAlreadyClaimed() throws Exception {
        long playerId = 50L;
        Player player = buildPlayer(playerId, "claimer-50", 3);
        log.info("[测试开始] 场景=领欢迎邮件成功 | playerId={} | playerName={} | 期望retcode={} | 期望goldGained=100",
                playerId, player.getName(), RetCode.OK);

        GetMailListScRsp mailList = GetMailListScRsp.parseFrom(
                hallService.handleGetMailList(playerId, GetMailListCsReq.newBuilder().build()).payload());
        long mailId = mailList.getMails(0).getMailId();
        log.info("[测试准备] 场景=领欢迎邮件成功 | playerId={} | mailId={} | claimed={}",
                playerId, mailId, mailList.getMails(0).getClaimed());

        when(playerCachePort.findById(playerId)).thenReturn(player);

        ClaimMailCsReq req = ClaimMailCsReq.newBuilder().setMailId(mailId).build();
        ClaimMailScRsp okRsp = ClaimMailScRsp.parseFrom(hallService.handleClaimMail(playerId, req).payload());
        log.info("[测试断言] 场景=领欢迎邮件成功 | retcode={} | mailId={} | goldGained={}",
                okRsp.getRetcode(), okRsp.getMailId(), okRsp.getGoldGained());
        assertThat(okRsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(okRsp.getMailId()).isEqualTo(mailId);
        assertThat(okRsp.getGoldGained()).isEqualTo(100);
        verify(playerProgressPort).addGold(eq(player), eq(100L));

        log.info("[测试开始] 场景=邮件已领取 | playerId={} | mailId={} | 期望retcode={}",
                playerId, mailId, RetCode.MAIL_ALREADY_CLAIMED);
        ClaimMailScRsp claimedRsp = ClaimMailScRsp.parseFrom(hallService.handleClaimMail(playerId, req).payload());
        log.info("[测试断言] 场景=邮件已领取 | retcode={} | mailId={} | goldGained={}",
                claimedRsp.getRetcode(), claimedRsp.getMailId(), claimedRsp.getGoldGained());
        assertThat(claimedRsp.getRetcode()).isEqualTo(RetCode.MAIL_ALREADY_CLAIMED);
        assertThat(claimedRsp.getGoldGained()).isZero();
        verify(playerProgressPort, times(1)).addGold(any(Player.class), eq(100L));
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleClaimMail_wrongMailIdAfterSeed() throws Exception {
        long playerId = 55L;
        long wrongMailId = 888L;
        log.info("[测试开始] 场景=邮箱有信但mailId不匹配 | playerId={} | wrongMailId={} | 期望retcode={}",
                playerId, wrongMailId, RetCode.MAIL_NOT_FOUND);

        GetMailListScRsp mailList = GetMailListScRsp.parseFrom(
                hallService.handleGetMailList(playerId, GetMailListCsReq.newBuilder().build()).payload());
        long realMailId = mailList.getMails(0).getMailId();
        log.info("[测试准备] 场景=邮箱有信但mailId不匹配 | playerId={} | realMailId={} | wrongMailId={}",
                playerId, realMailId, wrongMailId);

        ClaimMailCsReq req = ClaimMailCsReq.newBuilder().setMailId(wrongMailId).build();
        ClaimMailScRsp rsp = ClaimMailScRsp.parseFrom(hallService.handleClaimMail(playerId, req).payload());

        log.info("[测试断言] 场景=邮箱有信但mailId不匹配 | retcode={} | mailId={} | goldGained={}",
                rsp.getRetcode(), rsp.getMailId(), rsp.getGoldGained());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.MAIL_NOT_FOUND);
        assertThat(rsp.getMailId()).isEqualTo(wrongMailId);
        verify(playerProgressPort, never()).addGold(any(), anyLong());
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleGetRanking_invalidPlayer() throws Exception {
        long playerId = 0L;
        int rankingType = 1;
        int limit = 10;
        log.info("[测试开始] 场景=排行榜未选角色 | playerId={} | rankingType={} | limit={} | 期望retcode={}",
                playerId, rankingType, limit, RetCode.PLAYER_NOT_SELECTED);

        GetRankingCsReq req = GetRankingCsReq.newBuilder().setRankingType(rankingType).setLimit(limit).build();
        GetRankingScRsp rsp = GetRankingScRsp.parseFrom(hallService.handleGetRanking(playerId, req).payload());

        log.info("[测试断言] 场景=排行榜未选角色 | retcode={} | rankingType={} | entriesCount={}",
                rsp.getRetcode(), rsp.getRankingType(), rsp.getEntriesCount());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
        assertThat(rsp.getRankingType()).isEqualTo(rankingType);
        assertThat(rsp.getEntriesCount()).isZero();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleGetRanking_levelSuccess() throws Exception {
        long playerId = 60L;
        int rankingType = 1;
        int limit = 2;
        Player p1 = buildPlayer(1L, "top-level", 99);
        p1.setPowerScore(100);
        Player p2 = buildPlayer(2L, "second-level", 80);
        p2.setPowerScore(200);
        Player p3 = buildPlayer(3L, "third-level", 70);
        p3.setPowerScore(300);
        log.info("[测试开始] 场景=等级排行榜 | playerId={} | rankingType={} | limit={} | candidates={} | 期望retcode={}",
                playerId, rankingType, limit, 3, RetCode.OK);

        when(playerRepository.findTop50ByOrderByLevelDesc()).thenReturn(List.of(p1, p2, p3));
        GetRankingCsReq req = GetRankingCsReq.newBuilder().setRankingType(rankingType).setLimit(limit).build();
        GetRankingScRsp rsp = GetRankingScRsp.parseFrom(hallService.handleGetRanking(playerId, req).payload());

        log.info("[测试断言] 场景=等级排行榜 | retcode={} | rankingType={} | entriesCount={} | rank1Id={} | rank1Level={} | rank2Id={} | rank2Level={}",
                rsp.getRetcode(), rsp.getRankingType(), rsp.getEntriesCount(),
                rsp.getEntries(0).getPlayerId(), rsp.getEntries(0).getLevel(),
                rsp.getEntries(1).getPlayerId(), rsp.getEntries(1).getLevel());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getRankingType()).isEqualTo(rankingType);
        assertThat(rsp.getEntriesCount()).isEqualTo(limit);
        assertThat(rsp.getEntries(0).getPlayerId()).isEqualTo(1L);
        assertThat(rsp.getEntries(0).getLevel()).isEqualTo(99);
        assertThat(rsp.getEntries(1).getPlayerId()).isEqualTo(2L);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleGetRanking_powerDefaultLimit() throws Exception {
        long playerId = 70L;
        int rankingType = 2;
        int limit = 0;
        Player p1 = buildPlayer(11L, "power-king", 10);
        p1.setPowerScore(9999);
        log.info("[测试开始] 场景=战力排行榜默认limit | playerId={} | rankingType={} | limit={} | 期望默认上限=50 | 期望retcode={}",
                playerId, rankingType, limit, RetCode.OK);

        when(playerRepository.findTop50ByOrderByPowerScoreDesc()).thenReturn(List.of(p1));
        GetRankingCsReq req = GetRankingCsReq.newBuilder().setRankingType(rankingType).setLimit(limit).build();
        GetRankingScRsp rsp = GetRankingScRsp.parseFrom(hallService.handleGetRanking(playerId, req).payload());

        log.info("[测试断言] 场景=战力排行榜默认limit | retcode={} | rankingType={} | entriesCount={} | rank1Id={} | powerScore={}",
                rsp.getRetcode(), rsp.getRankingType(), rsp.getEntriesCount(),
                rsp.getEntries(0).getPlayerId(), rsp.getEntries(0).getPowerScore());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getRankingType()).isEqualTo(rankingType);
        assertThat(rsp.getEntriesCount()).isEqualTo(1);
        assertThat(rsp.getEntries(0).getPowerScore()).isEqualTo(9999);
        verify(playerRepository).findTop50ByOrderByPowerScoreDesc();
        verify(playerRepository, never()).findTop50ByOrderByLevelDesc();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleGetRanking_redisMiss_backfillsTop50ThenHitsRedis() throws Exception {
        long playerId = 1L;
        Player p1 = buildPlayer(101L, "甲", 30);
        p1.setPowerScore(100);
        Player p2 = buildPlayer(102L, "乙", 20);
        p2.setPowerScore(80);
        RankingScoreStore store = mock(RankingScoreStore.class);
        when(store.available()).thenReturn(true);
        when(store.top(eq(RankingScoreStore.KEY_LEVEL), anyInt()))
                .thenReturn(List.of())
                .thenReturn(List.of(
                        new RankingScoreStore.Entry(101L, 30),
                        new RankingScoreStore.Entry(102L, 20)));
        when(playerRepository.findTop50ByOrderByLevelDesc()).thenReturn(List.of(p1, p2));
        when(playerRepository.findById(101L)).thenReturn(Optional.of(p1));
        when(playerRepository.findById(102L)).thenReturn(Optional.of(p2));

        hallService = new HallService(
                playerRepository, playerFriendRepository, playerMailRepository,
                playerProgressPortProvider, playerCachePortProvider, mailItemGrantPortProvider,
                playerPresenceQuery, store, new ObjectMapper());

        GetRankingCsReq req = GetRankingCsReq.newBuilder().setRankingType(1).setLimit(50).build();
        GetRankingScRsp missRsp = GetRankingScRsp.parseFrom(hallService.handleGetRanking(playerId, req).payload());
        assertThat(missRsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(missRsp.getEntriesCount()).isEqualTo(2);
        verify(store).updatePlayer(101L, 30, 100);
        verify(store).updatePlayer(102L, 20, 80);

        GetRankingScRsp hitRsp = GetRankingScRsp.parseFrom(hallService.handleGetRanking(playerId, req).payload());
        assertThat(hitRsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(hitRsp.getEntriesCount()).isEqualTo(2);
        assertThat(hitRsp.getEntries(0).getPlayerId()).isEqualTo(101L);
        verify(playerRepository, times(1)).findTop50ByOrderByLevelDesc();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleAddFriend_onlineFlagFromPresence() throws Exception {
        long playerId = 10L;
        long targetPlayerId = 21L;
        Player target = buildPlayer(targetPlayerId, "online-friend", 6);
        when(playerRepository.existsById(targetPlayerId)).thenReturn(true);
        when(playerRepository.findById(targetPlayerId)).thenReturn(Optional.of(target));
        when(playerPresenceQuery.isOnline(targetPlayerId)).thenReturn(true);

        AddFriendScRsp rsp = AddFriendScRsp.parseFrom(
                hallService.handleAddFriend(playerId, AddFriendCsReq.newBuilder()
                        .setTargetPlayerId(targetPlayerId).build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getFriend().getOnline()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleClaimMail_itemAttachments_grantsViaPort() throws Exception {
        long playerId = 80L;
        PlayerMail mail = new PlayerMail();
        mail.setId(501L);
        mail.setPlayerId(playerId);
        mail.setTitle("道具附件");
        mail.setBody("body");
        mail.setGold(0);
        mail.setClaimed(false);
        mail.setCreatedAt(System.currentTimeMillis());
        mail.setAttachmentsJson("[{\"itemId\":1001,\"count\":2}]");
        mailsStore.put(playerId, new ArrayList<>(List.of(mail)));
        when(mailItemGrantPort.grantItemsForMail(eq(playerId), eq(501L), any())).thenReturn(0);

        ClaimMailScRsp rsp = ClaimMailScRsp.parseFrom(
                hallService.handleClaimMail(playerId, ClaimMailCsReq.newBuilder()
                        .setMailId(501L).build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(mail.getClaimed()).isTrue();
        verify(mailItemGrantPort).grantItemsForMail(eq(playerId), eq(501L), any());
        verify(playerProgressPort, never()).addGold(any(), anyLong());
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleClaimMail_itemGrantFails_keepsUnclaimed() throws Exception {
        long playerId = 81L;
        PlayerMail mail = new PlayerMail();
        mail.setId(502L);
        mail.setPlayerId(playerId);
        mail.setTitle("道具附件失败");
        mail.setBody("body");
        mail.setGold(50);
        mail.setClaimed(false);
        mail.setCreatedAt(System.currentTimeMillis());
        mail.setAttachmentsJson("[{\"itemId\":1001,\"count\":1}]");
        mailsStore.put(playerId, new ArrayList<>(List.of(mail)));
        when(mailItemGrantPort.grantItemsForMail(eq(playerId), eq(502L), any())).thenReturn(1);

        ClaimMailScRsp rsp = ClaimMailScRsp.parseFrom(
                hallService.handleClaimMail(playerId, ClaimMailCsReq.newBuilder()
                        .setMailId(502L).build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.INTERNAL_ERROR);
        assertThat(mail.getClaimed()).isFalse();
        verify(playerProgressPort, never()).addGold(any(), anyLong());
    }

    private static Player buildPlayer(long id, String name, int level) {
        Player player = new Player();
        player.setId(id);
        player.setName(name);
        player.setLevel(level);
        player.setPowerScore(0);
        player.setGold(0L);
        return player;
    }
}
