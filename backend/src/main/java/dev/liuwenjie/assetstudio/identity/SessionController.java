package dev.liuwenjie.assetstudio.identity;

import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;

/** 当前身份与切换身份。前端顶部用户区与 Account 弹窗用这两个接口。 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final UserService userService;
    private final UserIdentityFilter identityFilter;

    public SessionController(UserService userService, UserIdentityFilter identityFilter) {
        this.userService = userService;
        this.identityFilter = identityFilter;
    }

    public record SessionResponse(String userId, String displayName, LocalDateTime createdAt) {
        static SessionResponse from(User user) {
            return new SessionResponse(user.id(), user.displayName(), user.createdAt());
        }
    }

    @GetMapping
    public SessionResponse current(@CurrentUserId String userId) {
        return SessionResponse.from(userService.ensure(userId));
    }

    /** 演示用：换一个 uid，相当于换了一个人，个人记录随之隔离。 */
    @PostMapping("/switch")
    public SessionResponse switchUser(HttpServletResponse response) {
        String newUserId = identityFilter.rotate(response);
        return SessionResponse.from(userService.ensure(newUserId));
    }
}