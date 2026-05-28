package com.capstoneproject.codereviewsystem.services.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@Profile("cloud")
public class CloudinaryStorageProvider implements StorageProvider {

    private final Cloudinary cloudinary;

    @Value("${app.storage.cloudinary.cloud-name}")
    private String cloudName;

    public CloudinaryStorageProvider(
            @Value("${app.storage.cloudinary.cloud-name}") String cloudName,
            @Value("${app.storage.cloudinary.api-key}") String apiKey,
            @Value("${app.storage.cloudinary.api-secret}") String apiSecret) {

        this.cloudName = cloudName;
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true // always use https
        ));
        log.info("CloudinaryStorageProvider initialised (cloud: {})", cloudName);
    }


    private String toPublicId(String relativePath) {
        int dot = relativePath.lastIndexOf('.');
        return dot > 0 ? relativePath.substring(0, dot) : relativePath;
    }

    private String resourceType(String relativePath) {
        String lower = relativePath.toLowerCase();
        if (lower.startsWith("avatars/")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".webp")) {
            return "image";
        }
        return "raw"; // zip, text, source files, etc.
    }


    @Override
    public void saveText(String relativePath, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        saveBytes(relativePath, bytes);
    }

    @Override
    public void saveFile(String relativePath, MultipartFile file) throws IOException {
        String publicId = toPublicId(relativePath);
        String resType = resourceType(relativePath);

        Map<?, ?> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "public_id", publicId,
                        "resource_type", resType,
                        "overwrite", true));
        log.debug("Cloudinary upload → public_id={} url={}", publicId, result.get("secure_url"));
    }

    @Override
    public void saveBytes(String relativePath, byte[] bytes) throws IOException {
        String publicId = toPublicId(relativePath);
        String resType = resourceType(relativePath);

        Map<?, ?> result = cloudinary.uploader().upload(
                bytes,
                ObjectUtils.asMap(
                        "public_id", publicId,
                        "resource_type", resType,
                        "overwrite", true));
        log.debug("Cloudinary upload bytes → public_id={} url={}", publicId, result.get("secure_url"));
    }


    @Override
    public boolean exists(String relativePath) throws IOException {
        try {
            String publicId = toPublicId(relativePath);
            String resType = resourceType(relativePath);
            cloudinary.api().resource(publicId,
                    ObjectUtils.asMap("resource_type", resType));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public InputStream openStream(String relativePath) throws IOException {
        String url = getPublicUrl(relativePath);
        return URI.create(url).toURL().openStream();
    }

    @Override
    public String readText(String relativePath) throws IOException {
        try (InputStream is = openStream(relativePath)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }


    @Override
    public void copyDirectory(String sourcePath, String targetPath) throws IOException {
        try {
            String resType = "raw";
            Map<?, ?> result = cloudinary.api().resources(
                    ObjectUtils.asMap(
                            "type", "upload",
                            "resource_type", resType,
                            "prefix", sourcePath + "/",
                            "max_results", 500));

            @SuppressWarnings("unchecked")
            List<Map<?, ?>> resources = (List<Map<?, ?>>) result.get("resources");
            if (resources == null)
                return;

            for (Map<?, ?> resource : resources) {
                String srcPublicId = (String) resource.get("public_id");
                String destPublicId = targetPath + srcPublicId.substring(sourcePath.length());

                cloudinary.uploader().rename(srcPublicId, destPublicId,
                        ObjectUtils.asMap(
                                "resource_type", resType,
                                "overwrite", true,
                                "copy", true // keep source intact
                        ));
            }
            log.info("Cloudinary copy: {} → {}", sourcePath, targetPath);
        } catch (Exception e) {
            throw new IOException("Cloudinary copyDirectory failed: " + e.getMessage(), e);
        }
    }


    @Override
    public void deleteFile(String relativePath) throws IOException {
        try {
            String publicId = toPublicId(relativePath);
            String resType = resourceType(relativePath);
            cloudinary.uploader().destroy(publicId,
                    ObjectUtils.asMap("resource_type", resType));
            log.debug("Cloudinary deleted: {}", publicId);
        } catch (Exception e) {
            throw new IOException("Cloudinary deleteFile failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteDirectory(String relativePath) throws IOException {
        try {
            cloudinary.api().deleteResourcesByPrefix(relativePath + "/",
                    ObjectUtils.asMap("resource_type", "raw"));

            cloudinary.api().deleteResourcesByPrefix(relativePath + "/",
                    ObjectUtils.asMap("resource_type", "image"));

            log.info("Cloudinary deleted directory prefix: {}", relativePath);
        } catch (Exception e) {
            throw new IOException("Cloudinary deleteDirectory failed: " + e.getMessage(), e);
        }
    }


    @Override
    public String getPublicUrl(String relativePath) {
        String publicId = toPublicId(relativePath);
        String resType = resourceType(relativePath);
        String ext = relativePath.contains(".")
                ? relativePath.substring(relativePath.lastIndexOf('.') + 1)
                : "";
        String extSuffix = ext.isEmpty() ? "" : "." + ext;
        return String.format("https://res.cloudinary.com/%s/%s/upload/%s%s",
                cloudName, resType, publicId, extSuffix);
    }
}