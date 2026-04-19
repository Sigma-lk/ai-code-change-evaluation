package com.sigma.ai.evaluation.domain.index.service.impl;

import com.sigma.ai.evaluation.domain.index.service.FileWalkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * 文件扫描服务实现，基于 NIO Files.walk 递归遍历 .java 文件。
 */
@Slf4j
@Service
public class FileWalkerServiceImpl implements FileWalkerService {

    @Override
    public List<Path> walkJavaFiles(Path rootDir) {
        if (!Files.isDirectory(rootDir)) {
            log.warn("walkJavaFiles: 目录不存在或不是目录, path={}", rootDir);
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.walk(rootDir)) {
            List<Path> result = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
            log.info("扫描完成，共发现 {} 个 .java 文件，rootDir={}", result.size(), rootDir);
            return result;
        } catch (IOException e) {
            log.error("walkJavaFiles 异常, rootDir={}", rootDir, e);
            return Collections.emptyList();
        }
    }

    @Override
    public String computeChecksum(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // 由于包装了DigestInputStream，所以只需要读取即可更新md5
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("computeChecksum 异常, file={}", file, e);
            return "";
        }
    }
}
