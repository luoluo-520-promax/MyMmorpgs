package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.UploadStorageProperties;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class UploadStorageServiceTest {

    @Test
    public void mergeChunksWhenAllReceived() throws Exception {
        Path tempDir = Files.createTempDirectory("upload-test");
        UploadStorageProperties props = new UploadStorageProperties();
        props.setStorageDir(tempDir.toString());
        UploadStorageService service = new UploadStorageService(props);

        UploadStorageService.UploadResult r1 = service.storeChunk("file1", 0, 2, "hello ".getBytes());
        assertThat(r1.completed()).isFalse();
        assertThat(r1.receivedChunks()).isEqualTo(1);

        UploadStorageService.UploadResult r2 = service.storeChunk("file1", 1, 2, "world".getBytes());
        assertThat(r2.completed()).isTrue();
        assertThat(Files.readString(Path.of(r2.filePath()))).isEqualTo("hello world");
    }
}
