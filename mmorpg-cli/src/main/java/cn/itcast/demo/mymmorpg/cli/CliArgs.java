package cn.itcast.demo.mymmorpg.cli;

import java.util.HashMap;
import java.util.Map;

/**
 * 命令行参数解析工具：支持 --key value 与 --key=value 两种形式。
 */
final class CliArgs {

    private CliArgs() {
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String keyValue = arg.substring(2);
            int eq = keyValue.indexOf('=');
            if (eq >= 0) {
                map.put(keyValue.substring(0, eq), keyValue.substring(eq + 1));
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                map.put(keyValue, args[++i]);
            } else {
                map.put(keyValue, "true");
            }
        }
        return map;
    }

    static int getInt(Map<String, String> args, String key, int defaultValue) {
        String value = args.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    static long getLong(Map<String, String> args, String key, long defaultValue) {
        String value = args.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    static String getString(Map<String, String> args, String key, String defaultValue) {
        String value = args.get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
