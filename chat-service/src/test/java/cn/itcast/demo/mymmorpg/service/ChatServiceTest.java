package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.ChatRetCode;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SendChatMsgCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SendChatMsgScRsp;
import cn.itcast.demo.mymmorpg.support.ChatPolicy;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatService 主流程：世界/私聊/队伍成功路径；禁言/限频；公会未入会拒绝。
 */
public class ChatServiceTest {

    private PlayerCachePort playerCachePort;
    private ChatAsyncDbService chatAsyncDbService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private SetOperations<String, String> setOps;
    private ChatPolicy chatPolicy;
    private ChatEventPublisher chatEventPublisher;
    private PlayerNotificationPort notificationPort;
    private ChatService chatService;
    private final ConcurrentHashMap<String, String> kv = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> sets = new ConcurrentHashMap<>();
    private final AtomicLong rlCounter = new AtomicLong();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        kv.clear();
        sets.clear();
        rlCounter.set(0);
        playerCachePort = mock(PlayerCachePort.class);
        chatAsyncDbService = mock(ChatAsyncDbService.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        setOps = mock(SetOperations.class);
        chatPolicy = mock(ChatPolicy.class);
        chatEventPublisher = mock(ChatEventPublisher.class);
        notificationPort = mock(PlayerNotificationPort.class);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForSet()).thenReturn(setOps);
        when(valueOps.get(anyString())).thenAnswer(inv -> kv.get(inv.getArgument(0)));
        doAnswer(inv -> {
            kv.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString());
        when(valueOps.increment(anyString())).thenAnswer(inv -> rlCounter.incrementAndGet());
        when(setOps.members(anyString())).thenAnswer(inv -> sets.getOrDefault(inv.getArgument(0), Set.of()));
        when(setOps.add(anyString(), any())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            sets.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add((String) inv.getArgument(1));
            return 1L;
        });
        when(chatPolicy.filterContent(anyInt(), anyInt(), anyString())).thenAnswer(inv -> inv.getArgument(2));
        when(chatPolicy.isChannelUnlocked(anyLong(), anyInt())).thenReturn(true);

        Player sender = new Player();
        sender.setId(1001L);
        sender.setName("勇者");
        when(playerCachePort.findById(1001L)).thenReturn(sender);

        chatService = new ChatService(playerCachePort, chatAsyncDbService, redis,
                chatPolicy, chatEventPublisher, notificationPort);
    }

    @Test
    public void world_success_broadcasts() throws Exception {
        ProtocolMessage msg = chatService.handleSendChat(1001L, req(ChatService.CHANNEL_WORLD, 0, "世界你好"));
        SendChatMsgScRsp rsp = SendChatMsgScRsp.parseFrom(msg.payload());
        assertThat(msg.msgId()).isEqualTo(MessageId.SEND_CHAT_MSG_SC_RSP);
        assertThat(rsp.getRetcode()).isEqualTo(ChatRetCode.OK);
        verify(notificationPort).broadcastAllOnline(eq(MessageId.CHAT_MSG_SC_NOTIFY), any(), eq(1001L));
        verify(chatEventPublisher).publishChatSent(eq(1001L), eq(ChatService.CHANNEL_WORLD),
                eq(0L), eq(1), eq("世界你好"), anyLong());
    }

    @Test
    public void private_success_sendsToTarget() throws Exception {
        when(playerCachePort.existsById(2002L)).thenReturn(true);
        when(notificationPort.isOnline(2002L)).thenReturn(true);

        ProtocolMessage msg = chatService.handleSendChat(1001L, req(ChatService.CHANNEL_PRIVATE, 2002L, "私聊"));
        SendChatMsgScRsp rsp = SendChatMsgScRsp.parseFrom(msg.payload());
        assertThat(rsp.getRetcode()).isEqualTo(ChatRetCode.OK);
        verify(notificationPort).send(eq(2002L), eq(MessageId.CHAT_MSG_SC_NOTIFY), any());
    }

    @Test
    public void team_success_sendsToOnlineMembers() throws Exception {
        kv.put("chat:player:team:1001", "team-a");
        sets.put("chat:team:roster:team-a", Set.of("1001", "1002"));
        when(notificationPort.isOnline(1001L)).thenReturn(true);
        when(notificationPort.isOnline(1002L)).thenReturn(true);

        ProtocolMessage msg = chatService.handleSendChat(1001L, req(ChatService.CHANNEL_TEAM, 0, "队内"));
        SendChatMsgScRsp rsp = SendChatMsgScRsp.parseFrom(msg.payload());
        assertThat(rsp.getRetcode()).isEqualTo(ChatRetCode.OK);
        verify(notificationPort).sendToPlayers(any(), eq(MessageId.CHAT_MSG_SC_NOTIFY), any());
    }

    @Test
    public void muted_rejects() throws Exception {
        kv.put("chat:mute:until:1001", String.valueOf(System.currentTimeMillis() + 60_000L));
        ProtocolMessage msg = chatService.handleSendChat(1001L, req(ChatService.CHANNEL_WORLD, 0, "被禁"));
        SendChatMsgScRsp rsp = SendChatMsgScRsp.parseFrom(msg.payload());
        assertThat(rsp.getRetcode()).isEqualTo(ChatRetCode.MUTED);
        verify(notificationPort, never()).broadcastAllOnline(anyInt(), any(), any());
    }

    @Test
    public void rateLimit_rejects() throws Exception {
        rlCounter.set(5); // next increment -> 6 > RATE_MAX
        ProtocolMessage msg = chatService.handleSendChat(1001L, req(ChatService.CHANNEL_WORLD, 0, "刷屏"));
        SendChatMsgScRsp rsp = SendChatMsgScRsp.parseFrom(msg.payload());
        assertThat(rsp.getRetcode()).isEqualTo(ChatRetCode.RATE_LIMIT);
    }

    @Test
    public void guild_withoutMembership_rejects() throws Exception {
        ProtocolMessage msg = chatService.handleSendChat(1001L, req(ChatService.CHANNEL_GUILD, 0, "公会"));
        SendChatMsgScRsp rsp = SendChatMsgScRsp.parseFrom(msg.payload());
        assertThat(rsp.getRetcode()).isEqualTo(ChatRetCode.CHANNEL_UNAVAILABLE);
        assertThat(rsp.getErrorMsg()).contains("未加入公会");
    }

    private static SendChatMsgCsReq req(int channel, long targetId, String content) {
        return SendChatMsgCsReq.newBuilder()
                .setChannel(channel)
                .setMsgType(ChatService.MSG_TEXT)
                .setTargetId(targetId)
                .setContent(content)
                .setTimestamp(System.currentTimeMillis())
                .build();
    }
}
