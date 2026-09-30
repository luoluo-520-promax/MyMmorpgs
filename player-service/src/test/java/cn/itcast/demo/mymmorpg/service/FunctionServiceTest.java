package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.FunctionBoxStore;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.entity.PlayerQuestProgress;
import cn.itcast.demo.mymmorpg.model.ConfigFunction;
import cn.itcast.demo.mymmorpg.model.FunctionBox;
import cn.itcast.demo.mymmorpg.model.FunctionOpenType;
import cn.itcast.demo.mymmorpg.repository.PlayerQuestProgressRepository;
import jforgame.commons.eventbus.EventBus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FunctionServiceTest {

    private FunctionConfigService configService;
    private FunctionBoxStore boxStore;
    private EventBus eventBus;
    private PlayerQuestProgressRepository questProgressRepository;
    private FunctionService functionService;
    private FunctionBox box;

    @BeforeMethod
    public void setUp() {
        configService = mock(FunctionConfigService.class);
        boxStore = mock(FunctionBoxStore.class);
        eventBus = mock(EventBus.class);
        questProgressRepository = mock(PlayerQuestProgressRepository.class);
        box = new FunctionBox();
        when(boxStore.load(anyLong())).thenReturn(box);
        functionService = new FunctionService(configService, boxStore, eventBus, questProgressRepository);
    }

    @Test
    public void checkOpen_questCompleted_unlocksFunction() {
        ConfigFunction func = new ConfigFunction();
        func.setId(201);
        func.setOpenType(FunctionOpenType.Quest.getType());
        func.setOpenMainParam(1001);
        when(configService.queryByOpenType(FunctionOpenType.Quest.getType())).thenReturn(List.of(func));

        PlayerQuestProgress progress = new PlayerQuestProgress();
        progress.setPlayerId(9L);
        progress.setQuestId(1001);
        progress.setStatus(2);
        when(questProgressRepository.findByPlayerIdAndQuestId(9L, 1001)).thenReturn(Optional.of(progress));

        Player player = new Player();
        player.setId(9L);
        player.setLevel(1);

        functionService.checkOpen(player, FunctionOpenType.Quest.getType());

        assertThat(box.isOpened(201)).isTrue();
        verify(boxStore).save(eq(9L), any(FunctionBox.class));
        verify(eventBus).publish(any());
    }

    @Test
    public void checkOpen_questIncomplete_doesNotUnlock() {
        ConfigFunction func = new ConfigFunction();
        func.setId(202);
        func.setOpenType(FunctionOpenType.Quest.getType());
        func.setOpenMainParam(1002);
        when(configService.queryByOpenType(FunctionOpenType.Quest.getType())).thenReturn(List.of(func));
        when(questProgressRepository.findByPlayerIdAndQuestId(9L, 1002)).thenReturn(Optional.empty());

        Player player = new Player();
        player.setId(9L);

        functionService.checkOpen(player, FunctionOpenType.Quest.getType());

        assertThat(box.isOpened(202)).isFalse();
    }
}
