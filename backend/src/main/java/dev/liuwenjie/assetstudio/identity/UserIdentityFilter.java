package dev.liuwenjie.assetstudio.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 匿名身份：没有登录流程，靠一个 HttpOnly 的 uid Cookie 认人。
 * 任何 /api 请求进来时确保 Cookie 存在、用户行存在，并把 userId 挂到 request attribute 上供控制器读取。
 */
@Component
public class UserIdentityFilter extends OncePerRequestFilter {

    public static final String USER_ID_ATTRIBUTE = UserIdentityFilter.class.getName() + ".userId";
    public static final String COOKIE_NAME = "uid";

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);

    private final UserService userService;

    public UserIdentityFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 静态资源与探活之外的所有 API 都要身份；/api/health 也走这里没有副作用
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = readUserId(request);
        boolean freshlyIssued = userId == null;
        if (freshlyIssued) {
            userId = UUID.randomUUID().toString();
        }

        userService.ensure(userId);
        request.setAttribute(USER_ID_ATTRIBUTE, userId);

        if (freshlyIssued) {
            writeCookie(response, userId);
        }
        chain.doFilter(request, response);
    }

    /** 显式轮换身份：切换用户时前端调用，服务端换发新 uid。 */
    public String rotate(HttpServletResponse response) {
        String userId = UUID.randomUUID().toString();
        userService.ensure(userId);
        writeCookie(response, userId);
        return userId;
    }

    private String readUserId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value != null && UUID_PATTERN.matcher(value).matches() ? value : null;
            }
        }
        return null;
    }

    private void writeCookie(HttpServletResponse response, String userId) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, userId)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(COOKIE_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}