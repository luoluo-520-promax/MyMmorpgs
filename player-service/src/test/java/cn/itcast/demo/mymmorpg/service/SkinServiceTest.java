package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EquipSkinCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EquipSkinScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetWardrobeCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetWardrobeScRsp;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerSkinOwnedRepository;
import cn.itcast.demo.mymmorpg.skin.SkinConfig;
import cn.itcast.demo.mymmorpg.skin.SkinConfigRepository;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SkinServiceTest {

    private SkinConfigRepository skinConfigRepository;
    private PlayerRepository playerRepository;
    private PlayerSkinOwnedRepository playerSkinOwnedRepository;
    private SkinService skinService;

    @BeforeMethod
    public void setUp() {
        skinConfigRepository = mock(SkinConfigRepository.class);
        playerRepository = mock(PlayerRepository.class);
        playerSkinOwnedRepository = mock(PlayerSkinOwnedRepository.class);
        skinService = new SkinService(skinConfigRepository, playerRepository, playerSkinOwnedRepository);

        SkinConfig def = new SkinConfig();
        def.setSkinId(1001);
        def.setName("默认外观");
        def.setDefault(true);
        def.setEnabled(true);
        SkinConfig paid = new SkinConfig();
        paid.setSkinId(1002);
        paid.setItemId(71002);
        paid.setName("旅人披风");
        paid.setEnabled(true);
        when(skinConfigRepository.listEnabled()).thenReturn(List.of(def, paid));
        when(skinConfigRepository.find(1001)).thenReturn(def);
        when(skinConfigRepository.find(1002)).thenReturn(paid);
        when(skinConfigRepository.findByItemId(71002)).thenReturn(paid);
    }

    @Test
    public void unlockByItem_grantsOwnedSkin() {
        when(playerSkinOwnedRepository.existsByPlayerIdAndSkinId(1L, 1002)).thenReturn(false);
        when(playerSkinOwnedRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int rc = skinService.unlockByItem(1L, 71002);
        assertThat(rc).isEqualTo(RetCode.OK);
        verify(playerSkinOwnedRepository).save(any());
    }

    @Test
    public void unlockByItem_invalidItem() {
        when(skinConfigRepository.findByItemId(99999)).thenReturn(null);
        assertThat(skinService.unlockByItem(1L, 99999)).isEqualTo(RetCode.SKIN_ITEM_INVALID);
    }

    @Test
    public void wardrobe_listsSkins() throws Exception {
        Player p = new Player();
        p.setId(1L);
        p.setEquippedSkinId(0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));
        when(playerSkinOwnedRepository.findByPlayerId(1L)).thenReturn(List.of());
        when(playerSkinOwnedRepository.existsByPlayerIdAndSkinId(anyLong(), anyInt())).thenReturn(false);

        GetWardrobeScRsp rsp = GetWardrobeScRsp.parseFrom(
                skinService.handleGetWardrobe(1L, GetWardrobeCsReq.getDefaultInstance()).payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getSkinsCount()).isEqualTo(2);
        assertThat(rsp.getEquippedSkinId()).isEqualTo(1001);
    }

    @Test
    public void equip_requiresOwnership() throws Exception {
        Player p = new Player();
        p.setId(1L);
        p.setEquippedSkinId(1001);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));
        when(playerSkinOwnedRepository.existsByPlayerIdAndSkinId(1L, 1002)).thenReturn(false);

        EquipSkinScRsp rsp = EquipSkinScRsp.parseFrom(
                skinService.handleEquipSkin(1L, EquipSkinCsReq.newBuilder().setSkinId(1002).build()).payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.SKIN_NOT_OWNED);
    }

    @Test
    public void equip_ownedSkin_ok() throws Exception {
        Player p = new Player();
        p.setId(1L);
        p.setEquippedSkinId(1001);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));
        when(playerSkinOwnedRepository.existsByPlayerIdAndSkinId(eq(1L), eq(1002))).thenReturn(true);
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        EquipSkinScRsp rsp = EquipSkinScRsp.parseFrom(
                skinService.handleEquipSkin(1L, EquipSkinCsReq.newBuilder().setSkinId(1002).build()).payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getEquippedSkinId()).isEqualTo(1002);
        verify(playerRepository).save(any(Player.class));
    }
}
