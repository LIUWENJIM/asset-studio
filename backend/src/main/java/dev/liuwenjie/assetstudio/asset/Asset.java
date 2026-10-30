package dev.liuwenjie.assetstudio.asset;

import java.time.LocalDateTime;

/** 一次上传的素材。storedPath 是相对存储根目录的路径，对外通过 /files/ 前缀访问。 */
public record Asset(
        String id,
        String userId,
        String originalName,
        String storedPath,
        String mimeType,
        long sizeBytes,
        LocalDateTime createdAt) {
}