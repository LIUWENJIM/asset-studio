package dev.liuwenjie.assetstudio.simulation;

import dev.liuwenjie.assetstudio.job.JobStage;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 运行时可改的模拟参数。开发接口改这里，后台执行器每进入一个阶段都重新读取，
 * 因此「运行中调快/调慢」对后续阶段立即生效，方便演示。
 * <p>
 * 失败阶段不在这里被读取——它在任务受理时快照进 job.sim_fail_stage，
 * 这样切换配置不会影响已经受理的任务。
 */
@Component
public class SimulationSettings {

    private volatile long assetCheckMs;
    private volatile long generatingMs;
    private volatile long finalizingMs;
    private volatile JobStage failStage;

    public SimulationSettings(SimulationProperties properties) {
        this.assetCheckMs = properties.assetCheckMs();
        this.generatingMs = properties.generatingMs();
        this.finalizingMs = properties.finalizingMs();
        this.failStage = parseStage(properties.failStage());
    }

    public long assetCheckMs() {
        return assetCheckMs;
    }

    public long generatingMs() {
        return generatingMs;
    }

    public long finalizingMs() {
        return finalizingMs;
    }

    public JobStage failStage() {
        return failStage;
    }

    public long durationOf(JobStage stage) {
        return switch (stage) {
            case ASSET_CHECK -> assetCheckMs;
            case GENERATING -> generatingMs;
            case FINALIZING -> finalizingMs;
        };
    }

    public void update(Long assetCheckMs, Long generatingMs, Long finalizingMs, String failStage) {
        if (assetCheckMs != null) {
            this.assetCheckMs = clamp(assetCheckMs);
        }
        if (generatingMs != null) {
            this.generatingMs = clamp(generatingMs);
        }
        if (finalizingMs != null) {
            this.finalizingMs = clamp(finalizingMs);
        }
        if (failStage != null) {
            this.failStage = parseStage(failStage);
        }
    }

    /** 0 表示「不模拟失败」；其余按枚举名解析，大小写不敏感。 */
    public static JobStage parseStage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if ("NONE".equals(value)) {
            return null;
        }
        try {
            return JobStage.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知阶段：" + raw + "，可选 NONE / ASSET_CHECK / GENERATING / FINALIZING");
        }
    }

    private static long clamp(long value) {
        return Math.max(0, Math.min(value, 60_000));
    }
}