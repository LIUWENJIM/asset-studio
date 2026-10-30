package dev.liuwenjie.assetstudio.job;

import dev.liuwenjie.assetstudio.identity.CurrentUserId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /** 受理即返回，不等制作结束。前端拿到 id 后立刻开始跟踪。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse submit(@CurrentUserId String userId, @RequestBody JobSubmitRequest request) {
        return JobResponse.from(jobService.submit(userId, request));
    }

    /** 一次拿到当前用户全部任务，轮询只需要这一个接口。 */
    @GetMapping
    public List<JobResponse> list(@CurrentUserId String userId) {
        return jobService.list(userId).stream().map(JobResponse::from).toList();
    }

    @GetMapping("/{id}")
    public JobResponse get(@CurrentUserId String userId, @PathVariable("id") String id) {
        return JobResponse.from(jobService.get(userId, id));
    }
}