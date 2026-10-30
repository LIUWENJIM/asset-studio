package dev.liuwenjie.assetstudio.job;

import java.util.List;

/** 提交制作任务的请求体。 */
public record JobSubmitRequest(String templateId, List<String> assetIds) {
}