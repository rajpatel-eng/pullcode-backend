package com.capstoneproject.codereviewsystem.services.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;


@Slf4j
@Service
@Profile({"local", "default"})   // active when no profile set, or profile=local
public class LocalStorageProvider implements StorageProvider {

    @Value("${app.storage.local.base-path:uploads}")
    private String basePath;

    @Value("${app.storage.local.public-url:http://localhost:8080}")
    private String publicBaseUrl;


    private Path resolve(String relativePath) {
        return Paths.get(basePath, relativePath.replace("/", File.separator));
    }


    @Override
    public void saveText(String relativePath, String content) throws IOException {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("Saved text → {}", target);
    }

    @Override
    public void saveFile(String relativePath, MultipartFile file) throws IOException {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        file.transferTo(target.toFile());
        log.debug("Saved file → {}", target);
    }

    @Override
    public void saveBytes(String relativePath, byte[] bytes) throws IOException {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("Saved bytes ({} B) → {}", bytes.length, target);
    }


    @Override
    public boolean exists(String relativePath) throws IOException {
        Path p = resolve(relativePath);
        if (!Files.exists(p)) return false;
        if (Files.isDirectory(p)) {
            String[] children = p.toFile().list();
            return children != null && children.length > 0;
        }
        return true;
    }

    @Override
    public InputStream openStream(String relativePath) throws IOException {
        return Files.newInputStream(resolve(relativePath));
    }

    @Override
    public String readText(String relativePath) throws IOException {
        return Files.readString(resolve(relativePath));
    }


    @Override
    public void copyDirectory(String sourcePath, String targetPath) throws IOException {
        Path src = resolve(sourcePath);
        Path tgt = resolve(targetPath);

        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(tgt.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.copy(file, tgt.resolve(src.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
        log.info("Copied directory {} → {}", src, tgt);
    }


    @Override
    public void deleteFile(String relativePath) throws IOException {
        Path p = resolve(relativePath);
        Files.deleteIfExists(p);
        log.debug("Deleted file: {}", p);
    }

    @Override
    public void deleteDirectory(String relativePath) throws IOException {
        Path root = resolve(relativePath);
        if (!Files.exists(root)) return;

        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); }
                    catch (IOException e) { log.warn("Could not delete: {}", p); }
                });
        log.info("Deleted directory: {}", root);
    }


    @Override
    public String getPublicUrl(String relativePath) {
        String urlPath = relativePath.replace(File.separator, "/");
        return publicBaseUrl.stripTrailing() + "/" + urlPath;
    }
}