package dev.liuwenjie.assetstudio.identity;

import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 用户只做「按需创建」：第一次带着新 uid 的请求进来时建一行，
 * 之后所有业务都靠这个 id 做归属隔离。
 */
@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User ensure(String userId) {
        User existing = userMapper.findById(userId);
        if (existing != null) {
            return existing;
        }
        String displayName = "创作者 " + userId.substring(0, 4).toUpperCase();
        try {
            userMapper.insert(userId, displayName, LocalDateTime.now());
        } catch (DuplicateKeyException ignored) {
            // 并发首请求，另一路已经插入，读回即可
        }
        return userMapper.findById(userId);
    }
}