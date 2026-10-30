-- 任务受理时快照当时的模拟失败阶段：之后改全局模拟配置不影响已受理的任务
ALTER TABLE job ADD COLUMN sim_fail_stage VARCHAR(16);