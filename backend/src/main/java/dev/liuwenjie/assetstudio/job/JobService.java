package dev.liuwenjie.assetstudio.job;

import dev.liuwenjie.assetstudio.asset.Asset;
import dev.liuwenjie.assetstudio.asset.AssetService;
import dev.liuwenjie.assetstudio.common.ApiException;
import dev.liuwenjie.assetstudio.common.ErrorCode;
import dev.liuwenjie.assetstudio.simulation.SimulationSettings;
import dev.liuwenjie.assetstudio.template.Template;
import dev.liuwenjie.assetstudio.template.TemplateService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 任务受理。校验全部做完、记录落库之后立刻返回，真正的制作交给后台线程。
 */
@Service
public class JobService {

    private final JobMapper jobMapper;
    private final TemplateService templateService;
    private final AssetService assetService;
    private final SimulationSettings simulationSettings;
    private final JobDispatcher jobDispatcher;

    public JobService(JobMapper jobMapper,
                      TemplateService templateService,
                      AssetService assetService,
                      SimulationSettings simulationSettings,
                      JobDispatcher jobDispatcher) {
        this.jobMapper = jobMapper;
        this.templateService = templateService;
        this.assetService = assetService;
        this.simulationSettings = simulationSettings;
        this.jobDispatcher = jobDispatcher;
    }

    @Transactional
    public Job submit(String userId, JobSubmitRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "请求体不能为空", null);
        }
        String templateId = request.templateId();
        if (templateId == null || templateId.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "缺少模板 ID", "templateId");
        }
        List<String> assetIds = request.assetIds();
        if (assetIds == null || assetIds.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "请先上传图片", "assetIds");
        }

        Template template = templateService.get(templateId.trim());

        if (assetIds.size() != template.requiredImageCount()) {
            throw new ApiException(ErrorCode.ASSET_COUNT_MISMATCH,
                    "该模板需要 " + template.requiredImageCount() + " 张图片，当前提交 " + assetIds.size() + " 张",
                    "assetIds");
        }
        Set<String> distinct = new LinkedHashSet<>(assetIds);
        if (distinct.size() != assetIds.size()) {
            throw new ApiException(ErrorCode.ASSET_COUNT_MISMATCH, "同一张图片不能重复使用，请上传不同的图片", "assetIds");
        }

        List<String> accepted = splitMimeTypes(template.acceptedMimeTypes());
        for (String assetId : assetIds) {
            Asset asset = assetService.requireOwned(userId, assetId);
            if (!accepted.isEmpty() && !accepted.contains(asset.mimeType())) {
                throw new ApiException(ErrorCode.ASSET_TYPE_NOT_SUPPORTED,
                        "该模板只接受 " + describe(accepted) + " 格式", "assetIds");
            }
            if (asset.sizeBytes() > template.maxFileSizeBytes()) {
                throw new ApiException(ErrorCode.ASSET_TOO_LARGE,
                        "单张图片不能超过 " + megabytes(template.maxFileSizeBytes()) + " MB", "assetIds");
            }
        }

        String jobId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        jobMapper.insert(jobId, userId, template.id(), template.title(), template.coverUrl(),
                JobStatus.PENDING, null, null, null, null,
                // 失败阶段在此快照：之后改全局模拟配置不会影响已受理的任务
                simulationSettings.failStage(), now, now, null);
        for (int position = 0; position < assetIds.size(); position++) {
            jobMapper.insertAsset(jobId, assetIds.get(position), position);
        }

        Job job = jobMapper.findById(jobId);
        // 必须等事务提交后再交给线程池，否则后台线程可能查不到刚插入的行
        dispatchAfterCommit(jobId);
        return job;
    }

    public List<Job> list(String userId) {
        return jobMapper.findByUser(userId);
    }

    public Job get(String userId, String jobId) {
        Job job = jobMapper.findById(jobId);
        if (job == null || !job.userId().equals(userId)) {
            // 不区分「不存在」和「不属于你」，避免泄露别人的任务 ID
            throw new ApiException(ErrorCode.JOB_NOT_FOUND, "任务不存在：" + jobId, "jobId");
        }
        return job;
    }

    private void dispatchAfterCommit(String jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    jobDispatcher.dispatch(jobId);
                }
            });
        } else {
            jobDispatcher.dispatch(jobId);
        }
    }

    private static List<String> splitMimeTypes(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split(",")) {
            String value = part.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private static String describe(List<String> mimeTypes) {
        List<String> names = new ArrayList<>(mimeTypes.size());
        for (String mimeType : mimeTypes) {
            names.add(switch (mimeType) {
                case "image/jpeg" -> "JPG";
                case "image/png" -> "PNG";
                case "image/webp" -> "WebP";
                default -> mimeType;
            });
        }
        return String.join("、", names);
    }

    private static String megabytes(long bytes) {
        double mb = (double) bytes / (1024 * 1024);
        return mb == Math.rint(mb) ? String.valueOf((long) mb) : String.format(Locale.ROOT, "%.1f", mb);
    }
}