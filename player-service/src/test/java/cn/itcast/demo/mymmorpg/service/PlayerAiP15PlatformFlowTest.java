package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.ai.platform.AiPlatformFacade;
import cn.itcast.demo.mymmorpg.client.AiPlatformClient;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * player-service 侧 P15 路由：本地 Facade 模式覆盖分析师 / 伙伴 / 路点 / 人格。
 */
public class PlayerAiP15PlatformFlowTest {

    private PlayerAiPlatformService service;
    private AiPlatformFacade facade;

    @BeforeMethod
    public void setUp() {
        facade = AiPlatformFacade.createDefault();
        service = new PlayerAiPlatformService(false,
                new FixedProvider<>(facade),
                new FixedProvider<>(null));
    }

    @Test
    public void playerApis_analystCompanionVisionPersona() {
        long playerId = 15_200L;

        Map<String, Object> style = service.personaSetStyle(playerId, "简洁");
        assertThat(style.get("ok")).isEqualTo(true);
        assertThat(style.get("style")).isEqualTo("CONCISE");

        Map<String, Object> dialogue = service.companionDialogue(playerId, Map.of(
                "text", "我们继续走吧", "mood", "CALM"));
        assertThat(dialogue.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> bubble = (Map<String, Object>) dialogue.get("dialogue");
        assertThat(bubble.get("msgId")).isEqualTo(MessageId.COMPANION_DIALOGUE_SC_NOTIFY);

        String rid = String.valueOf(facade.battleReplays()
                .start("player-p15", 7L, System.currentTimeMillis()).get("replayId"));
        facade.battleReplays().append(rid, 1, 50, "SKILL_BURST", Map.of("castDelaySec", 1.0));
        Map<String, Object> feedback = service.analystFeedback(playerId, Map.of(
                "replayId", rid,
                "applyEnhanceHints", true,
                "abyssStars", 12,
                "regionExplorePercent", 80,
                "handbookProgress", 0.9));
        assertThat(feedback.get("ok")).isEqualTo(true);
        assertThat(feedback.get("mistakeTimeline")).isNotNull();

        Map<String, Object> vision = service.screenWaypointHelp(playerId, Map.of(
                "regionId", "1",
                "visualTags", List.of("statue"),
                "x", 95, "y", 0, "z", 95,
                "nowMs", System.currentTimeMillis()));
        assertThat(vision.get("ok")).isEqualTo(true);

        Map<String, Object> tactical = service.tacticalAdvise(playerId, Map.of(
                "bossHpRatio", 0.3,
                "partyHpRatio", 0.9,
                "partyEnergyRatio", 0.8,
                "abyssStars", 12,
                "regionExplorePercent", 80,
                "handbookProgress", 0.9));
        assertThat(tactical.get("ok")).isEqualTo(true);
        assertThat(tactical.get("personaCluster")).isEqualTo("HARDCORE");
    }

    @Test
    public void remoteFallback_usesFeignWhenEnabled() {
        AiPlatformClient stub = new AiPlatformClient() {
            @Override
            public Map<String, Object> recommend(Map<String, Object> body) {
                return Map.of();
            }

            @Override
            public Map<String, Object> recordBehavior(Map<String, Object> body) {
                return Map.of();
            }

            @Override
            public Map<String, Object> profile(long playerId) {
                return Map.of();
            }

            @Override
            public Map<String, Object> supportAsk(Map<String, Object> body) {
                return Map.of();
            }

            @Override
            public Map<String, Object> tacticalAdvise(Map<String, Object> body) {
                return Map.of();
            }

            @Override
            public Map<String, Object> retentionEvaluate(Map<String, Object> body) {
                return Map.of();
            }

            @Override
            public Map<String, Object> contentGenerate(Map<String, Object> body) {
                return Map.of();
            }

            @Override
            public Map<String, Object> contentValidate(Map<String, Object> body) {
                return Map.of();
            }

            @Override
            public Map<String, Object> infer(Map<String, Object> body) {
                return Map.of();
            }

            @Override
            public Map<String, Object> health() {
                return Map.of();
            }

            @Override
            public Map<String, Object> narrativeGenerate(Map<String, Object> body) {
                return Map.of("ok", true, "source", "remote");
            }

            @Override
            public Map<String, Object> companionDialogue(Map<String, Object> body) {
                return Map.of("ok", true, "source", "remote-companion");
            }

            @Override
            public Map<String, Object> analystFeedback(Map<String, Object> body) {
                return Map.of("ok", true, "source", "remote-analyst");
            }

            @Override
            public Map<String, Object> screenWaypoint(Map<String, Object> body) {
                return Map.of("ok", true, "source", "remote-vision");
            }

            @Override
            public Map<String, Object> personaStyle(Map<String, Object> body) {
                return Map.of("ok", true, "source", "remote-persona", "style", "DETAILED");
            }
        };

        PlayerAiPlatformService remoteSvc = new PlayerAiPlatformService(true,
                new FixedProvider<>(facade),
                new FixedProvider<>(stub));
        assertThat(remoteSvc.companionDialogue(1L, Map.of("text", "hi")).get("source"))
                .isEqualTo("remote-companion");
        assertThat(remoteSvc.analystFeedback(1L, Map.of("replayId", "x")).get("source"))
                .isEqualTo("remote-analyst");
        assertThat(remoteSvc.screenWaypointHelp(1L, Map.of()).get("source"))
                .isEqualTo("remote-vision");
        assertThat(remoteSvc.personaSetStyle(1L, "详细").get("source"))
                .isEqualTo("remote-persona");
    }

    private static final class FixedProvider<T> implements ObjectProvider<T> {
        private final T value;

        FixedProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getObject() {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfAvailable(Supplier<T> defaultSupplier) {
            return value != null ? value : defaultSupplier.get();
        }

        @Override
        public void ifAvailable(Consumer<T> dependencyConsumer) {
            if (value != null) {
                dependencyConsumer.accept(value);
            }
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getIfUnique(Supplier<T> defaultSupplier) {
            return value != null ? value : defaultSupplier.get();
        }

        @Override
        public void ifUnique(Consumer<T> dependencyConsumer) {
            if (value != null) {
                dependencyConsumer.accept(value);
            }
        }

        @Override
        public Stream<T> stream() {
            return value == null ? Stream.empty() : Stream.of(value);
        }

        @Override
        public Stream<T> orderedStream() {
            return stream();
        }

        @Override
        public Iterator<T> iterator() {
            return stream().iterator();
        }
    }
}
