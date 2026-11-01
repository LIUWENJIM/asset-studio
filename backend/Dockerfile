# syntax=docker/dockerfile:1

# ---- 构建阶段：Maven 打可执行 jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 默认走 Maven Central；国内网络可在 .env 里把 MAVEN_MIRROR 指向镜像站。
ARG MAVEN_MIRROR=
RUN if [ -n "$MAVEN_MIRROR" ]; then \
      mkdir -p /root/.m2 && \
      printf '%s\n' \
        '<settings>' \
        '  <mirrors>' \
        '    <mirror>' \
        '      <id>custom</id>' \
        "      <url>$MAVEN_MIRROR</url>" \
        '      <mirrorOf>*</mirrorOf>' \
        '    </mirror>' \
        '  </mirrors>' \
        '</settings>' > /root/.m2/settings.xml; \
    fi
# 先只拷贝 pom，依赖层可复用
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -Dmaven.test.skip=true package

# ---- 运行阶段：精简 JRE ----
# 不安装任何系统包：结果图里的说明文字在缺少字体时会自动跳过，
# 图片合成本身不依赖字体，因此镜像可以保持最小、构建也更快。
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN mkdir -p /app/data && chown -R 10001:10001 /app
COPY --from=build /build/target/*.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true" \
    STORAGE_ROOT=/app/data

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]