package dev.liuwenjie.assetstudio;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Map;
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
 * B1/B2/B3 的端到端冒烟测试：H2 + Flyway 真实建表灌种子，走真实 HTTP 接口。
 * 覆盖匿名身份、模板筛选、素材上传落盘与 /files 回读、以及主要错误分支。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BackendSmokeTest {

    /** 1x1 的 PNG，最小的合法图片字节。 */
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

    @Test
    void sessionIssuesAnonymousIdentityAndCookie() {
        ResponseEntity<Map> response = rest.getForEntity("/api/session", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("userId")).asString()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(response.getBody().get("displayName")).asString().startsWith("创作者 ");

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("uid=").contains("HttpOnly");
    }

    @Test
    void sameCookieResolvesToSameUser() {
        ResponseEntity<Map> first = rest.getForEntity("/api/session", Map.class);
        String cookie = first.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).isNotNull();
        String uid = cookie.substring(cookie.indexOf("uid=") + 4, cookie.indexOf(';'));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "uid=" + uid);
        ResponseEntity<Map> second = rest.exchange("/api/session", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().get("userId")).isEqualTo(first.getBody().get("userId"));
        // 已有有效 Cookie 时不应重复下发
        assertThat(second.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void listsAllTemplatesWithParsedMimeTypes() {
        ResponseEntity<List> response = rest.getForEntity("/api/templates", List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(6);

        Map<?, ?> postcard = (Map<?, ?>) response.getBody().get(0);
        assertThat(postcard.get("id")).isEqualTo("travel-postcard");
        assertThat(postcard.get("requiredImageCount")).isEqualTo(2);
        assertThat(((List<?>) postcard.get("acceptedMimeTypes")).stream().map(String::valueOf).toList())
                .containsExactly("image/jpeg", "image/png");
    }

    @Test
    void filtersTemplatesByCategory() {
        ResponseEntity<List> response = rest.getForEntity(
                URI.create("/api/templates?category=" + encode("创意图片")), List.class);

        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).allSatisfy(item ->
                assertThat(((Map<?, ?>) item).get("category")).isEqualTo("创意图片"));
    }

    @Test
    void exposesDistinctCategories() {
        ResponseEntity<List> response = rest.getForEntity("/api/templates/categories", List.class);

        assertThat(response.getBody()).containsExactlyInAnyOrder("创意图片", "品牌海报", "社交封面", "电商素材");
    }

    @Test
    void unknownTemplateReturns404WithBusinessCode() {
        ResponseEntity<Map> response = rest.getForEntity("/api/templates/not-exist", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("TEMPLATE_NOT_FOUND");
        assertThat(response.getBody().get("field")).isEqualTo("template");
    }

    @Test
    void uploadsPngAndServesItBack() {
        String cookie = issueCookie();

        ResponseEntity<Map> upload = postImage(cookie, "cat.png", MediaType.IMAGE_PNG, PNG_BYTES);
        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(upload.getBody()).isNotNull();
        assertThat(upload.getBody().get("assetId")).asString().isNotBlank();
        String url = (String) upload.getBody().get("url");
        assertThat(url).startsWith("/files/assets/").endsWith(".png");

        // 关键：素材必须能从服务端地址读回，而不是只在前端有预览
        ResponseEntity<byte[]> served = rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), byte[].class);
        assertThat(served.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(served.getBody()).isEqualTo(PNG_BYTES);
    }

    @Test
    void rejectsUnsupportedMimeType() {
        String cookie = issueCookie();

        ResponseEntity<Map> upload = postImage(cookie, "note.txt", MediaType.TEXT_PLAIN,
                "not an image".getBytes());

        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload.getBody()).isNotNull();
        assertThat(upload.getBody().get("code")).isEqualTo("ASSET_TYPE_NOT_SUPPORTED");
    }

    private String issueCookie() {
        ResponseEntity<Map> session = rest.getForEntity("/api/session", Map.class);
        String setCookie = session.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return "uid=" + setCookie.substring(setCookie.indexOf("uid=") + 4, setCookie.indexOf(';'));
    }

    private ResponseEntity<Map> postImage(String cookie, String filename, MediaType type, byte[] bytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE, cookie);
        return rest.postForEntity("/api/assets", new HttpEntity<>(body, headers), Map.class);
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}