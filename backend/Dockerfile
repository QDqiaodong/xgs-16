# =========================================================
# 后端 SpringBoot 多阶段分层构建
# 依赖层(pom.xml) 与 源码层(src) 分离：仅改业务代码不会重下依赖
# 阿里云 Maven 镜像 + 严格缓存控制
# 禁止使用 # syntax=docker/dockerfile:* 语法（避免远程拉取 frontend）
# =========================================================

# ARG 必须在 FROM 之前声明，docker-compose 通过 build.args 注入
# BASE_IMAGE 由 .env 中 DOCKER_REGISTRY + MAVEN_IMAGE 拼接（在 docker-compose.yml 中完成）
ARG BUILDER_IMAGE
ARG RUNTIME_IMAGE

# ---------- Stage 1: 编译构建 ----------
FROM ${BUILDER_IMAGE} AS builder
WORKDIR /app

# 1) 先单独 COPY settings.xml + pom.xml，仅依赖变更时才重建此层
COPY settings.xml /usr/share/maven/conf/settings.xml
COPY pom.xml ./

# 2) 依赖预下载层（pom.xml 不变则命中 Docker 缓存，不会重复下载）
RUN mvn compile -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true -B -q

# 3) 仅业务源码变更才会触发以下 COPY + 编译，不再触发依赖下载
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -B

# ---------- Stage 2: 运行时（精简镜像）----------
FROM ${RUNTIME_IMAGE}
WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/workspace-manage.jar app.jar

EXPOSE 8016
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=docker"]
