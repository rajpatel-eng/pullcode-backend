package com.capstoneproject.codereviewsystem.services.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;


public interface StorageProvider {

    void saveText(String relativePath, String content) throws IOException;

    void saveFile(String relativePath, MultipartFile file) throws IOException;

    void saveBytes(String relativePath, byte[] bytes) throws IOException;

    boolean exists(String relativePath) throws IOException;

    InputStream openStream(String relativePath) throws IOException;

    String readText(String relativePath) throws IOException;

    void copyDirectory(String sourcePath, String targetPath) throws IOException;

    void deleteFile(String relativePath) throws IOException;

    void deleteDirectory(String relativePath) throws IOException;

    String getPublicUrl(String relativePath);
}