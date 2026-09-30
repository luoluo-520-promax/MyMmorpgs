package cn.itcast.demo.mymmorpg.support;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class InMemorySessionKeyStore implements SessionKeyStore {

    private final Map<String, byte[]> keys = new ConcurrentHashMap<>();

    @Override
    public void put(String sessionId, byte[] keyBytes) {
        keys.put(sessionId, keyBytes);
    }

    @Override
    public byte[] get(String sessionId) {
        return keys.get(sessionId);
    }

    @Override
    public void remove(String sessionId) {
        keys.remove(sessionId);
    }
}
