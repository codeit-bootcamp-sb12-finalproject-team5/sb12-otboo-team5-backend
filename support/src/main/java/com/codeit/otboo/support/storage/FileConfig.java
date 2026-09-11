package com.codeit.otboo.support.storage;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileConfig {

    @Value("${otboo.storage.local.root-path:.otboo/storage}")
    private String rootPathStr;

    @Getter
    private Path rootPath;

    @PostConstruct
    public void init() throws IOException {
        rootPath = Paths.get(rootPathStr).toAbsolutePath();
        // attachments 하위 폴더까지 미리 생성
        Files.createDirectories(rootPath.resolve("attachments"));
    }
}
