package cn.itcast.demo.mymmorpg.gm;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 实时 GM 指令管道：绕过游戏协议，直接注入场景/世界权威。
 */
public final class GmCommandDispatcher {

    public enum Level {
        OBSERVER,
        MODERATOR,
        ADMIN,
        SUPER
    }

    public record GmCommand(
            String name,
            Level minLevel,
            String description,
            Function<Map<String, Object>, Map<String, Object>> handler) {
    }

    private final Map<String, GmCommand> commands = new LinkedHashMap<>();

    public void register(GmCommand command) {
        Objects.requireNonNull(command, "command");
        commands.put(command.name().toLowerCase(Locale.ROOT), command);
    }

    public Map<String, Object> dispatch(String name, Level operatorLevel, Map<String, Object> args) {
        if (name == null || name.isBlank()) {
            return Map.of("ok", false, "error", "empty_command");
        }
        GmCommand cmd = commands.get(name.trim().toLowerCase(Locale.ROOT));
        if (cmd == null) {
            return Map.of("ok", false, "error", "unknown_command", "name", name);
        }
        Level level = operatorLevel == null ? Level.OBSERVER : operatorLevel;
        if (level.ordinal() < cmd.minLevel().ordinal()) {
            return Map.of("ok", false, "error", "permission_denied",
                    "required", cmd.minLevel().name(), "actual", level.name());
        }
        try {
            Map<String, Object> result = cmd.handler().apply(args == null ? Map.of() : args);
            if (result == null) {
                return Map.of("ok", true, "command", cmd.name());
            }
            Map<String, Object> body = new LinkedHashMap<>(result);
            body.putIfAbsent("ok", true);
            body.put("command", cmd.name());
            return body;
        } catch (Exception e) {
            return Map.of("ok", false, "error", "handler_failed", "message", e.getMessage());
        }
    }

    public Map<String, Object> catalog() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        Map<String, Object> cmds = new LinkedHashMap<>();
        for (GmCommand c : commands.values()) {
            cmds.put(c.name(), Map.of(
                    "minLevel", c.minLevel().name(),
                    "description", c.description()));
        }
        out.put("commands", cmds);
        return out;
    }
}
