package cn.itcast.demo.mymmorpg.protocol;

/**
 * 客户端版本/资源更新专用返回码。
 */
public final class UpdateRetCode {

    public static final int OK = 0;
    public static final int VERSION_NOT_FOUND = 1;
    public static final int FORCE_UPDATE_REQUIRED = 2;
    public static final int CHECKSUM_MISMATCH = 3;
    public static final int MANIFEST_INVALID = 4;

    private UpdateRetCode() {
    }
}
