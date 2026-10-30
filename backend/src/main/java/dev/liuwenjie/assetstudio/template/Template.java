package dev.liuwenjie.assetstudio.template;

/** 模板行，与 template 表一一对应。acceptedMimeTypes 在库里是逗号分隔字符串。 */
public record Template(
        String id,
        String title,
        String category,
        String coverUrl,
        String description,
        int requiredImageCount,
        String acceptedMimeTypes,
        long maxFileSizeBytes,
        int sortOrder) {
}