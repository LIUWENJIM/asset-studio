package dev.liuwenjie.assetstudio.job;

/** 后台执行阶段，按声明顺序串行推进；任一步失败即停在该步。 */
public enum JobStage {
    ASSET_CHECK("素材检查"),
    GENERATING("模拟生成"),
    FINALIZING("结果整理");

    private final String label;

    JobStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}