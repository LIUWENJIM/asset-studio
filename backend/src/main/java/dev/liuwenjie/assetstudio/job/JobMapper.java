package dev.liuwenjie.assetstudio.job;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface JobMapper {

    String COLUMNS = "id, user_id, template_id, template_title, template_cover_url, status, stage, result_url, "
            + "failure_stage, failure_reason, sim_fail_stage, created_at, updated_at, finished_at";

    @Select("SELECT " + COLUMNS + " FROM job WHERE id = #{id}")
    Job findById(@Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM job WHERE user_id = #{userId} ORDER BY created_at DESC, id DESC")
    List<Job> findByUser(@Param("userId") String userId);

    @Insert("INSERT INTO job (id, user_id, template_id, template_title, template_cover_url, status, stage, "
            + "result_url, failure_stage, failure_reason, sim_fail_stage, created_at, updated_at, finished_at) "
            + "VALUES (#{id}, #{userId}, #{templateId}, #{templateTitle}, #{templateCoverUrl}, #{status}, #{stage}, "
            + "#{resultUrl}, #{failureStage}, #{failureReason}, #{simFailStage}, #{createdAt}, #{updatedAt}, "
            + "#{finishedAt})")
    int insert(@Param("id") String id, @Param("userId") String userId,
               @Param("templateId") String templateId, @Param("templateTitle") String templateTitle,
               @Param("templateCoverUrl") String templateCoverUrl, @Param("status") JobStatus status,
               @Param("stage") JobStage stage, @Param("resultUrl") String resultUrl,
               @Param("failureStage") JobStage failureStage, @Param("failureReason") String failureReason,
               @Param("simFailStage") JobStage simFailStage, @Param("createdAt") LocalDateTime createdAt,
               @Param("updatedAt") LocalDateTime updatedAt, @Param("finishedAt") LocalDateTime finishedAt);

    @Insert("INSERT INTO job_asset (job_id, asset_id, position) VALUES (#{jobId}, #{assetId}, #{position})")
    int insertAsset(@Param("jobId") String jobId, @Param("assetId") String assetId,
                    @Param("position") int position);

    /** 素材在磁盘上的相对路径，按用户提交顺序返回，结果图按这个顺序排版。 */
    @Select("SELECT a.stored_path FROM job_asset ja JOIN asset a ON a.id = ja.asset_id "
            + "WHERE ja.job_id = #{jobId} ORDER BY ja.position")
    List<String> findStoredPaths(@Param("jobId") String jobId);

    /**
     * 推进到下一个阶段。带上 status 条件是为了让「已完成的任务」永远不会被改回处理中。
     * 返回 0 表示任务已经不在可推进状态（并发或已结束），调用方应停止。
     */
    @Update("UPDATE job SET status = #{status}, stage = #{stage}, updated_at = #{updatedAt} "
            + "WHERE id = #{id} AND status IN ('PENDING', 'PROCESSING')")
    int beginStage(@Param("id") String id, @Param("status") JobStatus status,
                   @Param("stage") JobStage stage, @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE job SET status = 'SUCCEEDED', stage = NULL, result_url = #{resultUrl}, "
            + "updated_at = #{now}, finished_at = #{now} "
            + "WHERE id = #{id} AND status IN ('PENDING', 'PROCESSING')")
    int markSucceeded(@Param("id") String id, @Param("resultUrl") String resultUrl,
                      @Param("now") LocalDateTime now);

    /**
     * 失败时保留当前 stage 不动，另外记录 failure_stage，这样查询能同时看到「停在哪一步」和「哪一步失败」。
     */
    @Update("UPDATE job SET status = 'FAILED', failure_stage = #{failureStage}, failure_reason = #{failureReason}, "
            + "updated_at = #{now}, finished_at = #{now} "
            + "WHERE id = #{id} AND status IN ('PENDING', 'PROCESSING')")
    int markFailed(@Param("id") String id, @Param("failureStage") JobStage failureStage,
                   @Param("failureReason") String failureReason, @Param("now") LocalDateTime now);

    /** 进程重启时把上一轮残留的进行中任务收尾，不做续跑。 */
    @Update("UPDATE job SET status = 'FAILED', failure_stage = 'SERVICE_RESTART', "
            + "failure_reason = #{reason}, updated_at = #{now}, finished_at = #{now} "
            + "WHERE status IN ('PENDING', 'PROCESSING')")
    int failStale(@Param("reason") String reason, @Param("now") LocalDateTime now);
}