package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.UploadStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 加密上传分片本地存储：按 fileId 写入分片，末片到达后合并为完整文件。
 */
@Service
public class UploadStorageService {

    private static final Logger log = LoggerFactory.getLogger(UploadStorageService.class);

    private final Path baseDir;
    private final Map<String, Integer> receivedChunks = new ConcurrentHashMap<>();

    public UploadStorageService(UploadStorageProperties properties) throws IOException {
        this.baseDir = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
        Files.createDirectories(baseDir);
        Files.createDirectories(baseDir.resolve("chunks"));
        Files.createDirectories(baseDir.resolve("merged"));
    }

    public UploadResult storeChunk(String fileId, int chunkIndex, int totalChunks, byte[] data) throws IOException {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("fileId 不能为空");
        }
        if (totalChunks <= 0 || chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new IllegalArgumentException("分片参数非法");
        }
        String safeId = sanitize(fileId);
        Path chunkFile = baseDir.resolve("chunks").resolve(safeId + "_" + chunkIndex);
        Files.write(chunkFile, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        String progressKey = safeId + ":" + totalChunks;
        receivedChunks.merge(progressKey, 1, Integer::sum);
        int received = receivedChunks.getOrDefault(progressKey, 0);

        if (received < totalChunks) {
            return new UploadResult(false, received, totalChunks, null, chunkFile.toString());
        }

        Path merged = mergeChunks(safeId, totalChunks);
        receivedChunks.remove(progressKey);
        cleanupChunks(safeId, totalChunks);
        log.info("文件合并完成 fileId={} path={} size={}", safeId, merged, Files.size(merged));
        return new UploadResult(true, totalChunks, totalChunks, merged.toString(), null);
    }

    private Path mergeChunks(String safeId, int totalChunks) throws IOException {
        Path merged = baseDir.resolve("merged").resolve(safeId);
        Files.deleteIfExists(merged);
        for (int i = 0; i < totalChunks; i++) {
            Path chunk = baseDir.resolve("chunks").resolve(safeId + "_" + i);
            if (!Files.exists(chunk)) {
                throw new IOException("缺少分片 index=" + i);
            }
            Files.write(merged, Files.readAllBytes(chunk), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        return merged;
    }

    private void cleanupChunks(String safeId, int totalChunks) throws IOException {
        for (int i = 0; i < totalChunks; i++) {
            Files.deleteIfExists(baseDir.resolve("chunks").resolve(safeId + "_" + i));
        }
    }

    private static String sanitize(String fileId) {
        return fileId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record UploadResult(boolean completed, int receivedChunks, int totalChunks, String filePath, String chunkPath) {
    }
}
