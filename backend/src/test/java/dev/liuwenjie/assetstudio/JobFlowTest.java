package dev.liuwenjie.assetstudio;

import static org.assertj.core.api.Assertions.assertThat;

import dev.liuwenjie.assetstudio.simulation.SimulationSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * B4 的核心行为测试：受理即返回、三阶段按序推进、指定阶段失败后中断、失败快照按任务隔离、
 * 查询不推进任务、终态不回退、跨用户不可见。
 * 这些用例直接决定 R-04 / R-05 / R-06 / R-08 是否成立。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JobFlowTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
            0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89, 0x00, 0x00, 0x00,
            0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00,
            0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private SimulationSettings simulationSettings;

    /** 模拟配置是单例，用例之间必须复位，否则会互相污染。 */
    @AfterEach
    void resetSimulation() {
        simulationSettings.update(10L, 10L, 10L, "NONE");
    }

    @Test
    void submitReturnsImmediatelyThenSucceedsWithViewableResult() {
        String cookie = issueCookie();
        List<String> assetIds = upload(cookie, 2);

        ResponseEntity<Map> created = submitJob(cookie, "travel-postcard", assetIds);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> accepted = created.getBody();
        assertThat(accepted).isNotNull();
        assertThat(accepted.get("id")).asString().isNotBlank();
        // 受理返回值必须是「已受理」而不是「已做完」
        assertThat(accepted.get("status")).isIn("PENDING", "PROCESSING");

        Map<?, ?> finished = waitForTerminal(cookie, (String) accepted.get("id"), 15_000);
        assertThat(finished.get("status")).isEqualTo("SUCCEEDED");
        assertThat(finished.get("stage")).isNull();
        assertThat(finished.get("failureStage")).isNull();

        String resultUrl = (String) finished.get("resultUrl");
        assertThat(resultUrl).startsWith("/files/results/").endsWith(".png");

        ResponseEntity<byte[]> image = rest.exchange(resultUrl, HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), byte[].class);
        assertThat(image.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(image.getBody()).isNotEmpty();
    }

    @Test
    void stagesAdvanceInDeclaredOrder() {
        String cookie = issueCookie();
        List<String> assetIds = upload(cookie, 1);
        configure(400L, "NONE");

        Map<?, ?> created = submitJob(cookie, "film-portrait", assetIds).getBody();
        List<String> observed = trackStages(cookie, (String) created.get("id"), 20_000);

        assertThat(observed).containsExactly("ASSET_CHECK", "GENERATING", "FINALIZING");
    }

    @Test
    void failsAtConfiguredStageAndStopsRemainingSteps() {
        String cookie = issueCookie();
        List<String> assetIds = upload(cookie, 1);
        configure(80L, "GENERATING");

        Map<?, ?> created = submitJob(cookie, "film-portrait", assetIds).getBody();
        List<String> observed = trackStages(cookie, (String) created.get("id"), 15_000);
        Map<?, ?> finished = waitForTerminal(cookie, (String) created.get("id"), 15_000);

        assertThat(finished.get("status")).isEqualTo("FAILED");
        assertThat(finished.get("failureStage")).isEqualTo("GENERATING");
        assertThat(finished.get("failureReason")).asString().contains("模拟失败");
        // 失败即停：没有产物、没有走到结果整理
        assertThat(finished.get("resultUrl")).isNull();
        assertThat(observed).doesNotContain("FINALIZING");
        // stage 保留在失败的那一步，前端因此能显示「停在哪」
        assertThat(finished.get("stage")).isEqualTo("GENERATING");
    }

    @Test
    void failureSettingIsSnapshotPerJob() {
        String cookie = issueCookie();
        List<String> assetIds = upload(cookie, 1);

        configure(10L, "ASSET_CHECK");
        Map<?, ?> doomed = submitJob(cookie, "film-portrait", assetIds).getBody();

        // 后来者恢复成「不模拟失败」，不应影响已受理的那一条
        configure(10L, "NONE");
        Map<?, ?> healthy = submitJob(cookie, "film-portrait", assetIds).getBody();

        Map<?, ?> doomedResult = waitForTerminal(cookie, (String) doomed.get("id"), 15_000);
        Map<?, ?> healthyResult = waitForTerminal(cookie, (String) healthy.get("id"), 15_000);

        assertThat(doomedResult.get("status")).isEqualTo("FAILED");
        assertThat(doomedResult.get("failureStage")).isEqualTo("ASSET_CHECK");
        assertThat(healthyResult.get("status")).isEqualTo("SUCCEEDED");
    }

    @Test
    void pollingDoesNotAdvanceQueuedJob() {
        String cookie = issueCookie();
        List<String> assetIds = upload(cookie, 1);
        configure(400L, "NONE");

        Map<?, ?> first = submitJob(cookie, "film-portrait", assetIds).getBody();
        Map<?, ?> second = submitJob(cookie, "film-portrait", assetIds).getBody();

        // 线程池只有 1 条线程，第二条此刻一定还在排队
        Map<?, ?> queued = getJob(cookie, (String) second.get("id"));
        assertThat(queued.get("status")).isEqualTo("PENDING");
        assertThat(queued.get("stage")).isNull();

        // 反复查询 500ms，第一条仍在跑（3 x 400ms），第二条必须原封不动
        for (int i = 0; i < 5; i++) {
            sleep(100);
            Map<?, ?> again = getJob(cookie, (String) second.get("id"));
            assertThat(again.get("status")).isEqualTo("PENDING");
            assertThat(again.get("stage")).isNull();
        }

        assertThat(waitForTerminal(cookie, (String) first.get("id"), 20_000).get("status"))
                .isEqualTo("SUCCEEDED");
        assertThat(waitForTerminal(cookie, (String) second.get("id"), 20_000).get("status"))
                .isEqualTo("SUCCEEDED");
    }

    @Test
    void finishedJobNeverRevertsToProcessing() {
        String cookie = issueCookie();
        List<String> assetIds = upload(cookie, 1);

        Map<?, ?> created = submitJob(cookie, "film-portrait", assetIds).getBody();
        Map<?, ?> finished = waitForTerminal(cookie, (String) created.get("id"), 15_000);

        sleep(500);
        Map<?, ?> reread = getJob(cookie, (String) created.get("id"));

        assertThat(reread.get("status")).isEqualTo("SUCCEEDED");
        assertThat(reread.get("finishedAt")).isEqualTo(finished.get("finishedAt"));
        assertThat(reread.get("resultUrl")).isEqualTo(finished.get("resultUrl"));
    }

    @Test
    void rejectsAssetCountMismatch() {
        String cookie = issueCookie();
        List<String> oneImage = upload(cookie, 1);

        // travel-postcard 要求 2 张
        ResponseEntity<Map> response = submitJob(cookie, "travel-postcard", oneImage);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("ASSET_COUNT_MISMATCH");
        assertThat(response.getBody().get("field")).isEqualTo("assetIds");
    }

    @Test
    void rejectsUnknownTemplate() {
        String cookie = issueCookie();
        List<String> assetIds = upload(cookie, 1);

        ResponseEntity<Map> response = submitJob(cookie, "no-such-template", assetIds);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("TEMPLATE_NOT_FOUND");
    }

    @Test
    void rejectsAnotherUsersAsset() {
        String owner = issueCookie();
        String intruder = issueCookie();
        List<String> someoneElses = upload(owner, 1);

        ResponseEntity<Map> response = submitJob(intruder, "film-portrait", someoneElses);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("ASSET_NOT_OWNED");
    }

    @Test
    void otherUserCannotReadOrListJob() {
        String owner = issueCookie();
        String other = issueCookie();
        List<String> assetIds = upload(owner, 1);
        String jobId = (String) submitJob(owner, "film-portrait", assetIds).getBody().get("id");
        waitForTerminal(owner, jobId, 15_000);

        ResponseEntity<Map> direct = rest.exchange("/api/jobs/" + jobId, HttpMethod.GET,
                new HttpEntity<>(headers(other)), Map.class);
        assertThat(direct.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(direct.getBody().get("code")).isEqualTo("JOB_NOT_FOUND");

        ResponseEntity<List> list = rest.exchange("/api/jobs", HttpMethod.GET,
                new HttpEntity<>(headers(other)), List.class);
        assertThat(list.getBody()).isEmpty();
    }

    @Test
    void devApiRejectsUnknownStage() {
        ResponseEntity<Map> response = rest.exchange("/api/dev/simulation", HttpMethod.PUT,
                new HttpEntity<>(Map.of("failStage", "NOT_A_STAGE"), jsonHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
    }

    // ---------- helpers ----------

    private void configure(long stageMs, String failStage) {
        Map<String, Object> body = Map.of(
                "failStage", failStage,
                "assetCheckMs", stageMs,
                "generatingMs", stageMs,
                "finalizingMs", stageMs);
        ResponseEntity<Map> response = rest.exchange("/api/dev/simulation", HttpMethod.PUT,
                new HttpEntity<>(body, jsonHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String issueCookie() {
        ResponseEntity<Map> session = rest.getForEntity("/api/session", Map.class);
        String setCookie = session.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return "uid=" + setCookie.substring(setCookie.indexOf("uid=") + 4, setCookie.indexOf(';'));
    }

    private List<String> upload(String cookie, int count) {
        List<String> assetIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            String filename = "shot-" + i + ".png";
            body.add("file", new ByteArrayResource(PNG_BYTES) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.add(HttpHeaders.COOKIE, cookie);
            ResponseEntity<Map> response = rest.postForEntity("/api/assets",
                    new HttpEntity<>(body, headers), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assetIds.add((String) response.getBody().get("assetId"));
        }
        return assetIds;
    }

    private ResponseEntity<Map> submitJob(String cookie, String templateId, List<String> assetIds) {
        Map<String, Object> body = Map.of("templateId", templateId, "assetIds", assetIds);
        return rest.exchange("/api/jobs", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders(cookie)), Map.class);
    }

    private Map<?, ?> getJob(String cookie, String jobId) {
        ResponseEntity<Map> response = rest.exchange("/api/jobs/" + jobId, HttpMethod.GET,
                new HttpEntity<>(headers(cookie)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<?, ?> waitForTerminal(String cookie, String jobId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Map<?, ?> job = getJob(cookie, jobId);
        while (System.currentTimeMillis() < deadline) {
            Object status = job.get("status");
            if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
                return job;
            }
            sleep(50);
            job = getJob(cookie, jobId);
        }
        throw new AssertionError("任务 " + jobId + " 在 " + timeoutMs + "ms 内没有结束，最后状态："
                + job.get("status") + " / " + job.get("stage"));
    }

    /** 轮询期间收集出现过的阶段，用来证明后台确实按顺序走过每一步。 */
    private List<String> trackStages(String cookie, String jobId, long timeoutMs) {
        List<String> stages = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutMs;
        Map<?, ?> job = getJob(cookie, jobId);
        while (System.currentTimeMillis() < deadline) {
            Object stage = job.get("stage");
            if (stage != null) {
                String value = String.valueOf(stage);
                if (stages.isEmpty() || !stages.get(stages.size() - 1).equals(value)) {
                    stages.add(value);
                }
            }
            Object status = job.get("status");
            if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
                return stages;
            }
            sleep(60);
            job = getJob(cookie, jobId);
        }
        throw new AssertionError("任务 " + jobId + " 在 " + timeoutMs + "ms 内没有结束，阶段轨迹：" + stages);
    }

    private HttpHeaders headers(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return headers;
    }

    private HttpHeaders jsonHeaders() {
        return jsonHeaders(null);
    }

    private HttpHeaders jsonHeaders(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookie != null) {
            headers.add(HttpHeaders.COOKIE, cookie);
        }
        return headers;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}