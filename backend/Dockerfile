# syntax=docker/dockerfile:1

# ---- 构建阶段：Maven 打可执行 jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 先只拷贝 pom，依赖层可复用
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -Dmaven.test.skip=true package

# ---- 运行阶段：精简 JRE ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# ResultImageRenderer 用 Java2D 合成结果图，需要 fontconfig 与一套基础字体，
# 否则容器里没有可用字体、绘制文字会失败。
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --uid 10001 app \
    && mkdir -p /app/data \
    && chown -R app:app /app

COPY --from=build /build/target/*.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true" \
    STORAGE_ROOT=/app/data

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]