# =========================================================
# 前端 Vue3+Vite 多阶段分层构建
# 依赖层(package.json) 与 源码层(src) 分离：仅改业务代码不会重装依赖
# 淘宝 npmmirror 镜像 + 严格缓存控制
# 禁止使用 # syntax=docker/dockerfile:* 语法（避免远程拉取 frontend）
# =========================================================

# ARG 必须在 FROM 之前声明，docker-compose 通过 build.args 注入
# BUILDER_IMAGE / RUNTIME_IMAGE 由 .env 中 DOCKER_REGISTRY + 镜像名拼接（在 docker-compose.yml 中完成）
ARG BUILDER_IMAGE
ARG RUNTIME_IMAGE

# ---------- Stage 1: 编译构建 ----------
FROM ${BUILDER_IMAGE} AS builder
WORKDIR /app

# 1) 仅 COPY package.json/package-lock.json（若存在），依赖不变则命中缓存
COPY package.json ./
COPY package-lock.json* ./

# 2) 淘宝镜像源 + 依赖安装层（package.json 不变则命中 Docker 缓存，不会重复安装）
RUN npm config set registry https://registry.npmmirror.com \
    && npm install --no-audit --no-fund

# 3) 仅业务源码变更才会触发以下 COPY + 构建，不再触发依赖安装
COPY . .
RUN npm run build

# ---------- Stage 2: Nginx 静态部署 ----------
FROM ${RUNTIME_IMAGE}
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
