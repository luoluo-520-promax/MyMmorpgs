package cn.itcast.demo.mymmorpg.security;

public final class InternalApiAuthHeaders {

    public static final String PLAYER_ID = "X-Player-Id";
    public static final String TIMESTAMP = "X-Internal-Timestamp";
    public static final String SIGNATURE = "X-Internal-Signature";

    private InternalApiAuthHeaders() {
    }
}
