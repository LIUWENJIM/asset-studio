package dev.liuwenjie.assetstudio.simulation;

import dev.liuwenjie.assetstudio.common.ApiException;
import dev.liuwenjie.assetstudio.common.ErrorCode;
import dev.liuwenjie.assetstudio.job.JobStage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发期模拟控制接口：调整各阶段耗时、指定某个阶段固定失败。
 * 不参与正式页面；可用 simulation.dev-api-enabled=false 关掉。
 */
@RestController
@RequestMapping("/api/dev/simulation")
@ConditionalOnProperty(name = "simulation.dev-api-enabled", havingValue = "true", matchIfMissing = true)
public class SimulationController {

    private final SimulationSettings settings;

    public SimulationController(SimulationSettings settings) {
        this.settings = settings;
    }

    @GetMapping
    public SimulationView current() {
        return view();
    }

    /** 字段为 null 表示保持不变；failStage 传 "NONE" 表示恢复为「不模拟失败」。 */
    @PutMapping
    public SimulationView update(@RequestBody SimulationUpdate update) {
        try {
            settings.update(update.assetCheckMs(), update.generatingMs(),
                    update.finalizingMs(), update.failStage());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, e.getMessage(), "failStage");
        }
        return view();
    }

    private SimulationView view() {
        JobStage failStage = settings.failStage();
        return new SimulationView(
                failStage == null ? "NONE" : failStage.name(),
                settings.assetCheckMs(),
                settings.generatingMs(),
                settings.finalizingMs());
    }

    public record SimulationView(String failStage, long assetCheckMs, long generatingMs, long finalizingMs) {
    }

    public record SimulationUpdate(String failStage, Long assetCheckMs, Long generatingMs, Long finalizingMs) {
    }
}