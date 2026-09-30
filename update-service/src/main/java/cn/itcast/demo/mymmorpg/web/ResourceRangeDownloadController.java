package cn.itcast.demo.mymmorpg.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 资源下载：支持 HTTP Range 断点续传，便于客户端分片并发拉取。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "update-service")
@RequestMapping("/internal/update/download")
public class ResourceRangeDownloadController {

    private final Path resourceRoot;

    public ResourceRangeDownloadController(
            @Value("${game.update.resource-root:./data/update-resources}") String resourceRoot) {
        this.resourceRoot = Paths.get(resourceRoot).toAbsolutePath().normalize();
    }

    @GetMapping
    public ResponseEntity<Resource> download(
            @RequestParam("path") String relativePath,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws IOException {
        Path file = resourceRoot.resolve(relativePath).normalize();
        if (!file.startsWith(resourceRoot) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        long fileSize = Files.size(file);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"");

        if (rangeHeader == null || rangeHeader.isBlank()) {
            headers.setContentLength(fileSize);
            return ResponseEntity.ok().headers(headers).body(new FileSystemResource(file));
        }

        List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
        if (ranges.isEmpty()) {
            headers.setContentLength(fileSize);
            return ResponseEntity.ok().headers(headers).body(new FileSystemResource(file));
        }
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(fileSize);
        long end = range.getRangeEnd(fileSize);
        long len = end - start + 1;
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize);
        headers.setContentLength(len);

        byte[] slice = new byte[(int) len];
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(start);
            raf.readFully(slice);
        }
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .body(new org.springframework.core.io.ByteArrayResource(slice));
    }
}
