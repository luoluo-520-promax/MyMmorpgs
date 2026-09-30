package cn.itcast.demo.mymmorpg.security;

public final class AdminApiAuthHeaders {

    public static final String USER_ID = "X-Admin-User-Id";
    public static final String TIMESTAMP = "X-Admin-Timestamp";
    public static final String SIGNATURE = "X-Admin-Signature";
    public static final String API_KEY = "X-Admin-Api-Key";

    private AdminApiAuthHeaders() {
    }
}
