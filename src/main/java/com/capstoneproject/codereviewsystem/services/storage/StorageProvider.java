package com.capstoneproject.codereviewsystem.services.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;


public interface StorageProvider {

    // ── Write ──────────────────────────────────────────────────────────────

    /**
     * Store text content at the given relative path.
     * Creates intermediate directories automatically.
     */
    void saveText(String relativePath, String content) throws IOException;

    /**
     * Store a raw file (e.g. uploaded zip, avatar image) at the given path.
     */
    void saveFile(String relativePath, MultipartFile file) throws IOException;

    /**
     * Store raw bytes at the given path.
     */
    void saveBytes(String relativePath, byte[] bytes) throws IOException;

    // ── Read ───────────────────────────────────────────────────────────────

    /**
     * Returns true if the given path exists and is non-empty.
     */
    boolean exists(String relativePath) throws IOException;

    /**
     * Open an InputStream for the given path.
     */
    InputStream openStream(String relativePath) throws IOException;

    /**
     * Read the full content of a text file.
     */
    String readText(String relativePath) throws IOException;

    // ── Copy / Move ────────────────────────────────────────────────────────

    /**
     * Copy everything under {@code sourcePath} into {@code targetPath}.
     */
    void copyDirectory(String sourcePath, String targetPath) throws IOException;

    // ── Delete ─────────────────────────────────────────────────────────────

    /**
     * Delete a single file at the given path. No-op if absent.
     */
    void deleteFile(String relativePath) throws IOException;

    /**
     * Recursively delete a directory and all contents. No-op if absent.
     */
    void deleteDirectory(String relativePath) throws IOException;

    // ── URL ────────────────────────────────────────────────────────────────

    /**
     * Returns a publicly accessible URL for the given path.
     * For local storage this is an HTTP path served by Spring's resource handler.
     * For cloud storage this is a signed URL or a CDN URL.
     */
    String getPublicUrl(String relativePath);
}