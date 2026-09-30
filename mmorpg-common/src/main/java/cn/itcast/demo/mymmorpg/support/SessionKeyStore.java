package cn.itcast.demo.mymmorpg.support;

interface SessionKeyStore {

    void put(String sessionId, byte[] keyBytes);

    byte[] get(String sessionId);

    void remove(String sessionId);
}
