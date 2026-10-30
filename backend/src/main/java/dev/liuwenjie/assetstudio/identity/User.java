package dev.liuwenjie.assetstudio.identity;

import java.time.LocalDateTime;

/** 一个匿名用户。id 来自 Cookie 中的 uid，不涉及任何登录凭据。 */
public record User(String id, String displayName, LocalDateTime createdAt) {
}