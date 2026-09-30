package cn.itcast.demo.mymmorpg.web;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Range 断点续传：完整下载与字节区间切片。
 */
public class ResourceRangeDownloadControllerTest {

    private Path root;
    private ResourceRangeDownloadController controller;

    @BeforeMethod
    public void setUp() throws Exception {
        root = Files.createTempDirectory("update-range-test");
        Path file = root.resolve("packs/big.bin");
        Files.createDirectories(file.getParent());
        Files.write(file, "0123456789ABCDEF".getBytes());
        controller = new ResourceRangeDownloadController(root.toString());
    }

    @Test
    public void download_fullBodyWhenNoRange() throws Exception {
        ResponseEntity<Resource> rsp = controller.download("packs/big.bin", null);
        assertThat(rsp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rsp.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(rsp.getHeaders().getContentLength()).isEqualTo(16L);
        assertThat(rsp.getBody().contentLength()).isEqualTo(16L);
    }

    @Test
    public void download_partialContentForRange() throws Exception {
        ResponseEntity<Resource> rsp = controller.download("packs/big.bin", "bytes=0-3");
        assertThat(rsp.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(rsp.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-3/16");
        assertThat(rsp.getHeaders().getContentLength()).isEqualTo(4L);
        assertThat(rsp.getBody().getInputStream().readAllBytes()).isEqualTo("0123".getBytes());
    }

    @Test
    public void download_rejectsPathEscape() throws Exception {
        ResponseEntity<Resource> rsp = controller.download("../secret.bin", null);
        assertThat(rsp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
