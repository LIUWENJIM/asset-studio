package dev.liuwenjie.assetstudio.template;

import java.util.List;

/** 对外返回的模板视图：把逗号分隔的 MIME 拆成数组，其余字段透传。 */
public record TemplateResponse(
        String id,
        String title,
        String category,
        String coverUrl,
        String description,
        int requiredImageCount,
        List<String> acceptedMimeTypes,
        long maxFileSizeBytes) {

    static TemplateResponse from(Template template) {
        List<String> mimeTypes = template.acceptedMimeTypes() == null || template.acceptedMimeTypes().isBlank()
                ? List.of()
                : List.of(template.acceptedMimeTypes().split(",")).stream()
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
        return new TemplateResponse(
                template.id(),
                template.title(),
                template.category(),
                template.coverUrl(),
                template.description(),
                template.requiredImageCount(),
                mimeTypes,
                template.maxFileSizeBytes());
    }
}